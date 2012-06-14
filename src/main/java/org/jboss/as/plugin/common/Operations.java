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

package org.jboss.as.plugin.common;

import java.util.List;

import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Operations {

    public static final String CHILD_TYPE = "child-type";
    public static final String ENABLE = "enable";
    public static final String PROFILE = "profile";
    public static final String READ_ATTRIBUTE = "read-attribute";
    public static final String READ_CHILDREN_NAMES = "read-children-names";
    public static final String READ_RESOURCE = "read-resource";
    public static final String RECURSIVE = "recursive";
    public static final String RELOAD = "reload";
    public static final String REMOVE = "remove";
    public static final String SERVER_STATE = "server-state";
    public static final String SHUTDOWN = "shutdown";

    /**
     * Checks the result for a successful operation.
     *
     * @param result the result of executing an operation
     *
     * @return {@code true} if the operation was successful, otherwise {@code false}
     */
    public static boolean successful(final ModelNode result) {
        return result.get(ClientConstants.OUTCOME).asString().equals(ClientConstants.SUCCESS);
    }

    /**
     * Parses the result and returns the failure description. If the result was successful, an empty string is
     * returned.
     *
     * @param result the result of executing an operation
     *
     * @return the failure message or an empty string
     */
    public static String getFailureDescription(final ModelNode result) {
        if (successful(result)) {
            return "";
        }
        final String msg;
        if (result.hasDefined(ClientConstants.FAILURE_DESCRIPTION)) {
            if (result.hasDefined(ClientConstants.OP)) {
                msg = String.format("Operation '%s' at address '%s' failed: %s", result.get(ClientConstants.OP), result.get(ClientConstants.OP_ADDR), result.get(ClientConstants.FAILURE_DESCRIPTION));
            } else {
                msg = String.format("Operation failed: %s", result.get(ClientConstants.FAILURE_DESCRIPTION));
            }
        } else {
            msg = String.format("An unexpected response was found checking the deployment. Result: %s", result);
        }
        return msg;
    }

    /**
     * Creates an add operation.
     *
     * @param address the address for the operation
     *
     * @return the operation
     */
    public static ModelNode createAddOperation(final ModelNode address) {
        return createOperation(ClientConstants.ADD, address);
    }

    /**
     * Creates a remove operation.
     *
     * @param address the address for the operation
     *
     * @return the operation
     */
    public static ModelNode createRemoveOperation(final ModelNode address, final boolean recursive) {
        return createOperation(REMOVE, address, recursive);
    }

    /**
     * Creates an operation to list the deployments.
     *
     * @return the operation
     */
    public static ModelNode createListDeploymentsOperation() {
        final ModelNode op = createOperation(Operations.READ_CHILDREN_NAMES);
        op.get(Operations.CHILD_TYPE).set(ClientConstants.DEPLOYMENT);
        return op;
    }

    /**
     * Creates a composite operation with an empty address and empty steps that will rollback on a runtime failure.
     *
     * @return the operation
     */
    public static ModelNode createCompositeOperation() {
        final ModelNode op = createOperation(ClientConstants.COMPOSITE);
        op.get(ClientConstants.OP).set(ClientConstants.COMPOSITE);
        op.get(ClientConstants.OP_ADDR).setEmptyList();
        op.get(ClientConstants.ROLLBACK_ON_RUNTIME_FAILURE).set(true);
        op.get(ClientConstants.STEPS).setEmptyList();
        return op;
    }

    public static ModelNode createReadAttributeOperation(final String attributeName) {
        ModelNode op = new ModelNode();
        op.get(ClientConstants.OP_ADDR).setEmptyList();
        op.get(ClientConstants.OP).set(READ_ATTRIBUTE);
        op.get(ClientConstants.NAME).set(attributeName);
        return op;
    }

    /**
     * Creates a generic operation with no address.
     *
     * @param operation the operation to create
     *
     * @return the operation
     */
    public static ModelNode createOperation(final String operation) {
        final ModelNode op = new ModelNode();
        op.get(ClientConstants.OP).set(operation);
        op.get(ClientConstants.OP_ADDR).setEmptyList();
        return op;
    }

    /**
     * Creates an operation.
     *
     * @param operation the operation name
     * @param address   the address for the operation
     *
     * @return the operation
     *
     * @throws IllegalArgumentException if the address is not of type {@link ModelType#LIST}
     */
    public static ModelNode createOperation(final String operation, final ModelNode address) {
        if (address.getType() != ModelType.LIST) {
            throw new IllegalArgumentException("The address type must be a list.");
        }
        final ModelNode op = createOperation(operation);
        op.get(ClientConstants.OP_ADDR).set(address);
        return op;
    }

    /**
     * Creates an operation.
     *
     * @param operation the operation name
     * @param address   the address for the operation
     * @param recursive whether the operation is recursive or not
     *
     * @return the operation
     *
     * @throws IllegalArgumentException if the address is not of type {@link ModelType#LIST}
     */
    public static ModelNode createOperation(final String operation, final ModelNode address, final boolean recursive) {
        final ModelNode op = createOperation(operation, address);
        op.get(RECURSIVE).set(recursive);
        return op;
    }

    /**
     * Finds the last entry of the address list and returns it as a property.
     *
     * @param address the address to get the last part of
     *
     * @return the last part of the address
     *
     * @throws IllegalArgumentException if the address is not of type {@link ModelType#LIST} or is empty
     */
    public static Property getChildAddress(final ModelNode address) {
        if (address.getType() != ModelType.LIST) {
            throw new IllegalArgumentException("The address type must be a list.");
        }
        final List<Property> addressParts = address.asPropertyList();
        if (addressParts.isEmpty()) {
            throw new IllegalArgumentException("The address is empty.");
        }
        return addressParts.get(addressParts.size() - 1);
    }

    /**
     * Finds the parent address, everything before the last address part.
     *
     * @param address the address to get the parent
     *
     * @return the parent address
     *
     * @throws IllegalArgumentException if the address is not of type {@link ModelType#LIST} or is empty
     */
    public static ModelNode getParentAddress(final ModelNode address) {
        if (address.getType() != ModelType.LIST) {
            throw new IllegalArgumentException("The address type must be a list.");
        }
        final ModelNode result = new ModelNode();
        final List<Property> addressParts = address.asPropertyList();
        if (addressParts.isEmpty()) {
            throw new IllegalArgumentException("The address is empty.");
        }
        for (int i = 0; i < addressParts.size() - 1; ++i) {
            final Property property = addressParts.get(i);
            result.add(property.getName(), property.getValue());
        }
        return result;
    }

    public static class CompositeOperationBuilder {
        private final ModelNode op;

        private CompositeOperationBuilder(final ModelNode op) {
            this.op = op;
        }

        public static CompositeOperationBuilder create() {
            return new CompositeOperationBuilder(createCompositeOperation());
        }

        public Operation build() {
            return OperationBuilder.create(op).build();
        }

        public CompositeOperationBuilder addStep(final ModelNode op) {
            if (op.hasDefined(ClientConstants.OP)) {
                this.op.get(ClientConstants.STEPS).add(op);
            } else {
                throw new IllegalArgumentException(String.format("Invalid operations: %s", op));
            }
            return this;
        }
    }
}
