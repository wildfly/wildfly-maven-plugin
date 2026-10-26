/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.plugin.RepositorySystemTestSupport;
import org.wildfly.plugin.core.MavenRepositoriesEnricher;

/**
 * Checks the repositories that the channel manager passes to Aether on its bulk, direct-fallback and version-range
 * paths.
 */
public class ChannelRepositoryProxyTestCase {

    @TempDir
    private Path directory;

    private RepositorySystemTestSupport support;

    private ChannelMavenArtifactRepositoryManager manager;
    private List<RemoteRepository> effectiveRepositories;
    private final AtomicInteger directRequests = new AtomicInteger();
    private final AtomicInteger bulkRequests = new AtomicInteger();
    private final AtomicInteger rangeRequests = new AtomicInteger();
    private final org.eclipse.aether.repository.Proxy proxy = new org.eclipse.aether.repository.Proxy("http",
            "proxy.example.org",
            3128);

    @AfterEach
    public void tearDown() {
        if (support != null) {
            support.close();
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        final Path manifest = directory.resolve("channel.yaml");
        Files.writeString(manifest, """
                schemaVersion: "1.0.0"
                streams:
                  - groupId: "org.wildfly.plugin.test"
                    artifactId: "channel-artifact"
                    version: "1.0.0"
                """);
        final Path artifactFile = directory.resolve("artifact.jar");
        Files.writeString(artifactFile, "resolved-artifact");
        support = new RepositorySystemTestSupport(directory);
        final RepositorySystem delegate = support.getSystem();
        final RepositorySystem system = (RepositorySystem) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { RepositorySystem.class }, (instance, method, arguments) -> {
                    if (method.getName().equals("resolveArtifact")) {
                        directRequests.incrementAndGet();
                        return resolve((ArtifactRequest) arguments[1], artifactFile);
                    }
                    if (method.getName().equals("resolveArtifacts")) {
                        bulkRequests.incrementAndGet();
                        final List<ArtifactResult> results = new ArrayList<>();
                        for (Object request : (Collection<?>) arguments[1]) {
                            results.add(resolve((ArtifactRequest) request, artifactFile));
                        }
                        return results;
                    }
                    if (method.getName().equals("resolveVersionRange")) {
                        final VersionRangeRequest request = (VersionRangeRequest) arguments[1];
                        assertRepositories(request.getRepositories());
                        rangeRequests.incrementAndGet();
                        return new VersionRangeResult(request).addVersion(new GenericVersionScheme().parseVersion("2.0.0"));
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        final DefaultRepositorySystemSession session = support.getSession();
        session.setProxySelector(new DefaultProxySelector().add(proxy, ""));
        effectiveRepositories = MavenRepositoriesEnricher.enrich(delegate, session, List.of(
                new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build()));
        final ChannelConfiguration channel = new ChannelConfiguration();
        channel.set(manifest.toUri().toString());
        manager = new ChannelMavenArtifactRepositoryManager(List.of(channel), system, session, effectiveRepositories,
                new SystemStreamLog(), false);
    }

    /**
     * Verifies that the bulk request for a channel artifact and the direct fallback for an artifact the channel does
     * not list both reach Aether with the repositories given to the manager.
     */
    @Test
    public void testBulkAndDirectFallbackResolution() throws Exception {
        final MavenArtifact channelArtifact = artifact("channel-artifact", null);
        final MavenArtifact directArtifact = artifact("direct-artifact", "2.0.0");
        manager.resolveAll(List.of(channelArtifact, directArtifact));
        Assertions.assertNotNull(channelArtifact.getPath());
        Assertions.assertNotNull(directArtifact.getPath());
        Assertions.assertEquals(1, bulkRequests.get());
        Assertions.assertEquals(1, directRequests.get());
    }

    /**
     * Verifies that the version-range fallback for an artifact the channel does not list queries Aether with the
     * repositories given to the manager.
     */
    @Test
    public void testVersionRangeFallback() throws Exception {
        final MavenArtifact artifact = artifact("direct-artifact", null).setVersionRange("[1.0,3.0)");
        Assertions.assertEquals("2.0.0", manager.getLatestVersion(artifact));
        Assertions.assertEquals(1, rangeRequests.get());
    }

    private ArtifactResult resolve(final ArtifactRequest request, final Path artifactFile) {
        assertRepositories(request.getRepositories());
        return new ArtifactResult(request).setArtifact(request.getArtifact().setFile(artifactFile.toFile()));
    }

    private void assertRepositories(final List<RemoteRepository> repositories) {
        // The channel model keeps only repository id and URL, so on the bulk and direct paths the manager has to map them
        // back to the effective repositories; the range path passes the list itself.
        Assertions.assertEquals(effectiveRepositories, repositories);
    }

    private static MavenArtifact artifact(final String artifactId, final String version) {
        return new MavenArtifact().setGroupId("org.wildfly.plugin.test").setArtifactId(artifactId)
                .setVersion(version).setExtension("jar");
    }
}
