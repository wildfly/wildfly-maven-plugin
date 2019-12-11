/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.launcher.CliCommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.plugin.common.Environment;
import org.wildfly.plugin.common.MavenModelControllerClientConfiguration;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.common.StandardOutput;
import org.wildfly.plugin.core.ServerHelper;

/**
 * A command executor for executing CLI commands.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Singleton
@Named
public class CommandExecutor extends AbstractLogEnabled {

    /**
     * Executes CLI commands based on the configuration.
     *
     * @param config the configuration used to execute the CLI commands
     *
     * @throws MojoFailureException   if the JBoss Home directory is required and invalid
     * @throws MojoExecutionException if an error occurs executing the CLI commands
     */
    public void execute(final CommandConfiguration config) throws MojoFailureException, MojoExecutionException {
        if (config.isOffline()) {
            // The jbossHome is required for offline CLI
            if (!ServerHelper.isValidHomeDirectory(config.getJBossHome())) {
                throw new MojoFailureException("Invalid JBoss Home directory is not valid: " + config.getJBossHome());
            }
            executeInNewProcess(config);
        } else {
            if (config.isFork()) {
                executeInNewProcess(config);
            } else {
                executeInProcess(config);
            }
        }
    }

    private void executeInNewProcess(final CommandConfiguration config) throws MojoExecutionException {
        // If we have commands create a script file and execute
        if (!config.getCommands().isEmpty()) {
            Path scriptFile = null;
            try {
                scriptFile = ScriptWriter.create(config);
                executeInNewProcess(config, scriptFile);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed execute commands.", e);
            } finally {
                if (scriptFile != null) {
                    try {
                        Files.deleteIfExists(scriptFile);
                    } catch (IOException e) {
                        getLogger().debug("Failed to deleted CLI script file: " + scriptFile, e);
                    }
                }
            }
        }
        if (!config.getScripts().isEmpty()) {
            for (Path script : config.getScripts()) {
                executeInNewProcess(config, script);
            }
        }
    }

    private void executeInNewProcess(final CommandConfiguration config, final Path scriptFile) throws MojoExecutionException {
        getLogger().debug("Executing CLI scripts");
        try {
            final StandardOutput out = StandardOutput.parse(config.getStdout(), false);

            final int exitCode = executeInNewProcess(config, scriptFile, out);
            if (exitCode != 0) {
                final StringBuilder msg = new StringBuilder("Failed to execute commands: ");
                switch (out.getTarget()) {
                    case COLLECTING:
                        msg.append(out);
                        break;
                    case FILE:
                        final Path stdoutPath = out.getStdoutPath();
                        msg.append("See ").append(stdoutPath).append(" for full details of failure.").append(System.lineSeparator());
                        final List<String> lines = Files.readAllLines(stdoutPath);
                        lines.subList(Math.max(lines.size() - 4, 0), lines.size())
                                .forEach(line -> msg.append(line).append(System.lineSeparator()));
                        break;
                    case SYSTEM_ERR:
                    case SYSTEM_OUT:
                    case INHERIT:
                        msg.append("See previous messages for failure messages.");
                        break;
                    default:
                        msg.append("Reason unknown");
                }
                if (config.isFailOnError()) {
                    throw new MojoExecutionException(msg.toString());
                } else {
                    getLogger().warn(msg.toString());
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to execute scripts.", e);
        }
    }

    private int executeInNewProcess(final CommandConfiguration config, final Path scriptFile, final StandardOutput stdout) throws MojoExecutionException, IOException {
        final Logger log = getLogger();
        try (MavenModelControllerClientConfiguration clientConfiguration = config.getClientConfiguration()) {

            final CliCommandBuilder builder = CliCommandBuilder.of(config.getJBossHome())
                    .setScriptFile(scriptFile)
                    .setTimeout(config.getTimeout() * 1000);
            if (!config.isOffline()) {
                builder.setConnection(clientConfiguration.getController());
            }
            // Configure the authentication config url if defined
            if (clientConfiguration.getAuthenticationConfigUri() != null) {
                builder.addJavaOption("-Dwildfly.config.url=" + clientConfiguration.getAuthenticationConfigUri().toString());
            }
            // Workaround for WFCORE-4121
            if (Environment.isModularJvm(builder.getJavaHome())) {
                builder.addJavaOptions(Environment.getModularJvmArguments());
            }
            final Map<String, String> systemProperties = config.getSystemProperties();
            systemProperties.forEach((key, value) -> builder.addJavaOption(String.format("-D%s=%s", key, value)));
            if (systemProperties.containsKey("module.path")) {
                builder.setModuleDirs(systemProperties.get("module.path"));
            }

            final Properties properties = new Properties();
            for (Path file : config.getPropertiesFiles()) {
                parseProperties(file, properties);
            }
            for (String key : properties.stringPropertyNames()) {
                builder.addJavaOption(String.format("-D%s=%s", key, properties.getProperty(key)));
            }

            final Collection<String> javaOpts = config.getJvmOptions();
            if (log.isDebugEnabled() && !javaOpts.isEmpty()) {
                log.debug("java opts: " + javaOpts);
            }
            for (String opt : javaOpts) {
                if (!opt.trim().isEmpty()) {
                    builder.addJavaOption(opt);
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("process parameters: " + builder.build());
            }
            final Launcher launcher = Launcher.of(builder)
                    .addEnvironmentVariable("JBOSS_HOME", config.getJBossHome().toString())
                    .setRedirectErrorStream(true);
            stdout.getRedirect().ifPresent(launcher::redirectOutput);
            final Process process = launcher.launch();
            final Optional<Thread> consoleConsumer = stdout.startConsumer(process);
            try {
                return process.waitFor();
            } catch (InterruptedException e) {
                throw new MojoExecutionException("Failed to run goal execute-commands in forked process.", e);
            } finally {
                // Be safe and destroy the process to ensure we don't leave rouge processes running
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
                consoleConsumer.ifPresent(Thread::interrupt);
            }
        }
    }

    private void executeInProcess(final CommandConfiguration config) throws MojoExecutionException, MojoFailureException {
        // The jbossHome is not required, but if defined should be valid
        final Path jbossHome = config.getJBossHome();
        if (jbossHome != null && !ServerHelper.isValidHomeDirectory(jbossHome)) {
            throw new MojoFailureException("Invalid JBoss Home directory is not valid: " + jbossHome);
        }
        final Properties currentSystemProperties = System.getProperties();
        try {
            getLogger().debug("Executing commands");
            // Create new system properties with the defaults set to the current system properties
            final Properties newSystemProperties = new Properties(currentSystemProperties);

            // Add the JBoss Home if defined
            if (jbossHome != null) {
                newSystemProperties.setProperty("jboss.home", jbossHome.toString());
                newSystemProperties.setProperty("jboss.home.dir", jbossHome.toString());
            }

            for (Path file : config.getPropertiesFiles()) {
                parseProperties(file, newSystemProperties);
            }

            newSystemProperties.putAll(config.getSystemProperties());

            // Set the system properties for executing commands
            System.setProperties(newSystemProperties);
            CommandContext commandContext = null;
            try (ModelControllerClient client = config.getClient()) {
                commandContext = createCommandContext(client);
                final Collection<String> commands = config.getCommands();
                if (!commands.isEmpty()) {
                    if (config.isBatch()) {
                        executeBatch(commandContext, commands);
                    } else {
                        executeCommands(commandContext, commands, config.isFailOnError());
                    }
                }
                final Collection<Path> scripts = config.getScripts();
                if (!scripts.isEmpty()) {
                    for (Path scriptFile : scripts) {
                        final List<String> cmds = Files.readAllLines(scriptFile, StandardCharsets.UTF_8);
                        if (config.isBatch()) {
                            executeBatch(commandContext, cmds);
                        } else {
                            executeCommands(commandContext, cmds, config.isFailOnError());
                        }
                    }
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Could not execute commands.", e);
            } finally {
                if (commandContext != null) {
                    commandContext.terminateSession();
                }
            }
        } catch (IOException e) {
            throw new MojoFailureException("Failed to parse properties.", e);
        } finally {
            System.setProperties(currentSystemProperties);
        }
    }

    private static void executeCommands(final CommandContext ctx, final Iterable<String> commands, final boolean failOnError) throws MojoExecutionException {
        for (String cmd : commands) {
            try {
                if (failOnError) {
                    ctx.handle(cmd);
                } else {
                    ctx.handleSafe(cmd);
                }
            } catch (CommandFormatException e) {
                throw new MojoExecutionException(String.format("Command '%s' is invalid. %s", cmd, e.getLocalizedMessage()), e);
            } catch (CommandLineException e) {
                throw new MojoExecutionException(String.format("Command execution failed for command '%s'. %s", cmd, e.getLocalizedMessage()), e);
            }
        }
    }

    private static void executeBatch(final CommandContext ctx, final Iterable<String> commands) throws IOException, MojoExecutionException {
        final BatchManager batchManager = ctx.getBatchManager();
        if (batchManager.activateNewBatch()) {
            final Batch batch = batchManager.getActiveBatch();
            for (String cmd : commands) {
                try {
                    batch.add(ctx.toBatchedCommand(cmd));
                } catch (CommandFormatException e) {
                    throw new MojoExecutionException(String.format("Command '%s' is invalid. %s", cmd, e.getLocalizedMessage()), e);
                }
            }
            final ModelNode result = ctx.getModelControllerClient().execute(batch.toRequest());
            if (!ServerOperations.isSuccessfulOutcome(result)) {
                throw new MojoExecutionException(ServerOperations.getFailureDescriptionAsString(result));
            }
        }
    }

    private CommandContext createCommandContext(final ModelControllerClient client) {
        CommandContext commandContext = null;
        try {
            commandContext = CommandContextFactory.getInstance().newCommandContext();
            commandContext.bindClient(client);
        } catch (CommandLineException e) {
            throw new IllegalStateException("Failed to initialize CLI context", e);
        } catch (Exception e) {
            // Terminate the session if we've encountered an error
            if (commandContext != null) {
                commandContext.terminateSession();
            }
            throw new IllegalStateException("Failed to initialize CLI context", e);
        }
        return commandContext;
    }

    private static void parseProperties(final Path file, final Properties properties) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
    }
}
