/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.server;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.core.launcher.CommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.StandardOutput;
import org.wildfly.plugin.core.MavenRepositoriesEnricher;
import org.wildfly.plugin.tools.server.ServerManager;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractStartMojo extends AbstractServerConnection {

    @Inject
    protected RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    protected RepositorySystemSession session;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    protected List<RemoteRepository> repositories;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession mavenSession;

    /**
     * The JVM options to use.
     */
    @Parameter(alias = "java-opts", property = PropertyNames.JAVA_OPTS)
    protected String[] javaOpts;

    /**
     * The {@code JAVA_HOME} to use for launching the server.
     */
    @Parameter(alias = "java-home", property = PropertyNames.JAVA_HOME)
    protected String javaHome;

    /**
     * Starts the server with debugging enabled.
     */
    @Parameter(property = "wildfly.debug", defaultValue = "false")
    protected boolean debug;

    /**
     * Sets the hostname to listen on for debugging. An {@code *} means all hosts.
     */
    @Parameter(property = "wildfly.debug.host", defaultValue = "*")
    protected String debugHost;

    /**
     * Sets the port the debugger should listen on.
     */
    @Parameter(property = "wildfly.debug.port", defaultValue = "8787")
    protected int debugPort;

    /**
     * Indicates whether the server should suspend itself until a debugger is attached.
     */
    @Parameter(property = "wildfly.debug.suspend", defaultValue = "false")
    protected boolean debugSuspend;

    /**
     * The path to the system properties file to load.
     */
    @Parameter(alias = "properties-file", property = PropertyNames.PROPERTIES_FILE)
    protected String propertiesFile;

    /**
     * The timeout value to use when starting the server.
     */
    @Parameter(alias = "startup-timeout", defaultValue = "60", property = PropertyNames.STARTUP_TIMEOUT)
    private long startupTimeout;

    /**
     * The arguments to be passed to the server.
     */
    @Parameter(alias = "server-args", property = PropertyNames.SERVER_ARGS)
    protected String[] serverArgs;

    /**
     * Set to {@code true} if you want to skip this goal, otherwise {@code false}.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.SKIP)
    protected boolean skip;

    /**
     * Specifies the environment variables to be passed to the process being started.
     * <div>
     *
     * <pre>
     * &lt;env&gt;
     *     &lt;HOME&gt;/home/wildfly/&lt;/HOME&gt;
     * &lt;/env&gt;
     * </pre>
     *
     * </div>
     */
    @Parameter
    private Map<String, String> env;

    private final AtomicBoolean initialized = new AtomicBoolean();

    protected ServerManager serverManager;
    protected MavenRepoManager mavenRepoManager;

    protected void init() throws MojoExecutionException {
        // Setting the mavenRepoManager is not thread-safe, however creating it more than once won't hurt anything
        if (initialized.compareAndSet(false, true)) {
            MavenRepositoriesEnricher.enrich(mavenSession, project, repositories);
            mavenRepoManager = createMavenRepoManager();
        }
    }

    protected MavenRepoManager createMavenRepoManager() throws MojoExecutionException {
        return new MavenArtifactRepositoryManager(repoSystem, session, repositories);
    }

    protected abstract Path getServerHome() throws MojoExecutionException, MojoFailureException;

    protected ServerContext startServer(final ServerType serverType) throws MojoExecutionException, MojoFailureException {
        final Log log = getLog();
        init();

        Path server = getServerHome();

        // Determine how stdout should be consumed
        try {
            final StandardOutput out = standardOutput();
            // Create the server and close the client after the start. The process will continue running even after
            // the maven process may have been finished
            final ModelControllerClient client = createClient();
            if (ServerManager.isRunning(client)) {
                throw new MojoExecutionException(String.format("%s server is already running?", serverType));
            }
            final CommandBuilder commandBuilder = createCommandBuilder(server);
            log.info(String.format("%s server is starting up.", serverType));
            final Launcher launcher = Launcher.of(commandBuilder)
                    .setRedirectErrorStream(true);
            if (env != null) {
                for (Map.Entry<String, String> entry : env.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        launcher.addEnvironmentVariable(entry.getKey(), entry.getValue());
                    }
                }
            }
            out.getRedirect().ifPresent(launcher::redirectOutput);

            final Process process = launcher.launch();
            if (serverType == ServerType.DOMAIN) {
                serverManager = ServerManager.builder().process(process).client(client).domain();
            } else {
                serverManager = ServerManager.builder().process(process).client(client).standalone();
            }
            // Note that if this thread is started and no shutdown goal is executed this stop the stdout and stderr
            // from being logged any longer. The user was warned in the documentation.
            out.startConsumer(process);
            if (!serverManager.waitFor(startupTimeout, TimeUnit.SECONDS)) {
                throw new MojoExecutionException(String.format("Server failed to start in %s seconds.", startupTimeout));
            }
            if (!process.isAlive()) {
                throw new MojoExecutionException("The process has been terminated before the start goal has completed.");
            }
            return new ServerContext() {
                @Override
                public Process process() {
                    return process;
                }

                @Override
                public CommandBuilder commandBuilder() {
                    return commandBuilder;
                }

                @Override
                public Path jbossHome() {
                    return server;
                }
            };
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("The server failed to start", e);
        }
    }

    protected abstract CommandBuilder createCommandBuilder(final Path jbossHome) throws MojoExecutionException;

    protected StandardOutput standardOutput() throws IOException {
        return StandardOutput.parse(null, false);
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

    /**
     * Checks the current state of the server. If the server is in a state of
     * {@link ClientConstants#CONTROLLER_PROCESS_STATE_RESTART_REQUIRED}, the process is restarted and a new
     * {@link ServerContext} is returned. If the server is in a stat of
     * {@link ClientConstants#CONTROLLER_PROCESS_STATE_RELOAD_REQUIRED}, the server will be reloaded and wait until
     * the server is running. If the server is in any other state, other than
     * {@link ClientConstants#CONTROLLER_PROCESS_STATE_RUNNING}, a warning message is logged to let the user know
     * the state is unknown.
     *
     * @param client  the client used to communicate with the server
     * @param context the current server context
     * @return a new context if a restart was required, otherwise the same context
     * @throws IOException            if an error occurs communicating with the server
     * @throws MojoExecutionException if a failure occurs checking the state or reloading the server
     * @throws MojoFailureException   if a failure occurs checking the state or reloading the server
     */
    protected ServerContext actOnServerState(final ModelControllerClient client, final ServerContext context)
            throws IOException, MojoExecutionException, MojoFailureException {
        final String serverState = serverManager.serverState();
        if (ClientConstants.CONTROLLER_PROCESS_STATE_RESTART_REQUIRED.equals(serverState)) {
            // Shutdown the server
            serverManager.shutdown(timeout);
            // Restart the server process
            return startServer(ServerType.STANDALONE);
        } else if (ClientConstants.CONTROLLER_PROCESS_STATE_RELOAD_REQUIRED.equals(serverState)) {
            serverManager.executeReload(Operations.createOperation("reload"));
            try {
                if (!serverManager.waitFor(startupTimeout, TimeUnit.SECONDS)) {
                    throw new MojoExecutionException(String.format("Server failed to start in %s seconds.", startupTimeout));
                }
            } catch (InterruptedException e) {
                throw new MojoExecutionException("Failed to wait for standalone server after a reload.", e);
            }
        } else if (!ClientConstants.CONTROLLER_PROCESS_STATE_RUNNING.equals(serverState)) {
            getLog().warn(String.format(
                    "The server may be in an unexpected state for further interaction. The current state is %s", serverState));
        }
        return context;
    }

    @Override
    protected int getManagementPort() {
        // Check the java-opts for a management port override
        if (javaOpts != null) {
            for (String opt : javaOpts) {
                if (opt.startsWith("-Djboss.management.http.port=") || opt.startsWith("-Djboss.management.https.port=")) {
                    final int equals = opt.indexOf('=');
                    return Integer.parseInt(opt.substring(equals + 1).trim());
                }
                if (opt.startsWith("-Djboss.socket.binding.port-offset=")) {
                    final int equals = opt.indexOf('=');
                    return super.getManagementPort() + Integer.parseInt(opt.substring(equals + 1).trim());
                }
            }
        }
        return super.getManagementPort();
    }

    @Override
    protected String getManagementHostName() {
        // Check the java-opts for a management port override
        if (javaOpts != null) {
            for (String opt : javaOpts) {
                if (opt.startsWith("-Djboss.bind.address.management=")) {
                    final int equals = opt.indexOf('=');
                    return opt.substring(equals + 1).trim();
                }
            }
        }
        return super.getManagementHostName();
    }
}
