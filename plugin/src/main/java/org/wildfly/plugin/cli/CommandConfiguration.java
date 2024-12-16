/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.cli;

import java.util.function.Supplier;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;

/**
 * The configuration used to execute CLI commands.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CommandConfiguration extends BaseCommandConfiguration {

    private final Supplier<ModelControllerClient> client;
    private final Supplier<ModelControllerClientConfiguration> clientConfiguration;
    private final boolean fork;
    private final boolean offline;

    private final boolean autoReload;

    protected abstract static class AbstractBuilder<T extends AbstractBuilder<T>>
            extends BaseCommandConfiguration.AbstractBuilder<T> {

        private final Supplier<ModelControllerClient> client;
        private final Supplier<ModelControllerClientConfiguration> clientConfiguration;
        private boolean fork;
        private boolean offline;
        private boolean autoReload;

        AbstractBuilder(final Supplier<ModelControllerClient> clientSupplier,
                final Supplier<ModelControllerClientConfiguration> clientConfigurationSupplier) {
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
         *                 process
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

        /**
         * Set to {@code true} if a reload should execute after the commands are complete. The reload will only execute
         * if {@link #isOffline()} is {@code false} and the server state is in {@code reload-required}.
         *
         * @param autoReload {@code true} to enable auto-reload
         *
         * @return this configuration
         */
        public T setAutoReload(final boolean autoReload) {
            this.autoReload = autoReload;
            return builderInstance();
        }

        @Override
        public CommandConfiguration build() {
            return new CommandConfiguration(this);
        }
    }

    public static class Builder extends AbstractBuilder<Builder> {

        Builder(Supplier<ModelControllerClient> clientSupplier,
                Supplier<ModelControllerClientConfiguration> clientConfigurationSupplier) {
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
        autoReload = builder.autoReload;
    }

    /**
     * Creates a new command configuration Builder.
     *
     * @param clientSupplier              the supplier used to get a management client
     * @param clientConfigurationSupplier a supplier used to get the client
     *                                        configuration
     *
     * @return a new command configuration Builder.
     */
    public static Builder of(final Supplier<ModelControllerClient> clientSupplier,
            final Supplier<ModelControllerClientConfiguration> clientConfigurationSupplier) {
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
    public ModelControllerClientConfiguration getClientConfiguration() {
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
     *             {@code false}
     */
    public boolean isOffline() {
        return offline;
    }

    /**
     * Indicants if the server should be reloaded if the server is in the {@code reload-required} state and
     * {@link #isOffline()} is {@code false}.
     *
     * @return {@code true} if a reload should execute if it's required, otherwise {@code false}
     */
    public boolean isAutoReload() {
        return !offline && autoReload;
    }
}
