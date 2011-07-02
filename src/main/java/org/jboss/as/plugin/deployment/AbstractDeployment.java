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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.standalone.DeploymentAction;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentActionResult;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;
import org.jboss.as.controller.client.helpers.standalone.ServerUpdateActionResult;
import org.jboss.dmr.ModelNode;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

/**
 * The default implementation for executing build plans on the server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @requiresDependencyResolution runtime
 */
abstract class AbstractDeployment extends AbstractMojo {

    private static final String NO_NAME_MSG = "No name defined, using default deployment name.";
    private static final String NAME_DEFINED_MSG_FMT = "Using '%s' for the deployment name.";

    private volatile InetAddress address = null;

    private volatile ModelControllerClient client = null;

    /**
     * Specifies the name used for the deployment.
     *
     * @parameter expression="${echo.name}"
     */
    private String name;

    /**
     * Specifies the host name of the server where the deployment plan should be executed.
     *
     * @parameter expression="${echo.hostname}" default-value="localhost"
     */
    private String hostname;

    /**
     * Specifies the port number the server is listening on.
     *
     * @parameter expression="${echo.port}" default-value="9999"
     */
    private int port;

    /**
     * The target directory the application to be deployed is located.
     *
     * @parameter expression="${echo.target}" default-value="${project.build.directory}/"
     */
    private File targetDir;

    /**
     * The file name of the application to be deployed.
     *
     * @parameter expression="${echo.filename}" default-value="${project.build.finalName}.${project.packaging}"
     */
    private String filename;

    /**
     * Specifies whether force mode should be used or not.
     * </p>
     * If force mode is disabled, the deploy goal will cause a build failure if the application being deployed already
     * exists.
     *
     * @parameter expression="${echo.force}" default-value="true"
     */
    private boolean force;

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
     * Returns {@code true} if force is enabled, otherwise false.
     *
     * @return {@code true} if force is enabled, otherwise false.
     */
    public final boolean force() {
        return force;
    }

    /**
     * The target directory the archive is located. The default is {@code project.build.directory}.
     *
     * @return the target directory the archive is located.
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
    public abstract DeploymentPlan createPlan(DeploymentPlanBuilder builder) throws IOException, MojoFailureException;

    /**
     * The goal of the deployment.
     *
     * @return the goal of the deployment.
     */
    public abstract String goal();

    /**
     * Creates gets the address to the host name.
     *
     * @return the address.
     *
     * @throws UnknownHostException if the host name was not found.
     */
    protected final InetAddress hostAddress() throws UnknownHostException {
        // Lazy load the address
        if (address == null) {
            synchronized (this) {
                if (address == null) {
                    address = InetAddress.getByName(hostname());
                }
            }
        }
        return address;
    }

    /**
     * Creates a model controller client.
     *
     * @return the client.
     *
     * @throws UnknownHostException if the host name does not exist.
     */
    protected final ModelControllerClient client() throws UnknownHostException {
        // Lazy load the client
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = ModelControllerClient.Factory.create(hostAddress(), port());
                }
            }
        }
        return client;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        try {
            final File file = new File(targetDirectory(), filename());
            final InetAddress host = hostAddress();
            getLog().info(String.format("Executing goal %s for %s on server %s (%s) port %s.", goal(), file, host.getHostName(), host.getHostAddress(), port()));
            if (force()) {
                getLog().debug("force option is enabled");
            }
            final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client());
            final DeploymentPlanBuilder builder = manager.newDeploymentPlan();
            final DeploymentPlan plan = createPlan(builder);
            if (plan.getDeploymentActions().size() > 0) {
                final ServerDeploymentPlanResult planResult = manager.execute(plan).get();
                // Check the results
                for (DeploymentAction action : plan.getDeploymentActions()) {
                    final ServerDeploymentActionResult actionResult = planResult.getDeploymentActionResult(action.getId());
                    final ServerUpdateActionResult.Result result = actionResult.getResult();
                    switch (result) {
                        case FAILED:
                            throw new MojoExecutionException("Deployment failed.", actionResult.getDeploymentException());
                        case NOT_EXECUTED:
                            throw new MojoExecutionException("Deployment not executed.", actionResult.getDeploymentException());
                        case ROLLED_BACK:
                            throw new MojoExecutionException("Deployment failed and was rolled back.", actionResult.getDeploymentException());
                        case CONFIGURATION_MODIFIED_REQUIRES_RESTART:
                            getLog().warn("Action was executed, but the server requires a restart.");
                            break;
                        default:
                            break;
                    }
                    getLog().debug(String.format("Deployment Plan Id : %s", planResult.getDeploymentPlanId()));
                }
            } else {
                getLog().warn(String.format("Goal %s failed on file %s. No deployment actions exist. Plan: %s", goal(), filename(), plan));
            }
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Could not execute goal %s on %s. Reason: %s", goal(), filename(), e.getMessage()), e);
        }
    }

    /**
     * Checks to see if the deployment exists.
     *
     * @return {@code true} if the deployment exists, {@code false} if the deployment does not exist or cannot be
     *         determined.
     *
     * @throws java.io.IOException if an error occurred.
     */
    protected final boolean deploymentExists() throws IOException {
        // CLI :read-children-names(child-type=deployment)
        final ModelNode op = new ModelNode();
        op.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        op.get(CHILD_TYPE).set(DEPLOYMENT);
        final ModelNode result = client().execute(op);
        final String deploymentName = (name() == null ? filename() : name());
        // Check to make sure there is an outcome
        if (result.hasDefined(OUTCOME)) {
            if (result.get(OUTCOME).asString().equals(SUCCESS)) {
                final List<ModelNode> deployments = (result.hasDefined(RESULT) ? result.get(RESULT).asList() : Collections.<ModelNode>emptyList());
                for (ModelNode n : deployments) {
                    if (n.asString().equals(deploymentName)) {
                        return true;
                    }
                }
            } else if (result.get(OUTCOME).asString().equals(FAILED)) {
                getLog().warn(String.format("A failure occurred when checking existing deployments. Error: %s",
                        (result.hasDefined(FAILURE_DESCRIPTION) ? result.get(FAILURE_DESCRIPTION).asString() : "Unknown")));
            }
        } else {
            getLog().warn(String.format("An unexpected response was found checking the deployment. Result: %s", result));
        }
        return false;
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
        return String.format(NAME_DEFINED_MSG_FMT, name());
    }

}
