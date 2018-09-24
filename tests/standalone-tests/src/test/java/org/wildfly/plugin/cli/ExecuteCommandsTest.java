/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

import static org.junit.Assert.*;

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
    public void testExecuteLocalCommands() throws Exception {

        final Mojo executeCommandsMojo = lookupMojoAndVerify("execute-commands", "execute-commands-local-pom.xml");
        setValue(executeCommandsMojo, "jbossHome", TestEnvironment.WILDFLY_HOME.toString());

        executeCommandsMojo.execute();

        // Read the attribute
        ModelNode address = ServerOperations.createAddress("system-property", "org.wildfly.maven.plugin-local-cmd");
        ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        ModelNode result = executeOperation(op);
        assertEquals("true", ServerOperations.readResultAsString(result));

        // Clean up the property
        executeOperation(ServerOperations.createRemoveOperation(address));


        // Read the attribute
        address = ServerOperations.createAddress("system-property", "local-command");
        op = ServerOperations.createReadAttributeOperation(address, "value");
        result = executeOperation(op);
        assertEquals("set", ServerOperations.readResultAsString(result));

        // Clean up the property
        executeOperation(ServerOperations.createRemoveOperation(address));
    }

    @Test
    public void testExecuteBatchCommands() throws Exception {

        final Mojo executeCommandsMojo = lookupMojoAndVerify("execute-commands", "execute-batch-commands-pom.xml");

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
