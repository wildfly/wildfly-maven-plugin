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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.cli.CommandExecutor;
import org.wildfly.plugin.cli.Commands;
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.deployment.domain.Domain;
import org.wildfly.plugin.server.ServerHelper;

/**
 * The default implementation for executing build plans on the server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author Stuart Douglas
 */
abstract class AbstractDeployment extends AbstractServerConnection {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /**
     * Specifies the configuration for a domain server.
     */
    @Parameter
    private Domain domain;

    /**
     * Specifies the name used for the deployment.
     */
    @Parameter
    protected String name;

    /**
     * The runtime name for the dployment.
     * <p>
     * In some cases users may wish to have two deployments with the same {@code runtime-name} (e.g. two versions of
     * {@code example.war}) both available in the management configuration, in which case the deployments would need to
     * have distinct {@code name} values but would have the same {@code runtime-name}.
     * </p>
     */
    @Parameter(alias = "runtime-name", property = PropertyNames.DEPLOYMENT_RUNTIME_NAME)
    protected String runtimeName;

    /**
     * The WildFly Application Server's home directory. This is not required, but should be used for commands such as
     * {@code module add} as they are executed on the local file system.
     */
    @Parameter(alias = "jboss-home", property = PropertyNames.JBOSS_HOME)
    private String jbossHome;

    /**
     * Commands to run before the deployment
     */
    @Parameter(alias = "before-deployment")
    private Commands beforeDeployment;

    /**
     * Executions to run after the deployment
     */
    @Parameter(alias = "after-deployment")
    private Commands afterDeployment;

    /**
     * Set to {@code true} if you want the deployment to be skipped, otherwise {@code false}.
     */
    @Parameter(defaultValue = "false")
    private boolean skip;

    @Inject
    private CommandExecutor commandExecutor;

    /**
     * The archive file.
     *
     * @return the archive file.
     */
    protected abstract File file();

    /**
     * The goal of the deployment.
     *
     * @return the goal of the deployment.
     */
    public abstract String goal();

    /**
     * The type of the deployment.
     *
     * @return the deployment type.
     */
    public abstract Deployment.Type getType();

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping deployment of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        doExecute();
    }

    protected final void executeDeployment(final ModelControllerClient client, final Deployment deployment, final Path wildflyHome)
            throws DeploymentException, IOException {
        // Execute before deployment commands
        if (beforeDeployment != null)
            commandExecutor.execute(client, wildflyHome, beforeDeployment);
        // Deploy the deployment
        getLog().debug("Executing deployment");
        deployment.execute();
        // Execute after deployment commands
        if (afterDeployment != null)
            commandExecutor.execute(client, wildflyHome, afterDeployment);
    }

    /**
     * Executes additional processing.
     *
     * @see #execute()
     */
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        try (final ModelControllerClient client = createClient()) {
            final boolean isDomain = ServerHelper.isDomainServer(client);
            validate(client, isDomain);
            final String matchPattern = getMatchPattern();
            final MatchPatternStrategy matchPatternStrategy = getMatchPatternStrategy();
            final DeploymentBuilder deploymentBuilder = DeploymentBuilder.of(client, (domain == null ? null : domain.getServerGroups()));
            deploymentBuilder
                    .setContent(file())
                    .setName(name)
                    .setRuntimeName(runtimeName)
                    .setType(getType())
                    .setMatchPattern(matchPattern)
                    .setMatchPatternStrategy(matchPatternStrategy);
            final Path wildflyHome = jbossHome == null ? null : Paths.get(jbossHome);
            executeDeployment(client, deploymentBuilder.build(), wildflyHome);
        } catch (MojoExecutionException e) {
            throw e;
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Please make sure a server is running before executing goal " +
                    "%s on deployment %s. Reason: %s", goal(), file(), e.getMessage()), e);
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Could not execute goal %s on %s. Reason: %s", goal(), file(),
                    e.getMessage()), e);
        }
    }

    /**
     * Returns the matching pattern for undeploy and redeploy goals. By default {@code null} is returned.
     *
     * @return the pattern or {@code null}
     */
    protected String getMatchPattern() {
        return null;
    }

    /**
     * Returns the matching pattern strategy to use if more than one deployment matches the {@link #getMatchPattern()
     * pattern} returns more than one instance of a deployment. By default {@code null} is returned.
     *
     * @return the matching strategy or {@code null}
     */
    protected MatchPatternStrategy getMatchPatternStrategy() {
        return null;
    }

    /**
     * Validates the deployment.
     *
     * @param client   the client used for validation
     * @param isDomain {@code true} if this is a domain server, otherwise {@code false}
     *
     * @throws DeploymentException if the deployment is invalid
     */
    protected void validate(final ModelControllerClient client, final boolean isDomain) throws DeploymentException {
        if (isDomain) {
            if (domain == null || domain.getServerGroups().isEmpty()) {
                throw new DeploymentException(
                        "Server is running in domain mode, but no server groups have been defined.");
            }
        } else if (domain != null && !domain.getServerGroups().isEmpty()) {
            throw new DeploymentException("Server is running in standalone mode, but server groups have been defined.");
        }
    }
}
