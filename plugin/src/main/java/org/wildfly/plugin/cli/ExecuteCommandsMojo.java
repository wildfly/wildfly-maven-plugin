/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
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
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.plugin.common.Archives;
import org.wildfly.plugin.common.Environment;
import org.wildfly.plugin.common.MavenModelControllerClientConfiguration;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.common.StandardOutput;
import org.wildfly.plugin.core.ServerHelper;
import org.wildfly.plugin.repository.ArtifactNameBuilder;
import org.wildfly.plugin.repository.ArtifactResolver;

/**
 * Execute commands to the running WildFly Application Server.
 * <p/>
 * Commands should be formatted in the same manor CLI commands are formatted.
 * <p/>
 * Executing commands in a batch will rollback all changes if one command fails.
 * <pre>
 *      &lt;batch&gt;true&lt;/batch&gt;
 *      &lt;fail-on-error&gt;false&lt;/fail-on-error&gt;
 *      &lt;commands&gt;
 *          &lt;command&gt;/subsystem=logging/console=CONSOLE:write-attribute(name=level,value=DEBUG)&lt;/command&gt;
 *      &lt;/commands&gt;
 * </pre>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "execute-commands", threadSafe = true)
public class ExecuteCommandsMojo extends AbstractServerConnection {

    /**
     * {@code true} if commands execution should be skipped.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.SKIP)
    private boolean skip;

    /**
     * {@code true} if commands should be executed in a batch or {@code false} if they should be executed one at a
     * time.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.BATCH)
    private boolean batch;

    /**
     * The WildFly Application Server's home directory.
     * <p>
     * This parameter is required when {@code offline} is set to {@code true}. Otherwise this is not required, but
     * should be used for commands such as {@code module add} as they are executed on the local file system.
     * </p>
     */
    @Parameter(alias = "jboss-home", property = PropertyNames.JBOSS_HOME)
    private String jbossHome;

    /**
     * The system properties to be set when executing CLI commands.
     */
    @Parameter(alias = "system-properties")
    private Map<String, String> systemProperties;

    /**
     * The properties files to use when executing CLI scripts or commands.
     */
    @Parameter
    private List<File> propertiesFiles = new ArrayList<>();

    /**
     * The CLI commands to execute.
     */
    @Parameter(property = PropertyNames.COMMANDS)
    private List<String> commands = new ArrayList<>();

    /**
     * The CLI script files to execute.
     */
    @Parameter(property = PropertyNames.SCRIPTS)
    private List<File> scripts = new ArrayList<>();

    /**
     * Indicates whether or not subsequent commands should be executed if an error occurs executing a command. A value of
     * {@code false} will continue processing commands even if a previous command execution results in a failure.
     * <p>
     * Note that this value is ignored if {@code offline} is set to {@code true}.
     * </p>
     */
    @Parameter(alias = "fail-on-error", defaultValue = "true", property = PropertyNames.FAIL_ON_ERROR)
    private boolean failOnError = true;

    /**
     * Indicates the commands should be run in a new process. If the {@code jboss-home} property is not set an attempt
     * will be made to download a version of WildFly to execute commands on. However it's generally considered best
     * practice to set the {@code jboss-home} property if setting this value to {@code true}.
     * <p>
     * Note that if {@code offline} is set to {@code true} this setting really has no effect.
     * </p>
     * @since 2.0.0
     */
    @Parameter(defaultValue = "false", property = "wildfly.fork")
    private boolean fork;

    /**
     * Indicates whether or not CLI scrips or commands should be executed in an offline mode. This is useful for using
     * an embedded server or host controller.
     *
     * <p>This does not start an embedded server it instead skips checking if a server is running.</p>
     */
    @Parameter(name = "offline", defaultValue = "false", property = PropertyNames.OFFLINE)
    private boolean offline = false;

    /**
     * Indicates how {@code stdout} and {@code stderr} should be handled for the spawned CLI process. Currently a new
     * process is only spawned if {@code offline} is set to {@code true} or {@code fork} is set to {@code true}. Note
     * that {@code stderr} will be redirected to {@code stdout} if the value is defined unless the value is
     * {@code none}.
     * <div>
     * By default {@code stdout} and {@code stderr} are inherited from the current process. You can change the setting
     * to one of the follow:
     * <ul>
     * <li>{@code none} indicates the {@code stdout} and {@code stderr} stream should not be consumed</li>
     * <li>{@code System.out} or {@code System.err} to redirect to the current processes <em>(use this option if you
     * see odd behavior from maven with the default value)</em></li>
     * <li>Any other value is assumed to be the path to a file and the {@code stdout} and {@code stderr} will be
     * written there</li>
     * </ul>
     * </div>
     */
    @Parameter(name = "stdout", defaultValue = "System.out", property = PropertyNames.STDOUT)
    private String stdout;

    /**
     * The JVM options to pass to the offline process if the {@code offline} configuration parameter is set to
     * {@code true}.
     */
    @Parameter(alias = "java-opts", property = PropertyNames.JAVA_OPTS)
    private String[] javaOpts;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession session;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> repositories;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
    private File buildDir;

    @Inject
    private ArtifactResolver artifactResolver;

    @Override
    public String goal() {
        return "execute-commands";
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug("Skipping commands execution");
            return;
        }
        if (offline) {
            // The jbossHome is required for offline CLI
            if (!ServerHelper.isValidHomeDirectory(jbossHome)) {
                throw new MojoFailureException("Invalid JBoss Home directory is not valid: " + jbossHome);
            }
            executeInNewProcess(Paths.get(jbossHome));
        } else {
            if (fork) {
                // Check the jbossHome and if not found download it
                executeInNewProcess(extractIfRequired());
            } else {
                executeInProcess();
            }
        }
    }

    /**
     * Allows the {@link #javaOpts} to be set as a string. The string is assumed to be space delimited.
     *
     * @param value a spaced delimited value of JVM options
     */
    @SuppressWarnings("unused")
    public void setJavaOpts(final String value) {
        if (value != null) {
            javaOpts = value.split("\\s+");
        }
    }

    private void executeInNewProcess(final Path wildflyHome) throws MojoExecutionException {
        // If we have commands create a script file and execute
        if (commands != null && !commands.isEmpty()) {
            Path scriptFile = null;
            try {
                scriptFile = ScriptWriter.create(commands, batch, failOnError);
                executeInNewProcess(wildflyHome, scriptFile);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed execute commands.", e);
            } finally {
                if (scriptFile != null) {
                    try {
                        Files.deleteIfExists(scriptFile);
                    } catch (IOException e) {
                        getLog().debug("Failed to deleted CLI script file: " + scriptFile, e);
                    }
                }
            }
        }
        if (scripts != null && !scripts.isEmpty()) {
            for (File script : scripts) {
                executeInNewProcess(wildflyHome, script.toPath());
            }
        }
    }

    private void executeInNewProcess(final Path wildflyHome, final Path scriptFile) throws MojoExecutionException {
        getLog().debug("Executing CLI scripts");
        try {
            final StandardOutput out = StandardOutput.parse(stdout, false);

            final int exitCode = executeInNewProcess(wildflyHome, scriptFile, out);
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
                if (failOnError) {
                    throw new MojoExecutionException(msg.toString());
                } else {
                    getLog().warn(msg);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to execute scripts.", e);
        }
    }

    private int executeInNewProcess(final Path wildflyHome, final Path scriptFile, final StandardOutput stdout) throws MojoExecutionException, IOException {
        final Log log = getLog();
        try (MavenModelControllerClientConfiguration clientConfiguration = getClientConfiguration()) {

            final CliCommandBuilder builder = CliCommandBuilder.of(wildflyHome)
                    .setScriptFile(scriptFile);
            if (!offline) {
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
            if (systemProperties != null) {
                systemProperties.forEach((key, value) -> builder.addJavaOption(String.format("-D%s=%s", key, value)));
                if (systemProperties.containsKey("module.path")) {
                    builder.setModuleDirs(systemProperties.get("module.path"));
                }
            }

            if (propertiesFiles != null) {
                final Properties properties = new Properties();
                for (File file : propertiesFiles) {
                    parseProperties(file, properties);
                }
                for (String key : properties.stringPropertyNames()) {
                    builder.addJavaOption(String.format("-D%s=%s", key, properties.getProperty(key)));
                }
            }

            if (javaOpts != null) {
                if (log.isDebugEnabled()) {
                    log.debug("java opts: " + Arrays.toString(javaOpts));
                }
                for (String opt : javaOpts) {
                    if (!opt.trim().isEmpty()) {
                        builder.addJavaOption(opt);
                    }
                }

            }
            if (log.isDebugEnabled()) {
                log.debug("process parameters: " + builder.build());
            }
            final Launcher launcher = Launcher.of(builder)
                    .addEnvironmentVariable("JBOSS_HOME", wildflyHome.toString())
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

    private void executeInProcess() throws MojoExecutionException, MojoFailureException {
        // The jbossHome is not required, but if defined should be valid
        if (jbossHome != null && !ServerHelper.isValidHomeDirectory(jbossHome)) {
            throw new MojoFailureException("Invalid JBoss Home directory is not valid: " + jbossHome);
        }
        final Properties currentSystemProperties = System.getProperties();
        try {
            getLog().debug("Executing commands");
            // Create new system properties with the defaults set to the current system properties
            final Properties newSystemProperties = new Properties(currentSystemProperties);

            // Add the JBoss Home if defined
            if (jbossHome != null) {
                newSystemProperties.setProperty("jboss.home", jbossHome);
                newSystemProperties.setProperty("jboss.home.dir", jbossHome);
            }

            if (propertiesFiles != null) {
                for (File file : propertiesFiles) {
                    parseProperties(file, newSystemProperties);
                }
            }

            if (systemProperties != null) {
                newSystemProperties.putAll(systemProperties);
            }

            // Set the system properties for executing commands
            System.setProperties(newSystemProperties);
            CommandContext commandContext = null;
            try (ModelControllerClient client = createClient()) {
                commandContext = createCommandContext(client);
                if (commands != null && !commands.isEmpty()) {
                    if (batch) {
                        executeBatch(commandContext, commands);
                    } else {
                        executeCommands(commandContext, commands, failOnError);
                    }
                }
                if (scripts != null && !scripts.isEmpty()) {
                    for (File scriptFile : scripts) {
                        final List<String> commands = Files.readAllLines(scriptFile.toPath(), StandardCharsets.UTF_8);
                        if (batch) {
                            executeBatch(commandContext, commands);
                        } else {
                            executeCommands(commandContext, commands, failOnError);
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

    private Path extractIfRequired() throws MojoFailureException {
        if (jbossHome != null) {
            //we do not need to download WildFly
            return Paths.get(jbossHome);
        }
        final Path result = artifactResolver.resolve(session, repositories, ArtifactNameBuilder.forRuntime(null).build());
        try {
            return Archives.uncompress(result, buildDir.toPath());
        } catch (IOException e) {
            throw new MojoFailureException("Artifact was not successfully extracted: " + result, e);
        }
    }

    private static void parseProperties(final File file, final Properties properties) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
    }
}
