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
import org.jboss.as.plugin.common.DeploymentFailureException;
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
    private MavenProject project;

    /**
     * Specifies the configuration for a domain server.
     */
    @Parameter
    private Domain domain;

    /**
     * Specifies the name used for the deployment.
     */
    @Parameter
    private String name;

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

    /**
     * @return {@code true} if the package type should be checked for ignored packaging types
     */
    protected abstract boolean checkPackaging();

    /*
     * (non-Javadoc)
     *
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping deployment of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        try {
            // Check the packaging type
            if (checkPackaging() && IgnoredPackageTypes.isIgnored(project.getPackaging())) {
                getLog().debug(String.format("Ignoring packaging type %s.", project.getPackaging()));
            } else {
                synchronized (CLIENT_LOCK) {
                    validate();
                    final ModelControllerClient client = getClient();
                    final Deployment deployment;
                    if (isDomainServer()) {
                        deployment = DomainDeployment.create((DomainClient) getClient(), domain, file(), name, getType());
                    } else {
                        deployment = StandaloneDeployment.create(client, file(), name, getType());
                    }
                    // Execute before deployment hook
                    getBeforeDeploymentHook().execute(client);
                    // Deploy the deployment
                    getLog().debug("Executing deployment");
                    deployment.execute();
                    // Execute after deployment hook
                    getAfterDeploymentHook().execute(client);
                }
            }
        } catch (MojoFailureException e) {
            throw e;
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Could not execute goal %s on %s. Reason: %s", goal(), file(), e.getMessage()), e);
        } finally {
            close();
        }
    }

    /**
     * Validates the deployment.
     *
     * @throws DeploymentFailureException if the deployment is invalid.
     */
    protected void validate() throws DeploymentFailureException {
        if (isDomainServer()) {
            if (domain == null || domain.getServerGroups().isEmpty()) {
                throw new DeploymentFailureException("Server is running in domain mode, but no server groups have been defined.");
            }
        } else if (domain != null && !domain.getServerGroups().isEmpty()) {
            throw new DeploymentFailureException("Server is running in standalone mode, but server groups have been defined.");
        }
    }

    /**
     * Adds an optional hook to be run before the deployment takes place.
     *
     * @return the hook to execute
     */
    protected Hook getBeforeDeploymentHook() {
        return new Hook() {
            @Override
            public void execute(final ModelControllerClient client) throws IOException {
                if (beforeDeployment != null) {
                    getLog().debug("Executing before deployment commands");
                    beforeDeployment.execute(client);
                }
            }
        };
    }

    /**
     * Adds an optional hook to be run after the deployment takes place.
     *
     * @return the hook to execute
     */
    protected Hook getAfterDeploymentHook() {
        return new Hook() {
            @Override
            public void execute(final ModelControllerClient client) throws IOException {
                if (afterDeployment != null) {
                    getLog().debug("Executing after deployment commands");
                    afterDeployment.execute(client);
                }
            }
        };
    }

    /**
     * A simple hook to execute operations.
     */
    interface Hook {

        final Hook NO_OP_HOOK = new Hook() {
            @Override
            public void execute(final ModelControllerClient client) {
                // no-op
            }
        };

        /**
         * Executes operations for the hook.
         */
        void execute(ModelControllerClient client) throws IOException;
    }
}
