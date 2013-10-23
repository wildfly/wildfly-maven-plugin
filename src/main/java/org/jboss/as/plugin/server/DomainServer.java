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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.plugin.common.Files;
import org.jboss.as.plugin.common.IoUtils;
import org.jboss.as.plugin.deployment.domain.Domain;

/**
 * This is not yet in production. Here only as a place holder in case it's needed in the future.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class DomainServer extends Server {

    private static final String CONFIG_PATH = "/domain/configuration/";

    private final ServerInfo serverInfo;
    private final Domain domain;
    private volatile boolean isRunning;
    private DomainClient client;
    private final Map<ServerIdentity, ServerStatus> servers;

    /**
     * Creates a new domain server.
     *
     * @param serverInfo the server information used
     * @param domain     the domain information for deployments
     */
    public DomainServer(final ServerInfo serverInfo, final Domain domain) {
        super(serverInfo);
        this.serverInfo = serverInfo;
        this.domain = domain;
        isRunning = false;
        servers = new HashMap<ServerIdentity, ServerStatus>();
    }

    @Override
    protected void init() throws IOException {
        client = DomainClient.Factory.create(serverInfo.getConnectionInfo().getHostAddress(), serverInfo.getConnectionInfo().getPort(), serverInfo.getConnectionInfo().getCallbackHandler());
    }

    @Override
    protected void stopServer() {
        // This shutdown is not correct. The servers need to be shutdown, then the host controllers. More thought needs
        // to go into how to do this to make it production ready.
        try {
            if (client != null) {
                try {
                    for (ServerIdentity id : servers.keySet()) {
                        final ServerStatus status = servers.get(id);
                        switch (status) {
                            case STARTED: {
                                client.stopServer(id.getHostName(), id.getServerName(), 10L, TimeUnit.SECONDS);
                                break;
                            }
                        }
                    }
                } finally {
                    IoUtils.safeClose(client);
                    client = null;
                    servers.clear();
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
        if (client == null) {
            isRunning = false;
        } else {
            try {
                final Map<ServerIdentity, ServerStatus> statuses = client.getServerStatuses();
                for (ServerIdentity id : statuses.keySet()) {
                    final ServerStatus status = statuses.get(id);
                    switch (status) {
                        case DISABLED:
                        case STARTED: {
                            servers.put(id, status);
                            break;
                        }
                    }
                }
                isRunning = statuses.size() == servers.size();
            } catch (Throwable ignore) {
                isRunning = false;
            }
        }
        return isRunning;
    }

    @Override
    public synchronized DomainClient getClient() {
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
        cmd.add("-Dorg.jboss.boot.log.file=" + jbossHome + "/domain/log/process-controller.log");
        cmd.add("-Dlogging.configuration=file:" + jbossHome + CONFIG_PATH + "logging.properties");
        cmd.add("-Djboss.bundles.dir=" + serverInfo.getBundlesDir().getAbsolutePath());
        // TODO (jrp) if this goes into production, these need to be used
        // cmd.add("-Djboss.domain.default.config=" + config.getDomainConfig());
        // cmd.add("-Djboss.host.default.config=" + config.getHostConfig());
        cmd.add("-jar");
        cmd.add(modulesJar.getAbsolutePath());
        cmd.add("-mp");
        cmd.add(serverInfo.getModulesDir().getAbsolutePath());
        cmd.add("org.jboss.as.process-controller");
        cmd.add("-jboss-home");
        cmd.add(jbossHome.getAbsolutePath());
        cmd.add("-jvm");
        cmd.add(javaExec);
        cmd.add("--");
        cmd.add("-Dorg.jboss.boot.log.file=" + jbossHome + "/domain/log/host-controller.log");
        cmd.add("-Dlogging.configuration=file:" + jbossHome + CONFIG_PATH + "logging.properties");
        cmd.add("--");
        cmd.add("-default-jvm");
        cmd.add(javaExec);
        return cmd;
    }

    @Override
    protected void checkServerState() {
        // TODO need to implement
    }
}
