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

package org.wildfly.plugin.server;

import java.io.File;

import org.wildfly.plugin.common.ConnectionInfo;
import org.wildfly.plugin.common.Files;

/**
 * Server configuration information.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ServerInfo {
    private final ConnectionInfo connectionInfo;
    private final File jbossHome;
    private final String modulesDir;
    private final String[] jvmArgs;
    private final String javaHome;
    private final String serverConfig;
    private final String propertiesFile;
    private final long startupTimeout;
    private final String bindAddress;

    /**
     * Create a new instance of ServerInfo using the provided ServerInfoBuilder instance.
     * @param builder ServerInfoBuilder instance
     */
    private ServerInfo(ServerInfoBuilder builder) {
        this.connectionInfo = builder.connectionInfo;
        this.javaHome = builder.javaHome;
        this.jbossHome = builder.jbossHome;
        this.modulesDir = (builder.modulesDir == null ? Files.createPath(jbossHome.getAbsolutePath(), "modules") : builder.modulesDir);
        this.jvmArgs = builder.jvmArgs;
        this.serverConfig = builder.serverConfig;
        this.propertiesFile = builder.propertiesFile;
        this.startupTimeout = builder.startupTimeout;
        this.bindAddress = builder.bindAddress;
    }

    /**
     * Builder for creating ServerInfo instances.
     */
    public static class ServerInfoBuilder {
        private ConnectionInfo connectionInfo;
        private File jbossHome;
        private String modulesDir;
        private String[] jvmArgs;
        private String javaHome;
        private String serverConfig;
        private String propertiesFile;
        private long startupTimeout;
        private String bindAddress;

        /**
         * Sets the connection information for the management operations.
         * @param connectionInfo the connection information for the management client
         * @return this builder instance
         */
        public ServerInfoBuilder withConnectionInfo(ConnectionInfo connectionInfo) {
            this.connectionInfo = connectionInfo;
            return this;
        }

        /**
         * Sets the JBoss Application Server home directory.
         * @param jbossHome the Java home directory
         * @return this builder instance
         */
        public ServerInfoBuilder withJbossHome(File jbossHome) {
            this.jbossHome = jbossHome;
            return this;
        }

        /**
         * Sets the directory for all the modules.
         * @param modulesDir the directory for the modules to use
         * @return this builder instance
         */
        public ServerInfoBuilder withModulesDir(String modulesDir) {
            this.modulesDir = modulesDir;
            return this;
        }

        /**
         * Sets the optional JVM arguments.
         * @param jvmArgs the JVM arguments
         * @return this builder instance
         */
        public ServerInfoBuilder withJvmArgs(String[] jvmArgs) {
            this.jvmArgs = jvmArgs;
            return this;
        }

        /**
         * Sets the Java home directory
         * @param javaHome the Java home directory
         * @return this builder instance
         */
        public ServerInfoBuilder withJavaHome(String javaHome) {
            this.javaHome = javaHome;
            return this;
        }

        /**
         * Sets the path to the server configuration file to use.
         * @param serverConfig the path to the servers configuration file
         * @return this builder instance
         */
        public ServerInfoBuilder withServerConfig(String serverConfig) {
            this.serverConfig = serverConfig;
            return this;
        }

        /**
         * Sets the path to the system properties file to load.
         * @param propertiesFile the path to the system properties file to load
         * @return this builder instance
         */
        public ServerInfoBuilder withPropertiesFile(String propertiesFile) {
            this.propertiesFile = propertiesFile;
            return this;
        }

        /**
         * Sets the timeout to use for the server startup.
         * @param startupTimeout the startup timeout
         * @return this builder instance
         */
        public ServerInfoBuilder withStartupTimeout(long startupTimeout) {
            this.startupTimeout = startupTimeout;
            return this;
        }

        /**
         * Sets the address the server listens to serve traffic.
         * @param bindAddress the bind address
         * @return
         */
        public ServerInfoBuilder withBindAddress(String bindAddress) {
            this.bindAddress = bindAddress;
            return this;
        }

        /**
         * Create a new ServerInfo instance.
         * @return ServerInfo instance
         */
        public ServerInfo build() {
            return new ServerInfo(this);
        }

    }

    /**
     * The connection information for the management operations.
     *
     * @return the connection information
     */
    public ConnectionInfo getConnectionInfo() {
        return connectionInfo;
    }

    /**
     * The JBoss Application Server home directory.
     *
     * @return the home directory
     */
    public File getJbossHome() {
        return jbossHome;
    }

    /**
     * The directory for all the modules.
     *
     * @return the modules directory
     */
    public String getModulesDir() {
        return modulesDir;
    }

    /**
     * The optional JVM arguments.
     *
     * @return the JVM arguments or {@code null} if there are none
     */
    public String[] getJvmArgs() {
        return jvmArgs;
    }

    /**
     * The Java home directory.
     *
     * @return the Java home directory
     */
    public String getJavaHome() {
        return javaHome;
    }

    /**
     * The path to the server configuration file to use.
     *
     * @return the path to the configuration file or {@code null} if the default configuration file is being used
     */
    public String getServerConfig() {
        return serverConfig;
    }

    /**
     * The path to the system properties file to load.
     *
     * @return the path to the properties file or {@code null} if no properties should be loaded.
     */
    public String getPropertiesFile() {
        return propertiesFile;
    }

    /**
     * The timeout to use for the server startup.
     *
     * @return the server startup timeout
     */
    public long getStartupTimeout() {
        return startupTimeout;
    }

    /**
     * The address the server listens to serve traffic.
     *
     * @return the bind address
     */
    public String getBindAddress() {
        return bindAddress;
    }
}
