/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;

/**
 * Adds required repositories and prepares the repository list for resolution using the session settings.
 *
 * @author jdenise
 */
public class MavenRepositoriesEnricher {

    private static class RequiredRepository {

        private final String id;
        private final String type;
        private final String url;
        private final RepositoryPolicy releasePolicy;
        private final RepositoryPolicy snapshotPolicy;

        RequiredRepository(String id, String type, String url, RepositoryPolicy releasePolicy,
                RepositoryPolicy snapshotPolicy) {
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
        REQUIRED_REPOSITORIES.put(GA_REPO_URL, new RequiredRepository("jboss-ga-repository", DEFAULT_REPOSITORY_TYPE,
                GA_REPO_URL,
                new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_WARN),
                new RepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_FAIL)));
        REQUIRED_REPOSITORIES.put(NEXUS_REPO_URL, new RequiredRepository("jboss-public-repository", DEFAULT_REPOSITORY_TYPE,
                NEXUS_REPO_URL,
                new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_DAILY, RepositoryPolicy.CHECKSUM_POLICY_WARN),
                new RepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_FAIL)));
    }

    /**
     * Adds the required repositories and applies session mirrors, proxies and authentication.
     * Existing mirror decisions are preserved.
     *
     * @param repoSystem   the repository system
     * @param session      the repository session
     * @param repositories the configured repositories; never modified
     * @return the repositories to use for resolution
     */
    public static List<RemoteRepository> enrich(RepositorySystem repoSystem, RepositorySystemSession session,
            List<RemoteRepository> repositories) {
        List<RemoteRepository> result = new ArrayList<>(repositories);
        Set<String> configuredUrls = getUrlsIncludingMirroredRepositories(repositories);
        for (Entry<String, RequiredRepository> entry : REQUIRED_REPOSITORIES.entrySet()) {
            if (!configuredUrls.contains(entry.getKey())) {
                RequiredRepository repo = entry.getValue();
                result.add(new RemoteRepository.Builder(repo.id, repo.type, repo.url)
                        .setReleasePolicy(repo.releasePolicy)
                        .setSnapshotPolicy(repo.snapshotPolicy)
                        .build());
            }
        }
        // newResolutionRepositories applies session settings to repository prototypes. Maven may already have
        // selected mirrors, so select a mirror only for raw repositories to avoid redirecting a prepared mirror.
        DefaultRepositorySystemSession resolutionSession = new DefaultRepositorySystemSession(session);
        resolutionSession.setMirrorSelector(repository -> repository.getMirroredRepositories().isEmpty()
                ? session.getMirrorSelector().getMirror(repository)
                : null);
        return repoSystem.newResolutionRepositories(resolutionSession, result);
    }

    private static Set<String> getUrlsIncludingMirroredRepositories(List<RemoteRepository> repositories) {
        Set<String> set = new HashSet<>();
        for (RemoteRepository repo : repositories) {
            set.add(repo.getUrl());
            set.addAll(getUrlsIncludingMirroredRepositories(repo.getMirroredRepositories()));
        }
        return set;
    }
}
