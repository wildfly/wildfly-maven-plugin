/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.server;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.wildfly.plugin.core.ConsoleConsumer;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.core.ServerHelper;
import org.wildfly.plugin.tests.TestEnvironment;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StandaloneTestServer implements TestServer {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private volatile Process currentProcess;
    private volatile Thread shutdownThread;
    private volatile Thread consoleConsumer;
    private volatile ModelControllerClient client;
    private volatile DeploymentManager deploymentManager;

    @Override
    public void start() {
        if (STARTED.compareAndSet(false, true)) {
            try {
                final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(TestEnvironment.WILDFLY_HOME)
                        .setBindAddressHint("management", TestEnvironment.HOSTNAME)
                        .addJavaOption("-Djboss.management.http.port=" + TestEnvironment.PORT);
                final Process process = Launcher.of(commandBuilder)
                        .setRedirectErrorStream(true)
                        .launch();
                consoleConsumer = ConsoleConsumer.start(process, System.out);

                shutdownThread = ProcessHelper.addShutdownHook(process);
                client = ModelControllerClient.Factory.create(TestEnvironment.HOSTNAME, TestEnvironment.PORT);
                currentProcess = process;
                ServerHelper.waitForStandalone(process, client, TestEnvironment.TIMEOUT);
                deploymentManager = DeploymentManager.Factory.create(client);
            } catch (Throwable t) {
                try {
                    throw new RuntimeException("Failed to start server", t);
                } finally {
                    STARTED.set(false);
                    cleanUp();
                }
            }
        }
    }

    @Override
    public void stop() {
        try {
            try {
                final Thread shutdownThread = this.shutdownThread;
                if (shutdownThread != null) {
                    Runtime.getRuntime().removeShutdownHook(shutdownThread);
                    this.shutdownThread = null;
                }
            } catch (Exception ignore) {
            }
            if (STARTED.compareAndSet(true, false)) {
                ServerHelper.shutdownStandalone(client);
            }
            final Thread consoleConsumer = this.consoleConsumer;
            if (consoleConsumer != null) {
                this.consoleConsumer = null;
                consoleConsumer.interrupt();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            cleanUp();
        }
    }

    @Override
    public ModelControllerClient getClient() {
        return client;
    }

    @Override
    public DeploymentManager getDeploymentManager() {
        return deploymentManager;
    }

    private void cleanUp() {
        try {
            if (client != null)
                try {
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
        }
    }
}
