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

import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.client.helpers.ClientConstants.OUTCOME;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;
import static org.jboss.as.controller.client.helpers.ClientConstants.SUCCESS;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.jboss.as.controller.client.helpers.standalone.DeploymentAction;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentActionResult;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;
import org.jboss.as.controller.client.helpers.standalone.ServerUpdateActionResult;
import org.jboss.as.plugin.deployment.common.AbstractServerConnection;
import org.jboss.dmr.ModelNode;

/**
 * The default implementation for executing build plans on the server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author Stuart Douglas
 * @requiresDependencyResolution runtime
 */
abstract class AbstractDeployment extends AbstractServerConnection {
    // These will be moved org.jboss.as.controller.client.helpers.ClientConstants next release.
    private static final String CHILD_TYPE = "child-type";
    private static final String FAILED = "failed";
    private static final String READ_CHILDREN_NAMES_OPERATION = "read-children-names";

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
     * The deployment name. The default is {@code null}.
     *
     * @return the deployment name, otherwise {@code null}.
     */
    public String name() {
        return name;
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
     * The archive file.
     *
     * @return the archive file.
     */
    public File file() {
        return new File(targetDir, filename);
    }

    /**
     * @return The deployment name
     */
    public String deploymentName() {
        return (name() == null ? file().getName() : name());
    }

    /**
     * Creates the deployment plan.
     *
     * @param builder the builder used to create the deployment plan.
     *
     * @return the deployment plan or {@code null} to ignore the plan.
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
                final InetAddress host = hostAddress();
                getLog().info(String.format("Executing goal %s on server %s (%s) port %s.", goal(), host.getHostName(), host.getHostAddress(), port()));
                final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client());
                final DeploymentPlanBuilder builder = manager.newDeploymentPlan();
                final DeploymentPlan plan = createPlan(builder);
                if (plan == null) {
                    getLog().debug(String.format("Ignoring goal %s as the plan was null.", goal()));
                } else {
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
                        getLog().warn(String.format("Goal %s failed on file %s. No deployment actions exist. Plan: %s", goal(), file().getName(), plan));
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Could not execute goal %s on %s. Reason: %s", goal(), file().getName(), e.getMessage()), e);
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
        final String deploymentName = deploymentName();
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

    /**
     * @return True if the package type should be checked for ignored packaging types
     */
    protected boolean checkPackaging() {
        return true;
    }
}
