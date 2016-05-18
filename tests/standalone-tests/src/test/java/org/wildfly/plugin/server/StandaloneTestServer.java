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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.wildfly.plugin.deployment.DeploymentBuilder;
import org.wildfly.plugin.deployment.DeploymentException;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.deployment.Deployment;
import org.wildfly.plugin.tests.Environment;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StandaloneTestServer implements TestServer {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private volatile Process currentProcess;
    private volatile Thread shutdownThread;
    private volatile ModelControllerClient client;

    @Override
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
        } finally {
            cleanUp();
        }
    }

    @Override
    public ModelControllerClient getClient() {
        return client;
    }

    @Override
    public boolean isDeployed(final String deploymentName) throws IOException {
        checkState();
        final ModelNode address = ServerOperations.createAddress("deployment");
        final ModelNode op = ServerOperations.createReadResourceOperation(address);
        final ModelNode result = executeOperation(op);
        final List<ModelNode> deployments = ServerOperations.readResult(result).asList();
        for (ModelNode deployment : deployments) {
            if (deploymentName.equals(ServerOperations.readResult(deployment).get(ClientConstants.NAME).asString())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void deploy(final String deploymentName, final File content) throws IOException, DeploymentException {
        checkState();
        final Deployment deployment = DeploymentBuilder.of(client)
                .setContent(content)
                .setName(deploymentName)
                .setType(Deployment.Type.DEPLOY)
                .build();
        deployment.execute();

        // Verify deployed
        if (!isDeployed(deploymentName)) {
            throw createError("Deployment %s was not deployed", deploymentName);
        }

        // Check the status
        final ModelNode address = ServerOperations.createAddress("deployment", deploymentName);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = executeOperation(op);

        if (!"OK".equals(ServerOperations.readResultAsString(result))) {
            throw createError("Deployment is in an invalid state: %s", ServerOperations.readResultAsString(result));
        }
    }

    @Override
    public void undeploy(final String deploymentName) throws IOException, DeploymentException {
        checkState();
        final Deployment deployment = DeploymentBuilder.of(client)
                .setName(deploymentName)
                .setType(Deployment.Type.UNDEPLOY)
                .build();
        deployment.execute();

        // Verify not deployed
        if (isDeployed(deploymentName)) {
            throw createError("Deployment %s was not undeployed", deploymentName);
        }
    }

    private ModelNode executeOperation(final ModelNode op) throws IOException {
        final ModelNode result = client.execute(op);
        if (ServerOperations.isSuccessfulOutcome(result)) {
            return result;
        }
        throw new RuntimeException(ServerOperations.getFailureDescriptionAsString(result));
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
        }
    }

    private void checkState() {
        if (!STARTED.get()) {
            throw new IllegalStateException("The server has not been started");
        }
    }

    private static Thread startConsoleConsumer(final Process process) {
        final Thread result = new Thread(new ConsoleConsumer(process.getInputStream(), System.out));
        result.start();
        return result;
    }

    private static RuntimeException createError(final String format, final Object... args) {
        return new RuntimeException(String.format(format, args));
    }
}
