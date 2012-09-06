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

import org.apache.maven.plugin.MojoFailureException;

/**
 * Wrapped exception for the {@link MojoFailureException}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DeploymentFailureException extends MojoFailureException {
    public DeploymentFailureException(final Object source, final String shortMessage, final String longMessage) {
        super(source, shortMessage, longMessage);
    }

    public DeploymentFailureException(final String message) {
        super(message);
    }

    public DeploymentFailureException(final String format, final Object... args) {
        this(String.format(format, args));
    }

    public DeploymentFailureException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public DeploymentFailureException(final Throwable cause, final String format, final Object... args) {
        this(String.format(format, args), cause);
    }
}
