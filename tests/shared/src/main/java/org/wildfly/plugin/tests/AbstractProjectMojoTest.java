/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.tests;

import java.nio.file.Path;

import javax.inject.Inject;

import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

/**
 * Abstract base class for tests that require Maven project and session initialization.
 * Provides common setup for MavenProject and MavenSession injection and configuration.
 *
 * <p>
 * Tests extending this class will automatically have:
 * <ul>
 * <li>MavenSession injected and configured with test settings</li>
 * <li>MavenProject injected and initialized with test properties</li>
 * <li>MavenSession configured with remote repositories</li>
 * </ul>
 *
 * <p>
 * Subclasses can override {@link #configureMaven()} to add test-specific initialization,
 * but must call {@code super.configureMaven()} first:
 *
 * <pre>
 * &#64;Override
 * &#64;BeforeEach
 * public void configureMaven() {
 *     super.configureMaven();
 *     // Additional test-specific setup
 * }
 * </pre>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractProjectMojoTest {
    private static final Path TEST_PROJECT_LOCAL_REPO_PATH;

    static {
        final String localRepo = System.getProperty("maven.repo.local");
        final Path localRepoPath;
        if (localRepo == null) {
            localRepoPath = Path.of(System.getProperty("user.home"), ".m2", "repository");
        } else {
            localRepoPath = Path.of(localRepo);
        }
        TEST_PROJECT_LOCAL_REPO_PATH = localRepoPath.toAbsolutePath();
    }

    @Inject
    protected MavenSession session;

    @Inject
    protected MavenProject project;

    @BeforeEach
    public void configureMaven() {
        final Settings settings = new Settings();
        settings.setLocalRepository(TEST_PROJECT_LOCAL_REPO_PATH.toString());
        Mockito.when(session.getSettings()).thenReturn(settings);
        initializeProject(project);
        initializeSession(session);
    }

    /**
     * Initializes a project with required properties, the correct groupId and artifactId.
     *
     * @param project the project to configure
     */
    private static void initializeProject(final MavenProject project) {
        final var serverVersion = System.getProperty("version.org.wildfly");
        final var projectProperties = project.getProperties();
        if (serverVersion == null || serverVersion.isBlank()) {
            projectProperties.setProperty("wildfly.test.universe.location",
                    "wildfly@maven(org.jboss.universe:community-universe)");
        } else {
            projectProperties.setProperty("wildfly.test.universe.location",
                    "wildfly@maven(org.jboss.universe:community-universe)#%s".formatted(serverVersion));
        }
        if (TestEnvironment.WILDFLY_HOME != null) {
            projectProperties.setProperty("jboss.home", TestEnvironment.WILDFLY_HOME.toString());
        }
        projectProperties.setProperty("grpc.test.version", System.getProperty("grpc.test.version", ""));
        projectProperties.setProperty("test.project.basedir",
                Path.of(TestEnvironment.TEST_PROJECT_PATH).toAbsolutePath().toString());
        projectProperties.setProperty("test.project.baseuri",
                Path.of(TestEnvironment.TEST_PROJECT_PATH).toAbsolutePath().toUri().toString());
        project.setGroupId("testing");
        project.setArtifactId("testing");
    }

    @SuppressWarnings("deprecation")
    private static void initializeSession(final MavenSession session) {
        final var request = session.getRequest();
        request.setLocalRepositoryPath(TEST_PROJECT_LOCAL_REPO_PATH.toString());
        final ArtifactRepositoryPolicy snapshot = new ArtifactRepositoryPolicy(false,
                ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE);
        final ArtifactRepositoryPolicy release = new ArtifactRepositoryPolicy(true,
                ArtifactRepositoryPolicy.UPDATE_POLICY_DAILY,
                ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE);
        request.addRemoteRepository(
                new MavenArtifactRepository("jboss", "https://repository.jboss.org/nexus/content/groups/public/",
                        new DefaultRepositoryLayout(), snapshot, release));
        request.addRemoteRepository(new MavenArtifactRepository("redhat-ga", "https://maven.repository.redhat.com/ga/",
                new DefaultRepositoryLayout(), snapshot, release));
    }
}
