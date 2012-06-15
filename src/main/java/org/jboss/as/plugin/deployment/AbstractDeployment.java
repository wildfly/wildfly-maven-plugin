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
import org.jboss.as.plugin.common.AbstractServerConnection;
import org.jboss.as.plugin.common.Streams;
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
     * Specifies the name used for the deployment.
     */
    @Parameter
    private String name;

    /**
     * Specifies the packaging type.
     */
    @Parameter(defaultValue = "${project.packaging}")
    private String packaging;


    /**
     * The target directory the application to be deployed is located.
     */
    @Parameter(defaultValue = "${project.build.directory}/")
    private File targetDir;

    /**
     * The file name of the application to be deployed.
     */
    @Parameter(defaultValue = "${project.build.finalName}.${project.packaging}")
    private String filename;

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
    public File file() {
        return new File(targetDir, filename);
    }

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
            if (checkPackaging() && IgnoredPackageTypes.isIgnored(packaging)) {
                getLog().debug(String.format("Ignoring packaging type %s.", packaging));
            } else {
                validate();
                ModelControllerClient client = null;
                try {
                    final Deployment deployment;
                    if (isDomainServer()) {
                        final DomainClient domainClient = DomainClient.Factory.create(getHostAddress(), getPort(), getCallbackHandler());
                        deployment = DomainDeployment.create(domainClient, getDomain(), file(), name, getType());
                        client = domainClient;
                    } else {
                        client = ModelControllerClient.Factory.create(getHostAddress(), getPort(), getCallbackHandler());
                        deployment = StandaloneDeployment.create(client, file(), name, getType());
                    }
                    // Execute before deployment hook
                    getBeforeDeploymentHook().execute(client);
                    // Deploy the deployment
                    getLog().debug("Executing deployment");
                    deployment.execute();
                    // Execute after deployment hook
                    getAfterDeploymentHook().execute(client);
                } finally {
                    Streams.safeClose(client);
                }
            }
        } catch (MojoFailureException e) {
            throw e;
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Could not execute goal %s on %s. Reason: %s", goal(), file(), e.getMessage()), e);
        }
    }

    /**
     * Validates the deployment.
     *
     * @throws MojoFailureException if the deployment is invalid.
     */
    protected void validate() throws MojoFailureException {
        // no-op by default
    }

    /**
     * Adds an optional hook to be run before the deployment takes place.
     *
     * @return the hook to execute
     */
    protected Hook getBeforeDeploymentHook() {
        return Hook.NO_OP_HOOK;
    }

    /**
     * Adds an optional hook to be run after the deployment takes place.
     *
     * @return the hook to execute
     */
    protected Hook getAfterDeploymentHook() {
        return Hook.NO_OP_HOOK;
    }

    /**
     * @return True if the package type should be checked for ignored packaging types
     */
    protected boolean checkPackaging() {
        return true;
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
