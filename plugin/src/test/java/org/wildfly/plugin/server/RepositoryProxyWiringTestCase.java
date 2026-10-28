/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.server;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.wildfly.plugin.RepositorySystemTestSupport;
import org.wildfly.plugin.dev.DevMojo;
import org.wildfly.plugin.provision.ChannelConfiguration;
import org.wildfly.plugin.provision.ProvisionServerMojo;

/**
 * Verifies that the mojos which create their own repository manager for provisioning or starting a server pass it the
 * effective repositories, with the session proxy applied.
 */
public class RepositoryProxyWiringTestCase {

    @TempDir
    private Path directory;

    private RepositorySystemTestSupport support;
    private RepositorySystem system;
    private DefaultRepositorySystemSession session;
    private final org.eclipse.aether.repository.Proxy proxy = new org.eclipse.aether.repository.Proxy("http",
            "proxy.example.org", 3128);
    private final List<RemoteRepository> repositories = List.of(
            new RemoteRepository.Builder("central", "default", "https://repo.example.org/").build());
    private final List<ArtifactRequest> requests = new ArrayList<>();
    private Path artifactFile;
    private boolean expectedOffline;

    @BeforeEach
    public void setUp() throws Exception {
        artifactFile = directory.resolve("artifact.jar");
        Files.writeString(artifactFile, "resolved-artifact");
        support = new RepositorySystemTestSupport(directory);
        final RepositorySystem delegate = support.getSystem();
        session = support.getSession();
        session.setProxySelector(new DefaultProxySelector().add(proxy, ""));
        system = (RepositorySystem) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { RepositorySystem.class }, (instance, method, arguments) -> {
                    if (method.getName().equals("resolveArtifact")) {
                        Assertions.assertEquals(expectedOffline, ((RepositorySystemSession) arguments[0]).isOffline());
                        final ArtifactRequest request = (ArtifactRequest) arguments[1];
                        requests.add(request);
                        return new ArtifactResult(request).setArtifact(request.getArtifact().setFile(artifactFile.toFile()));
                    }
                    if (method.getName().equals("newResolutionRepositories")) {
                        try {
                            return method.invoke(delegate, arguments);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    }
                    if (method.getName().equals("newLocalRepositoryManager")) {
                        try {
                            return method.invoke(delegate, arguments);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @AfterEach
    public void tearDown() {
        if (support != null) {
            support.close();
        }
    }

    /**
     * Verifies that the {@code provision} goal resolves with the effective repositories, each with the session proxy,
     * and with an offline session exactly when offline provisioning is set. Every combination of channels and offline
     * provisioning is covered, since each one creates the repository manager differently. The mojo stops after the
     * resolution, before a server is provisioned.
     */
    @ParameterizedTest(name = "channels={0}, offline={1}")
    @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
    public void testProvisionUsesEffectiveRepositories(final boolean useChannels, final boolean offline) throws Exception {
        expectedOffline = offline;
        final MavenArtifact artifact = artifact();
        if (useChannels) {
            artifact.setVersion(null);
        }
        final ResolutionOnlyProvisionMojo mojo = new ResolutionOnlyProvisionMojo(artifact);
        final MavenProject project = new MavenProject();
        final Build build = new Build();
        build.setDirectory(directory.resolve("target").toString());
        project.getModel().setBuild(build);
        set(mojo, "project", project);
        set(mojo, "repoSystem", system);
        set(mojo, "repoSession", session);
        set(mojo, "repositories", repositories);
        set(mojo, "channels", channels(useChannels));
        set(mojo, "offlineProvisioning", offline);
        set(mojo, "provisioningDir", "server");
        set(mojo, "provisioningFile", directory.resolve("provisioning.xml").toFile());
        // No MavenSession is needed when the local repository property is already set.
        final String previousRepository = System.setProperty("maven.repo.local", directory.toString());
        try {
            Assertions.assertSame(mojo.finished, Assertions.assertThrows(MojoExecutionException.class, mojo::execute));
        } finally {
            if (previousRepository == null) {
                System.clearProperty("maven.repo.local");
            } else {
                System.setProperty("maven.repo.local", previousRepository);
            }
        }
        assertResolution(artifact);
    }

    /**
     * Verifies that the repository manager of the {@code start} goal resolves with the effective repositories, each
     * with the session proxy.
     */
    @Test
    public void testStartUsesEffectiveRepositories() throws Exception {
        final StartMojo mojo = new StartMojo();
        mojo.repoSystem = system;
        mojo.session = session;
        mojo.repositories = repositories;
        mojo.init();
        final MavenArtifact artifact = artifact();
        mojo.mavenRepoManager.resolve(artifact);
        assertResolution(artifact);
    }

    /**
     * Verifies that the {@code dev} goal, which overrides how the repository manager is created, resolves with the
     * effective repositories, each with the session proxy, and with an offline session exactly when offline
     * provisioning is set, for every combination of channels and offline provisioning.
     */
    @ParameterizedTest(name = "channels={0}, offline={1}")
    @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
    public void testDevUsesEffectiveRepositories(final boolean useChannels, final boolean offline) throws Exception {
        expectedOffline = offline;
        final AbstractStartMojo mojo = new DevMojo();
        mojo.repoSystem = system;
        mojo.session = session;
        mojo.repositories = repositories;
        set(mojo, "channels", channels(useChannels));
        set(mojo, "offlineProvisioning", offline);
        mojo.init();
        final MavenArtifact artifact = artifact();
        if (useChannels) {
            artifact.setVersion(null);
        }
        mojo.mavenRepoManager.resolve(artifact);
        assertResolution(artifact);
    }

    private void assertResolution(final MavenArtifact artifact) {
        Assertions.assertEquals(artifactFile, artifact.getPath());
        Assertions.assertEquals("1.0.0", artifact.getVersion());
        Assertions.assertEquals(1, requests.size());
        Assertions.assertEquals(3, requests.get(0).getRepositories().size());
        for (RemoteRepository repository : requests.get(0).getRepositories()) {
            Assertions.assertSame(proxy, repository.getProxy(), repository.getId());
        }
    }

    private List<ChannelConfiguration> channels(final boolean enabled) throws Exception {
        if (!enabled) {
            return List.of();
        }
        final Path manifest = directory.resolve("channel.yaml");
        Files.writeString(manifest, """
                schemaVersion: "1.0.0"
                streams:
                  - groupId: "org.wildfly.plugin.test"
                    artifactId: "server-artifact"
                    version: "1.0.0"
                """);
        final ChannelConfiguration channel = new ChannelConfiguration();
        channel.set(manifest.toUri().toString());
        return List.of(channel);
    }

    private static MavenArtifact artifact() {
        return new MavenArtifact().setGroupId("org.wildfly.plugin.test").setArtifactId("server-artifact")
                .setVersion("1.0.0").setExtension("jar");
    }

    private static void set(final Object mojo, final String name, final Object value) throws Exception {
        for (Class<?> type = mojo.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(mojo, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // Continue with the inherited Maven parameters.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static class ResolutionOnlyProvisionMojo extends ProvisionServerMojo {
        private final MavenArtifact artifact;
        private final MojoExecutionException finished = new MojoExecutionException("Resolution checked; no server provisioned");

        ResolutionOnlyProvisionMojo(final MavenArtifact artifact) {
            this.artifact = artifact;
        }

        @Override
        protected GalleonProvisioningConfig buildGalleonConfig(final GalleonBuilder builder) throws MojoExecutionException {
            try {
                artifactResolver.resolve(artifact);
            } catch (MavenUniverseException e) {
                throw new MojoExecutionException(e);
            }
            throw finished;
        }
    }
}
