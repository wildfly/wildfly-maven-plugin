/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.core;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;

/**
 * Represents the results of a deployment.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("unused")
public class DeploymentResult {

    static final DeploymentResult SUCCESSFUL = new DeploymentResult();

    private final boolean successful;
    private final String failureMessage;
    private final ModelNode result;

    private DeploymentResult() {
        successful = true;
        failureMessage = null;
        result = new ModelNode();
        result.protect();
    }

    /**
     * Creates a new deployment result based on the DMR result from the deployment operation.
     *
     * @param result the DMR result from the operation
     */
    DeploymentResult(final ModelNode result) {
        successful = Operations.isSuccessfulOutcome(result);
        if (successful) {
            failureMessage = null;
        } else {
            failureMessage = Operations.getFailureDescription(result).asString();
        }
        this.result = result.clone();
        this.result.protect();
    }

    /**
     * Creates an unsuccessful result with the failure description.
     *
     * @param failureMessage the failure description
     */
    DeploymentResult(final CharSequence failureMessage) {
        successful = false;
        this.failureMessage = failureMessage.toString();
        result = new ModelNode();
        result.protect();
    }

    /**
     * Creates an unsuccessful result with the failure description.
     *
     * @param format the format used for the failure description
     * @param args   the arguments for the format pattern
     */
    DeploymentResult(final String format, final Object... args) {
        this(String.format(format, args));
    }

    /**
     * Determines if the deployment was successful or not.
     *
     * @return {@code true} if the deployment was successful, otherwise {@code false}
     */
    public boolean successful() {
        return successful;
    }

    /**
     * Checks to see if the deployment was successful and if not throws a {@link DeploymentException} with the failure
     * message.
     *
     * @throws DeploymentException if the deployment was not successful
     */
    public void assertSuccess() throws DeploymentException {
        if (!successful) {
            throw new DeploymentException(failureMessage);
        }
    }

    /**
     * Returns the failure message if the deployment was not {@linkplain #successful() successful}.
     *
     * @return the failure description or {@code null} if the deployment was {@linkplain #successful() successful}.
     */
    public String getFailureMessage() {
        return failureMessage;
    }

    /**
     * The result from the deployment operation.
     * <p>
     * In some cases the result may be {@linkplain org.jboss.dmr.ModelType#UNDEFINED undefined}.
     * </p>
     *
     * @return the result
     */
    public ModelNode asModelNode() {
        return result;
    }
}
