/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.deployment;

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_FULL_REPLACE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_REDEPLOY_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_UNDEPLOY_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.PATH;
import static org.jboss.as.controller.client.helpers.ClientConstants.RUNTIME_NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.SERVER_GROUP;
import static org.jboss.as.controller.client.helpers.Operations.createAddOperation;
import static org.jboss.as.controller.client.helpers.Operations.createOperation;
import static org.wildfly.plugin.common.ServerOperations.ARCHIVE;
import static org.wildfly.plugin.common.ServerOperations.ENABLE;
import static org.wildfly.plugin.common.ServerOperations.createAddress;
import static org.wildfly.plugin.common.ServerOperations.createRemoveOperation;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.plugin.common.ServerOperations;

/**
 * A deployment for standalone servers.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Deployment {

    public enum Type {
        DEPLOY("deploying"),
        FORCE_DEPLOY("deploying"),
        UNDEPLOY("undeploying"),
        UNDEPLOY_IGNORE_MISSING("undeploying"),
        REDEPLOY("redeploying");

        final String verb;

        Type(final String verb) {
            this.verb = verb;
        }
    }

    private final File content;
    private final Set<String> serverGroups;
    private final ModelControllerClient client;
    private final String name;
    private final String runtimeName;
    private final Type type;
    private final String matchPattern;
    private final MatchPatternStrategy matchPatternStrategy;
    private final Deployments deployments;

    /**
     * Creates a new deployment.
     *
     * @param client               the client that is connected.
     * @param serverGroups         the server groups to deploy to
     * @param content              the content for the deployment.
     * @param name                 the name of the deployment, if {@code null} the name of the content file is used.
     * @param runtimeName          he runtime name of the deployment
     * @param type                 the deployment type.
     * @param matchPattern         the pattern for matching multiple artifacts, if {@code null} the name is used.
     * @param matchPatternStrategy the strategy for handling multiple artifacts.
     */
    Deployment(final ModelControllerClient client, final Set<String> serverGroups, final File content, final String name, final String runtimeName, final Type type,
               final String matchPattern, final MatchPatternStrategy matchPatternStrategy) {
        this.content = content;
        this.client = client;
        this.serverGroups = serverGroups;
        this.name = (name == null ? content.getName() : name);
        this.runtimeName = runtimeName;
        this.type = type;
        this.matchPattern = matchPattern;
        this.matchPatternStrategy = matchPatternStrategy;
        deployments = Deployments.create(client, serverGroups);
    }

    private void validateExistingDeployments(final Collection<String> existingDeployments) throws DeploymentException {
        if (matchPattern == null) {
            return;
        }

        if (matchPatternStrategy == MatchPatternStrategy.FAIL && existingDeployments.size() > 1) {
            throw new DeploymentException(String.format("Deployment failed, found %d deployed artifacts for pattern '%s' (%s)",
                    existingDeployments.size(), matchPattern, existingDeployments));
        }
    }

    /**
     * Executes the deployment
     *
     * @throws DeploymentException if the deployment fails
     */
    public void execute() throws DeploymentException {
        try {
            final Operation operation;
            switch (type) {
                case DEPLOY: {
                    operation = createDeployOperation();
                    break;
                }
                case FORCE_DEPLOY: {
                    if (deployments.hasDeployment(name)) {
                        operation = createReplaceOperation(true);
                    } else {
                        operation = createDeployOperation();
                    }
                    break;
                }
                case REDEPLOY: {
                    if (!deployments.hasDeployment(name)) {
                        throw new DeploymentException("Deployment '%s' not found, cannot redeploy", name);
                    }
                    operation = createRedeployOperation();
                    break;
                }
                case UNDEPLOY: {
                    operation = createUndeployOperation(matchPatternStrategy, true);
                    break;
                }
                case UNDEPLOY_IGNORE_MISSING: {
                    operation = createUndeployOperation(matchPatternStrategy, false);
                    // This may be null if there was nothing to deploy
                    if (operation == null) {
                        return;
                    }
                    break;
                }
                default:
                    throw new IllegalArgumentException("Invalid type: " + type);
            }
            final ModelNode result = client.execute(operation);
            if (!ServerOperations.isSuccessfulOutcome(result)) {
                throw new DeploymentException("Deployment failed: %s", ServerOperations.getFailureDescriptionAsString(result));
            }
        } catch (DeploymentException e) {
            throw e;
        } catch (CancellationException e) {
            throw new DeploymentException(e, "Error %s %s. The operation was cancelled. This may be caused by the client being closed.", type.verb, name);
        } catch (Exception e) {
            throw new DeploymentException(e, "Error %s %s", type.verb, name);
        }
    }

    /**
     * The type of the deployment.
     *
     * @return the type of the deployment.
     */
    public Type getType() {
        return type;
    }

    private void addContent(final OperationBuilder builder, final ModelNode op) {
        final ModelNode contentNode = op.get(CONTENT);
        final ModelNode contentItem = contentNode.get(0);
        // If the content points to a directory we are deploying exploded content
        if (content.isDirectory()) {
            contentItem.get(PATH).set(content.getAbsolutePath());
            contentItem.get(ARCHIVE).set(false);
        } else {
            contentItem.get(ServerOperations.INPUT_STREAM_INDEX).set(0);
            builder.addFileAsAttachment(content);
        }
    }

    private Operation createDeployOperation() throws IOException {
        final Operations.CompositeOperationBuilder builder = Operations.CompositeOperationBuilder.create(true);
        final ModelNode address = createAddress(DEPLOYMENT, name);
        final ModelNode addOperation = createAddOperation(address);
        if (runtimeName != null) {
            addOperation.get(RUNTIME_NAME).set(runtimeName);
        }
        addContent(builder, addOperation);
        builder.addStep(addOperation);

        // If the server groups are empty this is a standalone deployment
        if (serverGroups.isEmpty()) {
            builder.addStep(createOperation(ClientConstants.DEPLOYMENT_DEPLOY_OPERATION, address));
        } else {
            for (String serverGroup : serverGroups) {
                final ModelNode sgAddress = ServerOperations.createAddress(SERVER_GROUP, serverGroup, DEPLOYMENT, name);
                final ModelNode op = ServerOperations.createAddOperation(sgAddress);
                op.get(ServerOperations.ENABLED).set(true);
                if (runtimeName != null) {
                    op.get(RUNTIME_NAME).set(runtimeName);
                }
                builder.addStep(op);
            }
        }
        return builder.build();
    }

    private Operation createReplaceOperation(final boolean allowAddIfMissing) throws IOException {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        // Adds need to happen first on server-groups otherwise the full-replace-deployment will fail currently
        if (allowAddIfMissing) {
            // If deployment is not on the server group, add it but don't yet enable it. The full-replace-deployment
            // should handle that part.
            for (String serverGroup : serverGroups) {
                if (!deployments.hasDeployment(serverGroup, name)) {
                    final ModelNode sgAddress = ServerOperations.createAddress(SERVER_GROUP, serverGroup, DEPLOYMENT, name);
                    final ModelNode addOp = ServerOperations.createAddOperation(sgAddress);
                    addOp.get(ServerOperations.ENABLED).set(false);
                    if (runtimeName != null) {
                        addOp.get(RUNTIME_NAME).set(runtimeName);
                    }
                    builder.addStep(addOp);
                }
            }
        }
        final ModelNode op = createOperation(DEPLOYMENT_FULL_REPLACE_OPERATION);
        op.get(NAME).set(name);
        if (runtimeName != null) {
            op.get(RUNTIME_NAME).set(runtimeName);
        }
        addContent(builder, op);
        op.get(ENABLE).set(true);
        builder.addStep(op);
        return builder.build();
    }

    private Operation createRedeployOperation() throws IOException {
        if (serverGroups.isEmpty()) {
            return OperationBuilder.create(createOperation(DEPLOYMENT_REDEPLOY_OPERATION, createAddress(DEPLOYMENT, name))).build();
        }
        return createReplaceOperation(false);
    }

    private Operation createUndeployOperation(final MatchPatternStrategy matchPatternStrategy, final boolean failOnMissing) throws IOException, DeploymentException {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        boolean stepAdded = false;
        // Being empty we'll assume this is a standalone server
        if (serverGroups.isEmpty()) {
            final Set<String> deploymentNames = deployments.filter(name, matchPattern);
            validateExistingDeployments(deploymentNames);
            if (deploymentNames.isEmpty() && failOnMissing) {
                throw new DeploymentException("No deployment matching %s found to undeploy.", (matchPattern == null ? name : matchPattern));
            }
            // Undeploy each deployment required
            for (String deploymentName : deploymentNames) {
                stepAdded = true;
                final ModelNode address = createAddress(DEPLOYMENT, deploymentName);
                builder.addStep(createOperation(DEPLOYMENT_UNDEPLOY_OPERATION, address))
                        .addStep(createRemoveOperation(address));
                if (matchPatternStrategy == MatchPatternStrategy.FIRST) {
                    break;
                }
            }
        } else {
            final Set<String> toRemove = new HashSet<>();
            // Process each server group separately
            for (String serverGroup : serverGroups) {
                final Set<String> deploymentNames = deployments.filter(name, matchPattern, serverGroup);
                validateExistingDeployments(deploymentNames);
                if (deploymentNames.isEmpty() && failOnMissing) {
                    throw new DeploymentException("No deployment matching %s found to undeploy on server group %s.",
                            (matchPattern == null ? name : matchPattern), serverGroup);
                }
                // Undeploy each deployment required
                for (String deploymentName : deploymentNames) {
                    // If the deployment is present on the server group add the undeploy and remove operation
                    if (deployments.hasDeployment(serverGroup, deploymentName)) {
                        stepAdded = true;
                        final ModelNode address = createAddress(SERVER_GROUP, serverGroup, DEPLOYMENT, deploymentName);
                        builder.addStep(createOperation(DEPLOYMENT_UNDEPLOY_OPERATION, address))
                                .addStep(createRemoveOperation(address));
                    } else if (failOnMissing) {
                        throw new DeploymentException("Could not undeploy %s. Deployment was not found on server group %s.", deploymentName, serverGroup);
                    }
                    // Add the deployment to the list of deployments to be removed from the domain content repository
                    toRemove.add(deploymentName);
                    if (matchPatternStrategy == MatchPatternStrategy.FIRST) {
                        break;
                    }
                }
            }
            for (String deploymentName : toRemove) {
                stepAdded = true;
                builder.addStep(createRemoveOperation(createAddress(DEPLOYMENT, deploymentName)));
            }
        }
        // If no steps were added return null
        return (stepAdded ? builder.build() : null);
    }
}
