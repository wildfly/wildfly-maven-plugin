/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wildfly.plugin.provision.ExecUtil.execSilentWithTimeout;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import javax.inject.Inject;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.wildfly.plugin.categories.ChannelsRequired;
import org.wildfly.plugin.tests.AbstractProjectMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;
import org.wildfly.plugin.tools.TestSupport;

@MojoTest(realRepositorySession = true)
@Basedir(TestEnvironment.TEST_PROJECT_PATH)
@ChannelsRequired
public class ImageTest extends AbstractProjectMojoTest {

    @Inject
    private Log log;

    @Test
    @InjectMojo(goal = "image", pom = "image-pom.xml")
    @DisabledOnOs(OS.WINDOWS)
    public void testBuildImage(final ApplicationImageMojo imageMojo) throws Exception {
        final String binary = ExecUtil.resolveImageBinary();
        try {
            assertTrue(
                    execSilentWithTimeout(Duration.ofMillis(3000),
                            binary, "-v"));
            assertFalse(
                    ExecUtil.exec(log, binary, "inspect", "wildfly-maven-plugin/testing"));

            imageMojo.execute();
            Path jbossHome = Path.of(TestEnvironment.TEST_PROJECT_TARGET_PATH, "image-server");
            assertTrue(jbossHome.toFile().exists());
            Path dockerFile = Path.of(TestEnvironment.TEST_PROJECT_TARGET_PATH, "Dockerfile");
            assertTrue(dockerFile.toFile().exists());
            List<String> dockerfileLines = Files.readAllLines(dockerFile);
            assertLineContains(dockerfileLines, 1, "LABEL description=\"This text illustrates \\");
            assertLineContains(dockerfileLines, 2, "that label-values can span multiple lines.\"");
            assertLineContains(dockerfileLines, 3, "LABEL quoted.line=\"I have \\\"quoted myself\\\" here.\"");
            assertLineContains(dockerfileLines, 4, "LABEL version=\"1.0\"");
            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            assertTrue(ExecUtil.exec(stdout, binary, "inspect", "wildfly-maven-plugin/testing"),
                    () -> "Invalid output from the process: %s".formatted(stdout));
            TestSupport.assertEnvironmentUnset(stdout, "SERVER_ARGS=-c=");
        } finally {

            ExecUtil.exec(log, binary, "rmi", "wildfly-maven-plugin/testing");
        }
    }

    @Test
    @InjectMojo(goal = "image", pom = "image-unknown-docker-binary-pom.xml")
    public void testBuildImageWithUnknownDockerBinary(final ApplicationImageMojo imageMojo) {
        assertThrows(MojoExecutionException.class, imageMojo::execute);
    }

    private static void assertLineContains(final List<String> dockerfileLines, final int index, final String expected) {
        assertEquals(
                expected,
                dockerfileLines.get(index), String.format("Expected Dockerfile to contain %s at line %d%n%s", expected, index,
                        String.join(System.lineSeparator(), dockerfileLines)));
    }
}
