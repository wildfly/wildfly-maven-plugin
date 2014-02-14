/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.common;

import java.net.InetAddress;
import javax.security.auth.callback.CallbackHandler;

/**
 * Holds information on how to connect to the WildFly Application Server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface ConnectionInfo {

    /**
     * The protocol used to connect to the server for management operations.
     *
     * @return the protocol or {@code null} for the default
     */
    String getProtocol();

    /**
     * The port number of the server to deploy to.
     *
     * @return the port number to deploy to
     */
    int getPort();

    /**
     * Creates gets the address to the host name.
     *
     * @return the address
     */
    InetAddress getHostAddress();

    /**
     * The callback handler for authentication.
     *
     * @return the callback handler
     */
    CallbackHandler getCallbackHandler();
}
