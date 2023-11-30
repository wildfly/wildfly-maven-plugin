/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import java.nio.file.Path;

import org.apache.maven.plugin.Mojo;
import org.junit.Test;
import org.wildfly.plugin.tests.AbstractProvisionConfiguredMojoTestCase;
import org.wildfly.plugin.tests.AbstractWildFlyMojoTest;

public class ProvisionTest extends AbstractProvisionConfiguredMojoTestCase {

    public ProvisionTest() {
        super("wildfly-maven-plugin");
    }

    @Test
    public void testProvision() throws Exception {

        final Mojo provisionMojo = lookupConfiguredMojo(AbstractWildFlyMojoTest.getPomFile("provision-pom.xml").toFile(),
                "provision");

        provisionMojo.execute();
        Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("server");
        checkDomainWildFlyHome(jbossHome, 0, false);
    }

}
