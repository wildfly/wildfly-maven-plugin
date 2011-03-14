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

package org.jboss.as.plugin;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.DuplicateDeploymentNameException;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;

/**
 * The default implementation for executing build plans on the server.
 * 
 * @execute phase=compile
 * @requiresDependencyResolution runtime
 * 
 * @author James R. Perkins Jr. (jrp)
 */
abstract class AbstractDeployment extends AbstractMojo {

    /**
     * @parameter expression="${echo.name}"
     */
    private String name;

    /**
     * @parameter expression="${echo.hostname}" default-value="localhost"
     */
    private String hostname;

    /**
     * @parameter expression="${echo.port}" default-value="9999"
     */
    private int port;

    /**
     * @parameter expression="${echo.target}" default-value="${project.build.directory}/"
     */
    private File targetDir;

    /**
     * @parameter expression="${echo.filename}" default-value="${project.build.finalName}.${project.packaging}"
     */
    private String filename;

    /**
     * The deployment name. The default is {@code null}.
     * 
     * @return the deployment name, otherwise {@code null}.
     */
    public final String name() {
        return name;
    }

    /**
     * The hostname to deploy the archive to. The default is localhost.
     * 
     * @return the hostname of the server.
     */
    public final String hostname() {
        return hostname;
    }

    /**
     * The port number of the server to deploy to. The default is 9999.
     * 
     * @return the port number to deploy to.
     */
    public final int port() {
        return port;
    }

    /**
     * The target directory the archive is located. The default is {@code project.build.directory}.
     * 
     * @return the target directory the archvie is located.
     */
    public final File targetDirectory() {
        return targetDir;
    }

    /**
     * The file name of the archive not including the directory. The default is
     * {@code project.build.finalName + . + project.packaging}
     * 
     * @return the file name of the archive.
     */
    public final String filename() {
        return filename;
    }

    /**
     * The archive file.
     * 
     * @return the archive file.
     */
    public final File file() {
        return new File(targetDir, filename);
    }

    /**
     * Creates the deployment plan.
     * 
     * @param builder the builder used to create the deployment plan.
     * 
     * @return the deployment plan.
     * 
     * @throws IOException if the deployment plan cannot be created.
     */
    public abstract DeploymentPlan createPlan(DeploymentPlanBuilder builder) throws IOException;

    /**
     * The goal of the deployment.
     * 
     * @return the goal of the deployment.
     */
    public abstract String goal();

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        try {
            final File file = new File(targetDir, filename);
            final InetAddress host = InetAddress.getByName(hostname);
            final String s = Character.toUpperCase(goal().charAt(0)) + goal().substring(1);
            getLog().info(String.format("%sing %s to %s (%s) port %s.", s, file, host.getHostName(), host.getHostAddress(), port));
            final ModelControllerClient client = ModelControllerClient.Factory.create(host, port);
            final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);
            final DeploymentPlanBuilder builder = manager.newDeploymentPlan();
            final DeploymentPlan plan = createPlan(builder);
            if (plan.getDeploymentActions().size() > 0) {
                final ServerDeploymentPlanResult result = manager.execute(plan).get();
                getLog().info(String.format("Deployment Plan Id : %s", result.getDeploymentPlanId()));
            } else {
                getLog().warn(
                        String.format("File %s has not been %sed. No deployment actions exist. Plan: %s", goal(), filename(),
                                plan));
            }
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Could not %s %s. Reason: %s", goal(), filename(), e.getMessage()),
                    e);
        }
    }

}
