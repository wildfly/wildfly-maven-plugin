/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.deployment.standalone;

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_FULL_REPLACE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_REDEPLOY_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_UNDEPLOY_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.PATH;
import static org.jboss.as.controller.client.helpers.ClientConstants.RUNTIME_NAME;
import static org.jboss.as.controller.client.helpers.Operations.createAddOperation;
import static org.jboss.as.controller.client.helpers.Operations.createOperation;
import static org.wildfly.plugin.common.ServerOperations.ARCHIVE;
import static org.wildfly.plugin.common.ServerOperations.BYTES;
import static org.wildfly.plugin.common.ServerOperations.ENABLE;
import static org.wildfly.plugin.common.ServerOperations.createAddress;
import static org.wildfly.plugin.common.ServerOperations.createRemoveOperation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.plugin.common.DeploymentExecutionException;
import org.wildfly.plugin.common.DeploymentFailureException;
import org.wildfly.plugin.common.DeploymentInspector;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.deployment.Deployment;
import org.wildfly.plugin.deployment.MatchPatternStrategy;

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
    private final String matchPattern;
    private final MatchPatternStrategy matchPatternStrategy;

    /**
     * Creates a new deployment.
     *
     * @param client               the client that is connected.
     * @param content              the content for the deployment.
     * @param name                 the name of the deployment, if {@code null} the name of the content file is used.
     * @param type                 the deployment type.
     * @param matchPattern         the pattern for matching multiple artifacts, if {@code null} the name is used.
     * @param matchPatternStrategy the strategy for handling multiple artifacts.
     */
    public StandaloneDeployment(final ModelControllerClient client, final File content, final String name, final Type type,
                                final String matchPattern, final MatchPatternStrategy matchPatternStrategy) {
        this.content = content;
        this.client = client;
        this.name = (name == null ? content.getName() : name);
        this.type = type;
        this.matchPattern = matchPattern;
        this.matchPatternStrategy = matchPatternStrategy;
    }

    /**
     * Creates a new deployment.
     *
     * @param client               the client that is connected.
     * @param content              the content for the deployment.
     * @param name                 the name of the deployment, if {@code null} the name of the content file is used.
     * @param type                 the deployment type.
     * @param matchPattern         the pattern for matching multiple artifacts, if {@code null} the name is used.
     * @param matchPatternStrategy the strategy for handling multiple artifacts.
     *
     * @return the new deployment
     */
    public static StandaloneDeployment create(final ModelControllerClient client, final File content, final String name, final Type type,
                                              final String matchPattern, final MatchPatternStrategy matchPatternStrategy) {
        return new StandaloneDeployment(client, content, name, type, matchPattern, matchPatternStrategy);
    }

    private void validateExistingDeployments(List<String> existingDeployments) throws DeploymentFailureException {
        if (matchPattern == null) {
            return;
        }

        if (matchPatternStrategy == MatchPatternStrategy.FAIL && existingDeployments.size() > 1) {
            throw new DeploymentFailureException(String.format("Deployment failed, found %d deployed artifacts for pattern '%s' (%s)",
                    existingDeployments.size(), matchPattern, existingDeployments));
        }
    }

    @Override
    public Status execute() throws DeploymentExecutionException, DeploymentFailureException {
        try {
            final List<String> existingDeployments = DeploymentInspector.getDeployments(client, name, matchPattern);
            final Operation operation;
            switch (type) {
                case DEPLOY: {
                    operation = createDeployOperation(content.toPath(), name, null);
                    break;
                }
                case FORCE_DEPLOY: {
                    if (existingDeployments.contains(name)) {
                        operation = createReplaceOperation(content.toPath(), name, null);
                    } else {
                        operation = createDeployOperation(content.toPath(), name, null);
                    }
                    break;
                }
                case REDEPLOY: {
                    if (!existingDeployments.contains(name)) {
                        throw new DeploymentFailureException("Deployment '%s' not found, cannot redeploy", name);
                    }
                    operation = createRedeployOperation(name);
                    break;
                }
                case UNDEPLOY: {
                    validateExistingDeployments(existingDeployments);
                    operation = createUndeployOperation(matchPatternStrategy, existingDeployments);
                    break;
                }
                case UNDEPLOY_IGNORE_MISSING: {
                    if (existingDeployments.isEmpty()) {
                        operation = null;
                    } else {
                        validateExistingDeployments(existingDeployments);
                        operation = createUndeployOperation(matchPatternStrategy, existingDeployments);
                    }
                    break;
                }
                default:
                    throw new IllegalArgumentException("Invalid type: " + type);
            }
            if (operation == null) {
                return Status.SUCCESS;
            }
            final ModelNode result = client.execute(operation);
            if (ServerOperations.isSuccessfulOutcome(result)) {
                return Status.SUCCESS;
            }
            throw new DeploymentExecutionException("Deployment failed: %s", ServerOperations.getFailureDescriptionAsString(result));
        } catch (DeploymentExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new DeploymentExecutionException(e, "Error executing %s", type);
        }
    }

    @Override
    public Type getType() {
        return type;
    }

    private static void addContent(final Path deployment, final ModelNode op, final boolean unmanaged) throws IOException {
        final ModelNode contentNode = op.get(CONTENT);
        final ModelNode contentItem = contentNode.get(0);
        if (unmanaged) {
            contentItem.get(PATH).set(deployment.toString());
            contentItem.get(ARCHIVE).set(!Files.isDirectory(deployment));
        } else {
            contentItem.get(BYTES).set(Files.readAllBytes(deployment));
        }
    }

    private static Operation createDeployOperation(final Path deployment, final String name, final String runtimeName) throws IOException {
        final ModelNode address = createAddress(DEPLOYMENT, name);
        final ModelNode addOperation = createAddOperation(address);
        if (runtimeName != null) {
            addOperation.get(RUNTIME_NAME).set(runtimeName);
        }
        addContent(deployment, addOperation, Files.isDirectory(deployment));
        return CompositeOperationBuilder.create()
                .addStep(addOperation)
                .addStep(createOperation(ClientConstants.DEPLOYMENT_DEPLOY_OPERATION, address))
                .build();
    }

    private static Operation createReplaceOperation(final Path deployment, final String name, final String runtimeName) throws IOException {
        final ModelNode op = createOperation(DEPLOYMENT_FULL_REPLACE_OPERATION);
        op.get(NAME).set(name);
        if (runtimeName != null) {
            op.get(RUNTIME_NAME).set(runtimeName);
        }
        addContent(deployment, op, Files.isDirectory(deployment));
        op.get(ENABLE).set(true);
        return OperationBuilder.create(op).build();
    }

    private static Operation createRedeployOperation(final String name) throws IOException {
        return OperationBuilder.create(createOperation(DEPLOYMENT_REDEPLOY_OPERATION, createAddress(DEPLOYMENT, name))).build();
    }

    private static Operation createUndeployOperation(final MatchPatternStrategy matchPatternStrategy, final Collection<String> names) throws IOException {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        for (String name : names) {
            final ModelNode address = createAddress(DEPLOYMENT, name);
            builder.addStep(createOperation(DEPLOYMENT_UNDEPLOY_OPERATION, address))
                    .addStep(createRemoveOperation(address));

            if (matchPatternStrategy == MatchPatternStrategy.FIRST) {
                break;
            }
        }
        return builder.build();
    }

}
