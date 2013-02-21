package org.jboss.as.plugin.cli;

import java.io.File;
import java.util.Properties;

import org.apache.maven.plugin.Mojo;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.plugin.AbstractItTestCase;
import org.jboss.as.plugin.common.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

public class ExecuteCommandsTest extends AbstractItTestCase {
    @ContainerResource
    private ManagementClient managementClient;

    @Test
    public void testExecuteCommandsFromScript() throws Exception {

        final File pom = getPom("execute-script-pom.xml");

        final Mojo executeCommandsMojo = lookupMojo("execute-commands", pom);

        executeCommandsMojo.execute();

        // Create the address
        final ModelNode address = Operations.createAddress("system-property", "org.jboss.maven.plugin");
        final ModelNode op = Operations.createReadAttributeOperation(address, "value");

        final ModelNode result = executeOperation(managementClient.getControllerClient(), op);
        // The script adds a new system property that's value should be true
        assertEquals("true", Operations.readResultAsString(result));

    }

    @Test
    public void testExecuteCommands() throws Exception {

        final File pom = getPom("execute-commands-pom.xml");

        final Mojo executeCommandsMojo = lookupMojo("execute-commands", pom);

        executeCommandsMojo.execute();

        // Read a known attribute
        final ModelNode op = Operations.createReadAttributeOperation("launch-type");
        final ModelNode result = executeOperation(managementClient.getControllerClient(), op);
        assertEquals("STANDALONE", Operations.readResultAsString(result));
    }
}
