/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.core;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.DefaultAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.plugin.RepositorySystemTestSupport;

/**
 * Verifies that {@link MavenRepositoriesEnricher} adds the missing required repositories and applies the session's
 * mirror, proxy and authentication selection without redirecting mirrors Maven has already selected.
 */
public class MavenRepositoriesEnricherTestCase {

    private RepositorySystem system;
    private RepositorySystemTestSupport support;
    private DefaultRepositorySystemSession session;
    private final Proxy proxy = new Proxy("http", "proxy.example.org", 3128);

    @TempDir
    private Path localRepository;

    @BeforeEach
    public void setUp() throws Exception {
        support = new RepositorySystemTestSupport(localRepository);
        system = support.getSystem();
        session = support.getSession();
    }

    @AfterEach
    public void tearDown() {
        if (support != null) {
            support.close();
        }
    }

    /**
     * Verifies that a raw repository, as an embedded caller may pass it, is replaced by the session's mirror with that
     * mirror's authentication, and that all returned repositories carry the session proxy. Aether uses the repositories
     * of a request as supplied, so settings not applied here would be bypassed.
     */
    @Test
    public void testSessionMirrorProxyAndAuthenticationAreApplied() {
        final List<RemoteRepository> repositories = List.of(repository("central", "https://repo.maven.apache.org/maven2/"));
        final Authentication authentication = new AuthenticationBuilder().addUsername("test-user").build();
        session.setProxySelector(new DefaultProxySelector().add(proxy, ""));
        session.setMirrorSelector(new DefaultMirrorSelector().add("central-mirror", "https://mirror.example.org/",
                "default", false, false, "central", "*"));
        session.setAuthenticationSelector(new DefaultAuthenticationSelector().add("central-mirror", authentication));

        final List<RemoteRepository> enriched = MavenRepositoriesEnricher.enrich(system, session, repositories);
        Assertions.assertEquals(3, enriched.size());
        final RemoteRepository mirror = enriched.get(0);
        Assertions.assertEquals("central-mirror", mirror.getId());
        Assertions.assertEquals("https://mirror.example.org/", mirror.getUrl());
        Assertions.assertSame(authentication, mirror.getAuthentication());
        for (RemoteRepository repository : enriched) {
            Assertions.assertSame(proxy, repository.getProxy(), repository.getId());
        }
    }

    /**
     * Verifies that a mirror Maven has already selected keeps its endpoint and authentication although the session's
     * {@code external:*} mirror also matches it, and that the caller's session keeps its mirror selector. The added
     * required repositories are still resolved through the {@code external:*} mirror. All returned repositories carry
     * the session proxy.
     */
    @Test
    public void testPreparedCentralMirrorIsNotRedirectedToCorporateMirror() {
        session.setProxySelector(new DefaultProxySelector().add(proxy, ""));
        session.setMirrorSelector(new DefaultMirrorSelector()
                .add("central-exception", "https://central-mirror.example.org/", "default", false, false, "central", "*")
                .add("corporate", "https://nexus.example.org/repository/group/", "default", false, false, "external:*", "*"));
        final Authentication centralAuthentication = new AuthenticationBuilder().addUsername("central-user").build();
        final Authentication corporateAuthentication = new AuthenticationBuilder().addUsername("corporate-user").build();
        session.setAuthenticationSelector(new DefaultAuthenticationSelector()
                .add("central-exception", centralAuthentication).add("corporate", corporateAuthentication));
        // Maven prepares project.remoteProjectRepositories before invoking the plugin.
        final List<RemoteRepository> prepared = system.newResolutionRepositories(session,
                List.of(repository("central", "https://repo.maven.apache.org/maven2/")));
        Assertions.assertEquals("central-exception", prepared.get(0).getId());
        final MirrorSelector selector = session.getMirrorSelector();

        final List<RemoteRepository> enriched = MavenRepositoriesEnricher.enrich(system, session, List.copyOf(prepared));

        Assertions.assertSame(selector, session.getMirrorSelector());
        Assertions.assertEquals("central-exception", enriched.get(0).getId());
        Assertions.assertEquals("https://central-mirror.example.org/", enriched.get(0).getUrl());
        Assertions.assertSame(centralAuthentication, enriched.get(0).getAuthentication());
        Assertions.assertSame(proxy, enriched.get(0).getProxy());
        Assertions.assertEquals(2, enriched.size());
        Assertions.assertEquals("corporate", enriched.get(1).getId());
        Assertions.assertSame(corporateAuthentication, enriched.get(1).getAuthentication());
        Assertions.assertSame(proxy, enriched.get(1).getProxy());
        Assertions.assertEquals(2, enriched.get(1).getMirroredRepositories().size());
    }

