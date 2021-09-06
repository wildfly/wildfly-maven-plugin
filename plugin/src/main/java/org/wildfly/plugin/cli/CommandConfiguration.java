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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.common.MavenModelControllerClientConfiguration;

/**
 * The configuration used to execute CLI commands.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CommandConfiguration {

    private final Collection<String> jvmOptions;
    private final Collection<String> cliArguments;
    private final Collection<String> commands;
    private final Map<String, String> systemProperties;
    private final Collection<Path> propertiesFiles;
    private final Collection<Path> scripts;
    private final Supplier<ModelControllerClient> client;
    private final Supplier<MavenModelControllerClientConfiguration> clientConfiguration;
    private boolean batch;
    private boolean failOnError;
    private boolean fork;
    private Path jbossHome;
    private boolean offline;
    private String stdout;
    private int timeout;
    private boolean append;

    private CommandConfiguration(final Supplier<ModelControllerClient> client,
                                 final Supplier<MavenModelControllerClientConfiguration> clientConfiguration) {
        this.client = client;
        this.clientConfiguration = clientConfiguration;
        jvmOptions = new ArrayList<>();
        commands = new ArrayList<>();
        systemProperties = new LinkedHashMap<>();
        propertiesFiles = new ArrayList<>();
        scripts = new ArrayList<>();
        failOnError = true;
        cliArguments = new ArrayList<>();
    }

    /**
     * Creates a new command configuration.
     *
     * @param clientSupplier              the supplier used to get a management client
     * @param clientConfigurationSupplier a supplier used to get the client configuration
     *
     * @return a new command configuration
     */
    public static CommandConfiguration of(final Supplier<ModelControllerClient> clientSupplier,
                                          final Supplier<MavenModelControllerClientConfiguration> clientConfigurationSupplier) {
        return new CommandConfiguration(clientSupplier, clientConfigurationSupplier);
    }

    /**
     * Is output appended to file.
     */
    public boolean isAppend() {
        return append;
    }

    /**
     * If true append output to file, otherwise a new file is created.
     */
    public CommandConfiguration setAppend(boolean append) {
        this.append = append;
        return this;
    }

    /**
     * Indicates whether or not the commands should be run in a batch or not.
     *
     * @return {@code true} if the commands should be executed in a batch, otherwise {@code false}
     */
    public boolean isBatch() {
        return batch;
    }

    /**
     * Sets whether or not the commands should be executed in a batch or not.
     *
     * @param batch {@code true} if the commands should be executed in a batch, otherwise {@code false}
     *
     * @return this configuration
     */
    public CommandConfiguration setBatch(final boolean batch) {
        this.batch = batch;
        return this;
    }

    /**
     * Returns the management client.
     *
     * @return the management client
     */
    public ModelControllerClient getClient() {
        return client.get();
    }

    /**
     * Returns the management client configuration.
     *
     * @return the management client configuration
     */
    public MavenModelControllerClientConfiguration getClientConfiguration() {
        return clientConfiguration.get();
    }

    /**
     * Returns the JBoss Home directory.
     *
     * @return the JBoss Home directory or {@code null} if the value was not set
     */
    public Path getJBossHome() {
        return jbossHome;
    }

    /**
     * Sets the JBoss Home directory.
     *
     * @param jbossHome the JBoss Home directory or {@code null} if the value is not required
     *
     * @return this configuration
     */
    public CommandConfiguration setJBossHome(final String jbossHome) {
        if (jbossHome == null) {
            this.jbossHome = null;
        } else {
            this.jbossHome = Paths.get(jbossHome);
        }
        return this;
    }

    /**
     * Sets the JBoss Home directory.
     *
     * @param jbossHome the JBoss Home directory or {@code null} if the value is not required
     *
     * @return this configuration
     */
    public CommandConfiguration setJBossHome(final Path jbossHome) {
        this.jbossHome = jbossHome;
        return this;
    }

    /**
     * Returns the JVM options used if {@link #isFork()} or {@link #isOffline()} is set to {@code true}.
     *
     * @return the JVM options
     */
    public Collection<String> getJvmOptions() {
        return Collections.unmodifiableCollection(jvmOptions);
    }

    /**
     * Adds the JVM options used if {@link #isFork()} or {@link #isOffline()} is set to {@code true}.
     *
     * @param jvmOptions the JVM options or {@code null}
     *
     * @return this configuration
     */
    public CommandConfiguration addJvmOptions(final String... jvmOptions) {
        if (jvmOptions != null) {
            Collections.addAll(this.jvmOptions, jvmOptions);
        }
        return this;
    }

    /**
     * Returns the CLI arguments used if {@link #isFork()} or {@link #isOffline()} is set to {@code true}.
     *
     * @return the CLI arguments
     */
    public Collection<String> getCLIArguments() {
        return Collections.unmodifiableCollection(cliArguments);
    }

    /**
     * Adds the CLI arguments used if {@link #isFork()} or {@link #isOffline()} is set to {@code true}.
     *
     * @param arguments the CLI arguments or {@code null}
     *
     * @return this configuration
     */
    public CommandConfiguration addCLIArguments(final String... arguments) {
        if (arguments != null) {
            Collections.addAll(this.cliArguments, arguments);
        }
        return this;
    }

    /**
     * Returns the system properties to set before CLI commands are executed.
     *
     * @return the system properties to set before CLI commands are executed
     */
    public Map<String, String> getSystemProperties() {
        return Collections.unmodifiableMap(systemProperties);
    }

    /**
     * Adds to the system properties to set before the CLI commands are executed.
     *
     * @param systemProperties the system properties or {@code null}
     *
     * @return this configuration
     */
    public CommandConfiguration addSystemProperties(final Map<String, String> systemProperties) {
        if (systemProperties != null) {
            this.systemProperties.putAll(systemProperties);
        }
        return this;
    }

    /**
     * The properties files to use when executing CLI scripts or commands.
     *
     * @return the paths to the properties files
     */
    public Collection<Path> getPropertiesFiles() {
        return Collections.unmodifiableCollection(propertiesFiles);
    }

    /**
     * Adds the properties files to use when executing CLI scripts or commands.
     *
     * @param propertiesFiles the property files to add
     *
     * @return this configuration
     */
    public CommandConfiguration addPropertiesFiles(final Collection<File> propertiesFiles) {
        propertiesFiles.forEach(f -> this.propertiesFiles.add(f.toPath()));
        return this;
    }

    /**
     * Returns the commands to be executed.
     *
     * @return the commands to be executed
     */
    public Collection<String> getCommands() {
        return Collections.unmodifiableCollection(commands);
    }

    /**
     * Adds the commands to the CLI commands that should be executed.
     *
     * @param commands the commands to be executed
     *
     * @return this configuration
     */
    public CommandConfiguration addCommands(final Collection<String> commands) {
        if (commands != null) {
            this.commands.addAll(commands);
        }
        return this;
    }

    /**
     * Returns the scripts to be executed.
     *
     * @return the scripts to be executed
     */
    public Collection<Path> getScripts() {
        return Collections.unmodifiableCollection(scripts);
    }

    /**
     * Adds the scripts to be executed.
     *
     * @param scripts the scripts to be executed
     *
     * @return this configuration
     */
    public CommandConfiguration addScripts(final Collection<File> scripts) {
        scripts.forEach(f -> this.scripts.add(f.toPath()));
        return this;
    }

    /**
     * Indicates whether or not CLI commands should fail if the command ends in an error or not.
     *
     * @return {@code true} if a CLI command fails then the execution should fail
     */
    public boolean isFailOnError() {
        return failOnError;
    }

    /**
     * Sets whether or not CLI commands should fail if the command ends in an error or not.
     *
     * @param failOnError {@code true} if a CLI command fails then the execution should fail
     *
     * @return this configuration
     */
    public CommandConfiguration setFailOnError(final boolean failOnError) {
        this.failOnError = failOnError;
        return this;
    }

    /**
     * Indicates whether or not CLI commands should be executed in a new process.
     *
     * @return {@code true} to execute CLI commands in a new process
     */
    public boolean isFork() {
        return fork;
    }

    /**
     * Sets whether or not the commands should be executed in a new process.
     * <p>
     * Note that is {@link #isOffline()} is set to {@code true} this has no effect.
     * </p>
     *
     * @param fork {@code true} if commands should be executed in a new process
     *
     * @return this configuration
     */
    public CommandConfiguration setFork(final boolean fork) {
        this.fork = fork;
        return this;
    }

    /**
     * Indicates whether or not this should be an offline process.
     *
     * @return {@code true} if this should be an offline process, otherwise {@code false}
     */
    public boolean isOffline() {
        return offline;
    }

    /**
     * Sets whether a client should be associated with the CLI context.
     * <p>
     * Note this launches CLI in a new process.
     * </p>
     *
     * @param offline {@code true} if this should be an offline process
     *
     * @return this configuration
     */
    public CommandConfiguration setOffline(final boolean offline) {
        this.offline = offline;
        return this;
    }

    /**
     * The pattern used to determine how standard out is handled for a new CLI process.
     *
     * @return the standard out pattern
     */
    public String getStdout() {
        return stdout;
    }

    /**
     * Sets how the standard output stream should be handled if {@link #isFork()} or {@link #isOffline()} is set to
     * {@code true}.
     *
     * @param stdout the pattern for standard out
     *
     * @return this configuration
     */
    public CommandConfiguration setStdout(final String stdout) {
        this.stdout = stdout;
        return this;
    }

    /**
     * Gets the timeout, in seconds, used for the management connection.
     *
     * @return the timeout used for the management connection
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout, in seconds, used for the management client connection.
     *
     * @param timeout the timeout to use in seconds
     *
     * @return this configuration
     */
    public CommandConfiguration setTimeout(final int timeout) {
        this.timeout = timeout;
        return this;
    }
}
