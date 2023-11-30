/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.core;

import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;

/**
 * An error indicating an operation has failed to execute.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("WeakerAccess")
public class OperationExecutionException extends RuntimeException {
    private final ModelNode result;

    /**
     * Creates a new exception with the failure message from the result.
     *
     * @param operation the operation which failed
     * @param result    the result of the operation
     */
    public OperationExecutionException(final Operation operation, final ModelNode result) {
        this(null, operation, result);
    }

    /**
     * Creates a new exception with the failure message from the result.
     *
     * @param operation the operation which failed
     * @param result    the result of the operation
     */
    public OperationExecutionException(final ModelNode operation, final ModelNode result) {
        this(null, operation, result);
    }

    /**
     * Creates a new exception with the failure message from the result.
     *
     * @param message   the message to prepend to the failure message
     * @param operation the operation which failed
     * @param result    the result of the operation
     */
    public OperationExecutionException(final String message, final Operation operation, final ModelNode result) {
        this(message, operation.getOperation(), result);
    }

    /**
     * Creates a new exception with the failure message from the result.
     *
     * @param message   the message to prepend to the failure message
     * @param operation the operation which failed
     * @param result    the result of the operation
     */
    public OperationExecutionException(final String message, final ModelNode operation, final ModelNode result) {
        super(formatMessage(message, operation, result));
        this.result = result;
        this.result.protect();
    }

    /**
     * Returns the result from the operation executed.
     *
     * @return the result of the operation
     */
    @SuppressWarnings("unused")
    public ModelNode getExecutionResult() {
        return result;
    }

    private static String formatMessage(final String message, final ModelNode operation, final ModelNode result) {
        if (message == null) {
            return String.format("Failed to execute %s%nReason:%s", operation,
                    Operations.getFailureDescription(result).asString());
        }
        final String msg = (message.endsWith(".") ? message : message + ".");
        return String.format("%s Failed to execute %s%nReason:%s", msg, operation,
                Operations.getFailureDescription(result).asString());
    }
}
