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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.launcher.DomainCommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.plugin.common.DeploymentExecutionException;
import org.wildfly.plugin.common.DeploymentFailureException;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.deployment.Deployment;
import org.wildfly.plugin.deployment.domain.Domain;
import org.wildfly.plugin.deployment.domain.DomainDeploymentBuilder;
import org.wildfly.plugin.tests.Environment;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DomainTestServer implements TestServer {

    // A default domain based on current WildFly defaults. This could likely be generated in the start(), but we'll leave
    // it hard-coded for now.
    private static final Domain DEFAULT_DOMAIN = new Domain() {
        @Override
        public List<String> getProfiles() {
            return Collections.singletonList("full");
        }

        @Override
        public List<String> getServerGroups() {
            return Collections.singletonList("main-server-group");
        }
    };

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private final ConcurrentMap<ServerIdentity, ServerStatus> servers = new ConcurrentHashMap<>(3);
    private volatile Process currentProcess;
    private volatile Thread shutdownThread;
    private volatile DomainClient client;

    @Override
    public void start() {
        if (STARTED.compareAndSet(false, true)) {
            try {
                final DomainCommandBuilder commandBuilder = DomainCommandBuilder.of(Environment.WILDFLY_HOME)
                        .setBindAddressHint("management", Environment.HOSTNAME)
                        .addHostControllerJavaOption("-Djboss.management.http.port=" + Environment.PORT);
                final Process process = Launcher.of(commandBuilder)
                        .setRedirectErrorStream(true)
                        .launch();
                startConsoleConsumer(process);
                shutdownThread = ProcessHelper.addShutdownHook(process);
                client = DomainClient.Factory.create(ModelControllerClient.Factory.create(Environment.HOSTNAME, Environment.PORT));
                currentProcess = process;
                ServerHelper.waitForDomain(process, client, Environment.TIMEOUT);
                this.servers.putAll(servers);
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
                ServerHelper.shutdownDomain(client, servers);
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
    public void deploy(final String deploymentName, final File content) throws IOException, DeploymentExecutionException, DeploymentFailureException {
        checkState();
        final Deployment deployment = new DomainDeploymentBuilder(client, DEFAULT_DOMAIN)
                .setContent(content)
                .setName(deploymentName)
                .setType(Deployment.Type.DEPLOY)
                .build();
        if (deployment.execute() != Deployment.Status.SUCCESS) {
            throw createError("Failed to deploy %s. Content: %s", deploymentName, content);
        }

        // Verify deployed
        if (!isDeployed(deploymentName)) {
            throw createError("Deployment %s was not deployed", deploymentName);
        }

        // Check the status
        final ModelNode address = ServerOperations.createAddress("server-group", "main-server-group", "deployment", deploymentName);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "enabled");
        final ModelNode result = executeOperation(op);

        if (!ServerOperations.readResult(result).asBoolean()) {
            throw createError("Deployment was not enabled on the main-server-group");
        }
    }

    @Override
    public void undeploy(final String deploymentName) throws IOException, DeploymentExecutionException, DeploymentFailureException {
        checkState();
        final Deployment deployment = new DomainDeploymentBuilder(client, DEFAULT_DOMAIN)
                .setName(deploymentName)
                .setType(Deployment.Type.UNDEPLOY)
                .build();
        if (deployment.execute() != Deployment.Status.SUCCESS) {
            throw createError("Failed to undeploy %", deploymentName);
        }

        // Verify not deployed
        if (isDeployed(deploymentName)) {
            throw createError("Deployment %s was not undeployed", deploymentName);
        }
    }

    public Set<String> getServerGroups() {
        checkState();
        final Set<String> result = new HashSet<>();
        for (ServerIdentity identity : servers.keySet()) {
            if (servers.get(identity) == ServerStatus.STARTED) {
                result.add(identity.getServerGroupName());
            }
        }
        return result;
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
            servers.clear();
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
