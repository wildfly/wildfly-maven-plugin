/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.wildfly.plugin.categories.ChannelsRequired;
import org.wildfly.plugin.categories.SecurityManagerRequired;
import org.wildfly.plugin.tests.AbstractProvisionConfiguredMojoTestCase;
import org.wildfly.plugin.tests.AbstractWildFlyMojoTest;

@Category(ChannelsRequired.class)
public class PackageTest extends AbstractProvisionConfiguredMojoTestCase {

    public PackageTest() {
        super("wildfly-maven-plugin");
    }

    @Test
    public void testPackage() throws Exception {

        final Mojo packageMojo = lookupConfiguredMojo(AbstractWildFlyMojoTest.getPomFile("package-pom.xml").toFile(),
                "package");

        packageMojo.execute();
        Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("packaged-server");
        Assert.assertTrue(Files.exists(jbossHome.resolve("standalone").resolve("configuration").resolve("foo.txt")));
        String[] layers = { "jaxrs-server" };
        String[] excluded = { "deployment-scanner" };
        checkStandaloneWildFlyHome(jbossHome, 1, layers, excluded, true, "org.wildfly.maven.plugin-package-goal",
                "org.wildfly.maven.plugin-package-goal-from-script");
    }

    @Test
    public void testPackageWithChannel() throws Exception {

        final Mojo packageMojo = lookupConfiguredMojo(AbstractWildFlyMojoTest.getPomFile("package-channel-pom.xml").toFile(),
                "package");

        packageMojo.execute();
        Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("packaged-channel-server");
        Assert.assertTrue(Files.exists(jbossHome.resolve("standalone").resolve("configuration").resolve("foo.txt")));
        String[] layers = { "jaxrs-server" };
        String[] excluded = { "deployment-scanner" };
        checkStandaloneWildFlyHome(jbossHome, 1, layers, excluded, true, "org.wildfly.maven.plugin-package-goal",
                "org.wildfly.maven.plugin-package-goal-from-script");
    }

    // This test provisions WildFly 32 which does not boot on Java SE 24 without security manager support.
    @Test
    @Category(SecurityManagerRequired.class)
    public void testPackageWithChannelGlow() throws Exception {

        final Mojo packageMojo = lookupConfiguredMojo(
                AbstractWildFlyMojoTest.getPomFile("package-channel-glow-pom.xml").toFile(),
                "package");

        packageMojo.execute();
        Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("packaged-channel-glow-server");
        Assert.assertTrue(Files.exists(jbossHome.resolve("standalone").resolve("configuration").resolve("foo.txt")));
        String[] layers = { "ee-core-profile-server" };
        checkStandaloneWildFlyHome(jbossHome, 1, layers, null, true, "org.wildfly.maven.plugin-package-goal",
                "org.wildfly.maven.plugin-package-goal-from-script");
    }

    @Test
    public void testDefaultConfigPackage() throws Exception {

        final Mojo packageMojo = lookupConfiguredMojo(
                AbstractWildFlyMojoTest.getPomFile("package-default-config-pom.xml").toFile(), "package");

        packageMojo.execute();
        Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("packaged-default-config-server");
        checkStandaloneWildFlyHome(jbossHome, 0, null, null, true);
        checkDomainWildFlyHome(jbossHome, 0, true);
    }

    @Test
    public void testNoDeploymentPackage() throws Exception {

        final Mojo packageMojo = lookupConfiguredMojo(
                AbstractWildFlyMojoTest.getPomFile("package-no-deployment-pom.xml").toFile(), "package");

        packageMojo.execute();
        Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("packaged-no-dep-server");
        checkStandaloneWildFlyHome(jbossHome, 0, null, null, true);
    }

    @Test
    public void testGlowPackage() throws Exception {

        final Mojo packageMojo = lookupConfiguredMojo(
                AbstractWildFlyMojoTest.getPomFile("package-glow-pom.xml").toFile(), "package");
        String[] layers = { "ee-core-profile-server", "microprofile-openapi" };
        packageMojo.execute();
        Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("packaged-glow-server");
        checkStandaloneWildFlyHome(jbossHome, 1, layers, null, true);
    }

