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


import static org.junit.Assume.assumeTrue;
import static org.wildfly.plugin.provision.ExecUtil.exec;
import static org.wildfly.plugin.provision.ExecUtil.execSilentWithTimeout;

import java.nio.file.Path;
import java.time.Duration;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.plugin.tests.AbstractProvisionConfiguredMojoTestCase;
import org.wildfly.plugin.tests.AbstractWildFlyMojoTest;

public class ImageTest extends AbstractProvisionConfiguredMojoTestCase {

    public ImageTest() {
        super("wildfly-maven-plugin");
    }

    @BeforeClass
    public static void checkDockerInstallation() {
        assumeTrue("Docker is not present in the installation, skipping the tests",
                execSilentWithTimeout(Duration.ofMillis(3000),
                        "docker", "-v"));
    }

    @Test
    public void testBuildImage() throws Exception {
        try {
            assertTrue(
                    execSilentWithTimeout(Duration.ofMillis(3000),
                            "docker", "-v"));
            assertFalse(
                    exec(null,
                            "docker", "inspect", "wildfly-maven-plugin/testing"));

            final Mojo imageMojo = lookupConfiguredMojo(AbstractWildFlyMojoTest.getPomFile("image-pom.xml").toFile(), "image");

            imageMojo.execute();
            Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("image-server");
            assertTrue(jbossHome.toFile().exists());

            assertTrue(
                    exec(null,
                            "docker", "inspect", "wildfly-maven-plugin/testing"));
        } finally {

            exec(null,
                    "docker", "rmi", "wildfly-maven-plugin/testing");
        }
    }

    @Test(expected = MojoExecutionException.class)
    public void testBuildImageWithUnknownDockerBinary() throws Exception {
        final Mojo imageMojo = lookupConfiguredMojo(AbstractWildFlyMojoTest.getPomFile("image-unknown-docker-binary-pom.xml").toFile(), "image");
        imageMojo.execute();
    }
}
