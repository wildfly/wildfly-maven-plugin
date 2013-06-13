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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.plugin.common.Files;
import org.jboss.as.plugin.common.ServerOperations;
import org.jboss.as.plugin.common.Streams;
import org.jboss.dmr.ModelNode;

/**
 * A standalone server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class StandaloneServer extends Server {

    private static final String CONFIG_PATH = "/standalone/configuration/";
    private static final String STARTING = "STARTING";
    private static final String STOPPING = "STOPPING";

    private final ServerInfo serverInfo;
    private boolean isRunning;
    private ModelControllerClient client;

    /**
     * Creates a new standalone server.
     *
     * @param serverInfo the configuration information for the server
     */
    public StandaloneServer(final ServerInfo serverInfo) {
        super(serverInfo, "JBAS015950");
        this.serverInfo = serverInfo;
        isRunning = false;
    }

    @Override
    protected void init() throws IOException {
        client = ModelControllerClient.Factory.create(serverInfo.getConnectionInfo().getHostAddress(), serverInfo.getConnectionInfo().getPort(), serverInfo.getConnectionInfo().getCallbackHandler());
    }

    @Override
    protected void stopServer() {
        try {
            if (client != null) {
                try {
                    client.execute(ServerOperations.createOperation(ServerOperations.SHUTDOWN));
                } catch (IOException e) {
                    // no-op
                } finally {
                    Streams.safeClose(client);
                    client = null;
                }
                try {
                    getConsole().awaitShutdown(5L);
                } catch (InterruptedException ignore) {
                    // no-op
                }
            }
        } finally {
            isRunning = false;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        if (isRunning) {
            return isRunning;
        }
        checkServerState();
        return isRunning;
    }

    @Override
    public synchronized ModelControllerClient getClient() {
        return client;
    }

    @Override
    protected List<String> createLaunchCommand() {
        final File jbossHome = serverInfo.getJbossHome();
        final String javaHome = serverInfo.getJavaHome();
        final File modulesJar = new File(Files.createPath(jbossHome.getAbsolutePath(), "jboss-modules.jar"));
        if (!modulesJar.exists())
            throw new IllegalStateException("Cannot find: " + modulesJar);
        String javaExec = Files.createPath(javaHome, "bin", "java");
        if (javaHome.contains(" ")) {
            javaExec = "\"" + javaExec + "\"";
        }

        // Create the commands
        final List<String> cmd = new ArrayList<String>();
        cmd.add(javaExec);
        if (serverInfo.getJvmArgs() != null) {
            Collections.addAll(cmd, serverInfo.getJvmArgs());
        }

        cmd.add("-Djboss.home.dir=" + jbossHome);
        cmd.add("-Dorg.jboss.boot.log.file=" + jbossHome + "/standalone/log/boot.log");
        cmd.add("-Dlogging.configuration=file:" + jbossHome + CONFIG_PATH + "logging.properties");
        cmd.add("-Djboss.modules.dir=" + serverInfo.getModulesDir().getAbsolutePath());
        cmd.add("-Djboss.bundles.dir=" + serverInfo.getBundlesDir().getAbsolutePath());
        cmd.add("-jar");
        cmd.add(modulesJar.getAbsolutePath());
        cmd.add("-mp");
        cmd.add(serverInfo.getModulesDir().getAbsolutePath());
        cmd.add("-jaxpmodule");
        cmd.add("javax.xml.jaxp-provider");
        cmd.add("org.jboss.as.standalone");
        if (serverInfo.getServerConfig() != null) {
            cmd.add("-server-config");
            cmd.add(serverInfo.getServerConfig());
        }
        if (serverInfo.getPropertiesFile() != null) {
            cmd.add("-P");
            cmd.add(serverInfo.getPropertiesFile());
        }
        return cmd;
    }

    @Override
    protected void checkServerState() {
        if (client == null) {
            isRunning = false;
        } else {
            try {
                final ModelNode result = client.execute(ServerOperations.createReadAttributeOperation(ServerOperations.SERVER_STATE));
                isRunning = ServerOperations.isSuccessfulOutcome(result) && !STARTING.equals(ServerOperations.readResultAsString(result)) &&
                        !STOPPING.equals(ServerOperations.readResultAsString(result));
            } catch (Throwable ignore) {
                isRunning = false;
            }
        }
    }

}
