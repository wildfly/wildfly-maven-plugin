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

import java.io.File;

import org.apache.maven.plugin.Mojo;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.wildfly.plugin.AbstractWildFlyServerMojoTest;
import org.wildfly.plugin.common.ServerOperations;

public class ExecuteCommandsTest extends AbstractWildFlyServerMojoTest {

    @Test
    public void testExecuteCommandsFromScript() throws Exception {

        final File pom = getPom("execute-script-pom.xml");

        final Mojo executeCommandsMojo = rule.lookupMojo("execute-commands", pom);

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
    public void testExecuteCommands() throws Exception {

        final File pom = getPom("execute-commands-pom.xml");

        final Mojo executeCommandsMojo = rule.lookupMojo("execute-commands", pom);

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
    public void testExecuteBatchCommands() throws Exception {

        final File pom = getPom("execute-batch-commands-pom.xml");

        final Mojo executeCommandsMojo = rule.lookupMojo("execute-commands", pom);

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
