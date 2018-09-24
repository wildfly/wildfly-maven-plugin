/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.plugin.core;

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STARTING;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STOPPING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.wildfly.common.Assert;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"StaticMethodOnlyUsedInOneClass", "WeakerAccess", "unused", "MagicNumber"})
public class ServerHelper {
    private static final ModelNode EMPTY_ADDRESS = new ModelNode().setEmptyList();
    private static final Logger LOGGER = Logger.getLogger(ServerHelper.class);

    static {
        EMPTY_ADDRESS.protect();
    }

    /**
     * Checks whether or not the directory is a valid home directory for a server.
     * <p>
     * This validates the path is not {@code null}, exists, is a directory and contains a {@code jboss-modules.jar}.
     * </p>
     *
     * @param path the path to validate
     *
     * @return {@code true} if the path is valid otherwise {@code false}
     */
    public static boolean isValidHomeDirectory(final Path path) {
        return path != null
                && Files.exists(path)
                && Files.isDirectory(path)
                && Files.exists(path.resolve("jboss-modules.jar"));
    }

    /**
     * Checks whether or not the directory is a valid home directory for a server.
     * <p>
     * This validates the path is not {@code null}, exists, is a directory and contains a {@code jboss-modules.jar}.
     * </p>
     *
     * @param path the path to validate
     *
     * @return {@code true} if the path is valid otherwise {@code false}
     */
    public static boolean isValidHomeDirectory(final String path) {
        return path != null && isValidHomeDirectory(Paths.get(path));
    }

    /**
     * Returns the description of the running container.
     *
     * @param client the client used to query the server
     *
     * @return the description of the running container
     *
     * @throws IOException                 if an error occurs communicating with the server
     * @throws OperationExecutionException if the operation used to query the container fails
     */
    public static ContainerDescription getContainerDescription(final ModelControllerClient client) throws IOException, OperationExecutionException {
        return DefaultContainerDescription.lookup(Assert.checkNotNullParam("client", client));
    }

    /**
     * Checks the running server to determine if it is a managed domain server.
     *
     * @param client the client used to query the server
     *
     * @return {@code true} if the running server is a managed domain, otherwise {@code false}
     *
     * @throws IOException if an error occurs communicating with the server
     */
    public static boolean isDomainServer(final ModelControllerClient client) throws IOException {
        boolean result = false;
        // Check this is really a domain server
        final ModelNode op = Operations.createReadAttributeOperation(EMPTY_ADDRESS, "launch-type");
        final ModelNode opResult = Assert.checkNotNullParam("client", client).execute(op);
        if (Operations.isSuccessfulOutcome(opResult)) {
            result = ("DOMAIN".equalsIgnoreCase(Operations.readResult(opResult).asString()));
        }
        return result;
    }

    /**
     * Waits the given amount of time in seconds for a managed domain to start. A domain is considered started when each
     * of the servers in the domain are started unless the server is disabled.
     *
     * @param client         the client used to communicate with the server
     * @param startupTimeout the time, in seconds, to wait for the server start
     *
     * @throws InterruptedException if interrupted while waiting for the server to start
     * @throws RuntimeException     if the process has died
     * @throws TimeoutException     if the timeout has been reached and the server is still not started
     */
    public static void waitForDomain(final ModelControllerClient client, final long startupTimeout)
            throws InterruptedException, RuntimeException, TimeoutException {
        waitForDomain(null, client, startupTimeout);
    }

    /**
     * Waits the given amount of time in seconds for a managed domain to start. A domain is considered started when each
     * of the servers in the domain are started unless the server is disabled.
     * <p>
     * If the {@code process} is not {@code null} and a timeout occurs the process will be
     * {@linkplain Process#destroy() destroyed}.
     * </p>
     *
     * @param process        the Java process can be {@code null} if no process is available
     * @param client         the client used to communicate with the server
     * @param startupTimeout the time, in seconds, to wait for the server start
     *
     * @throws InterruptedException if interrupted while waiting for the server to start
     * @throws RuntimeException     if the process has died
     * @throws TimeoutException     if the timeout has been reached and the server is still not started
     */
    public static void waitForDomain(final Process process, final ModelControllerClient client, final long startupTimeout)
            throws InterruptedException, RuntimeException, TimeoutException {
        Assert.checkNotNullParam("client", client);
        long timeout = startupTimeout * 1000;
        final long sleep = 100;
        while (timeout > 0) {
            long before = System.currentTimeMillis();
            if (isDomainRunning(client)) {
                break;
            }
            timeout -= (System.currentTimeMillis() - before);
            if (process != null && !process.isAlive()) {
                throw new RuntimeException(String.format("The process has unexpectedly exited with code %d", process.exitValue()));
            }
            TimeUnit.MILLISECONDS.sleep(sleep);
            timeout -= sleep;
        }
        if (timeout <= 0) {
            if (process != null) {
                process.destroy();
            }
            throw new TimeoutException(String.format("The server did not start within %s seconds.", startupTimeout));
        }
    }

