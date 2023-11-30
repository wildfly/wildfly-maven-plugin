/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * Wrapped exception for {@link MojoExecutionException}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class MojoDeploymentException extends MojoExecutionException {

    public MojoDeploymentException(final String message, final Exception cause) {
        super(message, cause);
    }

    public MojoDeploymentException(final Exception cause, final String format, final Object... args) {
        this(String.format(format, args), cause);
    }

    public MojoDeploymentException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public MojoDeploymentException(final Throwable cause, final String format, final Object... args) {
        this(String.format(format, args), cause);
    }

    public MojoDeploymentException(final String message) {
        super(message);
    }

    public MojoDeploymentException(final String format, final Object... args) {
        this(String.format(format, args));
    }
}
