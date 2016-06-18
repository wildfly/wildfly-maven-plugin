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
        this.result = (successful ? Operations.readResult(result).clone() : new ModelNode());
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
        successful = false;
        this.failureMessage = String.format(format, args);
        result = new ModelNode();
        result.protect();
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
