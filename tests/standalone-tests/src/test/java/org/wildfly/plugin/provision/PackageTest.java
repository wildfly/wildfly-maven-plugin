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


import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;
import org.junit.Assert;
import org.wildfly.plugin.tests.AbstractProvisionConfiguredMojoTestCase;
import org.wildfly.plugin.tests.AbstractWildFlyMojoTest;

public class PackageTest extends AbstractProvisionConfiguredMojoTestCase {

    public PackageTest() {
        super("wildfly-maven-plugin");
    }

    @Test
    public void testPackage() throws Exception {

        final Mojo packageMojo =  lookupConfiguredMojo(AbstractWildFlyMojoTest.getPomFile("package-pom.xml").toFile(), "package");

        packageMojo.execute();
        Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("packaged-server");
        Assert.assertTrue(Files.exists(jbossHome.resolve("standalone").
                resolve("configuration").resolve("foo.txt")));
        String[] layers = {"jaxrs-server"};
        String[] excluded = {"deployment-scanner"};
        checkStandaloneWildFlyHome(jbossHome, 1, layers, excluded, true, "org.wildfly.maven.plugin-package-goal",
                "org.wildfly.maven.plugin-package-goal-from-script");
    }

    @Test
    public void testNoDeploymentPackage() throws Exception {

        final Mojo packageMojo =  lookupConfiguredMojo(AbstractWildFlyMojoTest.getPomFile("package-no-deployment-pom.xml").toFile(), "package");

        packageMojo.execute();
        Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("packaged-no-dep-server");
        checkStandaloneWildFlyHome(jbossHome, 0, null, null, true);
    }

    @Test
    public void testInvalidDeployment() throws Exception {

        final Mojo packageMojo =  lookupConfiguredMojo(AbstractWildFlyMojoTest.getPomFile("package-invalid-deployment-pom.xml").toFile(), "package");
        try {
            packageMojo.execute();
            throw new Exception("Execution should have failed");
        } catch(MojoExecutionException ex) {
            // XXX OK, expected.
            Assert.assertTrue(ex.getLocalizedMessage().contains("No deployment found wih name test-foo.war"));
        }
    }

    @Test
    public void testInvalidDeployment2() throws Exception {

        final Mojo packageMojo =  lookupConfiguredMojo(AbstractWildFlyMojoTest.getPomFile("package-invalid-deployment2-pom.xml").toFile(), "package");
        try {
            packageMojo.execute();
            throw new Exception("Execution should have failed");
        } catch(MojoExecutionException ex) {
            // XXX OK, expected.
            Assert.assertTrue(ex.getLocalizedMessage().contains("No deployment found with name foo.jar. "
                    + "A runtime-name has been set that indicates that a deployment is expected. "));
        }
    }
}
