/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.wildfly.plugin.Environment;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class TestServer {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private volatile Process currentProcess;
    private volatile Thread shutdownThread;
    private volatile ModelControllerClient client;

    public void start() {
        if (STARTED.compareAndSet(false, true)) {
            try {
                final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(Environment.WILDFLY_HOME)
                        .setBindAddressHint("management", Environment.HOSTNAME)
                        .addJavaOption("-Djboss.management.http.port=" + Environment.PORT);
                final Process process = Launcher.of(commandBuilder)
                        .setRedirectErrorStream(true)
                        .launch();
                startConsoleConsumer(process);
                shutdownThread = ProcessHelper.addShutdownHook(process);
                client = ModelControllerClient.Factory.create(Environment.HOSTNAME, Environment.PORT);
                currentProcess = process;
                ServerHelper.waitForStandalone(process, client, Environment.TIMEOUT);
            } catch (Throwable t) {
                try {
                    throw new RuntimeException("Failed to start server", t);
                } finally {
                    cleanUp();
                }
            }
        }
    }

    public void stop() {
        try {
            if (STARTED.get()) {
                try {
                    final Thread shutdownThread = this.shutdownThread;
                    if (shutdownThread != null) {
                        Runtime.getRuntime().removeShutdownHook(shutdownThread);
                        this.shutdownThread = null;
                    }
                } catch (Exception ignore) {
                }
                ServerHelper.shutdownStandalone(client);
            }
        } finally {
            cleanUp();
        }
    }

    public ModelControllerClient getClient() {
        return client;
    }

    private void cleanUp() {
        try {
            if (client != null) try {
                client.close();
            } catch (Exception ignore) {
            } finally {
                client = null;
            }
            try {
                ProcessHelper.destroyProcess(currentProcess);
            } catch (InterruptedException ignore) {
            }
        } finally {
            currentProcess = null;
            STARTED.set(false);
        }
    }

    private static Thread startConsoleConsumer(final Process process) {
        final Thread result = new Thread(new ConsoleConsumer(process.getInputStream(), System.out));
        result.start();
        return result;
    }

    private static class ConsoleConsumer implements Runnable {
        private final InputStream in;
        private final PrintStream out;

        ConsoleConsumer(final InputStream in, final PrintStream out) {
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
