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

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STARTING;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STOPPING;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.plugin.common.ServerOperations;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ServerHelper {
    public static final ModelNode EMPTY_ADDRESS = new ModelNode().setEmptyList();
    private static final Logger LOGGER = Logger.getLogger(ServerHelper.class);

    static {
        EMPTY_ADDRESS.protect();
    }

    public static boolean isDomainServer(final ModelControllerClient client) throws IOException {
        boolean result = false;
        // Check this is really a domain server
        final ModelNode op = ServerOperations.createReadAttributeOperation(ServerOperations.LAUNCH_TYPE);
        final ModelNode opResult = client.execute(op);
        if (ServerOperations.isSuccessfulOutcome(opResult)) {
            result = ("DOMAIN".equals(ServerOperations.readResultAsString(opResult)));
        }
        return result;
    }

    /**
     * @param process
     * @param client
     * @param servers
     * @param startupTimeout in seconds
     * @throws ServerLifecycleException
     *             in case the process dies unexpectedly or not up within the
     *             given {@code startupTimeout}
     * @throws InterruptedException if the current thread was interrupted
     */
    static void waitForDomain(final Process process, final DomainClient client,
            final Map<ServerIdentity, ServerStatus> servers, final long startupTimeout)
                    throws ServerLifecycleException, InterruptedException {
        long timeout = startupTimeout * 1000;
        final long sleep = 100;
        while (timeout > 0) {
            long before = System.currentTimeMillis();
            if (isDomainRunning(client, servers)) {
                return;
            }
            timeout -= (System.currentTimeMillis() - before);
            if (ProcessHelper.processHasDied(process)) {
                throw new ServerLifecycleException(
                        "The server process has unexpectedly exited with code " + process.exitValue());
            }
            TimeUnit.MILLISECONDS.sleep(sleep);
            timeout -= sleep;
        }
        throw new StartupTimeoutException(
                String.format("Managed server was not started within [%d] s", startupTimeout));
    }

    static boolean isDomainRunning(final DomainClient client) {
        return isDomainRunning(client, new HashMap<ServerIdentity, ServerStatus>(), false);
    }

    static boolean isDomainRunning(final DomainClient client, final Map<ServerIdentity, ServerStatus> servers) {
        return isDomainRunning(client, servers, false);
    }

    static void shutdownDomain(final DomainClient client) {
        shutdownDomain(client, new HashMap<ServerIdentity, ServerStatus>());
    }

    static ModelNode determineHostAddress(final ModelControllerClient client) throws IOException {
        ModelNode response = client.execute(Operations.createReadAttributeOperation(EMPTY_ADDRESS, "local-host-name"));
        if (Operations.isSuccessfulOutcome(response)) {
            return ServerOperations.createAddress("host", Operations.readResult(response).asString());
        }
        throw new RuntimeException("Could not determine host name; " + Operations.getFailureDescription(response).asString());
    }

    static void shutdownDomain(final DomainClient client, final Map<ServerIdentity, ServerStatus> servers) {
        final ModelNode address = new ModelNode().setEmptyList().add("host", "master");
        try {
            // First shutdown the servers
            ModelNode op = Operations.createOperation("stop-servers");
            ModelNode response = client.execute(op);
            if (Operations.isSuccessfulOutcome(response)) {
                op = Operations.createOperation("shutdown", address);
                response = client.execute(op);
                if (Operations.isSuccessfulOutcome(response)) {
                    // Wait until the process has died
                    while (true) {
                        if (isDomainRunning(client, servers, true)) {
                            try {
                                TimeUnit.MILLISECONDS.sleep(20L);
                            } catch (InterruptedException e) {
                                LOGGER.debug("Interrupted during sleep", e);
                            }
                        } else {
                            break;
                        }
                    }
                } else {
                    LOGGER.debugf("Failed to execute %s: %s", op, Operations.getFailureDescription(response));
                }
            } else {
                LOGGER.debugf("Failed to execute %s: %s", op, Operations.getFailureDescription(response));
            }
        } catch (IOException e) {
            LOGGER.debug("Error shutting down domain", e);
        }
    }

    /**
     * @param process
     * @param client
     * @param startupTimeout in seconds
     * @throws ServerLifecycleException
     *             in case the process dies unexpectedly or not up within the
     *             given {@code startupTimeout}
     * @throws InterruptedException if the current thread was interrupted
     */
    static void waitForStandalone(final Process process, final ModelControllerClient client, final long startupTimeout)
            throws ServerLifecycleException, InterruptedException {
        long timeout = startupTimeout * 1000;
        final long sleep = 100L;
        while (timeout > 0) {
            long before = System.currentTimeMillis();
            if (isStandaloneRunning(client)) {
                return;
            }
            timeout -= (System.currentTimeMillis() - before);
            if (ProcessHelper.processHasDied(process)) {
                throw new ServerLifecycleException(
                        "The server process has unexpectedly exited with code " + process.exitValue());
            }
            TimeUnit.MILLISECONDS.sleep(sleep);
            timeout -= sleep;
        }
        throw new StartupTimeoutException(
                String.format("Managed server was not started within [%d] s", startupTimeout));
    }

    static boolean isStandaloneRunning(final ModelControllerClient client) {
        try {
            final ModelNode response = client.execute(Operations.createReadAttributeOperation(EMPTY_ADDRESS, "server-state"));
            if (Operations.isSuccessfulOutcome(response)) {
                final String state = Operations.readResult(response).asString();
                return !CONTROLLER_PROCESS_STATE_STARTING.equals(state)
                        && !CONTROLLER_PROCESS_STATE_STOPPING.equals(state);
            }
        } catch (RuntimeException | IOException e) {
            LOGGER.debug("Interrupted determining if standalone is running", e);
        }
        return false;
    }

    static void shutdownStandalone(final ModelControllerClient client) {
        try {
            final ModelNode op = Operations.createOperation("shutdown");
            final ModelNode response = client.execute(op);
            if (Operations.isSuccessfulOutcome(response)) {
                while (true) {
                    if (isStandaloneRunning(client)) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(20L);
                        } catch (InterruptedException e) {
                            LOGGER.debug("Interrupted during sleep", e);
                        }
                    } else {
                        break;
                    }
                }
            } else {
                LOGGER.debugf("Failed to execute %s: %s", op, Operations.getFailureDescription(response));
            }
        } catch (IOException e) {
            LOGGER.debug("Interrupted shutting down standalone", e);
        }
    }

    private static boolean isDomainRunning(final DomainClient client, final Map<ServerIdentity, ServerStatus> servers, boolean shutdown) {
        try {
            // Check for admin-only
            final ModelNode hostAddress = determineHostAddress(client);
            final CompositeOperationBuilder builder = CompositeOperationBuilder.create()
                    .addStep(Operations.createReadAttributeOperation(hostAddress, "running-mode"))
                    .addStep(Operations.createReadAttributeOperation(hostAddress, "host-state"));
            ModelNode response = client.execute(builder.build());
            if (Operations.isSuccessfulOutcome(response)) {
                response = Operations.readResult(response);
                if ("ADMIN_ONLY".equals(Operations.readResult(response.get("step-1")).asString())) {
                    if (Operations.isSuccessfulOutcome(response.get("step-2"))) {
                        final String state = Operations.readResult(response).asString();
                        return !CONTROLLER_PROCESS_STATE_STARTING.equals(state)
                                && !CONTROLLER_PROCESS_STATE_STOPPING.equals(state);
                    }
                }
            }
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
            if (shutdown) {
                return statuses.isEmpty();
            }
            return statuses.size() == servers.size();
        } catch (Exception e) {
            LOGGER.debug("Interrupted determining if domain is running", e);
        }
        return false;
    }
}
