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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;

/**
 * Add required repositories if not present.
 * @author jdenise
 */
public class MavenRepositoriesEnricher {

    private static class RequiredRepository {

        private final String id;
        private final String type;
        private final String url;
        private final RepositoryPolicy releasePolicy;
        private final RepositoryPolicy snapshotPolicy;

        RequiredRepository(String id, String type, String url, RepositoryPolicy releasePolicy, RepositoryPolicy snapshotPolicy) {
            this.id = id;
            this.type = type;
            this.url = url;
            this.releasePolicy = releasePolicy;
            this.snapshotPolicy = snapshotPolicy;
        }
    }
    public static final String GA_REPO_URL = "https://maven.repository.redhat.com/ga/";
    public static final String NEXUS_REPO_URL = "https://repository.jboss.org/nexus/content/groups/public/";
    private static final String DEFAULT_REPOSITORY_TYPE = "default";

    private static final Map<String, RequiredRepository> REQUIRED_REPOSITORIES = new HashMap<>();

    static {
        REQUIRED_REPOSITORIES.put(GA_REPO_URL, new RequiredRepository("jboss-ga-repository", DEFAULT_REPOSITORY_TYPE, GA_REPO_URL,
                new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_WARN),
                new RepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_FAIL)));
        REQUIRED_REPOSITORIES.put(NEXUS_REPO_URL, new RequiredRepository("jboss-public-repository", DEFAULT_REPOSITORY_TYPE, NEXUS_REPO_URL,
                new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_WARN),
                new RepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_FAIL)));
    }

    public static void enrich(MavenSession session, MavenProject project, List<RemoteRepository> repositories) throws MojoExecutionException {
        Set<String> configuredUrls = getUrls(repositories);
        Settings settings = session.getSettings();
        Proxy proxy = settings.getActiveProxy();
        MavenProxySelector proxySelector = null;
        if (proxy != null) {
            MavenProxySelector.Builder selectorBuilder = new MavenProxySelector.Builder(proxy.getHost(), proxy.getPort(), proxy.getProtocol());
            selectorBuilder.setPassword(proxy.getPassword());
            selectorBuilder.setUserName(proxy.getUsername());
            if (proxy.getNonProxyHosts() != null) {
                String[] hosts = proxy.getNonProxyHosts().split("\\|");
                selectorBuilder.addNonProxyHosts(Arrays.asList(hosts));
            }
            proxySelector = selectorBuilder.build();
        }
        for (Entry<String, RequiredRepository> entry : REQUIRED_REPOSITORIES.entrySet()) {
            if (!configuredUrls.contains(entry.getKey())) {
                RequiredRepository repo = entry.getValue();
                RemoteRepository.Builder builder = new RemoteRepository.Builder(repo.id, repo.type, repo.url);
                builder.setReleasePolicy(repo.releasePolicy);
                builder.setSnapshotPolicy(repo.snapshotPolicy);
                if (proxySelector != null) {
                    try {
                        org.eclipse.aether.repository.Proxy aetherProxy = proxySelector.getProxy(new URL(repo.url).getHost());
                        if (aetherProxy != null) {
                            builder.setProxy(aetherProxy);
                        }
                    } catch (MalformedURLException ex) {
                        throw new MojoExecutionException("Invalid repo url " + repo.url, ex);
                    }
                }
                repositories.add(builder.build());
            }
        }
    }

    private static Set<String> getUrls(List<RemoteRepository> repositories) {
        Set<String> set = new HashSet<>();
        for (RemoteRepository repo : repositories) {
            set.add(repo.getUrl());
        }
        return set;
    }
}
