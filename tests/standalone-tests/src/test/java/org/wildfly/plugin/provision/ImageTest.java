/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import static org.junit.Assume.assumeNotNull;
import static org.wildfly.plugin.provision.ExecUtil.exec;
import static org.wildfly.plugin.provision.ExecUtil.execSilentWithTimeout;

import java.nio.file.Path;
import java.time.Duration;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.plugin.tests.AbstractProvisionConfiguredMojoTestCase;
import org.wildfly.plugin.tests.AbstractWildFlyMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;

public class ImageTest extends AbstractProvisionConfiguredMojoTestCase {

    public ImageTest() {
        super("wildfly-maven-plugin");
    }

    @BeforeClass
    public static void checkDockerInstallation() {
        assumeNotNull("Docker is not present in the installation, skipping the tests",
                ExecUtil.resolveImageBinary());
    }

    @Test
    public void testBuildImage() throws Exception {
        Assume.assumeFalse("This test is flaky on Windows, ignore it on Windows.", TestEnvironment.isWindows());
        final String binary = ExecUtil.resolveImageBinary();
        try {
            assertTrue(
                    execSilentWithTimeout(Duration.ofMillis(3000),
                            binary, "-v"));
            assertFalse(
                    exec(null,
                            binary, "inspect", "wildfly-maven-plugin/testing"));

            final Mojo imageMojo = lookupConfiguredMojo(AbstractWildFlyMojoTest.getPomFile("image-pom.xml").toFile(), "image");

            imageMojo.execute();
            Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("image-server");
            assertTrue(jbossHome.toFile().exists());

            assertTrue(
                    exec(null,
                            binary, "inspect", "wildfly-maven-plugin/testing"));
        } finally {

            exec(null,
                    binary, "rmi", "wildfly-maven-plugin/testing");
        }
    }

    @Test(expected = MojoExecutionException.class)
    public void testBuildImageWithUnknownDockerBinary() throws Exception {
        final Mojo imageMojo = lookupConfiguredMojo(
                AbstractWildFlyMojoTest.getPomFile("image-unknown-docker-binary-pom.xml").toFile(), "image");
        imageMojo.execute();
    }
}
