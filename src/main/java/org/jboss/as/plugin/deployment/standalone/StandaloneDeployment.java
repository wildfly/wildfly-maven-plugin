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

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.standalone.DeploymentAction;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentActionResult;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;
import org.jboss.as.controller.client.helpers.standalone.ServerUpdateActionResult;
import org.jboss.as.plugin.common.DeploymentExecutionException;
import org.jboss.as.plugin.common.DeploymentFailureException;
import org.jboss.as.plugin.common.ServerOperations;
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
     * @param client  the client that is connected.
     * @param content the content for the deployment.
     * @param name    the name of the deployment, if {@code null} the name of the content file is used.
     * @param type    the deployment type.
     */
    public StandaloneDeployment(final ModelControllerClient client, final File content, final String name, final Type type) {
        this.content = content;
        this.client = client;
        this.name = (name == null ? content.getName() : name);
        this.type = type;
    }

    /**
     * Creates a new deployment.
     *
     * @param client  the client that is connected.
     * @param content the content for the deployment.
     * @param name    the name of the deployment, if {@code null} the name of the content file is used.
     * @param type    the deployment type.
     *
     * @return the new deployment
     */
    public static StandaloneDeployment create(final ModelControllerClient client, final File content, final String name, final Type type) {
        return new StandaloneDeployment(client, content, name, type);
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
    public Status execute() throws DeploymentExecutionException, DeploymentFailureException {
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
                                throw new DeploymentExecutionException("Deployment failed.", actionResult.getDeploymentException());
                            case NOT_EXECUTED:
                                throw new DeploymentExecutionException("Deployment not executed.", actionResult.getDeploymentException());
                            case ROLLED_BACK:
                                throw new DeploymentExecutionException("Deployment failed and was rolled back.", actionResult.getDeploymentException());
                            case CONFIGURATION_MODIFIED_REQUIRES_RESTART:
                                resultStatus = Status.REQUIRES_RESTART;
                                break;
                        }
                    }
                }
            }
        } catch (DeploymentExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new DeploymentExecutionException(e, "Error executing %s", type);
        }
        return resultStatus;
    }

    @Override
    public Type getType() {
        return type;
    }

    private boolean exists() {
        // CLI :read-children-names(child-type=deployment)
        final ModelNode op = ServerOperations.createListDeploymentsOperation();
        final ModelNode result;
        try {
            result = client.execute(op);
            final String deploymentName = name;
            // Check to make sure there is an outcome
            if (ServerOperations.isSuccessfulOutcome(result)) {
                final List<ModelNode> deployments = ServerOperations.readResult(result).asList();
                for (ModelNode n : deployments) {
                    if (n.asString().equals(deploymentName)) {
                        return true;
                    }
                }
            } else {
                throw new IllegalStateException(ServerOperations.getFailureDescriptionAsString(result));
            }
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Could not execute operation '%s'", op), e);
        }
        return false;
    }
}
