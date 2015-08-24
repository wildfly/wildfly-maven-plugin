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

package org.wildfly.plugin.common;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.controller.client.ModelControllerClientConfiguration;

/**
 * Extends the {@link ModelControllerClientConfiguration} to return some additional information needed for needed for
 * launching commands in a CLI process.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class ManagementClientConfiguration implements ModelControllerClientConfiguration {
    private final ModelControllerClientConfiguration delegate;

    protected ManagementClientConfiguration(final ModelControllerClientConfiguration delegate) {
        this.delegate = delegate;
    }

    /**
     * Returns the configured username or {@code null} if one was not configured.
     *
     * @return the username or {@code null}
     */
    public abstract String getUsername();

    /**
     * Returns the password or {@code null} if one was not configured.
     *
     * @return the password or {@code null}
     */
    public abstract String getPassword();

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
        return delegate.getCallbackHandler();
    }

    @Override
    public Map<String, String> getSaslOptions() {
        return delegate.getSaslOptions();
    }

    @Override
    public SSLContext getSSLContext() {
        return delegate.getSSLContext();
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
    public void close() throws IOException {
        delegate.close();
    }
}
