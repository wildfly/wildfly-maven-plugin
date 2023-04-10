/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.common;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.wildfly.security.SecurityFactory;

/**
 * A configuration used to connect a {@link org.jboss.as.controller.client.ModelControllerClient} or used to connect a
 * CLI {@link org.jboss.as.cli.CommandContext}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class MavenModelControllerClientConfiguration implements ModelControllerClientConfiguration {

    private final ModelControllerClientConfiguration delegate;
    private final String username;
    private final String password;
    private final CallbackHandler callbackHandler;

    MavenModelControllerClientConfiguration(final ModelControllerClientConfiguration delegate, final String username,
            final String password) {
        this.delegate = delegate;
        this.username = username;
        this.password = password;
        if (delegate.getAuthenticationConfigUri() == null) {
            callbackHandler = new ClientCallbackHandler(username, password);
        } else {
            callbackHandler = delegate.getCallbackHandler();
        }
    }

    @Override
    public String getHost() {
        return delegate.getHost();
    }

    @Override
    public int getPort() {
        return delegate.getPort();
    }

    @Override
    public String getProtocol() {
        return delegate.getProtocol();
    }

    @Override
    public int getConnectionTimeout() {
        return delegate.getConnectionTimeout();
    }

    @Override
    public CallbackHandler getCallbackHandler() {
        return callbackHandler;
    }

    @Override
    public Map<String, String> getSaslOptions() {
        return delegate.getSaslOptions();
    }

    @Override
    @SuppressWarnings("deprecation")
    public SSLContext getSSLContext() {
        return delegate.getSSLContext();
    }

    @Override
    public SecurityFactory<SSLContext> getSslContextFactory() {
        return delegate.getSslContextFactory();
    }

    @Override
    public ExecutorService getExecutor() {
        return delegate.getExecutor();
    }

    @Override
    public String getClientBindAddress() {
        return delegate.getClientBindAddress();
    }

    @Override
    public URI getAuthenticationConfigUri() {
        return delegate.getAuthenticationConfigUri();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    /**
     * The username provided or {@code null} if one was not provided.
     *
     * @return the username or {@code null}
     */
    public String getUsername() {
        return username;
    }

    /**
     * The password providedor {@code null} if one was not provided.
     *
     * @return the password or {@code null}
     */
    public char[] getPassword() {
        if (password == null) {
            return null;
        }
        return password.toCharArray();
    }

    /**
     * Formats a connection string for CLI to use as it's controller connection.
     *
     * @return the controller string to connect CLI
     */
    public String getController() {
        final StringBuilder controller = new StringBuilder();
        if (getProtocol() != null) {
            controller.append(getProtocol()).append("://");
        }
        if (getHost() != null) {
            controller.append(getHost());
        } else {
            controller.append("localhost");
        }
        if (getPort() > 0) {
            controller.append(':').append(getPort());
        }
        return controller.toString();
    }
}
