/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.server;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.junit.Assert;
import org.wildfly.core.launcher.DomainCommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.plugin.tests.TestEnvironment;
import org.wildfly.plugin.tools.ConsoleConsumer;
import org.wildfly.plugin.tools.DeploymentManager;
import org.wildfly.plugin.tools.server.DomainManager;
import org.wildfly.plugin.tools.server.ServerManager;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("Duplicates")
public class DomainTestServer implements TestServer {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private volatile Process currentProcess;
    private volatile Thread shutdownThread;
    private volatile Thread consoleConsumer;
    private volatile DomainClient client;
    private volatile DomainManager serverManager;
    private volatile DeploymentManager deploymentManager;

    @Override
    public void start() {
        if (STARTED.compareAndSet(false, true)) {
            try {
                final DomainCommandBuilder commandBuilder = DomainCommandBuilder.of(TestEnvironment.WILDFLY_HOME)
                        .setBindAddressHint("management", TestEnvironment.HOSTNAME)
                        .addHostControllerJavaOption("-Djboss.management.http.port=" + TestEnvironment.PORT);

                // Workaround for WFCORE-4121
                if (TestEnvironment.isModularJvm()) {
                    commandBuilder.addHostControllerJavaOptions(TestEnvironment.getModularJvmArguments());
                }
                final Process process = Launcher.of(commandBuilder)
                        .setRedirectErrorStream(true)
                        .launch();
                consoleConsumer = ConsoleConsumer.start(process, System.out);
                shutdownThread = ProcessHelper.addShutdownHook(process);
                client = DomainClient.Factory
                        .create(ModelControllerClient.Factory.create(TestEnvironment.HOSTNAME, TestEnvironment.PORT));
                currentProcess = process;
                serverManager = ServerManager.builder().process(process).client(client).domain();
                if (!serverManager.waitFor(TestEnvironment.TIMEOUT, TimeUnit.SECONDS)) {
                    Assert.fail(String.format("Server did not start withing %d seconds.", TestEnvironment.TIMEOUT));
                }
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
                serverManager.shutdown();
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
