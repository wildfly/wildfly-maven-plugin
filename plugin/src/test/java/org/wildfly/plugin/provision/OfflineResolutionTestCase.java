/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.RemoteRepositoryFilterManager;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.channel.ChannelManifestCoordinate;

/**
 * Verifies that offline provisioning, with and without channels, resolves artifacts from the local Maven repository
 * which were downloaded from a remote repository. Such artifacts are tracked with the id of the remote repository they
 * were downloaded from and are only considered available if a repository with the same id is part of the request.
 */
public class OfflineResolutionTestCase {

    private static final String REPOSITORY_ID = "test-repository";
    private static final String GROUP_ID = "org.wildfly.plugin.test";
    private static final RemoteRepositoryFilter.Result ACCEPTED = new RemoteRepositoryFilter.Result() {
        @Override
        public boolean isAccepted() {
            return true;
        }

        @Override
        public String reasoning() {
            return "accepted";
        }
    };
    private static final RemoteRepositoryFilter ACCEPT_ALL = new RemoteRepositoryFilter() {
        @Override
        public Result acceptArtifact(final RemoteRepository remoteRepository, final Artifact artifact) {
            return ACCEPTED;
        }

        @Override
        public Result acceptMetadata(final RemoteRepository remoteRepository, final Metadata metadata) {
            return ACCEPTED;
        }
    };

    @TempDir
    private Path localRepository;

    private RepositorySystem system;
    private DefaultRepositorySystemSession session;
    private List<RemoteRepository> repositories;

    @BeforeEach
    public void setUp() throws Exception {
        final DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        // Maven Resolver 1.x only enforces the availability of artifacts in the local repository when a remote
        // repository filter is active, while Maven Resolver 2 always enforces it. Use a filter accepting everything
        // to get the same behavior as Maven Resolver 2.
        locator.setServices(RemoteRepositoryFilterManager.class, s -> ACCEPT_ALL);
        system = locator.getService(RepositorySystem.class);
        session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session,
                new LocalRepository(localRepository.toFile())));
        // The repository is never accessed, all artifacts are already present in the local repository
        repositories = List.of(new RemoteRepository.Builder(REPOSITORY_ID, "default", "https://localhost.invalid/").build());

        install("test-manifest", "1.0.0", "manifest", "yaml", """
                schemaVersion: "1.0.0"
                streams:
                  - groupId: "%s"
                    artifactId: "channel-artifact"
                    version: "1.0.0"
                """.formatted(GROUP_ID));
        install("channel-artifact", "1.0.0", null, "jar", "channel-artifact");
        install("direct-artifact", "2.0.0", null, "jar", "direct-artifact");
    }

    @Test
    public void testOfflineResolutionFromChannel(@TempDir final Path home) throws Exception {
        final MavenArtifact artifact = artifact("channel-artifact", null);
        final ChannelMavenArtifactRepositoryManager repositoryManager = createRepositoryManager();
        repositoryManager.resolve(artifact);
        Assertions.assertEquals("1.0.0", artifact.getVersion());
        assertResolved(artifact, "channel-artifact");

        // The repositories are only used for resolution and are not recorded in the provisioned server
        repositoryManager.done(home);
        final String recordedChannels = Files.readString(home.resolve(".installation").resolve("installer-channels.yaml"));
        Assertions.assertTrue(recordedChannels.contains("test-manifest"), recordedChannels);
        Assertions.assertFalse(recordedChannels.contains(REPOSITORY_ID), recordedChannels);
    }

    @Test
    public void testOfflineDirectResolution() throws Exception {
        // Not part of the channel manifest, resolved directly with its original version
        final MavenArtifact artifact = artifact("direct-artifact", "2.0.0");
        createRepositoryManager().resolve(artifact);
        assertResolved(artifact, "direct-artifact");
    }

    @Test
    public void testOfflineResolveAll() throws Exception {
        final MavenArtifact channelArtifact = artifact("channel-artifact", null);
        final MavenArtifact directArtifact = artifact("direct-artifact", "2.0.0");
        createRepositoryManager().resolveAll(List.of(channelArtifact, directArtifact));
        assertResolved(channelArtifact, "channel-artifact");
        assertResolved(directArtifact, "direct-artifact");
    }

    @Test
    public void testOfflineResolutionWithoutChannel() throws Exception {
        final MavenArtifact artifact = artifact("direct-artifact", "2.0.0");
        MavenArtifactRepositoryManager.offline(system, session, repositories).resolve(artifact);
        assertResolved(artifact, "direct-artifact");
    }

    private ChannelMavenArtifactRepositoryManager createRepositoryManager() throws Exception {
        final ChannelConfiguration channel = new ChannelConfiguration();
        channel.setName("test-channel");
        channel.setManifest(new ChannelManifestCoordinate(GROUP_ID, "test-manifest", "1.0.0"));
        return new ChannelMavenArtifactRepositoryManager(List.of(channel), system, session, repositories,
                new SystemStreamLog(), true);
    }

    private static MavenArtifact artifact(final String artifactId, final String version) {
        return new MavenArtifact()
                .setGroupId(GROUP_ID)
                .setArtifactId(artifactId)
                .setVersion(version)
                .setExtension("jar");
    }

    private static void assertResolved(final MavenArtifact artifact, final String expectedContent) throws IOException {
        Assertions.assertNotNull(artifact.getPath(), () -> artifact + " was not resolved");
        Assertions.assertEquals(expectedContent, Files.readString(artifact.getPath()));
    }

    /**
     * Installs an artifact into the local repository the same way the resolver does when downloading it from the
     * remote repository, i.e. tracking the id of the remote repository it was downloaded from.
     */
    private void install(final String artifactId, final String version, final String classifier, final String extension,
            final String content) throws IOException {
        final Path dir = localRepository.resolve(GROUP_ID.replace('.', '/')).resolve(artifactId).resolve(version);
        Files.createDirectories(dir);
        final String fileName = artifactId + "-" + version + (classifier == null ? "" : "-" + classifier) + "." + extension;
        Files.writeString(dir.resolve(fileName), content);
        Files.writeString(dir.resolve("_remote.repositories"), fileName + ">" + REPOSITORY_ID + "=\n");
    }
}
