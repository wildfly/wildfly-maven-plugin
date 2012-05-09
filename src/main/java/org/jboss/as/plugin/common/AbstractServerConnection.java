/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.security.auth.callback.CallbackHandler;

import org.apache.maven.plugin.AbstractMojo;
import org.jboss.as.plugin.deployment.domain.Domain;

/**
 * The default implementation for connecting to a running AS7 instance
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author Stuart Douglas
 * @requiresDependencyResolution runtime
 */
public abstract class AbstractServerConnection extends AbstractMojo implements ConnectionInfo {
    // These will be moved org.jboss.as.controller.client.helpers.ClientConstants next release.

    private volatile InetAddress address = null;

    private volatile CallbackHandler handler;

    /**
     * Specifies the host name of the server where the deployment plan should be executed.
     *
     * @parameter default-value="localhost" expression="${deploy.hostname}"
     */
    private String hostname;

    /**
     * Specifies the port number the server is listening on.
     *
     * @parameter default-value="9999" expression="${deploy.port}"
     */
    private int port;

    /**
     * Specifies the username to use if prompted to authenticate by the server.
     * <p/>
     * If no username is specified and the server requests authentication the user
     * will be prompted to supply the username,
     *
     * @parameter expression="${deploy.username}"
     */
    private String username;

    /**
     * Specifies the password to use if prompted to authenticate by the server.
     * <p/>
     * If no password is specified and the server requests authentication the user
     * will be prompted to supply the password,
     *
     * @parameter expression="${deploy.password}"
     */
    private String password;

    /**
     * Indicates if this should be a domain deployment.
     *
     * @parameter
     */
    private Domain domain;

    /**
     * The hostname to deploy the archive to. The default is localhost.
     *
     * @return the hostname of the server.
     */
    public final String hostname() {
        return hostname;
    }

    /**
     * The port number of the server to deploy to. The default is 9999.
     *
     * @return the port number to deploy to.
     */
    @Override
    public final int getPort() {
        return port;
    }

    /**
     * Returns {@code true} if the connection is for a domain server, otherwise {@code false}.
     *
     * @return {@code true} if the connection is for a domain server, otherwise {@code false}.
     */
    public final boolean isDomainServer() {
        return domain != null;
    }

    /**
     * Returns the domain if {@link #isDomainServer()} returns {@code true}, otherwise {@code null} is returned.
     *
     * @return the domain or {@code null}.
     */
    public final Domain getDomain() {
        return domain;
    }

    /**
     * The goal of the deployment.
     *
     * @return the goal of the deployment.
     */
    public abstract String goal();

    /**
     * Creates gets the address to the host name.
     *
     * @return the address.
     */
    @Override
    public final InetAddress getHostAddress() {
        InetAddress result = address;
        // Lazy load the address
        if (result == null) {
            synchronized (this) {
                result = address;
                if (result == null) {
                    try {
                        result = address = InetAddress.getByName(hostname());
                    } catch (UnknownHostException e) {
                        throw new IllegalArgumentException(String.format("Host name '%s' is invalid.", hostname), e);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public final CallbackHandler getCallbackHandler() {
        CallbackHandler result = handler;
        if (result == null) {
            synchronized (this) {
                result = handler;
                if (result == null) {
                    result = handler = new ClientCallbackHandler(username, password);
                }
            }
        }
        return result;
    }
}
