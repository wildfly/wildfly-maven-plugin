/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.apache.maven.plugin.Mojo;
import org.jboss.as.cli.CommandLineException;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.tests.AbstractWildFlyServerMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;

/**
 * @author <a href="mailto:sven-torben@sven-torben.de">Sven-Torben Janus</a>
 */
public class FailOnErrorTest extends AbstractWildFlyServerMojoTest {

    @Test
    public void testExecuteCommandsFailOnError() throws Exception {

        final Mojo executeCommandsMojo = lookupMojoAndVerify("execute-commands", "execute-commands-failOnError-pom.xml");

        try {
            executeCommandsMojo.execute();
            fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException e) {
            assertEquals(CommandLineException.class, e.getCause().getClass());
        }
        final ModelNode address = ServerOperations.createAddress("system-property", "propertyFailOnError");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = executeOperation(op);
        try {
            assertEquals("initial value", ServerOperations.readResultAsString(result));
        } finally {
            // Remove the system property
            executeOperation(ServerOperations.createRemoveOperation(address));
        }
    }

    @Test
    public void testExecuteCommandsLocalFailOnError() throws Exception {

        final Mojo executeCommandsMojo = lookupMojoAndVerify("execute-commands", "execute-commands-failOnError-pom.xml");
        // Set the JBoss home field so commands will be executed in a new process
        setValue(executeCommandsMojo, "jbossHome", TestEnvironment.WILDFLY_HOME.toString());

        try {
            executeCommandsMojo.execute();
            fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException ignore) {
        }
        final ModelNode address = ServerOperations.createAddress("system-property", "propertyFailOnError");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = executeOperation(op);
        try {
            assertEquals("initial value", ServerOperations.readResultAsString(result));
        } finally {
            // Remove the system property
            executeOperation(ServerOperations.createRemoveOperation(address));
        }
    }

    @Test
    public void testExecuteCommandsContinueOnError() throws Exception {

        final Mojo executeCommandsMojo = lookupMojoAndVerify("execute-commands", "execute-commands-continueOnError-pom.xml");

        executeCommandsMojo.execute();

        // Read the attribute
        final ModelNode address = ServerOperations.createAddress("system-property", "propertyContinueOnError");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = executeOperation(op);

        try {
            assertEquals("continue on error", ServerOperations.readResultAsString(result));
        } finally {
            // Clean up the property
            executeOperation(ServerOperations.createRemoveOperation(address));
        }
    }

    @Test
    public void testExecuteCommandsLocalContinueOnError() throws Exception {

        final Mojo executeCommandsMojo = lookupMojoAndVerify("execute-commands", "execute-commands-continueOnError-pom.xml");
        // Set the JBoss home field so commands will be executed in a new process
        setValue(executeCommandsMojo, "jbossHome", TestEnvironment.WILDFLY_HOME.toString());

        executeCommandsMojo.execute();

        // Read the attribute
        final ModelNode address = ServerOperations.createAddress("system-property", "propertyContinueOnError");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = executeOperation(op);

        try {
            assertEquals("continue on error", ServerOperations.readResultAsString(result));
        } finally {
            // Clean up the property
            executeOperation(ServerOperations.createRemoveOperation(address));
        }
    }

    @Test
    public void testExecuteCommandScriptFailOnError() throws Exception {

        final Mojo executeCommandsMojo = lookupMojoAndVerify("execute-commands", "execute-script-failOnError-pom.xml");

        try {
            executeCommandsMojo.execute();
            fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException e) {
            assertEquals(IllegalArgumentException.class, e.getCause().getClass());
            assertEquals(CommandLineException.class, e.getCause().getCause().getClass());
        }
        final ModelNode address = ServerOperations.createAddress("system-property", "scriptFailOnError");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = executeOperation(op);
        try {
            assertEquals("initial value", ServerOperations.readResultAsString(result));
        } finally {
            // Remove the system property
            executeOperation(ServerOperations.createRemoveOperation(address));
        }
    }

    @Test
    public void testExecuteCommandScriptContinueOnError() throws Exception {

        final Mojo executeCommandsMojo = lookupMojoAndVerify("execute-commands", "execute-script-continueOnError-pom.xml");

        executeCommandsMojo.execute();

        // Read the attribute
        final ModelNode address = ServerOperations.createAddress("system-property", "scriptContinueOnError");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = executeOperation(op);

        try {
            assertEquals("continue on error", ServerOperations.readResultAsString(result));
        } finally {
            // Clean up the property
            executeOperation(ServerOperations.createRemoveOperation(address));
        }
    }

}
