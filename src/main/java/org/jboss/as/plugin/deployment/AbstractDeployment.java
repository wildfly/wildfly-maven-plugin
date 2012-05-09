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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.jboss.as.plugin.common.AbstractServerConnection;
import org.jboss.as.plugin.common.Streams;
import org.jboss.as.plugin.deployment.domain.DomainDeployment;
import org.jboss.as.plugin.deployment.standalone.StandaloneDeployment;

/**
 * The default implementation for executing build plans on the server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author Stuart Douglas
 * @requiresDependencyResolution runtime
 */
abstract class AbstractDeployment extends AbstractServerConnection {

    // These will be moved org.jboss.as.controller.client.helpers.ClientConstants next release.

    private static final String NO_NAME_MSG = "No name defined, using default deployment name.";
    private static final String NAME_DEFINED_MSG_FMT = "Using '%s' for the deployment name.";

    /**
     * @parameter default-value="${project}"
     * @readonly
     * @required
     */
    private MavenProject project;

    /**
     * Specifies the name used for the deployment.
     *
     * @parameter
     */
    private String name;

    /**
     * Specifies the packaging type.
     *
     * @parameter default-value="${project.packaging}"
     */
    private String packaging;


    /**
     * The target directory the application to be deployed is located.
     *
     * @parameter default-value="${project.build.directory}/"
     */
    private File targetDir;

    /**
     * The file name of the application to be deployed.
     *
     * @parameter default-value="${project.build.finalName}.${project.packaging}"
     */
    private String filename;

    /**
     * Set to {@code true} if you want the deployment to be skipped, otherwise {@code false}.
     *
     * @parameter default-value="false"
     */
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
                Deployment deployment = null;
                try {
                    if (isDomainServer()) {
                        deployment = DomainDeployment.create(this, getDomain(), file(), name, getType());
                    } else {
                        deployment = StandaloneDeployment.create(this, file(), name, getType());
                    }
                    deployment.execute();
                    deployment.close();
                } finally {
                    Streams.safeClose(deployment);
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
     * A message indicating the name has not been defined.
     *
     * @return the message.
     */
    protected String nameNotDefinedMessage() {
        return NO_NAME_MSG;
    }

    /**
     * A message indicating the name has been defined and will be used.
     *
     * @return the message.
     */
    protected String nameDefinedMessage() {
        return String.format(NAME_DEFINED_MSG_FMT, name);
    }

    /**
     * @return True if the package type should be checked for ignored packaging types
     */
    protected boolean checkPackaging() {
        return true;
    }
}
