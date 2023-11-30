/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.Mojo;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.tests.AbstractWildFlyServerMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;

public class ExecuteCommandsTest extends AbstractWildFlyServerMojoTest {

    @Test
    public void testExecuteCommandsFromScript() throws Exception {

        final Mojo executeCommandsMojo = lookupMojoAndVerify("execute-commands", "execute-script-pom.xml");
        setValidSession(executeCommandsMojo);
        executeCommandsMojo.execute();

        // Create the address
        final ModelNode address = ServerOperations.createAddress("system-property", "org.wildfly.maven.plugin");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");

        final ModelNode result = executeOperation(op);
        // The script adds a new system property that's value should be true
        assertEquals("true", ServerOperations.readResultAsString(result));

        // Clean up the property
        executeOperation(ServerOperations.createRemoveOperation(address));

    }

    @Test
    public void testExecuteCommandsFromOfflineScript() throws Exception {

        final Mojo executeCommandsMojo = lookupMojoAndVerify("execute-commands", "execute-offline-script-pom.xml");
        setValue(executeCommandsMojo, "jbossHome", System.getProperty("jboss.home"));
        executeCommandsMojo.execute();
    }

    @Test
    public void testExecuteCommands() throws Exception {

        final Mojo executeCommandsMojo = lookupMojoAndVerify("execute-commands", "execute-commands-pom.xml");
        setValidSession(executeCommandsMojo);
        executeCommandsMojo.execute();

        // Read the attribute
        ModelNode address = ServerOperations.createAddress("system-property", "org.wildfly.maven.plugin-exec-cmd");
        ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        ModelNode result = executeOperation(op);
        assertEquals("true", ServerOperations.readResultAsString(result));

        // Clean up the property
        executeOperation(ServerOperations.createRemoveOperation(address));

        // Read the attribute
        address = ServerOperations.createAddress("system-property", "property2");
        op = ServerOperations.createReadAttributeOperation(address, "value");
        result = executeOperation(op);
        assertEquals("property 2", ServerOperations.readResultAsString(result));

        // Clean up the property
        executeOperation(ServerOperations.createRemoveOperation(address));
    }

    @Test
    public void testExecuteOfflineCommands() throws Exception {

        final Mojo executeCommandsMojo = lookupMojoAndVerify("execute-commands", "execute-commands-offline-pom.xml");
        setValue(executeCommandsMojo, "jbossHome", System.getProperty("jboss.home"));
        executeCommandsMojo.execute();
    }

    @Test
    public void testExecuteForkCommands() throws Exception {

        final Mojo executeCommandsMojo = lookupMojoAndVerify("execute-commands", "execute-commands-fork-pom.xml");
        setValue(executeCommandsMojo, "jbossHome", TestEnvironment.WILDFLY_HOME.toString());

        executeCommandsMojo.execute();

        // Read the attribute
        ModelNode address = ServerOperations.createAddress("system-property", "org.wildfly.maven.plugin-fork-cmd");
        ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        ModelNode result = executeOperation(op);
        assertEquals("true", ServerOperations.readResultAsString(result));

        // Ensure the module has been added
        final Path moduleDir = Paths.get(TestEnvironment.WILDFLY_HOME.toString(), "modules", "org", "wildfly", "plugin",
                "tests", "main");
        assertTrue(String.format("Expected %s to exist.", moduleDir), Files.exists(moduleDir));
        assertTrue("Expected the module.xml to exist in " + moduleDir, Files.exists(moduleDir.resolve("module.xml")));
        assertTrue("Expected the test.jar to exist in " + moduleDir, Files.exists(moduleDir.resolve("test.jar")));

        // Clean up the property
        executeOperation(ServerOperations.createRemoveOperation(address));

        // Read the attribute
        address = ServerOperations.createAddress("system-property", "fork-command");
        op = ServerOperations.createReadAttributeOperation(address, "value");
        result = executeOperation(op);
        assertEquals("set", ServerOperations.readResultAsString(result));

        // Clean up the property
        executeOperation(ServerOperations.createRemoveOperation(address));

        // Remove the module
        deleteRecursively(TestEnvironment.WILDFLY_HOME.resolve("modules").resolve("org"));
    }

    @Test
    public void testExecuteBatchCommands() throws Exception {

        final Mojo executeCommandsMojo = lookupMojoAndVerify("execute-commands", "execute-batch-commands-pom.xml");
        setValidSession(executeCommandsMojo);
        executeCommandsMojo.execute();

        // Read the attribute
        final ModelNode address = ServerOperations.createAddress("system-property", "org.wildfly.maven.plugin-batch");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = executeOperation(op);
        assertEquals("true", ServerOperations.readResultAsString(result));

        // Clean up the property
        executeOperation(ServerOperations.createRemoveOperation(address));
    }
}
