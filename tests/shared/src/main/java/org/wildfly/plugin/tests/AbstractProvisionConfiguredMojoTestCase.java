/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.tests;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonConfigurationWithLayers;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.PathsUtils;
import org.jboss.galleon.util.ZipUtils;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.plugin.tools.bootablejar.BootableJarSupport;
import org.wildfly.plugin.tools.server.ServerManager;

/**
 * A class to construct a properly configured MOJO.
 *
 */
@RunWith(JUnit4.class)
public abstract class AbstractProvisionConfiguredMojoTestCase extends AbstractMojoTestCase {
    private static final String TEST_REPLACE_WF_VERSION = "WF_VERSION";
    private static final String TEST_REPLACE_BASE_DIR_ABSOLUTE_URL = "WF_BASE_DIR_ABSOLUTE_URL";
    static final String WILDFLY_VERSION = "wildfly.test.version";
    private final String artifactId;

    protected AbstractProvisionConfiguredMojoTestCase(String artifactId) {
        this.artifactId = artifactId;
    }

    protected MavenSession newMavenSession() {
        try {
            MavenExecutionRequest request = new DefaultMavenExecutionRequest();
            MavenExecutionResult result = new DefaultMavenExecutionResult();

            MavenExecutionRequestPopulator populator;
            populator = getContainer().lookup(MavenExecutionRequestPopulator.class);
            populator.populateDefaults(request);
            // Required otherwise WARNING:The POM for org.wildfly.core:wildfly-jar-boot:jar:
            // is invalid, transitive dependencies (if any)
            // request.setSystemProperties(System.getProperties());

            DefaultMaven maven = (DefaultMaven) getContainer().lookup(Maven.class);
            DefaultRepositorySystemSession repoSession = (DefaultRepositorySystemSession) maven.newRepositorySession(request);

            // Add remote repositories required to resolve provisioned artifacts.
            ArtifactRepositoryPolicy snapshot = new ArtifactRepositoryPolicy(false,
                    ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE);
            ArtifactRepositoryPolicy release = new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_DAILY,
                    ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE);

            // Take into account maven.repo.local
            String path = System.getProperty("maven.repo.local", request.getLocalRepository().getBasedir());
            repoSession.setLocalRepositoryManager(
                    new SimpleLocalRepositoryManagerFactory().newInstance(repoSession,
                            new LocalRepository(path)));
            request.addRemoteRepository(
                    new MavenArtifactRepository("jboss", "https://repository.jboss.org/nexus/content/groups/public/",
                            new DefaultRepositoryLayout(), snapshot, release));
            request.addRemoteRepository(new MavenArtifactRepository("redhat-ga", "https://maven.repository.redhat.com/ga/",
                    new DefaultRepositoryLayout(), snapshot, release));

            @SuppressWarnings("deprecation")
            MavenSession session = new MavenSession(getContainer(),
                    repoSession,
                    request, result);
            return session;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected MavenSession newMavenSession(MavenProject project) {
        MavenSession session = newMavenSession();
        session.setCurrentProject(project);
        session.setProjects(Arrays.asList(project));
        return session;
    }

    protected Mojo lookupConfiguredMojo(File pom, String goal) throws Exception {
        return lookupConfiguredMojo(pom.toPath(), goal);
    }

    protected Mojo lookupConfiguredMojo(final Path pom, final String goal) throws Exception {
        assertNotNull(pom);
        assertTrue(Files.exists(pom));
        patchPomFile(pom);
        ProjectBuildingRequest buildingRequest = newMavenSession().getProjectBuildingRequest();
        // Need to resolve artifacts for tests that upgrade server components
        buildingRequest.setResolveDependencies(true);
        ProjectBuilder projectBuilder = lookup(ProjectBuilder.class);
        MavenProject project = projectBuilder.build(pom.toFile(), buildingRequest).getProject();

        Mojo mojo = lookupConfiguredMojo(project, goal);

        // For some reasons, the configuration item gets ignored in lookupConfiguredMojo
        // explicitly configure it
        configureMojo(mojo, artifactId, pom.toFile());

        return mojo;
    }

    private void patchPomFile(final Path pom) throws IOException {
        StringBuilder content = new StringBuilder();
        for (String s : Files.readAllLines(pom)) {
            if (s.contains(TEST_REPLACE_WF_VERSION)) {
                s = s.replace(TEST_REPLACE_WF_VERSION, System.getProperty(WILDFLY_VERSION));
            }
            if (s.contains(TEST_REPLACE_BASE_DIR_ABSOLUTE_URL)) {
                s = s.replace(TEST_REPLACE_BASE_DIR_ABSOLUTE_URL, pom.getParent().toUri().toString());
            }
            content.append(s).append(System.lineSeparator());
        }
        Files.write(pom, content.toString().getBytes());
    }

    @Before
    public void before() throws Exception {
        super.setUp();
    }

    public void checkStandaloneWildFlyHome(Path wildflyHome, int numDeployments,
            String[] layers, String[] excludedLayers, boolean stateRecorded, String... configTokens) throws Exception {
        Assert.assertTrue(TestEnvironment.isValidWildFlyHome(wildflyHome));
        if (numDeployments > 0) {
            // Must retrieve all deployments.
            Path rootDir = wildflyHome.resolve("standalone/deployments");
            String[] deployments = rootDir.toFile().list((dir, name) -> !name.equals("README.txt"));
            assertEquals(numDeployments, deployments.length);
        } else {
            // The directory should be empty if no deployment is expected, however in some cases it may not even be
            // created.
            Path rootDir = wildflyHome.resolve("standalone/deployments");
            String[] deployments = rootDir.toFile().list((dir, name) -> !name.equals("README.txt"));
            assertEquals(0, deployments.length);
        }
        Path history = wildflyHome.resolve("standalone").resolve("configuration").resolve("standalone_xml_history");
        assertFalse(Files.exists(history));

        Path configFile = wildflyHome.resolve("standalone/configuration/standalone.xml");
        assertTrue(Files.exists(configFile));
        if (layers != null) {
            Path provisioning = PathsUtils.getProvisioningXml(wildflyHome);
            assertTrue(Files.exists(provisioning));
            ProvisioningConfig config = ProvisioningXmlParser.parse(provisioning);
            ConfigModel cm = config.getDefinedConfig(new ConfigId("standalone", "standalone.xml"));
            assertNotNull(config.getDefinedConfigs().toString(), cm);
            assertEquals(layers.length, cm.getIncludedLayers().size());
            for (String layer : layers) {
                assertTrue(cm.getIncludedLayers().contains(layer));
            }
            if (excludedLayers != null) {
                for (String layer : excludedLayers) {
                    assertTrue(cm.getExcludedLayers().contains(layer));
                }
            }
        }
        if (configTokens != null) {
            String str = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
            for (String token : configTokens) {
                assertTrue(str, str.contains(token));
            }
        }
        assertEquals(Files.exists(wildflyHome.resolve(".galleon")), stateRecorded);
        assertEquals(Files.exists(wildflyHome.resolve(".wildfly-maven-plugin-provisioning.xml")), !stateRecorded);
    }

    public void checkDomainWildFlyHome(Path wildflyHome, int numDeployments,
            boolean stateRecorded, String... configTokens) throws Exception {
        Assert.assertTrue(TestEnvironment.isValidWildFlyHome(wildflyHome));
        if (numDeployments > 0) {
            // Must retrieve all content directories.
            Path rootDir = wildflyHome.resolve("domain/data/content");
            List<Path> deployments = new ArrayList<>();
            Files.walkFileTree(rootDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    if ("content".equals(file.getFileName().toString())) {
                        deployments.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            assertEquals(numDeployments, deployments.size());
        } else {
            // The directory should be empty if no deployment is expected, however in some cases it may not even be
            // created.
            if (Files.exists(wildflyHome.resolve("domain/data/content"))) {
                assertEquals(0, Files.list(wildflyHome.resolve("domain/data/content")).count());
            }
        }
        Path history = wildflyHome.resolve("domain").resolve("configuration").resolve("domain_xml_history");
        assertFalse(Files.exists(history));

        Path configFile = wildflyHome.resolve("domain/configuration/domain.xml");
        assertTrue(Files.exists(configFile));

        if (configTokens != null) {
            String str = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
            for (String token : configTokens) {
                assertTrue(str, str.contains(token));
            }
        }
        assertEquals(Files.exists(wildflyHome.resolve(".galleon")), stateRecorded);
        assertEquals(Files.exists(wildflyHome.resolve(".wildfly-maven-plugin-provisioning.xml")), !stateRecorded);
    }

    protected void checkJar(Path dir, String fileName, String deploymentName, boolean expectDeployment,
            String[] layers, String[] excludedLayers, boolean stateRecorded, String... configTokens) throws Exception {
        Path wildflyHome = null;
        try {
            wildflyHome = checkAndGetWildFlyHome(dir, fileName, deploymentName, expectDeployment, layers,
                    excludedLayers,
                    stateRecorded,
                    configTokens);
        } finally {
            if (wildflyHome != null) {
                IoUtils.recursiveDelete(wildflyHome);
            }
        }
    }

    protected Path checkAndGetWildFlyHome(Path dir, String fileName, String deploymentName, boolean expectDeployment,
            String[] layers, String[] excludedLayers, boolean stateRecorded, String... configTokens) throws Exception {
        Path tmpDir = Files.createTempDirectory("bootable-jar-test-unzipped");
        Path wildflyHome = Files.createTempDirectory("bootable-jar-test-unzipped-" + BootableJarSupport.BOOTABLE_SUFFIX);
        try {
            Path jar = dir.resolve("target")
                    .resolve(fileName == null ? "server-" + BootableJarSupport.BOOTABLE_SUFFIX + ".jar" : fileName);
            assertTrue(Files.exists(jar));

            ZipUtils.unzip(jar, tmpDir);
            Path zippedWildfly = tmpDir.resolve("wildfly.zip");
            assertTrue(Files.exists(zippedWildfly));

            Path provisioningFile = tmpDir.resolve("provisioning.xml");
            assertTrue(Files.exists(provisioningFile));

            ZipUtils.unzip(zippedWildfly, wildflyHome);
            if (expectDeployment) {
                assertTrue(Files.exists(wildflyHome.resolve("standalone/deployments").resolve(deploymentName)));
            } else {
                assertFalse(Files.exists(wildflyHome.resolve("standalone/deployments").resolve(deploymentName)));
            }
            Path history = wildflyHome.resolve("standalone").resolve("configuration").resolve("standalone_xml_history");
            assertFalse(Files.exists(history));

            Path configFile = wildflyHome.resolve("standalone/configuration/standalone.xml");
            assertTrue(Files.exists(configFile));
            if (layers != null) {
                Path pFile = PathsUtils.getProvisioningXml(wildflyHome);
                assertTrue(Files.exists(pFile));
                try (Provisioning provisioning = new GalleonBuilder().newProvisioningBuilder(pFile).build()) {
                    GalleonProvisioningConfig configDescription = provisioning.loadProvisioningConfig(pFile);
                    GalleonConfigurationWithLayers config = null;
                    for (GalleonConfigurationWithLayers c : configDescription.getDefinedConfigs()) {
                        if (c.getModel().equals("standalone") && c.getName().equals("standalone.xml")) {
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
                String str = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
                for (String token : configTokens) {
                    assertTrue(str, str.contains(token));
                }
            }
        } finally {
            IoUtils.recursiveDelete(tmpDir);
        }
        assertEquals(Files.exists(wildflyHome.resolve(".galleon")), stateRecorded);
        return wildflyHome;
    }

    protected void checkDeployment(Path dir, String fileName, String deploymentName) throws Exception {
        checkURL(dir, fileName, createUrl(TestEnvironment.HTTP_PORT, (deploymentName == null ? "" : deploymentName)), true);
    }

    protected static String createUrl(final int port, final String... paths) {
        final StringBuilder result = new StringBuilder(32)
                .append("http://")
                .append(TestEnvironment.HOSTNAME)
                .append(':')
                .append(port);
        for (String path : paths) {
            result.append('/')
                    .append(path);
        }
        return result.toString();
    }

    protected boolean checkURL(String url) {
        try {
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                HttpGet httpget = new HttpGet(url);

                CloseableHttpResponse response = httpclient.execute(httpget);
                System.out.println("STATUS CODE " + response.getStatusLine().getStatusCode());
                return response.getStatusLine().getStatusCode() == 200;
            }
        } catch (Exception ex) {
            System.out.println(ex);
            return false;
        }
    }

    private static void shutdown() throws IOException {
        try (ModelControllerClient client = ModelControllerClient.Factory.create(TestEnvironment.HOSTNAME,
                TestEnvironment.PORT)) {
            final ServerManager serverManager = ServerManager.builder().client(client).standalone();
            if (serverManager.isRunning()) {
                serverManager.shutdown(TestEnvironment.TIMEOUT);
            }
        }
    }

    protected void checkURL(Path dir, String fileName, String url, boolean start, String... args) throws Exception {
        Process process = null;
        int timeout = (int) TestEnvironment.TIMEOUT * 1000;
        long sleep = 1000;
        boolean success = false;
        try {
            if (start) {
                process = startServer(dir, fileName, args);
            }
            // Check the server state in all cases. All test cases are provisioning the manager layer.
            try (ModelControllerClient client = ModelControllerClient.Factory.create(TestEnvironment.HOSTNAME,
                    TestEnvironment.PORT)) {
                final ServerManager serverManager = ServerManager.builder().client(client).standalone();
                // Wait for the server to start, this calls into the management interface.
                serverManager.waitFor(TestEnvironment.TIMEOUT, TimeUnit.SECONDS);
            }

            if (url == null) {
                // Checking for the server state is enough.
                success = true;
            } else {
                while (timeout > 0) {
                    if (checkURL(url)) {
                        System.out.println("Successfully connected to " + url);
                        success = true;
                        break;
                    }
                    Thread.sleep(sleep);
                    timeout -= sleep;
                }
            }
            if (process != null) {
                assertTrue(process.isAlive());
            }
            shutdown();
            // If the process is not null wait for it to shutdown
            if (process != null) {
                assertTrue("The process has failed to shutdown", process.waitFor(TestEnvironment.TIMEOUT, TimeUnit.SECONDS));
            }
        } finally {
            ProcessHelper.destroyProcess(process);
        }
        if (!success) {
            throw new Exception("Unable to interact with deployed application");
        }
    }

    protected Process startServer(Path dir, String fileName, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(TestEnvironment.getJavaCommand(null));
        cmd.add("-jar");
        cmd.add(dir.resolve("target").resolve(fileName).toAbsolutePath().toString());
        cmd.add("-Djboss.management.http.port=" + TestEnvironment.PORT);
        cmd.add("-Djboss.http.port=" + TestEnvironment.HTTP_PORT);
        cmd.addAll(Arrays.asList(args));
        final Path out = Files.createTempFile("logs-package-bootable", "-process.txt");
        final Path parent = out.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
        return new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(out.toFile())
                .start();
    }
}
