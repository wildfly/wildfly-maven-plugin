/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import static org.wildfly.plugin.provision.ExecUtil.execSilentWithTimeout;
import static org.wildfly.plugin.tests.AbstractWildFlyMojoTest.getPomFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.wildfly.plugin.categories.ChannelsRequired;
import org.wildfly.plugin.tests.AbstractWildFlyMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;

@Category(ChannelsRequired.class)
public class ImageTest extends AbstractImageTest {

    @Test
    public void testBuildImage() throws Exception {
        Assume.assumeFalse("This test is flaky on Windows, ignore it on Windows.", TestEnvironment.isWindows());
        final String binary = ExecUtil.resolveImageBinary();
        try {
            assertTrue(
                    execSilentWithTimeout(Duration.ofMillis(3000),
                            binary, "-v"));
            assertFalse(
                    exec(binary, "inspect", "wildfly-maven-plugin/testing"));

            final Mojo imageMojo = lookupConfiguredMojo(getPomFile("image-pom.xml").toFile(), "image");

            imageMojo.execute();
            Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("image-server");
            assertTrue(jbossHome.toFile().exists());
            Path dockerFile = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("Dockerfile");
            assertTrue(dockerFile.toFile().exists());
            List<String> dockerfileLines = Files.readAllLines(dockerFile);
            assertLineContains(dockerfileLines, 1, "LABEL description=\"This text illustrates \\");
            assertLineContains(dockerfileLines, 2, "that label-values can span multiple lines.\"");
            assertLineContains(dockerfileLines, 3, "LABEL quoted.line=\"I have \\\"quoted myself\\\" here.\"");
            assertLineContains(dockerfileLines, 4, "LABEL version=\"1.0\"");
            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            assertTrue(ExecUtil.exec(stdout, binary, "inspect", "wildfly-maven-plugin/testing"));
            assertEnvironmentUnset(stdout, "SERVER_ARGS=-c=");
        } finally {

            exec(binary, "rmi", "wildfly-maven-plugin/testing");
        }
    }

    @Test(expected = MojoExecutionException.class)
    public void testBuildImageWithUnknownDockerBinary() throws Exception {
        final Mojo imageMojo = lookupConfiguredMojo(
                getPomFile("image-unknown-docker-binary-pom.xml").toFile(), "image");
        imageMojo.execute();
    }

    private static void assertLineContains(final List<String> dockerfileLines, final int index, final String expected) {
        assertEquals(
                String.format("Expected Dockerfile to contain %s at line %d%n%s", expected, index,
                        String.join(System.lineSeparator(), dockerfileLines)),
                expected, dockerfileLines.get(index));
    }
}