    @Test
    public void testGlowNoDeploymentPackage() throws Exception {

        final Mojo packageMojo = lookupConfiguredMojo(
                AbstractWildFlyMojoTest.getPomFile("package-glow-no-deployment-pom.xml").toFile(), "package");
        String[] layers = { "ee-core-profile-server", "microprofile-openapi" };
        packageMojo.execute();
        Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("packaged-glow-no-deployment-server");
        checkStandaloneWildFlyHome(jbossHome, 0, layers, null, true);
    }

    @Test
    public void testInvalidDeployment() throws Exception {

        final Mojo packageMojo = lookupConfiguredMojo(
                AbstractWildFlyMojoTest.getPomFile("package-invalid-deployment-pom.xml").toFile(), "package");
        try {
            packageMojo.execute();
            throw new Exception("Execution should have failed");
        } catch (MojoExecutionException ex) {
            // XXX OK, expected.
            Assert.assertTrue(ex.getLocalizedMessage().contains("No deployment found with name test-foo.war"));
        }
    }

    @Test
    public void testInvalidDeployment2() throws Exception {

        final Mojo packageMojo = lookupConfiguredMojo(
                AbstractWildFlyMojoTest.getPomFile("package-invalid-deployment2-pom.xml").toFile(), "package");
        try {
            packageMojo.execute();
            throw new Exception("Execution should have failed");
        } catch (MojoExecutionException ex) {
            // XXX OK, expected.
            Assert.assertTrue(ex.getLocalizedMessage().contains("No deployment found with name foo.jar. "
                    + "A runtime-name has been set that indicates that a deployment is expected. "));
        }
    }

    @Test
    public void testNoMultipleDeploymentsPackage() throws Exception {
        final Mojo packageMojo = lookupConfiguredMojo(
                AbstractWildFlyMojoTest.getPomFile("package-no-multiple-deployments-pom.xml").toFile(), "package");
        String[] layers = { "jaxrs-server" };
        packageMojo.execute();
        Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target")
                .resolve("packaged-no-multiple-deployments-server");
        checkStandaloneWildFlyHome(jbossHome, 1, layers, null, true);
    }

    @Test
    public void testMultipleDeploymentsPackage() throws Exception {
        final Mojo packageMojo = lookupConfiguredMojo(
                AbstractWildFlyMojoTest.getPomFile("package-multiple-deployments-pom.xml").toFile(), "package");
        String[] layers = { "jaxrs-server" };
        packageMojo.execute();
        Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("packaged-multiple-deployments-server");
        checkStandaloneWildFlyHome(jbossHome, 2, layers, null, true);
    }

    @Test
    public void testMultipleDeploymentsMissingPackage() throws Exception {
        final Mojo packageMojo = lookupConfiguredMojo(
                AbstractWildFlyMojoTest.getPomFile("package-multiple-deployments-missing-pom.xml").toFile(), "package");
        String[] layers = { "jaxrs-server" };
        packageMojo.execute();
        Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target")
                .resolve("packaged-multiple-deployments-missing-server");
        checkStandaloneWildFlyHome(jbossHome, 1, layers, null, true);
    }

    @Test
    public void testGrpcPackage() throws Exception {
        final Mojo packageMojo = lookupConfiguredMojo(AbstractWildFlyMojoTest.getPomFile("package-grpc-pom.xml").toFile(),
                "package");
        packageMojo.execute();
        Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("packaged-grpc-server");
        Assert.assertTrue(Files.exists(jbossHome.resolve("standalone").resolve("configuration").resolve("foo.txt")));
        String[] layers = { "jaxrs-server", "grpc" };
        checkStandaloneWildFlyHome(jbossHome, 1, layers, null, true, "org.wildfly.maven.plugin-package-goal",
                "org.wildfly.maven.plugin-package-goal-from-script");
    }
}
