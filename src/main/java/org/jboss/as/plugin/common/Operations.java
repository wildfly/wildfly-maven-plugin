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

import java.io.File;
import java.io.InputStream;
import java.util.List;

import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * A helper for creating operations.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Operations extends ClientConstants {

    public static final String CHILD_TYPE = "child-type";
    public static final String ENABLE = "enable";
    public static final String LAUNCH_TYPE = "launch-type";
    public static final String PROFILE = "profile";
    public static final String READ_ATTRIBUTE_OPERATION = "read-attribute";
    public static final String READ_CHILDREN_NAMES = "read-children-names";
    public static final String READ_RESOURCE = "read-resource";
    public static final String READ_RESOURCE_OPERATION = "read-resource";
    public static final String RECURSIVE = "recursive";
    public static final String RELOAD = "reload";
    public static final String REMOVE_OPERATION = "remove";
    public static final String SERVER_STATE = "server-state";
    public static final String SHUTDOWN = "shutdown";
    public static final String UNDEFINE_ATTRIBUTE_OPERATION = "undefine-attribute";
    public static final String VALUE = "value";
    public static final String WRITE_ATTRIBUTE_OPERATION = "write-attribute";

    /**
     * Checks the result for a successful operation outcome.
     *
     * @param outcome the result of executing an operation
     *
     * @return {@code true} if the operation was successful, otherwise {@code false}
     */
    public static boolean isSuccessfulOutcome(final ModelNode outcome) {
        return outcome.get(OUTCOME).asString().equals(SUCCESS);
    }

    /**
     * Checks the result for a successful operation.
     *
     * @param result the result of executing an operation
     *
     * @return {@code true} if the operation was successful, otherwise {@code false}
     */
    public static boolean successful(final ModelNode result) {
        return isSuccessfulOutcome(result);
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
        if (isSuccessfulOutcome(result)) {
            return "";
        }
        final String msg;
        if (result.hasDefined(FAILURE_DESCRIPTION)) {
            if (result.hasDefined(OP)) {
                msg = String.format("Operation '%s' at address '%s' failed: %s", result.get(OP), result.get(OP_ADDR), result
                        .get(FAILURE_DESCRIPTION));
            } else {
                msg = String.format("Operation failed: %s", result.get(FAILURE_DESCRIPTION));
            }
        } else {
            msg = String.format("An unexpected response was found checking the deployment. Result: %s", result);
        }
        return msg;
    }

    /**
     * Returns the address for the operation.
     *
     * @param op the operation
     *
     * @return the operation address or a new undefined model node
     */
    public static ModelNode getOperationAddress(final ModelNode op) {
        return op.hasDefined(OP_ADDR) ? op.get(OP_ADDR) : new ModelNode();
    }

    /**
     * Creates an add operation.
     *
     * @param address the address for the operation
     *
     * @return the operation
     */
    public static ModelNode createAddOperation(final ModelNode address) {
        return createOperation(ADD, address);
    }

    /**
     * Creates a remove operation.
     *
     * @param address the address for the operation
     *
     * @return the operation
     */
    public static ModelNode createRemoveOperation(final ModelNode address) {
        return createOperation(REMOVE_OPERATION, address);
    }

    /**
     * Creates a remove operation.
     *
     * @param address the address for the operation
     *
     * @return the operation
     */
    public static ModelNode createRemoveOperation(final ModelNode address, final boolean recursive) {
        return createOperation(REMOVE_OPERATION, address, recursive);
    }

    /**
     * Creates an operation to list the deployments.
     *
     * @return the operation
     */
    public static ModelNode createListDeploymentsOperation() {
        final ModelNode op = createOperation(Operations.READ_CHILDREN_NAMES);
        op.get(Operations.CHILD_TYPE).set(DEPLOYMENT);
        return op;
    }

    /**
     * Creates a composite operation with an empty address and empty steps that will rollback on a runtime failure.
     * <p/>
     * By default the {@link ClientConstants#ROLLBACK_ON_RUNTIME_FAILURE} is set to {@code true} to rollback all
     * operations if one fails.
     *
     * @return the operation
     */
    public static ModelNode createCompositeOperation() {
        final ModelNode op = createOperation(COMPOSITE);
        op.get(ROLLBACK_ON_RUNTIME_FAILURE).set(true);
        op.get(STEPS).setEmptyList();
        return op;
    }

    /**
     * Creates an operation to read the attribute represented by the {@code attributeName} parameter.
     *
     * @param attributeName the name of the parameter to read
     *
     * @return the operation
     */
    public static ModelNode createReadAttributeOperation(final String attributeName) {
        ModelNode op = new ModelNode();
        op.get(OP_ADDR).setEmptyList();
        op.get(OP).set(READ_ATTRIBUTE_OPERATION);
        op.get(NAME).set(attributeName);
        return op;
    }


    /**
     * Creates an operation to read the attribute represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the read attribute for
     * @param attributeName the name of the parameter to read
     *
     * @return the operation
     */
    public static ModelNode createReadAttributeOperation(final ModelNode address, final String attributeName) {
        final ModelNode op = createOperation(READ_ATTRIBUTE_OPERATION, address);
        op.get(NAME).set(attributeName);
        return op;
    }

    /**
     * Creates a non-recursive operation to read a resource.
     *
     * @param address the address to create the read for
     *
     * @return the operation
     */
    public static ModelNode createReadResourceOperation(final ModelNode address) {
        return createReadResourceOperation(address, false);
    }

    /**
     * Creates an operation to read a resource.
     *
     * @param address   the address to create the read for
     * @param recursive whether to search recursively or not
     *
     * @return the operation
     */
    public static ModelNode createReadResourceOperation(final ModelNode address, final boolean recursive) {
        final ModelNode op = createOperation(READ_RESOURCE_OPERATION, address);
        op.get(RECURSIVE).set(recursive);
        return op;
    }

    /**
     * Creates an operation to undefine an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name attribute to undefine
     *
     * @return the operation
     */
    public static ModelNode createUndefineAttributeOperation(final ModelNode address, final String attributeName) {
        final ModelNode op = createOperation(UNDEFINE_ATTRIBUTE_OPERATION, address);
        op.get(NAME).set(attributeName);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     *
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final boolean value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     *
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final int value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     *
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final long value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     *
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final String value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     *
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final ModelNode value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    public static ModelNode createAddress(final String key, final String name) {
        final ModelNode address = new ModelNode().setEmptyList();
        address.add(key, name);
        return address;
    }

    /**
     * Creates a generic operation with an empty (root) address.
     *
     * @param operation the operation to create
     *
     * @return the operation
     */
    public static ModelNode createOperation(final String operation) {
        final ModelNode op = new ModelNode();
        op.get(OP).set(operation);
        op.get(OP_ADDR).setEmptyList();
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
     * @throws IllegalArgumentException if the address is not of type {@link org.jboss.dmr.ModelType#LIST}
     */
    public static ModelNode createOperation(final String operation, final ModelNode address) {
        if (address.getType() != ModelType.LIST) {
            throw new IllegalArgumentException("The address type must be a list.");
        }
        final ModelNode op = createOperation(operation);
        op.get(OP_ADDR).set(address);
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

    /**
     * Reads the result of an operation and returns the result as a string. If the operation does not have a {@link
     * #RESULT} attribute and empty string is returned.
     *
     * @param result the result of executing an operation
     *
     * @return the result of the operation or an empty string
     */
    public static String readResultAsString(final ModelNode result) {
        return (result.hasDefined(RESULT) ? result.get(RESULT).asString() : "");
    }

    /**
     * Reads the result of an operation and returns the result. If the operation does not have a {@link
     * ClientConstants#RESULT} attribute, a new undefined {@link org.jboss.dmr.ModelNode} is returned.
     *
     * @param result the result of executing an operation
     *
     * @return the result of the operation or a new undefined model node
     */
    public static ModelNode readResult(final ModelNode result) {
        return (result.hasDefined(RESULT) ? result.get(RESULT) : new ModelNode());
    }

    private static ModelNode createNoValueWriteOperation(final ModelNode address, final String attributeName) {
        final ModelNode op = createOperation(WRITE_ATTRIBUTE_OPERATION, address);
        op.get(NAME).set(attributeName);
        return op;
    }

    /**
     * A builder for building composite operations.
     * <p/>
     *
     * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
     */
    public static class CompositeOperationBuilder extends OperationBuilder {
        private final ModelNode op;

        private CompositeOperationBuilder(final ModelNode op) {
            super(op);
            this.op = op;
        }

        private CompositeOperationBuilder(final ModelNode op, final boolean autoCloseStreams) {
            super(op, autoCloseStreams);
            this.op = op;
        }

        /**
         * Creates a new builder.
         *
         * @return a new builder
         */
        public static CompositeOperationBuilder create() {
            return new CompositeOperationBuilder(createCompositeOperation());
        }

        /**
         * Creates a new builder.
         *
         * @param autoCloseStreams whether streams should be automatically closed
         *
         * @return a new builder
         */
        public static CompositeOperationBuilder create(final boolean autoCloseStreams) {
            return new CompositeOperationBuilder(createCompositeOperation(), autoCloseStreams);
        }

        /**
         * Adds a new operation to the composite operation.
         * <p/>
         * Note that subsequent calls after a {@link #build() build} invocation will result the operation being
         * appended to and could result in unexpected behaviour.
         *
         * @param op the operation to add
         *
         * @return the current builder
         */
        public CompositeOperationBuilder addStep(final ModelNode op) {
            this.op.get(STEPS).add(op);
            return this;
        }

        @Override
        public CompositeOperationBuilder addFileAsAttachment(final File file) {
            super.addFileAsAttachment(file);
            return this;
        }

        @Override
        public CompositeOperationBuilder addInputStream(final InputStream in) {
            super.addInputStream(in);
            return this;
        }
    }
}
