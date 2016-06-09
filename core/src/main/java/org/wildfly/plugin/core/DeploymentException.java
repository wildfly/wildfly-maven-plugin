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

/**
 * An exception that represents a deployment error.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"WeakerAccess", "unused"})
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
