/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.plugin.Mojo;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonConfigurationWithLayers;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.util.PathsUtils;
import org.jboss.galleon.util.ZipUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.wildfly.core.launcher.BootableJarCommandBuilder;
import org.wildfly.plugin.categories.ChannelsRequired;
import org.wildfly.plugin.tests.AbstractProjectMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;
import org.wildfly.plugin.tools.TestSupport;
import org.wildfly.plugin.tools.bootablejar.BootableJarSupport;
import org.wildfly.plugin.tools.server.Configuration;
import org.wildfly.plugin.tools.server.ServerManager;

@MojoTest(realRepositorySession = true)
@Basedir(TestEnvironment.TEST_PROJECT_PATH)
@ChannelsRequired
public class PackageBootableTest extends AbstractProjectMojoTest {

    private static final String BOOTABLE_JAR_NAME = "server-bootable.jar";
    private static final Path PROJECT_TARGET_DIR = Path.of(TestEnvironment.TEST_PROJECT_TARGET_PATH).toAbsolutePath();

    @Override
    @BeforeEach
    public void configureMaven() {
        super.configureMaven();

        final ArtifactHandler artifactHandler = Mockito.mock(ArtifactHandler.class);
        Mockito.when(artifactHandler.getLanguage()).thenReturn("java");
        final Artifact artifact = new DefaultArtifact("testing", "testing-parent", "1.0", null, "pom", null, artifactHandler);
        project.setArtifact(artifact);
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-bootable-pom.xml")
    public void testBootablePackage(final PackageServerMojo packageMojo) throws Exception {
        packageMojo.execute();
        final String[] layers = { "jaxrs-server" };
        final String deploymentName = "test.war";
        checkJar(PROJECT_TARGET_DIR, BOOTABLE_JAR_NAME, deploymentName,
                true, layers, null, true);
        checkDeployment(BOOTABLE_JAR_NAME, "test");
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-bootable-root-pom.xml")
    public void testBootableRootPackage(final Mojo packageMojo) throws Exception {
        final String deploymentName = "ROOT.war";
        final Path rootWar = PROJECT_TARGET_DIR.resolve(deploymentName);
        final Path testWar = PROJECT_TARGET_DIR.resolve("test.war");
        Files.copy(testWar, rootWar, StandardCopyOption.REPLACE_EXISTING);
        packageMojo.execute();
        final String[] layers = { "jaxrs-server" };
        final String fileName = "jar-root.jar";
        checkJar(PROJECT_TARGET_DIR, fileName, deploymentName, true, layers, null, true);
        checkDeployment(fileName, null);
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-bootable-glow-pom.xml")
    public void testGlowPackage(final Mojo packageMojo) throws Exception {
        // We need to delete the directory from previous runs without a "clean" executed. This will happen when
        // there are defined javaXX.home properties are set.
        deleteProvisioned("packaged-bootable-glow-server");
        final String[] layers = { "ee-core-profile-server", "microprofile-openapi" };
        packageMojo.execute();
        final String deploymentName = "test.war";
        checkJar(PROJECT_TARGET_DIR, BOOTABLE_JAR_NAME, deploymentName,
                true, layers, null, true);
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-bootable-glow-cloud-pom.xml")
    public void testGlowCloudPackage(final Mojo packageMojo) throws Exception {
        // We need to delete the directory from previous runs without a "clean" executed. This will happen when
        // there are defined javaXX.home properties are set.
        deleteProvisioned("packaged-bootable-cloud-glow-server");
        final String[] layers = { "ee-core-profile-server" };
        packageMojo.execute();
        final String deploymentName = "test.war";
        checkJar(PROJECT_TARGET_DIR, BOOTABLE_JAR_NAME, deploymentName,
                true, layers, null, true);
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-bootable-grpc-pom.xml")
    public void testGrpcPackage(final Mojo packageMojo) throws Exception {
        packageMojo.execute();
        final String[] layers = { "jaxrs-server", "grpc" };
        final String deploymentName = "test.war";
        checkJar(PROJECT_TARGET_DIR, BOOTABLE_JAR_NAME, deploymentName,
                true, layers, null, true);
    }

    @Test
    @InjectMojo(goal = "package", pom = "package-bootable-config-name-pom.xml")
    public void testConfigNamePackage(final Mojo packageMojo) throws Exception {
        packageMojo.execute();
        final String[] layers = { "microprofile-config" };
        final String deploymentName = "test.war";
        checkAndGetWildFlyHome(PROJECT_TARGET_DIR, BOOTABLE_JAR_NAME, deploymentName, true, layers,
                null,
                "standalone-ha.xml",
                true,
                "jgroups");
    }

    private void checkDeployment(final String fileName, final String deploymentName) throws Exception {
        bootAndValidate(fileName, createUrl((deploymentName == null ? "" : deploymentName)));
    }

    private static String createUrl(final String... paths) {
        final StringBuilder result = new StringBuilder(32)
                .append("http://")
                .append(TestEnvironment.HOSTNAME)
                .append(':')
                .append(TestEnvironment.HTTP_PORT);
        for (final String path : paths) {
            result.append('/')
                    .append(path);
        }
        return result.toString();
    }

    private HttpResponse<String> sendRequest(final String url) throws IOException, InterruptedException {
        final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void bootAndValidate(final String fileName, final String url) throws Exception {
        try (ServerManager serverManager = startServer(fileName)) {
            assertTrue(serverManager.isRunning(), () -> "The server is not running with JAR %s".formatted(fileName));
            final HttpResponse<String> response = sendRequest(url);
            Assertions.assertEquals(200, response.statusCode(),
                    () -> "The server returned HTTP response code %s: %s".formatted(response.statusCode(), response.body()));
        }
    }

    private void checkJar(final Path dir, final String fileName, final String deploymentName, final boolean expectDeployment,
            final String[] layers, final String[] excludedLayers, final boolean stateRecorded, final String... configTokens)
            throws Exception {
        Path wildflyHome = null;
        try {
            wildflyHome = checkAndGetWildFlyHome(dir, fileName, deploymentName, expectDeployment, layers,
                    excludedLayers,
                    "standalone.xml",
                    stateRecorded,
                    configTokens);
        } finally {
            if (wildflyHome != null) {
                TestSupport.deleteRecursively(wildflyHome);
            }
        }
    }

    private Path checkAndGetWildFlyHome(final Path dir, final String fileName, final String deploymentName,
            final boolean expectDeployment,
            final String[] layers, final String[] excludedLayers, final String configName, final boolean stateRecorded,
            final String... configTokens)
            throws Exception {
        final Path tmpDir = Files.createTempDirectory("bootable-jar-test-unzipped");
        final Path wildflyHome = Files.createTempDirectory("bootable-jar-test-unzipped-" + BootableJarSupport.BOOTABLE_SUFFIX);
        try {
            final Path jar = dir.resolve(fileName == null ? "server-" + BootableJarSupport.BOOTABLE_SUFFIX + ".jar" : fileName);
            assertTrue(Files.exists(jar));

            ZipUtils.unzip(jar, tmpDir);
            final Path zippedWildfly = tmpDir.resolve("wildfly.zip");
            assertTrue(Files.exists(zippedWildfly));

            final Path provisioningFile = tmpDir.resolve("provisioning.xml");
            assertTrue(Files.exists(provisioningFile));

            ZipUtils.unzip(zippedWildfly, wildflyHome);
            if (expectDeployment) {
                assertTrue(Files.exists(wildflyHome.resolve("standalone/deployments").resolve(deploymentName)));
            } else {
                assertFalse(Files.exists(wildflyHome.resolve("standalone/deployments").resolve(deploymentName)));
            }
            final Path history = wildflyHome.resolve("standalone").resolve("configuration").resolve("standalone_xml_history");
            assertFalse(Files.exists(history));

            final Path configFile = wildflyHome.resolve("standalone/configuration/standalone.xml");
            assertTrue(Files.exists(configFile));
            if (layers != null) {
                final Path pFile = PathsUtils.getProvisioningXml(wildflyHome);
                assertTrue(Files.exists(pFile));
                try (Provisioning provisioning = new GalleonBuilder().newProvisioningBuilder(pFile).build()) {
                    final GalleonProvisioningConfig configDescription = provisioning.loadProvisioningConfig(pFile);
                    GalleonConfigurationWithLayers config = null;
                    for (GalleonConfigurationWithLayers c : configDescription.getDefinedConfigs()) {
                        if (c.getModel().equals("standalone") && c.getName().equals(configName)) {
                            config = c;
                        }
                    }
                    assertNotNull(config);
                    assertEquals(layers.length, config.getIncludedLayers().size());
                    for (String layer : layers) {
                        assertTrue(config.getIncludedLayers().contains(layer));
                    }
                    if (excludedLayers != null) {
                        for (String layer : excludedLayers) {
                            assertTrue(config.getExcludedLayers().contains(layer));
                        }
                    }
                }
            }
            if (configTokens != null) {
                final String str = Files.readString(configFile);
                for (String token : configTokens) {
                    assertTrue(str.contains(token), str);
                }
            }
        } finally {
            TestSupport.deleteRecursively(tmpDir);
        }
        assertEquals(Files.exists(wildflyHome.resolve(".galleon")), stateRecorded);
        return wildflyHome;
    }

    // ServerManager is returned and managed by caller with try-with-resources in bootAndValidate()
    @SuppressWarnings("resource")
    private ServerManager startServer(final String fileName) throws Exception {
        final BootableJarCommandBuilder commandBuilder = BootableJarCommandBuilder.of(
                Path.of(TestEnvironment.TEST_PROJECT_TARGET_PATH, fileName).toAbsolutePath())
                .addJavaOption("-Djboss.management.http.port=" + TestEnvironment.PORT)
                .addJavaOption("-Djboss.http.port=" + TestEnvironment.HTTP_PORT);

        final Path out = Files.createTempFile("logs-package-bootable", "-process.txt");
        final Path parent = out.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
        return ServerManager.of(
                Configuration.create(commandBuilder)
                        .redirectErrorStream(true)
                        .redirectOutput(out.toFile())
                        .shutdownOnClose(true))
                .start(TestEnvironment.TIMEOUT, TimeUnit.SECONDS);
    }

    private static void deleteProvisioned(final String fileName) throws IOException {
        TestSupport.deleteRecursively(Path.of(TestEnvironment.TEST_PROJECT_TARGET_PATH, fileName));
    }
}
