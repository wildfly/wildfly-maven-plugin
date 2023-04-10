/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.plugin.core;

import static org.jboss.as.controller.client.helpers.ClientConstants.CHILD_TYPE;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.SERVER_GROUP;
import static org.wildfly.plugin.core.DeploymentOperations.createAddress;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.wildfly.common.Assert;

/**
 * The default deployment manager.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("Duplicates")
class DefaultDeploymentManager implements DeploymentManager {

    private final ModelControllerClient client;
    private final ContainerDescription containerDescription = new LazyContainerDescription();

    DefaultDeploymentManager(final ModelControllerClient client) {
        this.client = client;
    }

    @Override
    public DeploymentResult deploy(final Deployment deployment) throws IOException {
        final DeploymentResult failedResult = validateDeployment(deployment);
        if (failedResult != null) {
            return failedResult;
        }
        return execute(DeploymentOperations.createAddDeploymentOperation(deployment));
    }

    @Override
    public DeploymentResult deploy(final Set<Deployment> deployments) throws IOException {
        final DeploymentResult failedResult = validateDeployment(deployments);
        if (failedResult != null) {
            return failedResult;
        }
        return execute(DeploymentOperations.createAddDeploymentOperation(deployments));
    }

    @Override
    public DeploymentResult forceDeploy(final Deployment deployment) throws IOException {
        final DeploymentResult failedResult = validateDeployment(deployment);
        if (failedResult != null) {
            return failedResult;
        }
        if (hasDeployment(deployment.getName())) {
            // Special handling for domains if the deployment is already in the content repository
            if (containerDescription.isDomain()) {
                // Retrieve the currently deployment content description
                final DeploymentDescription current = getServerGroupDeployment(deployment.getName());
                final CompositeOperationBuilder builder = CompositeOperationBuilder.create(true);
                // Add a full-replace-deployment as well as add the deployment to any missing server groups
                DeploymentOperations.addReplaceOperationSteps(builder, deployment, current, true);
                return execute(builder.build());
            }
            return redeploy(deployment);
        }
        return deploy(deployment);
    }

    @Override
    public DeploymentResult forceDeploy(final Set<Deployment> deployments) throws IOException {
        final DeploymentResult failedResult = validateDeployment(deployments);
        if (failedResult != null) {
            return failedResult;
        }
        @SuppressWarnings("TypeMayBeWeakened")
        final Set<Deployment> toDeploy = new LinkedHashSet<>();
        final Map<Deployment, DeploymentDescription> toRedeploy = new LinkedHashMap<>();
        final Set<DeploymentDescription> currentDeployments = getDeployments();
        for (Deployment deployment : deployments) {
            // Find if the current deployment with the same name as the deployment to be deployed, if found it will
            // be replaced. If not found it will be deployed
            final DeploymentDescription currentDeployment = findDeployment(currentDeployments, deployment);
            if (currentDeployment != null) {
                toRedeploy.put(deployment, currentDeployment);
            } else {
                toDeploy.add(deployment);
            }
        }
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create(true);
        // Add all the deploy steps
        for (Deployment deployment : toDeploy) {
            DeploymentOperations.addDeploymentOperationStep(builder, deployment);
        }
        // Add all the redeploy steps
        for (Deployment deployment : toRedeploy.keySet()) {
            if (containerDescription.isDomain()) {
                // Adds the full-replace-deployment operation for domains as well as adds missing deployments on server
                // groups
                DeploymentOperations.addReplaceOperationSteps(builder, deployment, toRedeploy.get(deployment), true);
            } else {
                DeploymentOperations.addReplaceOperationSteps(builder, deployment);
            }
        }

        return execute(builder.build());
    }

    @Override
    public DeploymentResult deployToRuntime(final DeploymentDescription deployment) throws IOException {
        return execute(DeploymentOperations.createDeployOperation(Assert.checkNotNullParam("deployment", deployment)));
    }

    @Override
    public DeploymentResult deployToRuntime(final Set<DeploymentDescription> deployments) throws IOException {
        return execute(DeploymentOperations
                .createDeployOperation(Assertions.requiresNotNullOrNotEmptyParameter("deployments", deployments)));
    }

    @Override
    public DeploymentResult redeploy(final Deployment deployment) throws IOException {
        final DeploymentResult failedResult = validateDeployment(deployment);
        if (failedResult != null) {
            return failedResult;
        }
        return execute(DeploymentOperations.createReplaceOperation(deployment));
    }

    @Override
    public DeploymentResult redeploy(final Set<Deployment> deployments) throws IOException {
        final DeploymentResult failedResult = validateDeployment(deployments);
        if (failedResult != null) {
            return failedResult;
        }
        return execute(DeploymentOperations.createReplaceOperation(deployments));
    }

    @Override
    public DeploymentResult redeployToRuntime(final DeploymentDescription deployment) throws IOException {
        return execute(DeploymentOperations.createRedeployOperation(Assert.checkNotNullParam("deployment", deployment)));
    }

    @Override
    public DeploymentResult redeployToRuntime(final Set<DeploymentDescription> deployments) throws IOException {
        return execute(DeploymentOperations
                .createRedeployOperation(Assertions.requiresNotNullOrNotEmptyParameter("deployments", deployments)));
    }

    @Override
    public DeploymentResult undeploy(final UndeployDescription undeployDescription) throws IOException {
        final DeploymentResult failedResult = validateDeployment(undeployDescription);
        if (failedResult != null) {
            return failedResult;
        }
        if (undeployDescription.isFailOnMissing()) {
            return execute(DeploymentOperations.createUndeployOperation(undeployDescription));
        }
        final Set<DeploymentDescription> currentDeployments = getDeployments();
        final DeploymentDescription found = findDeployment(currentDeployments, undeployDescription);
        if (currentDeployments.isEmpty() || found == null) {
            return DeploymentResult.SUCCESSFUL;
        }
        return execute(DeploymentOperations.createUndeployOperation(copyIfRequired(undeployDescription, found)));
    }

    @Override
    public DeploymentResult undeploy(final Set<UndeployDescription> undeployDescriptions) throws IOException {
        final DeploymentResult failedResult = validateDeployment(undeployDescriptions);
        if (failedResult != null) {
            return failedResult;
        }
        final Set<DeploymentDescription> currentDeployments = getDeployments();
        final Set<UndeployDescription> toRemove = new LinkedHashSet<>(undeployDescriptions.size());
        final Set<UndeployDescription> skipped = new LinkedHashSet<>(undeployDescriptions.size());
        boolean failOnMissing = false;
        for (UndeployDescription undeployDescription : undeployDescriptions) {
            if (undeployDescription.isFailOnMissing()) {
                failOnMissing = true;
            }
            if (failOnMissing) {
                toRemove.add(undeployDescription);
            } else {
                final DeploymentDescription found = findDeployment(currentDeployments, undeployDescription);
                if (found == null) {
                    skipped.add(undeployDescription);
                } else {
                    toRemove.add(copyIfRequired(undeployDescription, found));
                }
            }
        }
        if (toRemove.isEmpty()) {
            if (failOnMissing) {
                return new DeploymentResult("No deployments were found matching any of the following deployments: %s",
                        undeployDescriptions);
            }
            return DeploymentResult.SUCCESSFUL;
        }
        // If this is expecting to fail we need to add the skipped ones so execution will fail and the failure message
        // will be accurate.
        if (failOnMissing) {
            toRemove.addAll(skipped);
        }
        return execute(DeploymentOperations.createUndeployOperation(toRemove));
    }

    @Override
    public Set<DeploymentDescription> getDeployments() throws IOException {
        final ModelNode readDeployments = Operations.createOperation(READ_CHILDREN_NAMES_OPERATION);
        readDeployments.get(CHILD_TYPE).set(DEPLOYMENT);
        if (containerDescription.isDomain()) {
            // Represents the deployment and each server-group the deployment belongs to
            final Map<String, Set<String>> serverGroupDeployments = new LinkedHashMap<>();
            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
            // Get all the deployments in the deployment repository
            builder.addStep(readDeployments);

            final ModelNode address = createAddress(SERVER_GROUP, "*", DEPLOYMENT, "*");
            // Get all the deployments that are deployed to server groups
            builder.addStep(Operations.createReadResourceOperation(address));

            final ModelNode result = client.execute(builder.build());
            if (Operations.isSuccessfulOutcome(result)) {
                final ModelNode results = Operations.readResult(result);
                // Load all deployments from the content repository
                for (ModelNode r : Operations.readResult(results.get("step-1")).asList()) {
                    serverGroupDeployments.put(r.asString(), new LinkedHashSet<String>());
                }
                // Add the server groups to the deployments which belong to server groups
                for (ModelNode r : Operations.readResult(results.get("step-2")).asList()) {
                    final List<Property> resultAddress = Operations.getOperationAddress(r).asPropertyList();
                    String serverGroup = null;
                    String deployment = null;
                    for (Property property : resultAddress) {
                        if (SERVER_GROUP.equals(property.getName())) {
                            serverGroup = property.getValue().asString();
                        } else if (DEPLOYMENT.equals(property.getName())) {
                            deployment = property.getValue().asString();
                        }
                    }
                    // Add the server-group to the map of deployments
                    final Set<String> serverGroups = serverGroupDeployments.get(deployment);
                    serverGroups.add(serverGroup);
                }
                // Convert the server-group mapping to deployment descriptions
                final Set<DeploymentDescription> deployments = new LinkedHashSet<>();
                for (Map.Entry<String, Set<String>> entry : serverGroupDeployments.entrySet()) {
                    final String name = entry.getKey();
                    deployments.add(SimpleDeploymentDescription.of(name, entry.getValue()));
                }
                return deployments;
            }
            throw new RuntimeException(
                    "Failed to get listing of deployments. Reason: " + Operations.getFailureDescription(result).asString());
        }
        // Handle servers other than managed domains
        final Set<DeploymentDescription> deployments = new LinkedHashSet<>();
        final ModelNode result = client.execute(readDeployments);
        if (Operations.isSuccessfulOutcome(result)) {
            for (ModelNode deployment : Operations.readResult(result).asList()) {
                final String deploymentName = deployment.asString();
                deployments.add(SimpleDeploymentDescription.of(deploymentName));
            }
            return deployments;
        }
        throw new RuntimeException(
                "Failed to get listing of deployments. Reason: " + Operations.getFailureDescription(result).asString());
    }

    @Override
    public Set<DeploymentDescription> getDeployments(final String serverGroup) throws IOException {
        Assertions.requiresNotNullOrNotEmptyParameter("serverGroup", serverGroup);
        if (!containerDescription.isDomain()) {
            throw new IllegalStateException("Server is not a managed domain. Running container: " + containerDescription);
        }
        // Get all the current deployments and filter them to only return deployments located on this server group
        final Set<DeploymentDescription> deployments = new LinkedHashSet<>();
        for (DeploymentDescription deployment : getDeployments()) {
            final Set<String> serverGroups = Collections.unmodifiableSet(deployment.getServerGroups());
            if (serverGroups.contains(serverGroup)) {
                deployments.add(deployment);
            }
        }
        return deployments;
    }

    @Override
    public Set<String> getDeploymentNames() throws IOException {
        final ModelNode readDeployments = Operations.createOperation(READ_CHILDREN_NAMES_OPERATION);
        readDeployments.get(CHILD_TYPE).set(DEPLOYMENT);
        final Set<String> deployments = new LinkedHashSet<>();
        final ModelNode result = client.execute(readDeployments);
        if (Operations.isSuccessfulOutcome(result)) {
            for (ModelNode deployment : Operations.readResult(result).asList()) {
                final String deploymentName = deployment.asString();
                deployments.add(deploymentName);
            }
            return deployments;
        }
        throw new RuntimeException(
                "Failed to get listing of deployments. Reason: " + Operations.getFailureDescription(result).asString());
    }

    @Override
    public boolean hasDeployment(final String name) {
        return hasDeployment(DeploymentOperations.EMPTY_ADDRESS, Assertions.requiresNotNullOrNotEmptyParameter("name", name));
    }

    @Override
    public boolean hasDeployment(final String name, final String serverGroup) {
        final ModelNode address = DeploymentOperations.createAddress(SERVER_GROUP,
                Assertions.requiresNotNullOrNotEmptyParameter("serverGroup", serverGroup));
        return hasDeployment(address, Assertions.requiresNotNullOrNotEmptyParameter("name", name));
    }

    @Override
    public boolean isEnabled(final String name) {
        return isEnabled(
                DeploymentOperations.createAddress(DEPLOYMENT, Assertions.requiresNotNullOrNotEmptyParameter("name", name)));
    }

    @Override
    public boolean isEnabled(final String name, final String serverGroup) {
        return isEnabled(DeploymentOperations.createAddress(
                SERVER_GROUP,
                Assertions.requiresNotNullOrNotEmptyParameter("serverGroup", serverGroup),
                DEPLOYMENT,
                Assertions.requiresNotNullOrNotEmptyParameter("name", name)));
    }

    private boolean hasDeployment(final ModelNode address, final String name) {
        final ModelNode op = Operations.createOperation(READ_CHILDREN_NAMES_OPERATION, address);
        op.get(CHILD_TYPE).set(DEPLOYMENT);
        final ModelNode listDeploymentsResult;
        try {
            listDeploymentsResult = client.execute(op);
            // Check to make sure there is an outcome
            if (Operations.isSuccessfulOutcome(listDeploymentsResult)) {
                final List<ModelNode> deployments = Operations.readResult(listDeploymentsResult).asList();
                for (ModelNode deployment : deployments) {
                    if (name.equals(deployment.asString())) {
                        return true;
                    }
                }
            } else {
                throw new IllegalStateException(Operations.getFailureDescription(listDeploymentsResult).asString());
            }
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Could not execute operation '%s'", op), e);
        }
        return false;
    }

    private boolean isEnabled(final ModelNode address) {
        final ModelNode op = Operations.createReadAttributeOperation(address, DeploymentOperations.ENABLED);
        try {
            final ModelNode result = client.execute(op);
            // Check to make sure there is an outcome
            if (Operations.isSuccessfulOutcome(result)) {
                return Operations.readResult(result).asBoolean();
            } else {
                throw new IllegalStateException(Operations.getFailureDescription(result).asString());
            }
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Could not execute operation '%s'", op), e);
        }
    }

    private DeploymentResult execute(final Operation op) throws IOException {
        final ModelNode result = client.execute(op);
        return new DeploymentResult(result);
    }

    private DeploymentDescription getServerGroupDeployment(final String name) throws IOException {
        final Set<String> serverGroups = new LinkedHashSet<>();
        final ModelNode address = createAddress(SERVER_GROUP, "*", DEPLOYMENT, name);

        final ModelNode result = client.execute(Operations.createReadResourceOperation(address));
        if (Operations.isSuccessfulOutcome(result)) {
            // Load the server groups
            for (ModelNode r : Operations.readResult(result).asList()) {
                final List<Property> resultAddress = Operations.getOperationAddress(r).asPropertyList();
                String foundServerGroup = null;
                for (Property property : resultAddress) {
                    if (SERVER_GROUP.equals(property.getName())) {
                        foundServerGroup = property.getValue().asString();
                    }
                }
                // Add the server-group to the map of deployments
                serverGroups.add(foundServerGroup);
            }
            return SimpleDeploymentDescription.of(name, serverGroups);
        }
        throw new RuntimeException(
                "Failed to get listing of deployments. Reason: " + Operations.getFailureDescription(result).asString());
    }

    private DeploymentResult validateDeployment(final DeploymentDescription deployment) {
        Assert.checkNotNullParam("deployment", deployment);
        final Set<String> serverGroups = deployment.getServerGroups();
        if (containerDescription.isDomain() && serverGroups.isEmpty()) {
            return new DeploymentResult("No server groups were defined for the deployment operation. Deployment: %s",
                    deployment);
        } else if (!containerDescription.isDomain() && !serverGroups.isEmpty()) {
            return new DeploymentResult("Server is not a managed domain, but server groups were defined. Deployment: %s",
                    deployment);
        }
        return null;
    }

    private DeploymentResult validateDeployment(final Set<? extends DeploymentDescription> deployments) {
        Assertions.requiresNotNullOrNotEmptyParameter("deployments", deployments);

        final Collection<DeploymentDescription> missingServerGroups = new LinkedHashSet<>();
        final Collection<DeploymentDescription> standaloneWithServerGroups = new LinkedHashSet<>();
        boolean error = false;
        for (DeploymentDescription deployment : deployments) {
            final Set<String> serverGroups = deployment.getServerGroups();
            if (containerDescription.isDomain() && serverGroups.isEmpty()) {
                error = true;
                missingServerGroups.add(deployment);
            } else if (!containerDescription.isDomain() && !serverGroups.isEmpty()) {
                error = true;
                standaloneWithServerGroups.add(deployment);
            }
        }
        if (error) {
            final StringBuilder message = new StringBuilder();
            if (!missingServerGroups.isEmpty()) {
                message.append("No server groups were defined on the following deployments: ")
                        .append(missingServerGroups);
            }
            if (!standaloneWithServerGroups.isEmpty()) {
                message.append("Server is not a managed domain but the following deployments had server groups defined: ")
                        .append(standaloneWithServerGroups);
            }
            return new DeploymentResult(message);
        }
        return null;
    }

    private static DeploymentDescription findDeployment(final Iterable<DeploymentDescription> deployments,
            final DeploymentDescription deployment) {
        for (DeploymentDescription deploymentDescription : deployments) {
            if (deploymentDescription.getName().equals(deployment.getName())) {
                return deploymentDescription;
            }
        }
        return null;
    }

    private static UndeployDescription copyIfRequired(final UndeployDescription undeployDescription,
            final DeploymentDescription found) {
        final Collection<String> unmatched = new LinkedHashSet<>(undeployDescription.getServerGroups());
        unmatched.removeAll(found.getServerGroups());
        if (unmatched.isEmpty()) {
            return undeployDescription;
        }
        final Collection<String> toRemove = new LinkedHashSet<>(undeployDescription.getServerGroups());
        toRemove.removeAll(unmatched);
        // The parameter has more server-groups than the deployment found, create a new UndeployDescription description
        // using only the server-groups the deployment exists on
        return UndeployDescription.of(undeployDescription.getName())
                .addServerGroups(toRemove)
                .setFailOnMissing(undeployDescription.isFailOnMissing())
                .setRemoveContent(undeployDescription.isRemoveContent());
    }

    private class LazyContainerDescription implements ContainerDescription {
        private volatile ContainerDescription containerDescription;

        @Override
        public String getProductName() {
            return get().getProductName();
        }

        @Override
        public String getProductVersion() {
            return get().getProductVersion();
        }

        @Override
        public String getReleaseVersion() {
            return get().getReleaseVersion();
        }

        @Override
        public String getLaunchType() {
            return get().getLaunchType();
        }

        @Override
        public boolean isDomain() {
            return get().isDomain();
        }

        @Override
        public String toString() {
            return get().toString();
        }

        private ContainerDescription get() {
            if (containerDescription == null) {
                synchronized (this) {
                    if (containerDescription == null) {
                        try {
                            containerDescription = ServerHelper.getContainerDescription(client);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                }
            }
            return containerDescription;
        }
    }
}
