/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.Mockito;
import org.wildfly.plugin.categories.ChannelsRequired;
import org.wildfly.plugin.tests.AbstractProjectMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;

/**
 * Tests the additional deployments, resolved from the project dependencies, are added to the application image.
 */
@MojoTest(realRepositorySession = true)
@Basedir(TestEnvironment.TEST_PROJECT_PATH)
@ChannelsRequired
@DisabledOnOs(OS.WINDOWS)
public class ExtraDeploymentsImageTest extends AbstractProjectMojoTest {

    // The JBOSS_HOME directory of the WildFly runtime image
    private static final String IMAGE_JBOSS_HOME = "/opt/server";

    @Inject
    private Log log;

    @Override
    @BeforeEach
    public void configureMaven() {
        super.configureMaven();
        final Dependency dependency = new Dependency();
        dependency.setGroupId("testing");
        dependency.setArtifactId("dummy");
        dependency.setVersion("1.0");
        dependency.setScope("system");
        dependency.setSystemPath(Path.of(TestEnvironment.TEST_PROJECT_PATH, "dummy.jar").toString());
        Mockito.when(project.getDependencies()).thenReturn(List.of(dependency));

        final ArtifactHandler artifactHandler = Mockito.mock(ArtifactHandler.class);
        Mockito.when(artifactHandler.getLanguage()).thenReturn("java");
        final Artifact artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(),
                dependency.getVersion(), dependency.getScope(), "jar", null, artifactHandler);
        artifact.setFile(new File(dependency.getSystemPath()));
        project.setArtifacts(Set.of(artifact));
    }

    @Test
    @InjectMojo(goal = "image", pom = "image-extra-deployments-pom.xml")
    public void extraDeployments(final Mojo imageMojo) throws Exception {
        final String imageName = "wildfly-image-extra-deployments-maven-plugin/testing";
        final String binary = ExecUtil.resolveImageBinary();
        try {
            imageMojo.execute();
            // As for the primary deployment, the additional deployment is not part of the server layer
            final Path jbossHome = TestEnvironment.resolveProjectTarget("image-extra-deployments-server");
            TestEnvironment.checkStandaloneWildFlyHome(jbossHome, 0, null, null, false);
            assertTrue(Files.exists(TestEnvironment.resolveProjectTarget("image-deployments", "dummy.jar")));

            final List<String> dockerfileLines = Files.readAllLines(TestEnvironment.resolveProjectTarget("Dockerfile"));
            assertEquals(List.of(
                    "COPY --chown=jboss:root image-extra-deployments-server $JBOSS_HOME",
                    "RUN chmod -R ug+rwX $JBOSS_HOME",
                    "COPY --chown=jboss:root image-deployments/dummy.jar $JBOSS_HOME/standalone/deployments/",
                    "COPY --chown=jboss:root test.war $JBOSS_HOME/standalone/deployments/test.war"),
                    dockerfileLines.subList(1, dockerfileLines.size()));

            final String deployments = execute(binary, "run", "--rm", "--entrypoint", "/bin/sh", imageName, "-c",
                    "ls $JBOSS_HOME/standalone/deployments");
            assertEquals(Set.of("README.txt", "dummy.jar", "test.war"), Set.of(deployments.trim().split("\\s+")));
        } finally {
            ExecUtil.exec(log, binary, "rmi", imageName);
        }
    }

    /**
     * Builds an image without a primary deployment. The Galleon configuration disables the deployment scanner and
     * registers the additional deployment as an unmanaged deployment, which allows the container to run with a
     * read-only file system.
     */
    @Test
    @InjectMojo(goal = "image", pom = "image-extra-deployments-no-primary-pom.xml")
    public void extraDeploymentsWithoutPrimaryDeployment(final Mojo imageMojo) throws Exception {
        final String imageName = "wildfly-image-extra-deployments-no-primary-maven-plugin/testing";
        final String containerName = "wildfly-maven-plugin-extra-deployments-test";
        final String binary = ExecUtil.resolveImageBinary();
        Mockito.when(project.getPackaging()).thenReturn("pom");
        final String provisioning = Files.readString(
                Path.of(TestEnvironment.TEST_PROJECT_PATH, "image-extra-deployments-provisioning.xml"))
                .replace("${wildfly.test.universe.location}",
                        project.getProperties().getProperty("wildfly.test.universe.location"));
        Files.createDirectories(Path.of(TestEnvironment.TEST_PROJECT_TARGET_PATH));
        Files.writeString(TestEnvironment.resolveProjectTarget("image-extra-deployments-provisioning.xml"), provisioning);
        try {
            imageMojo.execute();
            final Path jbossHome = TestEnvironment.resolveProjectTarget("image-extra-deployments-no-primary-server");
            TestEnvironment.checkStandaloneWildFlyHome(jbossHome, 0, null, null, true,
                    "<fs-archive path=\"deployments/dummy.jar\" relative-to=\"jboss.server.base.dir\"/>",
                    "scan-enabled=\"false\"");

            // Without a primary deployment only the server and the additional deployments are copied
            final List<String> dockerfileLines = Files.readAllLines(TestEnvironment.resolveProjectTarget("Dockerfile"));
            assertEquals(List.of(
                    "COPY --chown=jboss:root image-extra-deployments-no-primary-server $JBOSS_HOME",
                    "RUN chmod -R ug+rwX $JBOSS_HOME",
                    "COPY --chown=jboss:root image-deployments/dummy.jar $JBOSS_HOME/standalone/deployments/"),
                    dockerfileLines.subList(1, dockerfileLines.size()));

            final String tmpfsOptions = ":rw,uid=185,gid=0,mode=0770";
            execute(binary, "run", "-d", "--name", containerName, "--read-only",
                    "--tmpfs", "/tmp:rw,mode=1777",
                    "--tmpfs", IMAGE_JBOSS_HOME + "/standalone/data" + tmpfsOptions,
                    "--tmpfs", IMAGE_JBOSS_HOME + "/standalone/log" + tmpfsOptions,
                    "--tmpfs", IMAGE_JBOSS_HOME + "/standalone/tmp" + tmpfsOptions,
                    "-e", "SERVER_ARGS=--read-only-server-config=standalone.xml",
                    imageName);
            final String serverLog = waitForServer(binary, containerName);
            assertTrue(serverLog.contains("WFLYSRV0010: Deployed \"dummy.jar\""), serverLog);
            assertFalse(serverLog.contains("WFLYSRV0026"), serverLog);
        } finally {
            ExecUtil.exec(log, binary, "rm", "-f", containerName);
            ExecUtil.exec(log, binary, "rmi", imageName);
        }
    }

    @Test
    @InjectMojo(goal = "image", pom = "image-extra-deployments-conflict-pom.xml")
    public void extraDeploymentsDirectoryConflict(final Mojo imageMojo) {
        final MojoExecutionException e = assertThrows(MojoExecutionException.class, imageMojo::execute);
        assertTrue(e.getMessage().contains("must not overlap"), e.getMessage());
        // The conflict is detected before the server is provisioned
        assertFalse(TestEnvironment.isValidWildFlyHome(TestEnvironment.resolveProjectTarget("image-deployments")));
    }

    private static String waitForServer(final String binary, final String containerName) throws Exception {
        final long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TestEnvironment.TIMEOUT);
        String serverLog = "";
        while (System.currentTimeMillis() < timeout) {
            serverLog = execute(binary, "logs", containerName);
            if (serverLog.contains("WFLYSRV0025") || serverLog.contains("WFLYSRV0026")) {
                return serverLog;
            }
            TimeUnit.SECONDS.sleep(1L);
        }
        throw new AssertionError("The server in container " + containerName + " did not start:\n" + serverLog);
    }

    private static String execute(final String... command) throws IOException, InterruptedException {
        final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        final int exitCode = process.waitFor();
        assertEquals(0, exitCode, () -> "Command %s failed:%n%s".formatted(List.of(command), output));
        return output;
    }
}
