/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.core.Deployment;
import org.wildfly.plugin.core.DeploymentDescription;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.core.DeploymentResult;
import org.wildfly.plugin.core.UndeployDescription;

/**
 * A deployment for standalone servers.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class MavenDeployment {

    public enum Type {
        DEPLOY("deploying"),
        FORCE_DEPLOY("deploying"),
        UNDEPLOY("undeploying"),
        UNDEPLOY_IGNORE_MISSING("undeploying"),
        REDEPLOY("redeploying");

        final String verb;

        Type(final String verb) {
            this.verb = verb;
        }
    }

    private final File content;
    private final Set<String> serverGroups;
    private final String name;
    private final String runtimeName;
    private final Type type;
    private final String matchPattern;
    private final MatchPatternStrategy matchPatternStrategy;
    private final DeploymentManager deploymentManager;

    /**
     * Creates a new deployment.
     *
     * @param client               the client that is connected.
     * @param serverGroups         the server groups to deploy to
     * @param content              the content for the deployment.
     * @param name                 the name of the deployment, if {@code null} the name of the content file is used.
     * @param runtimeName          he runtime name of the deployment
     * @param type                 the deployment type.
     * @param matchPattern         the pattern for matching multiple artifacts, if {@code null} the name is used.
     * @param matchPatternStrategy the strategy for handling multiple artifacts.
     */
    MavenDeployment(final ModelControllerClient client, final Set<String> serverGroups, final File content, final String name, final String runtimeName, final Type type,
                    final String matchPattern, final MatchPatternStrategy matchPatternStrategy) {
        this.content = content;
        this.serverGroups = serverGroups;
        this.name = (name == null ? content.getName() : name);
        this.runtimeName = runtimeName;
        this.type = type;
        this.matchPattern = matchPattern;
        this.matchPatternStrategy = matchPatternStrategy;
        deploymentManager = DeploymentManager.Factory.create(client);
    }

    /**
     * Executes the deployment
     *
     * @throws MojoDeploymentException if the deployment fails
     */
    public void execute() throws MojoDeploymentException {
        try {
            final DeploymentResult result;
            switch (type) {
                case DEPLOY: {
                    result = deploymentManager.deploy(Deployment.of(content).setName(name).setRuntimeName(runtimeName).addServerGroups(serverGroups));
                    break;
                }
                case FORCE_DEPLOY: {
                    result = deploymentManager.forceDeploy(Deployment.of(content).setName(name).setRuntimeName(runtimeName).addServerGroups(serverGroups));
                    break;
                }
                case REDEPLOY: {
                    if (!deploymentManager.hasDeployment(name)) {
                        throw new MojoDeploymentException("Deployment '%s' not found, cannot redeploy", name);
                    }
                    result = deploymentManager.redeploy(Deployment.of(content).setName(name).setRuntimeName(runtimeName).addServerGroups(serverGroups));
                    break;
                }
                case UNDEPLOY: {
                    if (matchPattern == null) {
                        result = deploymentManager.undeploy(UndeployDescription.of(name).addServerGroups(serverGroups).setFailOnMissing(true));
                    } else {
                        final Set<UndeployDescription> matchedDeployments = findDeployments(true);
                        if (matchedDeployments.isEmpty()) {
                            throw new MojoDeploymentException("No deployments matched the match-pattern %s.", matchPattern);
                        }
                        result = deploymentManager.undeploy(matchedDeployments);
                    }
                    break;
                }
                case UNDEPLOY_IGNORE_MISSING: {
                    if (matchPattern == null) {
                        result = deploymentManager.undeploy(UndeployDescription.of(name).addServerGroups(serverGroups).setFailOnMissing(false));
                    } else {
                        final Set<UndeployDescription> matchedDeployments = findDeployments(false);
                        if (matchedDeployments.isEmpty()) {
                            return;
                        }
                        result = deploymentManager.undeploy(matchedDeployments);
                    }
                    break;
                }
                default:
                    throw new IllegalArgumentException("Invalid type: " + type);
            }
            if (!result.successful()) {
                throw new MojoDeploymentException("Failed %s deployment. Reason: %s", type.verb, result.getFailureMessage());
            }
        } catch (MojoDeploymentException e) {
            throw e;
        } catch (CancellationException e) {
            throw new MojoDeploymentException(e, "Error %s %s. The operation was cancelled. This may be caused by the client being closed.", type.verb, name);
        } catch (Exception e) {
            throw new MojoDeploymentException(e, "Error %s %s", type.verb, name);
        }
    }

    /**
     * The type of the deployment.
     *
     * @return the type of the deployment.
     */
    public Type getType() {
        return type;
    }


    private Set<UndeployDescription> findDeployments(final boolean failOnMissing) throws IOException {
        if (name == null && matchPattern == null) {
            throw new IllegalArgumentException("deploymentName and matchPattern are null. One of them must "
                    + "be set in order to find an existing deployment.");
        }

        final Set<UndeployDescription> matchedDeployments = new TreeSet<>();
        final Collection<DeploymentDescription> deployments = deploymentManager.getDeployments();
        final Pattern pattern = Pattern.compile(matchPattern);
        for (DeploymentDescription deployment : deployments) {
            boolean matchFound = false;
            final String deploymentName = deployment.getName();
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
            throw new RuntimeException(String.format("Deployment failed, found %d deployed artifacts for pattern '%s' (%s)",
                    matchedDeployments.size(), matchPattern, matchedDeployments));
        }
        return matchedDeployments;
    }
}
