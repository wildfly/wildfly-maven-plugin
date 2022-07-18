/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.core.launcher.CliCommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.plugin.common.Environment;
import org.wildfly.plugin.common.StandardOutput;
import static org.wildfly.plugin.core.Constants.CLI_RESOLVE_PARAMETERS_VALUES;

/**
 * An abstract command executor for executing CLI commands.
 *
 * @author jdenise@redhat.com
 */
public abstract class AbstractCommandExecutor<T extends BaseCommandConfiguration> extends AbstractLogEnabled {

    /**
     * Executes CLI commands based on the configuration.
     *
     * @param config the configuration used to execute the CLI commands
     * @param artifactResolver Resolver to retrieve CLI artifact for in-process execution.
     *
     * @throws MojoFailureException   if the JBoss Home directory is required and invalid
     * @throws MojoExecutionException if an error occurs executing the CLI commands
     */
    public abstract void execute(final T config, MavenRepoManager artifactResolver) throws MojoFailureException, MojoExecutionException;
    protected abstract int executeInNewProcess(final T config, final Path scriptFile, final StandardOutput stdout) throws MojoExecutionException, IOException;

    protected void executeInNewProcess(final T config) throws MojoExecutionException {
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

    private void executeInNewProcess(final T config, final Path scriptFile) throws MojoExecutionException {
        getLogger().debug("Executing CLI scripts");
        try {
            final StandardOutput out = StandardOutput.parse(config.getStdout(), false, config.isAppend());

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

    protected CliCommandBuilder createCommandBuilder(final T config, final Path scriptFile) throws IOException {
        final Logger log = getLogger();
        final CliCommandBuilder builder = CliCommandBuilder.of(config.getJBossHome())
                .setScriptFile(scriptFile)
                .addCliArguments(config.getCLIArguments())
                .setTimeout(config.getTimeout() * 1000);

        // Workaround for WFCORE-4121
        if (Environment.isModularJvm(builder.getJavaHome())) {
            builder.addJavaOptions(Environment.getModularJvmArguments());
        }
        final Map<String, String> systemProperties = config.getSystemProperties();
        systemProperties.forEach((key, value) -> builder.addJavaOption(String.format("-D%s=%s", key, value)));
        if (systemProperties.containsKey("module.path")) {
            String[] modulePaths = systemProperties.get("module.path").split(File.pathSeparator);
            builder.setModuleDirs(modulePaths);
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
        if (config.isExpressionResolved()) {
            builder.addCliArgument(CLI_RESOLVE_PARAMETERS_VALUES);
        }
        if (log.isDebugEnabled()) {
            log.debug("process parameters: " + builder.build());
        }
        return builder;
    }

    protected int launchProcess(CliCommandBuilder builder, final T config,
            final StandardOutput stdout) throws MojoExecutionException, IOException {
        final Logger log = getLogger();

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

    static void parseProperties(final Path file, final Properties properties) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
    }
}
