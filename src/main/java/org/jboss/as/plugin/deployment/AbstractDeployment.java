/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.plugin.deployment;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.plugin.cli.Commands;
import org.jboss.as.plugin.common.AbstractServerConnection;
import org.jboss.as.plugin.common.DeploymentExecutionException;
import org.jboss.as.plugin.common.DeploymentFailureException;
import org.jboss.as.plugin.deployment.Deployment.Status;
import org.jboss.as.plugin.deployment.domain.Domain;
import org.jboss.as.plugin.deployment.domain.DomainDeployment;
import org.jboss.as.plugin.deployment.standalone.StandaloneDeployment;

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

    protected final Status executeDeployment(final ModelControllerClient client, final Deployment deployment)
            throws DeploymentExecutionException, DeploymentFailureException, IOException {
        // Execute before deployment commands
        if (beforeDeployment != null)
            beforeDeployment.execute(client);
        // Deploy the deployment
        getLog().debug("Executing deployment");
        final Status status = deployment.execute();
        // Execute after deployment commands
        if (afterDeployment != null)
            afterDeployment.execute(client);
        return status;
    }

    /**
     * Executes additional processing.
     *
     * @see #execute()
     */
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        try {
            synchronized (CLIENT_LOCK) {
                validate();
                final ModelControllerClient client = getClient();
                final String matchPattern = getMatchPattern();
                final MatchPatternStrategy matchPatternStrategy = getMatchPatternStrategy();
                final Deployment deployment;
                if (isDomainServer()) {
                    deployment = DomainDeployment.create((DomainClient) client, domain, file(), name, getType(), matchPattern, matchPatternStrategy);
                } else {
                    deployment = StandaloneDeployment.create(client, file(), name, getType(), matchPattern, matchPatternStrategy);
                }
                switch (executeDeployment(client, deployment)) {
                    case REQUIRES_RESTART: {
                        getLog().info("Server requires a restart");
                        break;
                    }
                    case SUCCESS:
                        break;
                }
            }
        } catch (MojoFailureException e) {
            throw e;
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Could not execute goal %s on %s. Reason: %s", goal(), file(),
                    e.getMessage()), e);
        } finally {
            close();
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
     * @throws DeploymentFailureException if the deployment is invalid.
     */
    protected void validate() throws DeploymentFailureException {
        if (isDomainServer()) {
            if (domain == null || domain.getServerGroups().isEmpty()) {
                throw new DeploymentFailureException(
                        "Server is running in domain mode, but no server groups have been defined.");
            }
        } else if (domain != null && !domain.getServerGroups().isEmpty()) {
            throw new DeploymentFailureException("Server is running in standalone mode, but server groups have been defined.");
        }
    }
}
