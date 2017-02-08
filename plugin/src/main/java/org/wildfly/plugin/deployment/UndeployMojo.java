/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.plugin.deployment;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.cli.CommandExecutor;
import org.wildfly.plugin.cli.Commands;
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.core.DeploymentDescription;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.core.DeploymentResult;
import org.wildfly.plugin.core.UndeployDescription;
import org.wildfly.plugin.deployment.domain.Domain;

/**
 * Undeploys the application to the WildFly Application Server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "undeploy", threadSafe = true)
public class UndeployMojo extends AbstractServerConnection {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Specifies the configuration for a domain server.
     *
     * @deprecated use {@code <server-groups/>} property
     */
    @Parameter
    @Deprecated
    private Domain domain;

    /**
     * The server groups the content should be deployed to.
     */
    @Parameter(alias = "server-groups", property = PropertyNames.SERVER_GROUPS)
    private List<String> serverGroups;

    /**
     * Specifies the name used for the deployment.
     * <p>
     * The default name is derived from the {@code project.build.finalName} and the packaging type.
     * </p>
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_NAME)
    private String name;

    /**
     * Commands to run before the deployment
     *
     * @deprecated use the {@code execute-commands} goal
     */
    @Parameter(alias = "before-deployment")
    @Deprecated
    private Commands beforeDeployment;

    /**
     * Executions to run after the deployment
     *
     * @deprecated use the {@code execute-commands} goal
     */
    @Parameter(alias = "after-deployment")
    @Deprecated
    private Commands afterDeployment;

    /**
     * By default certain package types are ignored when processing, e.g. {@code maven-project} and {@code pom}. Set
     * this value to {@code false} if this check should be bypassed.
     */
    @Parameter(alias = "check-packaging", property = PropertyNames.CHECK_PACKAGING, defaultValue = "true")
    private boolean checkPackaging;

    /**
     * Specifies the name match pattern for undeploying/replacing artifacts.
     */
    @Parameter(alias = "match-pattern")
    private String matchPattern;

    /**
     * Specifies the strategy in case more than one matching artifact is found.
     * <ul>
     * <li>first: The first artifact is taken for undeployment/replacement. Other artifacts won't be touched.
     * The list of artifacts is sorted using the default collator.</li>
     * <li>all: All matching artifacts are undeployed.</li>
     * <li>fail: Deployment fails.</li>
     * </ul>
     */
    @Parameter(alias = "match-pattern-strategy")
    private String matchPatternStrategy = MatchPatternStrategy.FAIL.toString();

    /**
     * Indicates whether undeploy should ignore the undeploy operation if the deployment does not exist.
     */
    @Parameter(defaultValue = "true", property = PropertyNames.IGNORE_MISSING_DEPLOYMENT)
    private boolean ignoreMissingDeployment;

    /**
     * Set to {@code true} if you want the deployment to be skipped, otherwise {@code false}.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.SKIP)
    private boolean skip;

    @Inject
    private CommandExecutor commandExecutor;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping undeploy of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        final PackageType packageType = PackageType.resolve(project);
        // Configure the name if it wasn't yet set
        if (name == null) {
            name = String.format("%s.%s", project.getBuild().getFinalName(), packageType.getFileExtension());
        }
        if (checkPackaging && packageType.isIgnored()) {
            getLog().debug(String.format("Ignoring packaging type %s.", packageType.getPackaging()));
        } else {
            final DeploymentResult result;
            try (ModelControllerClient client = createClient()) {
                if (beforeDeployment != null) {
                    commandExecutor.execute(client, beforeDeployment);
                }
                final boolean failOnMissing = !ignoreMissingDeployment;
                final DeploymentManager deploymentManager = DeploymentManager.Factory.create(client);
                if (matchPattern == null) {
                    result = deploymentManager.undeploy(UndeployDescription.of(name).addServerGroups(getServerGroups()).setFailOnMissing(failOnMissing));
                } else {
                    final Set<UndeployDescription> matchedDeployments = findDeployments(deploymentManager, failOnMissing);
                    if (matchedDeployments.isEmpty()) {
                        if (failOnMissing) {
                            throw new MojoDeploymentException("No deployments matched the match-pattern %s.", matchPattern);
                        }
                        // nothing to undeploy
                        return;
                    }
                    result = deploymentManager.undeploy(matchedDeployments);
                }
                if (afterDeployment != null) {
                    commandExecutor.execute(client, afterDeployment);
                }
            } catch (IOException e) {
                throw new MojoFailureException("Failed to execute undeploy goal.", e);
            }
            if (!result.successful()) {
                throw new MojoDeploymentException("Failed to undeploy %s. Reason: %s", name, result.getFailureMessage());
            }
        }
    }

    @Override
    public String goal() {
        return "undeploy";
    }


    private Set<UndeployDescription> findDeployments(final DeploymentManager deploymentManager, final boolean failOnMissing) throws IOException, MojoDeploymentException {
        if (name == null && matchPattern == null) {
            throw new IllegalArgumentException("deploymentName and matchPattern are null. One of them must "
                    + "be set in order to find an existing deployment.");
        }
        final MatchPatternStrategy matchPatternStrategy = getMatchPatternStrategy();

        final Set<UndeployDescription> matchedDeployments = new TreeSet<>();
        final Collection<DeploymentDescription> deployments = deploymentManager.getDeployments();
        final Pattern pattern = Pattern.compile(matchPattern);
        for (DeploymentDescription deployment : deployments) {
            boolean matchFound = false;
            final String deploymentName = deployment.getName();
            final Collection<String> serverGroups = getServerGroups();
            if (pattern.matcher(deploymentName).matches()) {
                if (serverGroups.isEmpty()) {
                    matchFound = true;
                    matchedDeployments.add(UndeployDescription.of(deploymentName).setFailOnMissing(failOnMissing));
                } else {
                    final UndeployDescription undeployDescription = UndeployDescription.of(deploymentName);
                    for (String serverGroup : serverGroups) {
                        if (deployment.getServerGroups().contains(serverGroup)) {
                            matchFound = true;
                            undeployDescription.addServerGroup(serverGroup);
                        }
                    }
                    if (matchFound) {
                        matchedDeployments.add(undeployDescription.setFailOnMissing(failOnMissing));
                    }
                }
                if (matchFound && matchPatternStrategy == MatchPatternStrategy.FIRST) {
                    break;
                }
            }
        }
        if (matchPatternStrategy == MatchPatternStrategy.FAIL && matchedDeployments.size() > 1) {
            throw new MojoDeploymentException("Deployment failed, found %d deployed artifacts for pattern '%s' (%s)",
                    matchedDeployments.size(), matchPattern, matchedDeployments);
        }
        return matchedDeployments;
    }


    private MatchPatternStrategy getMatchPatternStrategy() {
        if (MatchPatternStrategy.FAIL.toString().equalsIgnoreCase(matchPatternStrategy)) {
            return MatchPatternStrategy.FAIL;
        } else if (MatchPatternStrategy.FIRST.toString().equalsIgnoreCase(matchPatternStrategy)) {
            return MatchPatternStrategy.FIRST;
        } else if (MatchPatternStrategy.ALL.toString().equalsIgnoreCase(matchPatternStrategy)) {
            return MatchPatternStrategy.ALL;
        }
        throw new IllegalStateException(
                String.format("matchPatternStrategy '%s' is not a valid strategy. Valid strategies are %s, %s and %s",
                        matchPatternStrategy, MatchPatternStrategy.ALL, MatchPatternStrategy.FAIL, MatchPatternStrategy.FIRST));
    }

    private Collection<String> getServerGroups() {
        final Collection<String> result = new LinkedHashSet<>();
        if (domain != null) {
            result.addAll(domain.getServerGroups());
        }
        if (serverGroups != null) {
            result.addAll(serverGroups);
        }
        return result;
    }
}
