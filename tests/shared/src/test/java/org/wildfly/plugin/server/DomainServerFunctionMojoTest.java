/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.server;

import java.net.UnknownHostException;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.jboss.as.controller.client.ModelControllerClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.core.launcher.DomainCommandBuilder;
import org.wildfly.plugin.tests.AbstractProjectMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;
import org.wildfly.plugin.tools.server.DomainConfiguration;
import org.wildfly.plugin.tools.server.DomainManager;
import org.wildfly.plugin.tools.server.ServerManager;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@MojoTest(realRepositorySession = true)
@Basedir("target/test-classes/test-project")
public class DomainServerFunctionMojoTest extends AbstractProjectMojoTest {

    @Test
    @InjectMojo(goal = "start", pom = "start-domain-pom.xml")
    public void testStartDomain(final StartMojo mojo) throws Exception {
        mojo.execute();
        try (
                DomainManager serverManager = ServerManager.builder().client(createClient()).shutdownOnClose(true).domain()) {
            // Verify the server is running
            Assertions.assertTrue(serverManager.isRunning(), "The start goal did not start the server.");
            Assertions.assertTrue(serverManager.containerDescription().isDomain(),
                    "This should be a domain server server, but found a standalone server.");
        }
    }

    @Test
    @InjectMojo(goal = "shutdown", pom = "shutdown-pom.xml")
    public void testShutdownDomain(final ShutdownMojo stopMojo) throws Exception {
        try (DomainManager serverManager = ServerManager.of(DomainConfiguration.create(
                DomainCommandBuilder.of(TestEnvironment.WILDFLY_HOME)))) {
            serverManager.start();
            // First check the server is running
            Assertions.assertTrue(serverManager.isRunning(), "The server is not currently running.");

            // Attempt to stop
            stopMojo.execute();
            // Verify the server is running
            Assertions.assertFalse(serverManager.isRunning(), "The start goal did not start the server.");
        }
    }

    private static ModelControllerClient createClient() throws UnknownHostException {
        return ModelControllerClient.Factory.create(TestEnvironment.HOSTNAME, TestEnvironment.PORT);
    }
}
