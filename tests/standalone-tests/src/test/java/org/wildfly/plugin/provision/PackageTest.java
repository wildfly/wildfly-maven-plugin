/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.wildfly.plugin.categories.ChannelsRequired;
import org.wildfly.plugin.tests.AbstractProjectMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;

@MojoTest(realRepositorySession = true)
@Basedir(TestEnvironment.TEST_PROJECT_PATH)
@ChannelsRequired
public class PackageTest extends AbstractProjectMojoTest {

    @Override
    @BeforeEach
    public void configureMaven() {
        super.configureMaven();
        // The testInvalidDeployment2 test expects the final name to be set to "foo" resulting in "foo.jar" for the
        // artifact name.
        project.getBuild().setFinalName("foo");
        addDependencies();
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-pom.xml")
    public void testPackage(final PackageServerMojo packageMojo) throws Exception {
        packageMojo.execute();
        Path jbossHome = resolvePath("packaged-server");
        assertTrue(Files.exists(jbossHome.resolve("standalone").resolve("configuration").resolve("foo.txt")));
        final String[] layers = { "jaxrs-server" };
        final String[] excluded = { "deployment-scanner" };
        TestEnvironment.checkStandaloneWildFlyHome(jbossHome, 1, layers, excluded, true,
                "org.wildfly.maven.plugin-package-goal",
                "org.wildfly.maven.plugin-package-goal-from-script");
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-channel-pom.xml")
    public void testPackageWithChannel(final Mojo packageMojo) throws Exception {
        packageMojo.execute();
        Path jbossHome = resolvePath("packaged-channel-server");
        assertTrue(Files.exists(jbossHome.resolve("standalone").resolve("configuration").resolve("foo.txt")));
        String[] layers = { "jaxrs-server" };
        String[] excluded = { "deployment-scanner" };
        TestEnvironment.checkStandaloneWildFlyHome(jbossHome, 1, layers, excluded, true,
                "org.wildfly.maven.plugin-package-goal",
                "org.wildfly.maven.plugin-package-goal-from-script");
    }

    // This test provisions WildFly 32 which does not boot on Java SE 24 without security manager support.
    @Test
    @Tag("SecurityManagerRequired")
    @InjectMojo(goal = "package", pom = "package-channel-glow-pom.xml")
    public void testPackageWithChannelGlow(final Mojo packageMojo) throws Exception {
        packageMojo.execute();
        Path jbossHome = resolvePath("packaged-channel-glow-server");
        assertTrue(Files.exists(jbossHome.resolve("standalone").resolve("configuration").resolve("foo.txt")));
        String[] layers = { "ee-core-profile-server" };
        TestEnvironment.checkStandaloneWildFlyHome(jbossHome, 1, layers, null, true, "org.wildfly.maven.plugin-package-goal",
                "org.wildfly.maven.plugin-package-goal-from-script");
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-default-config-pom.xml")
    public void testDefaultConfigPackage(final Mojo packageMojo) throws Exception {
        packageMojo.execute();
        Path jbossHome = resolvePath("packaged-default-config-server");
        TestEnvironment.checkStandaloneWildFlyHome(jbossHome, 0, null, null, true);
        TestEnvironment.checkDomainWildFlyHome(jbossHome, 0, true);
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-no-deployment-pom.xml")
    public void testNoDeploymentPackage(final Mojo packageMojo) throws Exception {
        packageMojo.execute();
        Path jbossHome = resolvePath("packaged-no-dep-server");
        TestEnvironment.checkStandaloneWildFlyHome(jbossHome, 0, null, null, true);
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-glow-pom.xml")
    public void testGlowPackage(final Mojo packageMojo) throws Exception {
        String[] layers = { "ee-core-profile-server", "microprofile-openapi" };
        packageMojo.execute();
        Path jbossHome = resolvePath("packaged-glow-server");
        TestEnvironment.checkStandaloneWildFlyHome(jbossHome, 1, layers, null, true);
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-glow-no-deployment-pom.xml")
    public void testGlowNoDeploymentPackage(final Mojo packageMojo) throws Exception {
        String[] layers = { "ee-core-profile-server", "microprofile-openapi" };
        packageMojo.execute();
        Path jbossHome = resolvePath("packaged-glow-no-deployment-server");
        TestEnvironment.checkStandaloneWildFlyHome(jbossHome, 0, layers, null, true);
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-invalid-deployment-pom.xml")
    public void testInvalidDeployment(final Mojo packageMojo) throws Exception {
        try {
            packageMojo.execute();
            throw new Exception("Execution should have failed");
        } catch (MojoExecutionException ex) {
            // XXX OK, expected.
            assertTrue(ex.getLocalizedMessage().contains("No deployment found with name test-foo.war"));
        }
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-invalid-deployment2-pom.xml")
    public void testInvalidDeployment2(final Mojo packageMojo) throws Exception {
        try {
            packageMojo.execute();
            throw new Exception("Execution should have failed");
        } catch (MojoExecutionException ex) {
            // XXX OK, expected.
            assertTrue(ex.getLocalizedMessage().contains("No deployment found with name foo.jar. "
                    + "A runtime-name has been set that indicates that a deployment is expected. "), () -> {
                        try (
                                StringWriter writer = new StringWriter();
                                PrintWriter out = new PrintWriter(writer)) {
                            out.printf("A MojoExecutionException has been thrown, but the wrong message was found: %s%n",
                                    ex.getMessage());
                            ex.printStackTrace(out);

                            return writer.toString();
                        } catch (IOException e) {
                            // This shouldn't ever happen, but close throws it so we have to handle it
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-no-multiple-deployments-pom.xml")
    public void testNoMultipleDeploymentsPackage(final Mojo packageMojo) throws Exception {
        String[] layers = { "jaxrs-server" };
        packageMojo.execute();
        Path jbossHome = Path.of(TestEnvironment.TEST_PROJECT_TARGET_PATH, "packaged-no-multiple-deployments-server");
        TestEnvironment.checkStandaloneWildFlyHome(jbossHome, 1, layers, null, true);
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-multiple-deployments-pom.xml")
    public void testMultipleDeploymentsPackage(final Mojo packageMojo) throws Exception {
        String[] layers = { "jaxrs-server" };
        packageMojo.execute();
        Path jbossHome = resolvePath("packaged-multiple-deployments-server");
        TestEnvironment.checkStandaloneWildFlyHome(jbossHome, 2, layers, null, true);
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-multiple-deployments-missing-pom.xml")
    public void testMultipleDeploymentsMissingPackage(final Mojo packageMojo) throws Exception {
        String[] layers = { "jaxrs-server" };
        packageMojo.execute();
        Path jbossHome = Path.of(TestEnvironment.TEST_PROJECT_TARGET_PATH, "packaged-multiple-deployments-missing-server");
        TestEnvironment.checkStandaloneWildFlyHome(jbossHome, 1, layers, null, true);
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-grpc-pom.xml")
    public void testGrpcPackage(final Mojo packageMojo) throws Exception {
        packageMojo.execute();
        Path jbossHome = resolvePath("packaged-grpc-server");
        assertTrue(Files.exists(jbossHome.resolve("standalone").resolve("configuration").resolve("foo.txt")));
        String[] layers = { "jaxrs-server", "grpc" };
        TestEnvironment.checkStandaloneWildFlyHome(jbossHome, 1, layers, null, true, "org.wildfly.maven.plugin-package-goal",
                "org.wildfly.maven.plugin-package-goal-from-script");
    }

    private void addDependencies() {
        final List<Dependency> dependencies = new ArrayList<>();
        dependencies.add(
                createDependency("testing", "dummy", "1.0", "system", Path.of(TestEnvironment.TEST_PROJECT_PATH, "dummy.jar")));
        dependencies.add(createDependency("testing", "dummy-test", "1.0", "system",
                Path.of(TestEnvironment.TEST_PROJECT_PATH, "dummy-test.jar")));
        dependencies.add(createDependency("testing", "dummy-test-common", "1.0", "system",
                Path.of(TestEnvironment.TEST_PROJECT_PATH, "dummy-test-common.jar")));
        dependencies.add(createDependency("org.junit.jupiter", "junit-jupiter", "5.11.3", "test", null));
        Mockito.when(project.getDependencies()).thenReturn(dependencies);

        final ArtifactHandler artifactHandler = Mockito.mock(ArtifactHandler.class);
        Mockito.when(artifactHandler.getLanguage()).thenReturn("java");
        final Set<Artifact> artifacts = new LinkedHashSet<>();
        for (final Dependency dependency : dependencies) {
            final Artifact artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(),
                    dependency.getVersion(), dependency.getScope(), "jar", null, artifactHandler);
            if (dependency.getSystemPath() != null) {
                artifact.setFile(new File(dependency.getSystemPath()));
            }
            artifacts.add(artifact);
        }
        project.setArtifacts(artifacts);
    }

    private static Dependency createDependency(final String groupId, final String artifactId, final String version,
            final String scope, final Path archive) {
        final Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        dependency.setScope(scope == null ? "compile" : scope);
        if (archive != null && "system".equals(scope)) {
            dependency.setSystemPath(archive.toString());
        }
        return dependency;
    }

    private Path resolvePath(final String path) {
        return Path.of(TestEnvironment.TEST_PROJECT_TARGET_PATH, path);
    }

}
