/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.jboss.as.controller.client.ModelControllerClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.wildfly.plugin.tests.AbstractProjectMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;
import org.wildfly.plugin.tools.server.ServerManager;
import org.wildfly.plugin.tools.server.StandaloneConfiguration;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@MojoTest(realRepositorySession = true)
@Basedir("target/test-classes/test-project")
public class StandaloneServerFunctionMojoTest extends AbstractProjectMojoTest {

    @Test
    @InjectMojo(goal = "start", pom = "start-pom.xml")
    public void testStartStandalone(final StartMojo mojo) throws Exception {
        mojo.execute();
        // Verify the server is running
        try (ServerManager serverManager = createServerManager()) {
            Assertions.assertTrue(serverManager.isRunning(), "The start goal did not start the server.");
            Assertions.assertFalse(serverManager.containerDescription().isDomain(),
                    "This should be a standalone server, but found a domain server.");
        }
    }

    @Test
    @InjectMojo(goal = "shutdown", pom = "shutdown-pom.xml")
    public void testShutdownStandalone(final ShutdownMojo stopMojo) throws Exception {
        try (ServerManager serverManager = ServerManager.of(
                StandaloneConfiguration.create(StandaloneCommandBuilder.of(TestEnvironment.WILDFLY_HOME)))) {
            serverManager.start();
            // First check the server is running
            Assertions.assertTrue(serverManager.isRunning(), "The server is not currently running.");
            // Attempt to stop
            stopMojo.execute();
            // Verify the server is running
            Assertions.assertFalse(serverManager.isRunning(), "The start goal did not start the server.");
        }
    }

    @Test
    @InjectMojo(goal = "start", pom = "start-pom-with-users.xml")
    public void testStartAndAddUserStandalone(final StartMojo mojo) throws Exception {
        // The MOJO lookup replaces a configured add-users configuration with a default value so we need to manually
        // create and insert the field for testing
        mojo.execute();
        try (ServerManager serverManager = createServerManager()) {
            // Verify the server is running
            Assertions.assertTrue(serverManager.isRunning(), "The start goal did not start the server.");

            final Path standaloneConfigDir = TestEnvironment.WILDFLY_HOME.resolve("standalone")
                    .resolve("configuration");

            // Check the management users
            final Path mgmtUsers = standaloneConfigDir.resolve("mgmt-users.properties");
            Assertions.assertTrue(Files.exists(mgmtUsers), "File " + mgmtUsers + " does not exist");
            Assertions.assertTrue(fileContains(mgmtUsers, "admin="),
                    "User admin was not added to the mgmt-user.properties file");

            // Check the management users
            final Path mgmtGroups = standaloneConfigDir.resolve("mgmt-groups.properties");
            Assertions.assertTrue(Files.exists(mgmtGroups), "File " + mgmtGroups + " does not exist");
            Assertions.assertTrue(fileContains(mgmtGroups, "admin=admin"),
                    "User admin was not added to the mgmt-groups.properties file");

            // Check the application users
            final Path appUsers = standaloneConfigDir.resolve("application-users.properties");
            Assertions.assertTrue(Files.exists(appUsers), "File " + appUsers + " does not exist");
            Assertions.assertTrue(fileContains(appUsers, "user="),
                    "User user was not added to the application-user.properties file");

            // Check the management users
            final Path appGroups = standaloneConfigDir.resolve("application-roles.properties");
            Assertions.assertTrue(Files.exists(appGroups), "File " + appGroups + " does not exist");
            Assertions.assertTrue(fileContains(appGroups, "user=user,mgmt"),
                    "User user was not added to the application-roles.properties file");
        }
    }

    private ServerManager createServerManager() throws Exception {
        return ServerManager.builder().client(createClient()).process(ServerManager.findProcess().orElse(null))
                .shutdownOnClose(true).standalone();
    }

    private static boolean fileContains(final Path path, final String text) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ModelControllerClient createClient() throws UnknownHostException {
        return ModelControllerClient.Factory.create(TestEnvironment.HOSTNAME, TestEnvironment.PORT);
    }
}
