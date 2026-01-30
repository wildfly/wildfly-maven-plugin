/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import java.nio.file.Path;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.junit.jupiter.api.Test;
import org.wildfly.plugin.tests.AbstractProjectMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;

@MojoTest(realRepositorySession = true)
@Basedir(TestEnvironment.TEST_PROJECT_PATH)
public class ProvisionTest extends AbstractProjectMojoTest {

    @Test
    @InjectMojo(goal = "provision", pom = "provision-pom.xml")
    public void testProvision(final ProvisionServerMojo provisionMojo) throws Exception {
        provisionMojo.execute();
        final Path jbossHome = Path.of(TestEnvironment.TEST_PROJECT_TARGET_PATH, "server");
        TestEnvironment.checkDomainWildFlyHome(jbossHome, 0, false);
    }

}
