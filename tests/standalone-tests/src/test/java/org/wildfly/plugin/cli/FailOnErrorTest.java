/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.jboss.dmr.ModelNode;
import org.junit.jupiter.api.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.tests.AbstractProjectMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;
import org.wildfly.plugin.tools.TestSupport;
import org.wildfly.plugin.tools.server.ServerManager;
import org.wildfly.testing.junit.extension.annotation.ServerResource;
import org.wildfly.testing.junit.extension.annotation.WildFlyTest;

/**
 * @author <a href="mailto:sven-torben@sven-torben.de">Sven-Torben Janus</a>
 */
@MojoTest(realRepositorySession = true)
@WildFlyTest
@Basedir(TestEnvironment.TEST_PROJECT_PATH)
public class FailOnErrorTest extends AbstractProjectMojoTest {

    @ServerResource
    private ServerManager serverManager;

    @Test
    @InjectMojo(goal = "execute-commands", pom = "execute-commands-failOnError-pom.xml")
    public void testExecuteCommandsFailOnError(final Mojo executeCommandsMojo) throws Exception {
        final MojoExecutionException ex = assertThrows(MojoExecutionException.class,
                executeCommandsMojo::execute);
        assertEquals("org.jboss.as.cli.CommandLineException", ex.getCause().getClass().getName());
        final ModelNode address = ServerOperations.createAddress("system-property", "propertyFailOnError");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = serverManager.executeOperation(op);
        try {
            assertEquals("initial value", result.asString());
        } finally {
            // Remove the system property
            serverManager.executeOperation(ServerOperations.createRemoveOperation(address));
        }
    }

    @Test
    @InjectMojo(goal = "execute-commands", pom = "execute-commands-fork-op-failOnError-pom.xml")
    public void testExecuteCommandsForkOpFailOnError(final Mojo executeCommandsMojo) throws Exception {
        assertThrows(MojoExecutionException.class, executeCommandsMojo::execute);

        final ModelNode address = ServerOperations.createAddress("system-property", "propertyFailOnError");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = serverManager.executeOperation(op);
        try {
            assertEquals("initial value", result.asString());
        } finally {
            // Remove the system property
            serverManager.executeOperation(ServerOperations.createRemoveOperation(address));
        }
    }

    @Test
    @InjectMojo(goal = "execute-commands", pom = "execute-commands-fork-cmd-failOnError-pom.xml")
    public void testExecuteCommandsForkCmdFailOnError(final Mojo executeCommandsMojo) throws Exception {
        assertThrows(MojoExecutionException.class, executeCommandsMojo::execute);
        // Read the attribute
        final ModelNode address = ServerOperations.createAddress("system-property", "propertyFailOnError.in.try");
        final ModelNode result = serverManager
                .executeOperation(ServerOperations.createReadAttributeOperation(address, "value"));

        try {
            assertEquals("inside catch", result.asString());
        } finally {
            // Remove the system property
            serverManager.executeOperation(ServerOperations.createRemoveOperation(address));
        }
    }

    @Test
    @InjectMojo(goal = "execute-commands", pom = "execute-commands-continueOnError-pom.xml")
    public void testExecuteCommandsContinueOnError(final Mojo executeCommandsMojo) throws Exception {
        executeCommandsMojo.execute();

        // Read the attribute
        final ModelNode address = ServerOperations.createAddress("system-property", "propertyContinueOnError");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = serverManager.executeOperation(op);

        try {
            assertEquals("continue on error", result.asString());
        } finally {
            // Clean up the property
            serverManager.executeOperation(ServerOperations.createRemoveOperation(address));
        }
    }

    @Test
    @InjectMojo(goal = "execute-commands", pom = "execute-commands-fork-continueOnError-pom.xml")
    public void testExecuteCommandsForkContinueOnError(final Mojo executeCommandsMojo) throws Exception {

        executeCommandsMojo.execute();

        // Read the attribute
        final ModelNode address = ServerOperations.createAddress("system-property", "propertyContinueOnError");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = serverManager.executeOperation(op);

        // Read the attribute
        final ModelNode inTryAddress = ServerOperations.createAddress("system-property", "propertyContinueOnError.in.try");
        final ModelNode inTryResult = serverManager
                .executeOperation(ServerOperations.createReadAttributeOperation(inTryAddress, "value"));

        try {
            assertEquals("continue on error", result.asString());
            assertEquals("inside catch", inTryResult.asString());

            // The invalid module directory should not exist
            final Path baseModuleDir = Paths.get(TestEnvironment.WILDFLY_HOME.toString(), "modules", "org", "wildfly", "plugin",
                    "tests");
            final Path invalidModuleDir = baseModuleDir.resolve("invalid");
            assertTrue(Files.notExists(invalidModuleDir), String.format("Expected %s to not exist.", invalidModuleDir));

            // The valid module directory should exist
            final Path moduleDir = baseModuleDir.resolve("main");
            assertTrue(Files.exists(moduleDir), String.format("Expected %s to exist.", moduleDir));
            assertTrue(Files.exists(moduleDir.resolve("module.xml")), "Expected the module.xml to exist in " + moduleDir);
            assertTrue(Files.exists(moduleDir.resolve("test.jar")), "Expected the test.jar to exist in " + moduleDir);
        } finally {
            // Clean up the property
            serverManager.executeOperation(ServerOperations.createRemoveOperation(address));
            serverManager.executeOperation(ServerOperations.createRemoveOperation(inTryAddress));

            // Remove the module
            TestSupport.deleteRecursively(TestEnvironment.WILDFLY_HOME.resolve("modules").resolve("org"));
        }
    }

    @Test
    @InjectMojo(goal = "execute-commands", pom = "execute-script-failOnError-pom.xml")
    public void testExecuteCommandScriptFailOnError(final Mojo executeCommandsMojo) throws Exception {
        final MojoExecutionException ex = assertThrows(MojoExecutionException.class,
                executeCommandsMojo::execute);
        assertEquals("org.jboss.as.cli.CommandLineException", ex.getCause().getClass().getName());
        final ModelNode address = ServerOperations.createAddress("system-property", "scriptFailOnError");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = serverManager.executeOperation(op);
        try {
            assertEquals("initial value", result.asString());
        } finally {
            // Remove the system property
            serverManager.executeOperation(ServerOperations.createRemoveOperation(address));
        }
    }

    @Test
    @InjectMojo(goal = "execute-commands", pom = "execute-script-continueOnError-pom.xml")
    public void testExecuteCommandScriptContinueOnError(final Mojo executeCommandsMojo) throws Exception {
        executeCommandsMojo.execute();

        // Read the attribute
        final ModelNode address = ServerOperations.createAddress("system-property", "scriptContinueOnError");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = serverManager.executeOperation(op);

        try {
            assertEquals("continue on error", result.asString());
        } finally {
            // Clean up the property
            serverManager.executeOperation(ServerOperations.createRemoveOperation(address));
        }
    }

}
