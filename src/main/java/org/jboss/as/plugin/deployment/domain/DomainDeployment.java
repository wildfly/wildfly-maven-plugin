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

package org.jboss.as.plugin.deployment.domain;

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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.jboss.as.controller.client.helpers.domain.DeploymentActionResult;
import org.jboss.as.controller.client.helpers.domain.DeploymentActionsCompleteBuilder;
import org.jboss.as.controller.client.helpers.domain.DeploymentPlan;
import org.jboss.as.controller.client.helpers.domain.DeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.domain.DeploymentPlanResult;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.DomainDeploymentManager;
import org.jboss.as.controller.client.helpers.domain.DuplicateDeploymentNameException;
import org.jboss.as.controller.client.helpers.domain.ServerGroupDeploymentActionResult;
import org.jboss.as.controller.client.helpers.domain.ServerGroupDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.client.helpers.domain.ServerUpdateResult;
import org.jboss.as.plugin.deployment.ConnectionInfo;
import org.jboss.as.plugin.deployment.Deployment;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DomainDeployment implements Deployment {

    private final File content;
    private final DomainClient client;
    private final Domain domain;
    private final String name;
    private final Type type;

    /**
     * Creates a new deployment.
     *
     * @param connectionInfo the connection information.
     * @param domain         the domain information.
     * @param content        the content for the deployment.
     * @param name           the name of the deployment, if {@code null} the name of the content file is used.
     * @param type           the deployment type.
     */
    public DomainDeployment(final ConnectionInfo connectionInfo, final Domain domain, final File content, final String name, final Type type) {
        this.content = content;
        this.client = DomainClient.Factory.create(connectionInfo.getHostAddress(), connectionInfo.getPort(), connectionInfo.getCallbackHandler());
        this.domain = domain;
        this.name = (name == null ? content.getName() : name);
        this.type = type;
    }

    /**
     * Creates a new deployment.
     *
     * @param connectionInfo the connection information.
     * @param domain         the domain information.
     * @param content        the content for the deployment.
     * @param name           the name of the deployment, if {@code null} the name of the content file is used.
     * @param type           the deployment type.
     *
     * @return the new deployment
     */
    public static DomainDeployment create(final ConnectionInfo connectionInfo, final Domain domain, final File content, final String name, final Type type) {
        return new DomainDeployment(connectionInfo, domain, content, name, type);
    }

    private DeploymentPlan createPlan(final DeploymentPlanBuilder builder) throws IOException, DuplicateDeploymentNameException, MojoFailureException {
        final boolean deploymentExists = exists();
        DeploymentActionsCompleteBuilder completeBuilder = null;
        switch (type) {
            case DEPLOY: {
                completeBuilder = builder.add(name, content).andDeploy();
                break;
            }
            case FORCE_DEPLOY: {
                if (deploymentExists) {
                    completeBuilder = builder.replace(name, content);
                } else {
                    completeBuilder = builder.add(name, content).andDeploy();
                }
                break;
            }
            case REDEPLOY: {
                completeBuilder = builder.replace(name, content);
                break;
            }
            case UNDEPLOY: {
                completeBuilder = builder.undeploy(name).andRemoveUndeployed();
                break;
            }
            case UNDEPLOY_IGNORE_MISSING: {
                if (deploymentExists) {
                    completeBuilder = builder.undeploy(name).andRemoveUndeployed();
                } else {
                    return null;
                }
            }
        }
        if (completeBuilder != null) {
            ServerGroupDeploymentPlanBuilder groupDeploymentBuilder = null;
            for (String serverGroupName : domain.getServerGroups()) {
                groupDeploymentBuilder = (groupDeploymentBuilder == null ? completeBuilder.toServerGroup(serverGroupName) :
                        groupDeploymentBuilder.toServerGroup(serverGroupName));
            }
            if (groupDeploymentBuilder == null) {
                throw new MojoFailureException("No server groups were defined for the deployment.");
            }
            return groupDeploymentBuilder.build();
        }
        throw new IllegalStateException(String.format("Invalid type '%s' for deployment", type));
    }

    @Override
    public Status execute() throws MojoExecutionException, MojoFailureException {
        try {
            validate();
            final DomainDeploymentManager manager = client.getDeploymentManager();
            final DeploymentPlanBuilder builder = manager.newDeploymentPlan();
            DeploymentPlan plan = createPlan(builder);
            if (plan != null) {
                executePlan(manager, plan);
            }
        } catch (MojoFailureException e) {
            throw e;
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Error executing %s", type), e);
        }
        return Status.SUCCESS;
    }

    void validate() throws MojoFailureException {
        final Map<ServerIdentity, ServerStatus> statuses = client.getServerStatuses();
        final List<String> serverGroups = domain.getServerGroups();
        for (String serverGroup : serverGroups) {
            boolean notFound = true;
            // Check the servers
            for (ServerIdentity serverId : statuses.keySet()) {
                if (serverGroup.equals(serverId.getServerGroupName())) {
                    ServerStatus currentStatus = statuses.get(serverId);
                    if (currentStatus != ServerStatus.STARTED) {
                        throw new MojoFailureException(
                                String.format("Status of server group '%s' is '%s', but is required to be '%s'.",
                                        serverGroup, currentStatus, ServerStatus.STARTED));
                    }
                    notFound = false;
                    break;
                }
            }
            if (notFound) {
                throw new MojoFailureException(String.format("Server group '%s' does not exist on the server.", serverGroup));
            }
        }
    }

    @Override
    public Type getType() {
        return type;
    }

    private void executePlan(final DomainDeploymentManager manager, final DeploymentPlan plan) throws MojoExecutionException, ExecutionException, InterruptedException {
        if (plan.getDeploymentActions().size() > 0) {
            final DeploymentPlanResult planResult = manager.execute(plan).get();
            final Map<UUID, DeploymentActionResult> actionResults = planResult.getDeploymentActionResults();
            for (UUID uuid : actionResults.keySet()) {
                final Map<String, ServerGroupDeploymentActionResult> groupDeploymentActionResults = actionResults.get(uuid).getResultsByServerGroup();
                for (String serverGroup2 : groupDeploymentActionResults.keySet()) {
                    final Map<String, ServerUpdateResult> serverUpdateResults = groupDeploymentActionResults.get(serverGroup2).getResultByServer();
                    for (String server : serverUpdateResults.keySet()) {
                        final Throwable t = serverUpdateResults.get(server).getFailureResult();
                        if (t != null) {
                            throw new MojoExecutionException(String.format("Error executing %s", type), t);
                        }
                    }
                }
            }
        }
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
