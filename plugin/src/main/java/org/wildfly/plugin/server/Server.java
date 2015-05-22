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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private final ScheduledExecutorService timerService;
    private final CommandBuilder commandBuilder;
    private final OutputStream stdout;
    private volatile Thread shutdownHook;
    private Process process;

    private Server(final CommandBuilder commandBuilder, final OutputStream stdout) {
        this.commandBuilder = commandBuilder;
        timerService = Executors.newScheduledThreadPool(1);
        this.stdout = stdout;
    }

    static Server create(final CommandBuilder commandBuilder, final ModelControllerClient client) {
        return create(commandBuilder, client, null);
    }

    static Server create(final CommandBuilder commandBuilder, final ModelControllerClient client, final OutputStream stdout) {
        if (commandBuilder instanceof DomainCommandBuilder) {
            return new Server(commandBuilder, stdout) {
                final DomainClient domainClient = DomainClient.Factory.create(client);
                final Map<ServerIdentity, ServerStatus> servers = new HashMap<>();
                volatile boolean isRunning = false;

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
                public boolean isRunning() {
                    return isRunning;
                }

                @Override
                protected void checkServerState() {
                    isRunning = ServerHelper.isDomainRunning(domainClient, servers);
                }
            };
        }
        return new Server(commandBuilder, stdout) {
            volatile boolean isRunning = false;

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
            public boolean isRunning() {
                return isRunning;
            }

            @Override
            protected void checkServerState() {
                isRunning = ServerHelper.isStandaloneRunning(client);
            }
        };
    }

    /**
     * Starts the server.
     *
     * @throws IOException the an error occurs creating the process
     */
    public final synchronized void start(final long timeout) throws IOException, InterruptedException {
        final Launcher launcher = Launcher.of(commandBuilder);
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
        // Running maven in a SM is unlikely, but we'll be safe
        shutdownHook = AccessController.doPrivileged(new PrivilegedAction<Thread>() {
            @Override
            public Thread run() {
                return ProcessHelper.addShutdownHook(process);
            }
        });
        if (waitForStart(timeout)) {
            timerService.scheduleWithFixedDelay(new Reaper(), 20, 10, TimeUnit.SECONDS);
        } else {
            try {
                ProcessHelper.destroyProcess(process);
            } catch (InterruptedException ignore) {
            }
            throw new IllegalStateException(String.format("Managed server was not started within [%d] s", timeout));
        }
    }

    /**
     * Stops the server.
     */
    public final synchronized void stop() {
        try {
            // Remove the shutdown hook. Running maven in a SM is unlikely, but we'll be safe
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        Runtime.getRuntime().removeShutdownHook(shutdownHook);
                    } catch (Exception ignore) {
                    }
                    return null;
                }
            });
            // Shutdown the reaper
            try {
                timerService.shutdown();
            } catch (Exception ignore) {
            }
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
    public abstract boolean isRunning();

    /**
     * Checks whether the server is running or not. If the server is no longer running the {@link #isRunning()} should
     * return {@code false}.
     */
    protected abstract void checkServerState();

    private class Reaper implements Runnable {

        @Override
        public void run() {
            checkServerState();
            if (!isRunning()) {
                stop();
            }
        }
    }

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
