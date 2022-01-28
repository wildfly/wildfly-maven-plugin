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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The configuration used to execute forked offline CLI commands.
 *
 * @author jdenise@redhat.com
 */
public class BaseCommandConfiguration {

    private final Collection<String> jvmOptions;
    private final Collection<String> cliArguments;
    private final Collection<String> commands;
    private final Map<String, String> systemProperties;
    private final Collection<Path> propertiesFiles;
    private final Collection<Path> scripts;
    private final boolean batch;
    private final boolean failOnError;
    private final Path jbossHome;
    private final String stdout;
    private final int timeout;
    private final boolean append;
    private final boolean resolveExpression;

    protected abstract static class AbstractBuilder<T extends AbstractBuilder<T>> {

        private final Collection<String> jvmOptions = new ArrayList<>();
        private final Collection<String> cliArguments = new ArrayList<>();
        private final Collection<String> commands = new ArrayList<>();
        private final Map<String, String> systemProperties = new LinkedHashMap<>();
        private final Collection<Path> propertiesFiles = new ArrayList<>();
        private final Collection<Path> scripts = new ArrayList<>();
        private boolean batch;
        private boolean failOnError = true;
        private Path jbossHome;
        private String stdout;
        private int timeout;
        private boolean append;
        private boolean resolveExpression;

        protected abstract T builderInstance();

        /**
         * If true append output to file, otherwise a new file is created.
         *
         * @param append true to append to the file.
         * @return this
         */
        public T setAppend(boolean append) {
            this.append = append;
            return builderInstance();
        }

        /**
         * Sets whether or not the commands should be executed in a batch or
         * not.
         *
         * @param batch {@code true} if the commands should be executed in a
         * batch, otherwise {@code false}
         *
         * @return this configuration
         */
        public T setBatch(final boolean batch) {
            this.batch = batch;
            return builderInstance();
        }

        /**
         * Sets the JBoss Home directory.
         *
         * @param jbossHome the JBoss Home directory or {@code null} if the
         * value is not required
         *
         * @return this configuration
         */
        public T setJBossHome(final String jbossHome) {
            if (jbossHome == null) {
                this.jbossHome = null;
            } else {
                this.jbossHome = Paths.get(jbossHome);
            }
            return builderInstance();
        }

        /**
         * Sets the JBoss Home directory.
         *
         * @param jbossHome the JBoss Home directory or {@code null} if the
         * value is not required
         *
         * @return this configuration
         */
        public T setJBossHome(final Path jbossHome) {
            this.jbossHome = jbossHome;
            return builderInstance();
        }

        /**
         * Adds the JVM options used if {@link #isFork()} or
         * {@link #isOffline()} is set to {@code true}.
         *
         * @param jvmOptions the JVM options or {@code null}
         *
         * @return this configuration
         */
        public T addJvmOptions(final String... jvmOptions) {
            if (jvmOptions != null) {
                Collections.addAll(this.jvmOptions, jvmOptions);
            }
            return builderInstance();
        }

        /**
         * Adds the CLI arguments used if {@link #isFork()} or
         * {@link #isOffline()} is set to {@code true}.
         *
         * @param arguments the CLI arguments or {@code null}
         *
         * @return this configuration
         */
        public T addCLIArguments(final String... arguments) {
            if (arguments != null) {
                Collections.addAll(this.cliArguments, arguments);
            }
            return builderInstance();
        }

        /**
         * Adds to the system properties to set before the CLI commands are
         * executed.
         *
         * @param systemProperties the system properties or {@code null}
         *
         * @return this configuration
         */
        public T addSystemProperties(final Map<String, String> systemProperties) {
            if (systemProperties != null) {
                this.systemProperties.putAll(systemProperties);
            }
            return builderInstance();
        }

        /**
         * Adds the properties files to use when executing CLI scripts or
         * commands.
         *
         * @param propertiesFiles the property files to add
         *
         * @return this configuration
         */
        public T addPropertiesFiles(final Collection<File> propertiesFiles) {
            propertiesFiles.forEach(f -> this.propertiesFiles.add(f.toPath()));
            return builderInstance();
        }

        /**
         * Adds the commands to the CLI commands that should be executed.
         *
         * @param commands the commands to be executed
         *
         * @return this configuration
         */
        public T addCommands(final Collection<String> commands) {
            if (commands != null) {
                this.commands.addAll(commands);
            }
            return builderInstance();
        }

