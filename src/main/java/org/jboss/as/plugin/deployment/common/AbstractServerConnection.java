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

package org.jboss.as.plugin.deployment.common;

import org.apache.maven.plugin.AbstractMojo;
import org.jboss.as.controller.client.ModelControllerClient;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * The default implementation for connecting to a running AS7 instance
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author Stuart Douglas
 * @requiresDependencyResolution runtime
 */
public abstract class AbstractServerConnection extends AbstractMojo {
    // These will be moved org.jboss.as.controller.client.helpers.ClientConstants next release.

    private volatile InetAddress address = null;

    private volatile ModelControllerClient client = null;

    /**
     * Specifies the host name of the server where the deployment plan should be executed.
     *
     * @parameter default-value="localhost"
     */
    private String hostname;

    /**
     * Specifies the port number the server is listening on.
     *
     * @parameter default-value="9999"
     */
    private int port;

    /**
     * Specifies the username to use if prompted to authenticate by the server.
     *
     * If no username is specified and the server requests authentication the user
     * will be prompted to supply the username,
     *
     * @parameter
     */
    private String username;

    /**
     * Specifies the password to use if prompted to authenticate by the server.
     *
     * If no password is specified and the server requests authentication the user
     * will be prompted to supply the password,
     *
     * @parameter
     */
    private String password;

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
    public final int port() {
        return port;
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
     *
     * @throws java.net.UnknownHostException if the host name was not found.
     */
    protected final InetAddress hostAddress() throws UnknownHostException {
        // Lazy load the address
        if (address == null) {
            synchronized (this) {
                if (address == null) {
                    address = InetAddress.getByName(hostname());
                }
            }
        }
        return address;
    }

    /**
     * Creates a model controller client.
     *
     * @return the client.
     *
     * @throws java.net.UnknownHostException if the host name does not exist.
     */
    protected final ModelControllerClient client() throws UnknownHostException {
        // Lazy load the client
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = ModelControllerClient.Factory.create(hostAddress(), port(),
                                                                  new ClientCallbackHandler(username, password));
                }
            }
        }
        return client;
    }
}
