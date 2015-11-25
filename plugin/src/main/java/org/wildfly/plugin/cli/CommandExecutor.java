/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import javax.inject.Named;
import javax.inject.Singleton;

import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.jboss.threads.AsyncFuture;
import org.wildfly.core.launcher.CliCommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.plugin.common.ManagementClient;
import org.wildfly.plugin.common.ManagementClientConfiguration;
import org.wildfly.plugin.common.ServerOperations;

/**
 * Executes CLI commands.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Named
@Singleton
public class CommandExecutor {

    /**
     * Executes the commands and scripts provided.
     *
     * @param client      the client use
     * @param wildflyHome the path to WildFly or {@code null} to use the management client to send commands
     * @param commands    the commands to execute
     *
     * @throws IOException if an error occurs processing the commands
     */
    public void execute(final ManagementClient client, final Path wildflyHome, final Commands commands) throws IOException {
        if (wildflyHome != null) {
            executeLocal(wildflyHome, client.getConfiguration(), commands);
        } else {
            executeCommands(client, commands);
        }
    }

    /**
     * Executes the commands and scripts provided.
     *
     * @param client      the client use
     * @param wildflyHome the path to WildFly or {@code null} to use the management client to send commands
     * @param commands    the commands to execute
     *
     * @throws IOException if an error occurs processing the commands
     */
    public void execute(final ManagementClient client, final String wildflyHome, final Commands commands) throws IOException {
        Path path = null;
        if (wildflyHome != null) {
            path = Paths.get(wildflyHome);
        }
        execute(client, path, commands);
    }

    private void executeCommands(final ModelControllerClient client, final Commands commands) throws IOException {

        if (commands.hasCommands() || commands.hasScripts()) {
            final ModelControllerClient c = new NonClosingModelControllerClient(client);
            final CommandContext ctx = create(c);
            try {

                if (commands.isBatch()) {
                    executeBatch(ctx, commands.getCommands());
                } else {
                    executeCommands(ctx, commands.getCommands(), commands.isFailOnError());
                }
                executeScripts(ctx, commands.getScripts());

            } finally {
                ctx.terminateSession();
                ctx.bindClient(null);
            }
        }
    }

    private void executeLocal(final Path wildflyHome, final ManagementClientConfiguration configuration, final Commands commands) throws IOException {
        if (commands.hasCommands()) {
            final boolean safeExecute = !commands.isFailOnError();
            // Generate a file which can be executed locally
            final Path scriptFile = Files.createTempFile("localCliScript", ".cli");
            try {
                try (final PrintWriter writer = new PrintWriter(Files.newBufferedWriter(scriptFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE))) {
                    if (commands.isBatch()) {
                        writer.println("batch");
                    }
                    for (String cmd : commands.getCommands()) {
                        // Wrap in a try-catch if we want to safely execute the command
                        if (safeExecute) {
                            writer.println("try");
                        }
                        writer.println(cmd);
                        if (safeExecute) {
                            writer.println("catch");
                            // Echo the failing command to CLI if the execution fails
                            writer.printf("echo The following command failed; see the server log for more details. %s%n", cmd);
                            writer.println("end-try");
                        }
                    }
                    if (commands.isBatch()) {
                        writer.println("run-batch");
                    }
                }
                executeLocal(wildflyHome, configuration, scriptFile);
            } finally {
                Files.deleteIfExists(scriptFile);
            }
        }

        if (commands.hasScripts()) {
            for (File scriptFile : commands.getScripts()) {
                executeLocal(wildflyHome, configuration, scriptFile.toPath());
            }
        }
    }

    private void executeLocal(final Path wildflyHome, final ManagementClientConfiguration configuration, final Path scriptFile) throws IOException {
        // Ensure the file was created
        if (Files.notExists(scriptFile)) {
            throw new IllegalArgumentException(String.format("File %s does not exist and cannot.", scriptFile));
        }
        // Create the command
        final CliCommandBuilder cliBuilder = CliCommandBuilder.of(wildflyHome);
        cliBuilder.setConnection(configuration.getProtocol(), configuration.getHost(), configuration.getPort());
        cliBuilder.setUser(configuration.getUsername());
        cliBuilder.setPassword(configuration.getPassword());
        cliBuilder.addCliArguments("--file=" + scriptFile.toAbsolutePath());
        final Process process = Launcher.of(cliBuilder)
                .addEnvironmentVariable("JBOSS_HOME", wildflyHome.toAbsolutePath().toString())
                .inherit()
                .launch();
        try {
            // Wait for process to complete
            final int status = process.waitFor();
            if (status != 0) {
                throw new IllegalStateException("Process did not end normally: " + status);
            }
        } catch (InterruptedException ignore) {
        } finally {
            try {
                ProcessHelper.destroyProcess(process);
            } catch (InterruptedException ignore) {
            }
        }
    }

    private static void executeScripts(final CommandContext ctx, final Iterable<File> scripts) throws IOException {

        for (File script : scripts) {
            try (BufferedReader reader = Files.newBufferedReader(script.toPath(), StandardCharsets.UTF_8)) {
                String line = reader.readLine();
                while (!ctx.isTerminated() && line != null) {

                    try {
                        ctx.handle(line.trim());
                    } catch (CommandFormatException e) {
                        throw new IllegalArgumentException(String.format("Command '%s' is invalid. %s", line, e.getLocalizedMessage()), e);
                    } catch (CommandLineException e) {
                        throw new IllegalArgumentException(String.format("Command execution failed for command '%s'. %s", line, e.getLocalizedMessage()), e);
                    }
                    line = reader.readLine();

                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to process file '" + script.getAbsolutePath() + "'", e);
            }
        }
    }

    private static void executeCommands(final CommandContext ctx, final Iterable<String> commands, final boolean failOnError) throws IOException {
        for (String cmd : commands) {
            try {
                if (failOnError) {
                    ctx.handle(cmd);
                } else {
                    ctx.handleSafe(cmd);
                }
            } catch (CommandFormatException e) {
                throw new IllegalArgumentException(String.format("Command '%s' is invalid. %s", cmd, e.getLocalizedMessage()), e);
            } catch (CommandLineException e) {
                throw new IllegalArgumentException(String.format("Command execution failed for command '%s'. %s", cmd, e.getLocalizedMessage()), e);
            }
        }
    }

    private static void executeBatch(final CommandContext ctx, final Iterable<String> commands) throws IOException {
        final BatchManager batchManager = ctx.getBatchManager();
        if (batchManager.activateNewBatch()) {
            final Batch batch = batchManager.getActiveBatch();
            for (String cmd : commands) {
                try {
                    batch.add(ctx.toBatchedCommand(cmd));
                } catch (CommandFormatException e) {
                    throw new IllegalArgumentException(String.format("Command '%s' is invalid. %s", cmd, e.getLocalizedMessage()), e);
                }
            }
            final ModelNode result = ctx.getModelControllerClient().execute(batch.toRequest());
            if (!ServerOperations.isSuccessfulOutcome(result)) {
                throw new IllegalArgumentException(ServerOperations.getFailureDescriptionAsString(result));
            }
        }
    }

    /**
     * Creates the command context and binds the client to the context.
     * <p/>
     * If the client is {@code null}, no client is bound to the context.
     *
     * @param client current connected client
     *
     * @return the command line context
     *
     * @throws IllegalStateException if the context fails to initialize
     */
    private static CommandContext create(final ModelControllerClient client) {
        final CommandContext commandContext;
        try {
            commandContext = CommandContextFactory.getInstance().newCommandContext();
            commandContext.bindClient(client);
        } catch (CliInitializationException e) {
            throw new IllegalStateException("Failed to initialize CLI context", e);
        }
        return commandContext;
    }

    /**
     * A client the delegates to the client from the constructor, but does nothing in the {@link #close() close}. The
     * delegate client will not be closed.
     */
    private static class NonClosingModelControllerClient implements ModelControllerClient {

        private final ModelControllerClient delegate;

        NonClosingModelControllerClient(final ModelControllerClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public ModelNode execute(final ModelNode operation) throws IOException {
            return delegate.execute(operation);
        }

        @Override
        public ModelNode execute(final Operation operation) throws IOException {
            return delegate.execute(operation);
        }

        @Override
        public ModelNode execute(final ModelNode operation, final OperationMessageHandler messageHandler) throws IOException {
            return delegate.execute(operation, messageHandler);
        }

        @Override
        public ModelNode execute(final Operation operation, final OperationMessageHandler messageHandler) throws IOException {
            return delegate.execute(operation, messageHandler);
        }

        @Override
        public OperationResponse executeOperation(final Operation operation, final OperationMessageHandler messageHandler) throws IOException {
            return delegate.executeOperation(operation, messageHandler);
        }

        @Override
        public AsyncFuture<ModelNode> executeAsync(final ModelNode operation, final OperationMessageHandler messageHandler) {
            return delegate.executeAsync(operation, messageHandler);
        }

        @Override
        public AsyncFuture<ModelNode> executeAsync(final Operation operation, final OperationMessageHandler messageHandler) {
            return delegate.executeAsync(operation, messageHandler);
        }

        @Override
        public AsyncFuture<OperationResponse> executeOperationAsync(final Operation operation, final OperationMessageHandler messageHandler) {
            return delegate.executeOperationAsync(operation, messageHandler);
        }

        @Override
        public void close() throws IOException {
            // Do nothing
        }
    }
}
