/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.plugin.provision;

import static org.wildfly.plugin.provision.ExecUtil.execSilentWithTimeout;
import static org.wildfly.plugin.tests.AbstractWildFlyMojoTest.getPomFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assume;
import org.junit.Test;
import org.wildfly.plugin.tests.AbstractWildFlyMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;

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
}