    /**
     * Checks to see if the domain is running. If the server is not in admin only mode each servers running state is
     * checked. If any server is not in a started state the domain is not considered to be running.
     *
     * @param client the client used to communicate with the server
     *
     * @return {@code true} if the server is in a running state, otherwise {@code false}
     */
    public static boolean isDomainRunning(final ModelControllerClient client) {
        return isDomainRunning(client, false);
    }

    /**
     * Shuts down a managed domain container. The servers are first stopped, then the host controller is shutdown.
     *
     * @param client the client used to communicate with the server
     *
     * @throws IOException                 if an error occurs communicating with the server
     * @throws OperationExecutionException if the operation used to shutdown the managed domain failed
     */
    public static void shutdownDomain(final ModelControllerClient client) throws IOException, OperationExecutionException {
        shutdownDomain(client, 0);
    }

    /**
     * Shuts down a managed domain container. The servers are first stopped, then the host controller is shutdown.
     *
     * @param client  the client used to communicate with the server
     * @param timeout the graceful shutdown timeout, a value of {@code -1} will wait indefinitely and a value of
     *                {@code 0} will not attempt a graceful shutdown
     *
     * @throws IOException                 if an error occurs communicating with the server
     * @throws OperationExecutionException if the operation used to shutdown the managed domain failed
     */
    public static void shutdownDomain(final ModelControllerClient client, final int timeout) throws IOException, OperationExecutionException {
        // Note the following two operations used to shutdown a domain don't seem to work well in a composite operation.
        // The operation occasionally sees a java.util.concurrent.CancellationException because the operation client
        // is likely closed before the AsyncFuture.get() is complete. Using a non-composite operation doesn't seem to
        // have this issue.

        // First shutdown the servers
        final ModelNode stopServersOp = Operations.createOperation("stop-servers");
        stopServersOp.get("blocking").set(true);
        stopServersOp.get("timeout").set(timeout);
        ModelNode response = client.execute(stopServersOp);
        if (!Operations.isSuccessfulOutcome(response)) {
            throw new OperationExecutionException("Failed to stop servers.", stopServersOp, response);
        }

        // Now shutdown the host
        final ModelNode address = determineHostAddress(client);
        final ModelNode shutdownOp = Operations.createOperation("shutdown", address);
        response = client.execute(shutdownOp);
        if (Operations.isSuccessfulOutcome(response)) {
            // Wait until the process has died
            while (true) {
                if (isDomainRunning(client, true)) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(20L);
                    } catch (InterruptedException e) {
                        LOGGER.trace("Interrupted during sleep", e);
                    }
                } else {
                    break;
                }
            }
        } else {
            throw new OperationExecutionException("Failed to shutdown host.", shutdownOp, response);
        }
    }

    /**
     * Determines the address for the host being used.
     *
     * @param client the client used to communicate with the server
     *
     * @return the address of the host
     *
     * @throws IOException                 if an error occurs communicating with the server
     * @throws OperationExecutionException if the operation used to determine the host name fails
     */
    public static ModelNode determineHostAddress(final ModelControllerClient client) throws IOException, OperationExecutionException {
        final ModelNode op = Operations.createReadAttributeOperation(EMPTY_ADDRESS, "local-host-name");
        ModelNode response = client.execute(op);
        if (Operations.isSuccessfulOutcome(response)) {
            return DeploymentOperations.createAddress("host", Operations.readResult(response).asString());
        }
        throw new OperationExecutionException(op, response);
    }

    /**
     * Waits the given amount of time in seconds for a standalone server to start.
     *
     * @param client         the client used to communicate with the server
     * @param startupTimeout the time, in seconds, to wait for the server start
     *
     * @throws InterruptedException if interrupted while waiting for the server to start
     * @throws RuntimeException     if the process has died
     * @throws TimeoutException     if the timeout has been reached and the server is still not started
     */
    public static void waitForStandalone(final ModelControllerClient client, final long startupTimeout)
            throws InterruptedException, RuntimeException, TimeoutException {
        waitForStandalone(null, client, startupTimeout);
    }

    /**
     * Waits the given amount of time in seconds for a standalone server to start.
     * <p>
     * If the {@code process} is not {@code null} and a timeout occurs the process will be
     * {@linkplain Process#destroy() destroyed}.
     * </p>
     *
     * @param process        the Java process can be {@code null} if no process is available
     * @param client         the client used to communicate with the server
     * @param startupTimeout the time, in seconds, to wait for the server start
     *
     * @throws InterruptedException if interrupted while waiting for the server to start
     * @throws RuntimeException     if the process has died
     * @throws TimeoutException     if the timeout has been reached and the server is still not started
     */
    public static void waitForStandalone(final Process process, final ModelControllerClient client, final long startupTimeout)
            throws InterruptedException, RuntimeException, TimeoutException {
        Assert.checkNotNullParam("client", client);
        long timeout = startupTimeout * 1000;
        final long sleep = 100L;
        while (timeout > 0) {
            long before = System.currentTimeMillis();
            if (isStandaloneRunning(client))
                break;
            timeout -= (System.currentTimeMillis() - before);
            if (process != null && !process.isAlive()) {
                throw new RuntimeException(String.format("The process has unexpectedly exited with code %d", process.exitValue()));
            }
            TimeUnit.MILLISECONDS.sleep(sleep);
            timeout -= sleep;
        }
        if (timeout <= 0) {
            if (process != null) {
                process.destroy();
            }
            throw new TimeoutException(String.format("The server did not start within %s seconds.", startupTimeout));
        }
    }

    /**
     * Checks to see if a standalone server is running.
     *
     * @param client the client used to communicate with the server
     *
     * @return {@code true} if the server is running, otherwise {@code false}
     */
    public static boolean isStandaloneRunning(final ModelControllerClient client) {
        try {
            final ModelNode response = client.execute(Operations.createReadAttributeOperation(EMPTY_ADDRESS, "server-state"));
            if (Operations.isSuccessfulOutcome(response)) {
                final String state = Operations.readResult(response).asString();
                return !CONTROLLER_PROCESS_STATE_STARTING.equals(state)
                        && !CONTROLLER_PROCESS_STATE_STOPPING.equals(state);
            }
        } catch (RuntimeException | IOException e) {
            LOGGER.trace("Interrupted determining if standalone is running", e);
        }
        return false;
    }

    /**
     * Shuts down a standalone server.
     *
     * @param client the client used to communicate with the server
     *
     * @throws IOException if an error occurs communicating with the server
     */
    public static void shutdownStandalone(final ModelControllerClient client) throws IOException {
        shutdownStandalone(client, 0);
    }

    /**
     * Shuts down a standalone server.
     *
     * @param client  the client used to communicate with the server
     * @param timeout the graceful shutdown timeout, a value of {@code -1} will wait indefinitely and a value of
     *                {@code 0} will not attempt a graceful shutdown
     *
     * @throws IOException if an error occurs communicating with the server
     */
    public static void shutdownStandalone(final ModelControllerClient client, final int timeout) throws IOException {
        final ModelNode op = Operations.createOperation("shutdown");
        op.get("timeout").set(timeout);
        final ModelNode response = client.execute(op);
        if (Operations.isSuccessfulOutcome(response)) {
            while (true) {
                if (isStandaloneRunning(client)) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(20L);
                    } catch (InterruptedException e) {
                        LOGGER.trace("Interrupted during sleep", e);
                    }
                } else {
                    break;
                }
            }
        } else {
            throw new OperationExecutionException(op, response);
        }
    }

    private static boolean isDomainRunning(final ModelControllerClient client, boolean shutdown) {
        final DomainClient domainClient = (client instanceof DomainClient ? (DomainClient) client : DomainClient.Factory.create(client));
        try {
            // Check for admin-only
            final ModelNode hostAddress = determineHostAddress(domainClient);
            final CompositeOperationBuilder builder = CompositeOperationBuilder.create()
                    .addStep(Operations.createReadAttributeOperation(hostAddress, "running-mode"))
                    .addStep(Operations.createReadAttributeOperation(hostAddress, "host-state"));
            ModelNode response = domainClient.execute(builder.build());
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
            final Map<ServerIdentity, ServerStatus> servers = new HashMap<>();
            final Map<ServerIdentity, ServerStatus> statuses = domainClient.getServerStatuses();
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
            LOGGER.trace("Interrupted determining if domain is running", e);
        }
        return false;
    }
}
