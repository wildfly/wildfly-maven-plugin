/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugin.Mojo;
import org.jboss.dmr.ModelNode;
import org.junit.jupiter.api.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.tests.AbstractProjectMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;
import org.wildfly.plugin.tools.TestSupport;
import org.wildfly.plugin.tools.server.ServerManager;
import org.wildfly.testing.junit.extension.annotation.ServerResource;
import org.wildfly.testing.junit.extension.annotation.WildFlyTest;

@MojoTest(realRepositorySession = true)
@WildFlyTest
@Basedir(TestEnvironment.TEST_PROJECT_PATH)
public class ExecuteCommandsTest extends AbstractProjectMojoTest {

    @ServerResource
    private ServerManager serverManager;

    @Test
    @InjectMojo(goal = "execute-commands", pom = "execute-script-pom.xml")
    public void testExecuteCommandsFromScript(final ExecuteCommandsMojo executeCommandsMojo) throws Exception {
        executeCommandsMojo.execute();

        // Create the address
        final ModelNode address = ServerOperations.createAddress("system-property", "org.wildfly.maven.plugin");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");

        final ModelNode result = serverManager.executeOperation(op);
        // The script adds a new system property that's value should be true
        assertEquals("true", result.asString());

        // Clean up the property
        serverManager.executeOperation(ServerOperations.createRemoveOperation(address));

    }

    @Test
    @InjectMojo(goal = "execute-commands", pom = "execute-offline-script-pom.xml")
    public void testExecuteCommandsFromOfflineScript(final Mojo executeCommandsMojo) throws Exception {
        executeCommandsMojo.execute();
    }

    @Test
    @InjectMojo(goal = "execute-commands", pom = "execute-commands-pom.xml")
    public void testExecuteCommands(final Mojo executeCommandsMojo) throws Exception {
        executeCommandsMojo.execute();

        // Read the attribute
        ModelNode address = ServerOperations.createAddress("system-property", "org.wildfly.maven.plugin-exec-cmd");
        ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        ModelNode result = serverManager.executeOperation(op);
        assertEquals("true", result.asString());

        // Clean up the property
        serverManager.executeOperation(ServerOperations.createRemoveOperation(address));

        // Read the attribute
        address = ServerOperations.createAddress("system-property", "property2");
        op = ServerOperations.createReadAttributeOperation(address, "value");
        result = serverManager.executeOperation(op);
        assertEquals("property 2", result.asString());

        // Clean up the property
        serverManager.executeOperation(ServerOperations.createRemoveOperation(address));
    }

    @Test
    @InjectMojo(goal = "execute-commands", pom = "execute-commands-offline-pom.xml")
    public void testExecuteOfflineCommands(final Mojo executeCommandsMojo) throws Exception {
        executeCommandsMojo.execute();
    }

    @Test
    @InjectMojo(goal = "execute-commands", pom = "execute-commands-fork-pom.xml")
    public void testExecuteForkCommands(final Mojo executeCommandsMojo) throws Exception {
        executeCommandsMojo.execute();

        // Read the attribute
        ModelNode address = ServerOperations.createAddress("system-property", "org.wildfly.maven.plugin-fork-cmd");
        ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        ModelNode result = serverManager.executeOperation(op);
        assertEquals("true", result.asString());

        // Ensure the module has been added
        final Path moduleDir = Paths.get(TestEnvironment.WILDFLY_HOME.toString(), "modules", "org", "wildfly", "plugin",
                "tests", "main");
        assertTrue(Files.exists(moduleDir), String.format("Expected %s to exist.", moduleDir));
        assertTrue(Files.exists(moduleDir.resolve("module.xml")), "Expected the module.xml to exist in " + moduleDir);
        assertTrue(Files.exists(moduleDir.resolve("test.jar")), "Expected the test.jar to exist in " + moduleDir);

        // Clean up the property
        serverManager.executeOperation(ServerOperations.createRemoveOperation(address));

        // Read the attribute
        address = ServerOperations.createAddress("system-property", "fork-command");
        op = ServerOperations.createReadAttributeOperation(address, "value");
        result = serverManager.executeOperation(op);
        assertEquals("set", result.asString());

        // Clean up the property
        serverManager.executeOperation(ServerOperations.createRemoveOperation(address));

        // Remove the module
        TestSupport.deleteRecursively(TestEnvironment.WILDFLY_HOME.resolve("modules").resolve("org"));
    }

    @Test
    @InjectMojo(goal = "execute-commands", pom = "execute-batch-commands-pom.xml")
    public void testExecuteBatchCommands(final Mojo executeCommandsMojo) throws Exception {
        executeCommandsMojo.execute();

        // Read the attribute
        final ModelNode address = ServerOperations.createAddress("system-property", "org.wildfly.maven.plugin-batch");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = serverManager.executeOperation(op);
        assertEquals("true", result.asString());

        // Clean up the property
        serverManager.executeOperation(ServerOperations.createRemoveOperation(address));
    }
}
