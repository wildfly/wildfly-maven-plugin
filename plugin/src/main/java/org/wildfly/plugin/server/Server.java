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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.wildfly.core.launcher.CommandBuilder;
import org.wildfly.core.launcher.DomainCommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class Server {
    private final CommandBuilder commandBuilder;
    private final OutputStream stdout;
    private final Map<String, String> env;
    private final long stateCheckTimeout;
    private volatile long lastStateCheckTime;
    private volatile boolean running;
    private Process process;

    private Server(final CommandBuilder commandBuilder, final Map<String, String> env, final OutputStream stdout) {
        this.commandBuilder = commandBuilder;
        this.stdout = stdout;
        this.env = (env == null ? Collections.<String, String>emptyMap() : new HashMap<>(env));
        stateCheckTimeout = 5000L;
    }

    static Server create(final CommandBuilder commandBuilder, final Map<String, String> env, final ModelControllerClient client) {
        return create(commandBuilder, env, client, null);
    }

    static Server create(final CommandBuilder commandBuilder, final Map<String, String> env, final ModelControllerClient client, final OutputStream stdout) {
        if (commandBuilder instanceof DomainCommandBuilder) {
            return new Server(commandBuilder, env, stdout) {
                final DomainClient domainClient = DomainClient.Factory.create(client);
                final Map<ServerIdentity, ServerStatus> servers = new HashMap<>();

                @Override
                protected void stopServer() {
                    ServerHelper.shutdownDomain(domainClient, servers);
                    if (domainClient != null) try {
                        domainClient.close();
                    } catch (Exception ignore) {
                    }
                }

                @Override
                protected boolean waitForStart(final long timeout) throws IOException, InterruptedException {
                    return ServerHelper.waitForDomain(super.process, domainClient, servers, timeout);
                }

                @Override
                protected boolean isServerRunning() {
                    return ServerHelper.isDomainRunning(domainClient, servers);
                }
            };
        }
        return new Server(commandBuilder, env, stdout) {

            @Override
            protected void stopServer() {
                ServerHelper.shutdownStandalone(client);
                if (client != null) try {
                    client.close();
                } catch (Exception ignore) {
                }
            }

            @Override
            protected boolean waitForStart(final long timeout) throws IOException, InterruptedException {
                return ServerHelper.waitForStandalone(super.process, client, timeout);
            }

            @Override
            protected boolean isServerRunning() {
                return ServerHelper.isStandaloneRunning(client);
            }
        };
    }

    /**
     * Starts the server.
     *
     * @throws IOException the an error occurs creating the process
     */
    public final synchronized void start(final long timeout) throws IOException, InterruptedException {
        final Launcher launcher = Launcher.of(commandBuilder)
                .addEnvironmentVariables(env);
        // Determine if we should consume stdout
        if (stdout == null) {
            launcher.inherit();
        } else {
            launcher.setRedirectErrorStream(true);
        }
        process = launcher.launch();
        if (stdout != null) {
            new Thread(new ConsoleConsumer(process.getInputStream(), stdout)).start();
        }
        if (waitForStart(timeout)) {
            running = true;
        } else {
            try {
                ProcessHelper.destroyProcess(process);
            } catch (InterruptedException ignore) {
            } finally {
                running = false;
            }
            throw new IllegalStateException(String.format("Managed server was not started within [%d] s", timeout));
        }
    }

    /**
     * Stops the server.
     */
    public final synchronized void stop() {
        try {
            // Stop the servers
            stopServer();
        } finally {
            try {
                ProcessHelper.destroyProcess(process);
            } catch (InterruptedException ignore) {
                // no-op
            }
        }
    }

    /**
     * Stops the server before the process is destroyed. A no-op override will just destroy the process.
     */
    protected abstract void stopServer();

    protected abstract boolean waitForStart(long timeout) throws IOException, InterruptedException;

    /**
     * Checks the status of the server and returns {@code true} if the server is fully started.
     *
     * @return {@code true} if the server is fully started, otherwise {@code false}
     */
    public final boolean isRunning() {
        final long currentTime = System.currentTimeMillis();
        final boolean isRunning;
        if ((currentTime - lastStateCheckTime) > stateCheckTimeout) {
            running = isRunning = isServerRunning();
            lastStateCheckTime = currentTime;
        } else {
            isRunning = running;
        }
        return isRunning;
    }

    /**
     * Queries the state of the server to determine if it's running or not.
     *
     * @return {@code true} if the server appears to be running otherwise {@code false}
     */
    protected abstract boolean isServerRunning();

    static class ConsoleConsumer implements Runnable {
        private final InputStream in;
        private final OutputStream out;

        ConsoleConsumer(final InputStream in, final OutputStream out) {
            this.in = in;
            this.out = out;
        }


        @Override
        public void run() {
            byte[] buffer = new byte[64];
            try {
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            } catch (IOException ignore) {
            }
        }
    }
}