    /**
     * Verifies that the session's non-proxy hosts are honoured: a repository on such a host gets no proxy, while the
     * required repositories get the proxy and are enabled for releases only.
     */
    @Test
    public void testNonProxyHostsAndRequiredRepositoryPolicies() {
        session.setProxySelector(new DefaultProxySelector().add(proxy, "*.example.org"));
        final List<RemoteRepository> enriched = MavenRepositoriesEnricher.enrich(system, session,
                List.of(repository("internal", "https://repository.example.org/")));
        Assertions.assertEquals(3, enriched.size());
        Assertions.assertNull(enriched.get(0).getProxy());
        for (RemoteRepository repository : enriched.subList(1, enriched.size())) {
            Assertions.assertSame(proxy, repository.getProxy());
            Assertions.assertTrue(repository.getPolicy(false).isEnabled());
            Assertions.assertFalse(repository.getPolicy(true).isEnabled());
        }
    }

    /**
     * Verifies that the required repositories go through the session's mirror selection: with a {@code mirrorOf *}
     * mirror they are resolved through that mirror instead of being added as direct entries that bypass it.
     */
    @Test
    public void testRequiredRepositoriesShareSessionMirror() {
        session.setProxySelector(new DefaultProxySelector().add(proxy, ""));
        session.setMirrorSelector(new DefaultMirrorSelector().add("all-mirror", "https://mirror.example.org/",
                "default", false, false, "*", "*"));
        final List<RemoteRepository> enriched = MavenRepositoriesEnricher.enrich(system, session,
                List.of(repository("central", "https://repo.maven.apache.org/maven2/")));
        Assertions.assertEquals(1, enriched.size());
        Assertions.assertSame(proxy, enriched.get(0).getProxy());
        Assertions.assertEquals(3, enriched.get(0).getMirroredRepositories().size());
    }

    /**
     * Verifies that a required repository is not added when a mirror Maven has already selected lists its URL among the
     * repositories it mirrors, because a direct entry would bypass that mirror.
     */
    @Test
    public void testRequiredRepositoryMirroredByMavenIsNotAddedAgain() {
        session.setMirrorSelector(new DefaultMirrorSelector().add("redhat-mirror", "https://redhat-mirror.example.org/",
                "default", false, false, "redhat-ga", "*"));
        // Maven prepares project.remoteProjectRepositories before invoking the plugin.
        final List<RemoteRepository> prepared = system.newResolutionRepositories(session,
                List.of(repository("redhat-ga", MavenRepositoriesEnricher.GA_REPO_URL)));

        final List<RemoteRepository> enriched = MavenRepositoriesEnricher.enrich(system, session, List.copyOf(prepared));

        Assertions.assertEquals(List.of("redhat-mirror", "jboss-public-repository"),
                enriched.stream().map(RemoteRepository::getId).toList());
    }

    /**
     * Verifies that a required repository already configured under a different id is not added a second time, and
     * that the configured repository keeps its own release policy.
     */
    @Test
    public void testConfiguredRequiredRepositoryIsNotAddedAgain() {
        final RepositoryPolicy releases = new RepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_NEVER,
                RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        final RemoteRepository required = new RemoteRepository.Builder("configured-redhat", "default",
                MavenRepositoriesEnricher.GA_REPO_URL).setReleasePolicy(releases).build();
        final List<RemoteRepository> enriched = MavenRepositoriesEnricher.enrich(system, session, List.of(required));
        Assertions.assertEquals(2, enriched.size());
        Assertions.assertEquals("configured-redhat", enriched.get(0).getId());
        Assertions.assertEquals(releases, enriched.get(0).getPolicy(false));
    }

    private static RemoteRepository repository(final String id, final String url) {
        return new RemoteRepository.Builder(id, "default", url).build();
    }
}
