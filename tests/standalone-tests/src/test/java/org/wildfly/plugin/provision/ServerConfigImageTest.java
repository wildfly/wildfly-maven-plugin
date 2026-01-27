/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import javax.inject.Inject;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugin.Mojo;
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
@DisabledOnOs(OS.WINDOWS)
public class ServerConfigImageTest extends AbstractProjectMojoTest {

    @Inject
    private Log log;

    @Test
    @InjectMojo(goal = "image", pom = "image-server-config-pom.xml")
    public void serverConfig(final Mojo imageMojo) throws Exception {
        final String imageName = "wildfly-image-server-config-maven-plugin/testing";
        final String binary = ExecUtil.resolveImageBinary();
        try {
            imageMojo.execute();
            final Path jbossHome = Path.of(TestEnvironment.TEST_PROJECT_TARGET_PATH, "image-server-config");
            assertTrue(TestEnvironment.isValidWildFlyHome(jbossHome),
                    () -> "Invalid JBoss Home directory %s".formatted(jbossHome));

            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            assertTrue(ExecUtil.exec(stdout, binary, "inspect", imageName));
            TestSupport.assertEnvironmentSet(stdout, "SERVER_ARGS=-c=standalone-microprofile.xml");

        } finally {
            ExecUtil.exec(log, binary, "rmi", imageName);
        }
    }
}
