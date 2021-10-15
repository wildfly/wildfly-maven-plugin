/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.plugin.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

/**
 *
 * @author jdenise
 */
class MavenProxySelector {

    static class Builder {

        private String userName;
        private String password;
        private final String host;
        private final int port;
        private final String protocol;
        private final List<Pattern> nonProxyHosts = new ArrayList<>();

        Builder(String host, int port, String protocol) {
            this.host = host;
            this.port = port;
            this.protocol = protocol;
        }

        Builder setUserName(String userName) {
            this.userName = userName;
            return this;
        }

        Builder setPassword(String password) {
            this.password = password;
            return this;
        }

        Builder addNonProxyHosts(List<String> lst) {
            for (String h : lst) {
                h = h.replaceAll("\\*", ".*");
                nonProxyHosts.add(Pattern.compile(h));
            }
            return this;
        }

        MavenProxySelector build() {
            return new MavenProxySelector(host, port, protocol, userName,
                    password, nonProxyHosts);
        }
    }

    private final List<Pattern> nonProxyHosts;
    private final Proxy proxy;
    MavenProxySelector(String host, int port, String protocol, String userName,
            String password, List<Pattern> nonProxyHosts) {
        this.nonProxyHosts = nonProxyHosts;
        if (userName != null && password != null) {
            AuthenticationBuilder builder = new AuthenticationBuilder();
            builder.addPassword(password);
            builder.addUsername(userName);
            proxy = new Proxy(protocol, host, port, builder.build());
        } else {
            proxy = new Proxy(protocol, host, port);
        }
    }

    public Proxy getProxy(String host) {
        return proxyFor(host) ? proxy : null;
    }

    private boolean proxyFor(String host) {
        boolean match = false;
        for (Pattern p : nonProxyHosts) {
            if (p.matcher(host).matches()) {
                match = true;
                break;
            }
        }
        return !match;
    }
}
