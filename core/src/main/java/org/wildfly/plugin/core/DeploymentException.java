/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.core;

/**
 * An exception that represents a deployment error.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({ "WeakerAccess", "unused" })
public class DeploymentException extends RuntimeException {

    /**
     * Creates a new deployment exception.
     *
     * @param message the message for the exception
     */
    public DeploymentException(final String message) {
        super(message);
    }

    /**
     * Creates a new deployment exception.
     *
     * @param cause the cause of the exception
     */
    public DeploymentException(final Throwable cause) {
        super(cause);
    }

    /**
     * Creates a new deployment exception.
     *
     * @param message the message for the exception
     * @param cause   the cause of the exception
     */
    public DeploymentException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
