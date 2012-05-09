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

package org.jboss.as.plugin.server;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.plugin.common.ConnectionInfo;
import org.jboss.as.plugin.common.Files;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ServerInfo {
    private final ConnectionInfo connectionInfo;
    private final File jbossHome;
    private final File modulesPath;
    private final File bundlesPath;
    private final String[] jvmArgs;
    private final String javaHome;
    private final String serverConfig;
    private final long startupTimeout;

    private ServerInfo(final ConnectionInfo connectionInfo, final String javaHome, final File jbossHome, final String modulesPath, final String bundlesPath, final String[] jvmArgs, final String serverConfig, final long startupTimeout) {
        this.connectionInfo = connectionInfo;
        this.javaHome = javaHome;
        this.jbossHome = jbossHome;
        this.modulesPath = (modulesPath == null ? Files.createFile(jbossHome, "modules") : new File(modulesPath));
        this.bundlesPath = (bundlesPath == null ? Files.createFile(jbossHome, "bundles") : new File(bundlesPath));
        this.jvmArgs = jvmArgs;
        this.serverConfig = serverConfig;
        this.startupTimeout = startupTimeout;
    }

    public static ServerInfo of(final ConnectionInfo connectionInfo, final String javaHome, final File jbossHome, final String modulesPath, final String bundlesPath, final String[] jvmArgs, final String serverConfig, final long startupTimeout) {
        return new ServerInfo(connectionInfo, javaHome, jbossHome, modulesPath, bundlesPath, jvmArgs, serverConfig, startupTimeout);
    }

    public ConnectionInfo getConnectionInfo() {
        return connectionInfo;
    }

    public File getJbossHome() {
        return jbossHome;
    }

    public File getModulesPath() {
        return modulesPath;
    }

    public File getBundlesPath() {
        return bundlesPath;
    }

    public String[] getJvmArgs() {
        return jvmArgs;
    }

    public String getJavaHome() {
        return javaHome;
    }

    public String getServerConfig() {
        return serverConfig;
    }

    public long getStartupTimeout() {
        return startupTimeout;
    }
}
