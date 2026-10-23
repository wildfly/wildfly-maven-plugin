/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.cli;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.plugin.RepositorySystemTestSupport;

/**
 * Verifies that the {@code execute-commands} goal hands its command executor a repository manager that resolves with
 * the effective repositories.
 */
public class ExecuteCommandsRepositoryProxyTestCase {

    @TempDir
    private Path directory;

    /**
     * Verifies that the {@code execute-commands} goal hands the command executor a repository manager that resolves
     * with the effective repositories: the configured repository and the required repositories, each with the session
     * proxy. When the commands run in process, the local CLI resolves its artifacts through this manager.
     */
    @Test
    public void testExecuteCommandsPassesEffectiveRepositoriesToCommandExecutor() throws Exception {
        final Path artifactFile = directory.resolve("artifact.jar");
        Files.writeString(artifactFile, "resolved-artifact");
        final org.eclipse.aether.repository.Proxy proxy = new org.eclipse.aether.repository.Proxy("http", "proxy.example.org",
                3128);
        final AtomicInteger requests = new AtomicInteger();
        try (RepositorySystemTestSupport support = new RepositorySystemTestSupport(directory)) {
            final RepositorySystem system = (RepositorySystem) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { RepositorySystem.class }, (instance, method, arguments) -> {
                        if (method.getName().equals("resolveArtifact")) {
                            final ArtifactRequest request = (ArtifactRequest) arguments[1];
                            Assertions.assertEquals(3, request.getRepositories().size());
                            for (RemoteRepository repository : request.getRepositories()) {
                                Assertions.assertSame(proxy, repository.getProxy(), repository.getId());
                            }
                            requests.incrementAndGet();
                            return new ArtifactResult(request)
                                    .setArtifact(request.getArtifact().setFile(artifactFile.toFile()));
                        }
                        if (method.getName().equals("newResolutionRepositories")) {
                            try {
                                return method.invoke(support.getSystem(), arguments);
                            } catch (InvocationTargetException e) {
                                throw e.getCause();
                            }
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
            support.getSession().setProxySelector(new DefaultProxySelector().add(proxy, ""));
            final ExecuteCommandsMojo mojo = new ExecuteCommandsMojo();
            mojo.repoSystem = system;
            set(mojo, "session", support.getSession());
            set(mojo, "repositories", List.of(new RemoteRepository.Builder("central", "default", "https://repo.example.org/")
                    .build()));
            // Resolve through the manager passed to the executor, without starting a management client.
            set(mojo, "offline", true);
            set(mojo, "commandExecutor", new CommandExecutor() {
                @Override
                public void execute(final CommandConfiguration config, final MavenRepoManager manager)
                        throws MojoExecutionException {
                    final MavenArtifact artifact = new MavenArtifact().setGroupId("org.wildfly.plugin.test")
                            .setArtifactId("command-artifact").setVersion("1.0.0").setExtension("jar");
                    try {
                        manager.resolve(artifact);
                    } catch (MavenUniverseException e) {
                        throw new MojoExecutionException(e);
                    }
                }
            });
            mojo.execute();
            Assertions.assertEquals(1, requests.get());
        }
    }

    private static void set(final ExecuteCommandsMojo mojo, final String name, final Object value) throws Exception {
        final Field field = ExecuteCommandsMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(mojo, value);
    }
}