        /**
         * Adds the scripts to be executed.
         *
         * @param scripts the scripts to be executed
         *
         * @return this configuration
         */
        public T addScripts(final Collection<File> scripts) {
            scripts.forEach(f -> this.scripts.add(f.toPath()));
            return builderInstance();
        }

        /**
         * Sets whether or not CLI commands should fail if the command ends in
         * an error or not.
         *
         * @param failOnError {@code true} if a CLI command fails then the
         * execution should fail
         *
         * @return this configuration
         */
        public T setFailOnError(final boolean failOnError) {
            this.failOnError = failOnError;
            return builderInstance();
        }

        /**
         * Sets how the standard output stream should be handled if
         * {@link #isFork()} or {@link #isOffline()} is set to {@code true}.
         *
         * @param stdout the pattern for standard out
         *
         * @return this configuration
         */
        public T setStdout(final String stdout) {
            this.stdout = stdout;
            return builderInstance();
        }

        /**
         * Sets the timeout, in seconds, used for the management client
         * connection.
         *
         * @param timeout the timeout to use in seconds
         *
         * @return this configuration
         */
        public T setTimeout(final int timeout) {
            this.timeout = timeout;
            return builderInstance();
        }

        /**
         * If true resolve expression prior to send the operation to the server
         *
         * @param resolveExpression
         * @return this
         */
        public T setResolveExpression(boolean resolveExpression) {
            this.resolveExpression = resolveExpression;
            return builderInstance();
        }

        public BaseCommandConfiguration build() {
            return new BaseCommandConfiguration(this);
        }
    }

    public static class Builder extends AbstractBuilder<Builder> {

        @Override
        protected Builder builderInstance() {
            return this;
        }
    }

    protected BaseCommandConfiguration(AbstractBuilder<?> builder) {
        jvmOptions = builder.jvmOptions;
        commands = builder.commands;
        systemProperties = builder.systemProperties;
        propertiesFiles = builder.propertiesFiles;
        scripts = builder.scripts;
        cliArguments = builder.cliArguments;
        batch = builder.batch;
        failOnError = builder.failOnError;
        jbossHome = builder.jbossHome;
        stdout = builder.stdout;
        timeout = builder.timeout;
        append = builder.append;
        resolveExpression = builder.resolveExpression;
    }

    /**
     * Is output appended to file.
     * @return true to append to the file
     */
    public boolean isAppend() {
        return append;
    }

    /**
     * Indicates whether or not the commands should be run in a batch or not.
     *
     * @return {@code true} if the commands should be executed in a batch,
     * otherwise {@code false}
     */
    public boolean isBatch() {
        return batch;
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
     * Returns the JVM options used if {@link #isFork()} or {@link #isOffline()}
     * is set to {@code true}.
     *
     * @return the JVM options
     */
    public Collection<String> getJvmOptions() {
        return Collections.unmodifiableCollection(jvmOptions);
    }

    /**
     * Returns the CLI arguments used if {@link #isFork()} or
     * {@link #isOffline()} is set to {@code true}.
     *
     * @return the CLI arguments
     */
    public Collection<String> getCLIArguments() {
        return Collections.unmodifiableCollection(cliArguments);
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
     * The properties files to use when executing CLI scripts or commands.
     *
     * @return the paths to the properties files
     */
    public Collection<Path> getPropertiesFiles() {
        return Collections.unmodifiableCollection(propertiesFiles);
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
     * Returns the scripts to be executed.
     *
     * @return the scripts to be executed
     */
    public Collection<Path> getScripts() {
        return Collections.unmodifiableCollection(scripts);
    }


    /**
     * Indicates whether or not CLI commands should fail if the command ends in
     * an error or not.
     *
     * @return {@code true} if a CLI command fails then the execution should
     * fail
     */
    public boolean isFailOnError() {
        return failOnError;
    }

    /**
     * The pattern used to determine how standard out is handled for a new CLI
     * process.
     *
     * @return the standard out pattern
     */
    public String getStdout() {
        return stdout;
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
     * Is expression resolved.
     * @return true is expressions are resolved.
     */
    public boolean isExpressionResolved() {
        return resolveExpression;
    }

}
