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

import java.util.function.Supplier;

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.common.MavenModelControllerClientConfiguration;

/**
 * The configuration used to execute CLI commands.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CommandConfiguration extends BaseCommandConfiguration {

    private final Supplier<ModelControllerClient> client;
    private final Supplier<MavenModelControllerClientConfiguration> clientConfiguration;
    private final boolean fork;
    private final boolean offline;

    protected abstract static class AbstractBuilder<T extends AbstractBuilder<T>> extends BaseCommandConfiguration.AbstractBuilder<T> {

        private final Supplier<ModelControllerClient> client;
        private final Supplier<MavenModelControllerClientConfiguration> clientConfiguration;
        private boolean fork;
        private boolean offline;

        AbstractBuilder(final Supplier<ModelControllerClient> clientSupplier,
                final Supplier<MavenModelControllerClientConfiguration> clientConfigurationSupplier) {
            this.client = clientSupplier;
            this.clientConfiguration = clientConfigurationSupplier;
        }

        /**
         * Sets whether or not the commands should be executed in a new process.
         * <p>
         * Note that is {@link #isOffline()} is set to {@code true} this has no
         * effect.
         * </p>
         *
         * @param fork {@code true} if commands should be executed in a new
         * process
         *
         * @return this configuration
         */
        public T setFork(final boolean fork) {
            this.fork = fork;
            return builderInstance();
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
        public T setOffline(final boolean offline) {
            this.offline = offline;
            return builderInstance();
        }

        @Override
        public CommandConfiguration build() {
            return new CommandConfiguration(this);
        }
    }

    public static class Builder extends AbstractBuilder<Builder> {

        Builder(Supplier<ModelControllerClient> clientSupplier,
                Supplier<MavenModelControllerClientConfiguration> clientConfigurationSupplier) {
            super(clientSupplier, clientConfigurationSupplier);
        }

        @Override
        protected Builder builderInstance() {
            return this;
        }
    }

    protected CommandConfiguration(AbstractBuilder<?> builder) {
        super(builder);
        client = builder.client;
        clientConfiguration = builder.clientConfiguration;
        fork = builder.fork;
        offline = builder.offline;
    }

    /**
     * Creates a new command configuration Builder.
     *
     * @param clientSupplier the supplier used to get a management client
     * @param clientConfigurationSupplier a supplier used to get the client
     * configuration
     *
     * @return a new command configuration Builder.
     */
    public static Builder of(final Supplier<ModelControllerClient> clientSupplier,
            final Supplier<MavenModelControllerClientConfiguration> clientConfigurationSupplier) {
        return new Builder(clientSupplier, clientConfigurationSupplier);
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
     * Indicates whether or not CLI commands should be executed in a new
     * process.
     *
     * @return {@code true} to execute CLI commands in a new process
     */
    public boolean isFork() {
        return fork;
    }

    /**
     * Indicates whether or not this should be an offline process.
     *
     * @return {@code true} if this should be an offline process, otherwise
     * {@code false}
     */
    public boolean isOffline() {
        return offline;
    }
}
