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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Named;
import javax.inject.Singleton;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.dmr.ModelNode;
import org.wildfly.plugin.common.MavenModelControllerClientConfiguration;
import org.wildfly.plugin.common.ServerOperations;

/**
 * Executes CLI commands.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Named
@Singleton
public class CommandExecutor {
    private static final String WILDFLY_CONFIG_KEY = "wildfly.config.url";

    /**
     * Executes the commands and scripts provided.
     *
     * @param configuration the configuration to use when connecting the {@link CommandContext}
     * @param commands      the commands to execute
     *
     * @throws IOException if an error occurs processing the commands
     */
    public void execute(final MavenModelControllerClientConfiguration configuration, final Commands commands) throws IOException {
        execute(configuration, (Path) null, commands);
    }

    /**
     * Executes the commands and scripts provided.
     *
     * @param configuration the configuration to use when connecting the {@link CommandContext}
     * @param wildflyHome   the path to WildFly for setting the {@code jboss.home.dir} system property or {@code null} if
     *                      should not be set
     * @param commands      the commands to execute
     *
     * @throws IOException if an error occurs processing the commands
     */
    public void execute(final MavenModelControllerClientConfiguration configuration, final Path wildflyHome, final Commands commands) throws IOException {
        if (wildflyHome != null) {
            try {
                System.setProperty("jboss.home.dir", wildflyHome.toString());
                executeCommands(configuration, commands);
            } finally {
                System.clearProperty("jboss.home.dir");
            }
        } else {
            executeCommands(configuration, commands);
        }
    }

    /**
     * Executes the commands and scripts provided.
     *
     * @param configuration the configuration to use when connecting the {@link CommandContext}
     * @param wildflyHome   the path to WildFly for setting the {@code jboss.home.dir} system property or {@code null} if
     *                      should not be set
     * @param commands      the commands to execute
     *
     * @throws IOException if an error occurs processing the commands
     */
    public void execute(final MavenModelControllerClientConfiguration configuration, final String wildflyHome, final Commands commands) throws IOException {
        Path path = null;
        if (wildflyHome != null) {
            path = Paths.get(wildflyHome);
        }
        execute(configuration, path, commands);
    }

    private void executeCommands(final MavenModelControllerClientConfiguration configuration, final Commands commands) throws IOException {

        if (commands.hasCommands() || commands.hasScripts()) {

            try {
                ModuleEnvironment.initJaxp();
                final String currentWildFlyConfUrl = System.getProperty(WILDFLY_CONFIG_KEY);
                // Configure the authentication config url if defined
                if (configuration.getAuthenticationConfigUri() != null) {
                    System.setProperty(WILDFLY_CONFIG_KEY, configuration.getAuthenticationConfigUri().toString());
                }
                final CommandContext ctx = create(configuration);
                try {

                    if (commands.isBatch()) {
                        executeBatch(ctx, commands.getCommands());
                    } else {
                        executeCommands(ctx, commands.getCommands(), commands.isFailOnError());
                    }
                    executeScripts(ctx, commands.getScripts(), commands.isFailOnError());

                } finally {
                    // Reset the authentication config property
                    if (currentWildFlyConfUrl == null) {
                        System.clearProperty(WILDFLY_CONFIG_KEY);
                    } else {
                        System.setProperty(WILDFLY_CONFIG_KEY, currentWildFlyConfUrl);
                    }
                    ctx.terminateSession();
                    ctx.bindClient(null);
                }
            } finally {
                ModuleEnvironment.restorePlatform();
            }
        }
    }

    private static void executeScripts(final CommandContext ctx, final Iterable<File> scripts, final boolean failOnError) {
        for (File script : scripts) {
            try {
                executeCommands(ctx, Files.readAllLines(script.toPath(), StandardCharsets.UTF_8), failOnError);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to process file '" + script.getAbsolutePath() + "'", e);
            }
        }
    }

    private static void executeCommands(final CommandContext ctx, final Iterable<String> commands, final boolean failOnError) {
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
     * Creates the command context and connects the context.
     *
     * @param configuration the configuration to use when connecting the {@link CommandContext}
     *
     * @return the command line context
     *
     * @throws IllegalStateException if the context fails to initialize
     */
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    private CommandContext create(final MavenModelControllerClientConfiguration configuration) {
        CommandContext commandContext = null;
        try {
            // Use System.in and System.out to allow prompting for a username and password if required
            commandContext = CommandContextFactory.getInstance().newCommandContext(configuration.getController(),
                    configuration.getUsername(), configuration.getPassword(), System.in, System.out);
            // Connect the controller
            commandContext.connectController();
        } catch (CommandLineException e) {
            // Terminate the session if we've encountered an error
            if (commandContext != null) {
                commandContext.terminateSession();
            }
            throw new IllegalStateException("Failed to initialize CLI context", e);
        }
        return commandContext;
    }
}
