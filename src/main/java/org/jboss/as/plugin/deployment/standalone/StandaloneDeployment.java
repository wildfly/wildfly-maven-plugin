/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.plugin.deployment.standalone;

import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.client.helpers.ClientConstants.OUTCOME;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;
import static org.jboss.as.controller.client.helpers.ClientConstants.SUCCESS;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

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
import org.jboss.as.plugin.deployment.ConnectionInfo;
import org.jboss.as.plugin.deployment.Deployment;
import org.jboss.dmr.ModelNode;

/**
 * A deployment for standalone servers.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StandaloneDeployment implements Deployment {

    private final File content;
    private final ModelControllerClient client;
    private final String name;
    private final Type type;

    /**
     * Creates a new deployment.
     *
     * @param connectionInfo the connection information.
     * @param content        the content for the deployment.
     * @param name           the name of the deployment, if {@code null} the name of the content file is used.
     * @param type           the deployment type.
     */
    public StandaloneDeployment(final ConnectionInfo connectionInfo, final File content, final String name, final Type type) {
        this.content = content;
        this.client = ModelControllerClient.Factory.create(connectionInfo.getHostAddress(), connectionInfo.getPort(), connectionInfo.getCallbackHandler());
        this.name = (name == null ? content.getName() : name);
        this.type = type;
    }

    /**
     * Creates a new deployment.
     *
     * @param connectionInfo the connection information.
     * @param content        the content for the deployment.
     * @param name           the name of the deployment, if {@code null} the name of the content file is used.
     * @param type           the deployment type.
     *
     * @return the new deployment
     */
    public static StandaloneDeployment create(final ConnectionInfo connectionInfo, final File content, final String name, final Type type) {
        return new StandaloneDeployment(connectionInfo, content, name, type);
    }

    private DeploymentPlan createPlan(final DeploymentPlanBuilder builder) throws IOException {
        DeploymentPlanBuilder planBuilder = builder;
        switch (type) {
            case DEPLOY: {
                planBuilder = builder.add(name, content).andDeploy();
                break;
            }
            case REDEPLOY: {
                planBuilder = builder.replace(name, content).redeploy(name);
                break;
            }
            case UNDEPLOY: {
                planBuilder = builder.undeploy(name).remove(name);
                break;
            }
            case FORCE_DEPLOY: {
                if (exists()) {
                    planBuilder = builder.replace(name, content).redeploy(name);
                } else {
                    planBuilder = builder.add(name, content).andDeploy();
                }
                break;
            }
            case UNDEPLOY_IGNORE_MISSING: {
                if (exists()) {
                    planBuilder = builder.undeploy(name).remove(name);
                } else {
                    return null;
                }
                break;
            }
        }
        return planBuilder.build();
    }

    @Override
    public Status execute() throws MojoExecutionException, MojoFailureException {
        Status resultStatus = Status.SUCCESS;
        try {
            final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);
            final DeploymentPlanBuilder builder = manager.newDeploymentPlan();
            final DeploymentPlan plan = createPlan(builder);
            if (plan != null) {
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
                                resultStatus = Status.REQUIRES_RESTART;
                                break;
                        }
                    }
                }
            }
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Error executing %s", type), e);
        }
        return resultStatus;
    }

    @Override
    public Type getType() {
        return type;
    }

    private boolean exists() {
        // CLI :read-children-names(child-type=deployment)
        final ModelNode op = new ModelNode();
        op.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        op.get(CHILD_TYPE).set(DEPLOYMENT);
        final ModelNode result;
        try {
            result = client.execute(op);
            final String deploymentName = name;
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
                    throw new IllegalStateException(String.format("A failure occurred when checking existing deployments. Error: %s",
                            (result.hasDefined(FAILURE_DESCRIPTION) ? result.get(FAILURE_DESCRIPTION).asString() : "Unknown")));
                }
            } else {
                throw new IllegalStateException(String.format("An unexpected response was found checking the deployment. Result: %s", result));
            }
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Could not execute operation '%s'", op), e);
        }
        return false;
    }

    @Override
    public void close() {
        if (client != null) try {
            client.close();
        } catch (Throwable t) {
            // no-op
        }
    }
}
