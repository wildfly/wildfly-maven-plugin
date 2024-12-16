/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.tools.DeploymentManager;
import org.wildfly.plugin.tools.DeploymentResult;
import org.wildfly.plugin.tools.UndeployDescription;

/**
 * Undeploys (removes) an arbitrary artifact to the WildFly application server
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "undeploy-artifact", requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public class UndeployArtifactMojo extends AbstractServerConnection {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /**
     * The artifact to deploys groupId
     */
    @Parameter
    private String groupId;

    /**
     * The artifact to deploys artifactId
     */
    @Parameter
    private String artifactId;

    /**
     * The artifact to deploys classifier. Note that the classifier must also be set on the dependency being deployed.
     */
    @Parameter
    private String classifier;

    /**
     * Specifies the name used for the deployment.
     * <p>
     * The default name is derived from the {@code project.build.finalName} and the packaging type.
     * </p>
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_NAME)
    private String name;

    /**
     * The server groups the content should be deployed to.
     */
    @Parameter(alias = "server-groups", property = PropertyNames.SERVER_GROUPS)
    private List<String> serverGroups;

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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping undeploy of artifact %s:%s", groupId, artifactId));
            return;
        }
        if (artifactId == null) {
            throw new MojoDeploymentException("undeploy-artifact must specify the artifactId");
        }
        if (groupId == null) {
            throw new MojoDeploymentException("undeploy-artifact must specify the groupId");
        }
        final String deploymentName;
        if (name == null) {
            final Set<Artifact> dependencies = project.getArtifacts();
            Artifact artifact = null;
            for (final Artifact a : dependencies) {
                if (Objects.equals(a.getArtifactId(), artifactId) &&
                        Objects.equals(a.getGroupId(), groupId) &&
                        Objects.equals(a.getClassifier(), classifier)) {
                    artifact = a;
                    break;
                }
            }
            if (artifact == null) {
                throw new MojoDeploymentException("Could not resolve artifact to deploy %s:%s", groupId, artifactId);
            }
            deploymentName = artifact.getFile().getName();
        } else {
            deploymentName = name;
        }
        final DeploymentResult result;
        try (ModelControllerClient client = createClient()) {
            final boolean failOnMissing = !ignoreMissingDeployment;
            final DeploymentManager deploymentManager = DeploymentManager.create(client);
            result = deploymentManager.undeploy(
                    UndeployDescription.of(deploymentName).addServerGroups(getServerGroups()).setFailOnMissing(failOnMissing));
        } catch (IOException e) {
            throw new MojoFailureException(String.format("Failed to execute %s goal.", goal()), e);
        }
        if (!result.successful()) {
            throw new MojoDeploymentException("Failed to undeploy %s. Reason: %s", deploymentName, result.getFailureMessage());
        }
    }

    @Override
    public String goal() {
        return "undeploy-artifact";
    }

    private Collection<String> getServerGroups() {
        return serverGroups == null ? Collections.emptyList() : serverGroups;
    }
}
