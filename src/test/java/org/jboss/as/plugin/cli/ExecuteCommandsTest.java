package org.jboss.as.plugin.cli;

import java.io.File;

import org.apache.maven.plugin.Mojo;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.plugin.AbstractItTestCase;
import org.jboss.as.plugin.common.ServerOperations;
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
        final ModelNode address = ServerOperations.createAddress("system-property", "org.jboss.maven.plugin");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");

        final ModelNode result = executeOperation(managementClient.getControllerClient(), op);
        // The script adds a new system property that's value should be true
        assertEquals("true", ServerOperations.readResultAsString(result));

        // Clean up the property
        executeOperation(managementClient.getControllerClient(), ServerOperations.createRemoveOperation(address));

    }

    @Test
    public void testExecuteCommands() throws Exception {

        final File pom = getPom("execute-commands-pom.xml");

        final Mojo executeCommandsMojo = lookupMojo("execute-commands", pom);

        executeCommandsMojo.execute();

        // Read the attribute
        ModelNode address = ServerOperations.createAddress("system-property", "org.jboss.maven.plugin-exec-cmd");
        ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        ModelNode result = executeOperation(managementClient.getControllerClient(), op);
        assertEquals("true", ServerOperations.readResultAsString(result));

        // Clean up the property
        executeOperation(managementClient.getControllerClient(), ServerOperations.createRemoveOperation(address));


        // Read the attribute
        address = ServerOperations.createAddress("system-property", "property2");
        op = ServerOperations.createReadAttributeOperation(address, "value");
        result = executeOperation(managementClient.getControllerClient(), op);
        assertEquals("property 2", ServerOperations.readResultAsString(result));

        // Clean up the property
        executeOperation(managementClient.getControllerClient(), ServerOperations.createRemoveOperation(address));
    }

    @Test
    public void testExecuteBatchCommands() throws Exception {

        final File pom = getPom("execute-batch-commands-pom.xml");

        final Mojo executeCommandsMojo = lookupMojo("execute-commands", pom);

        executeCommandsMojo.execute();

        // Read the attribute
        final ModelNode address = ServerOperations.createAddress("system-property", "org.jboss.maven.plugin-batch");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "value");
        final ModelNode result = executeOperation(managementClient.getControllerClient(), op);
        assertEquals("true", ServerOperations.readResultAsString(result));

        // Clean up the property
        executeOperation(managementClient.getControllerClient(), ServerOperations.createRemoveOperation(address));
    }
}
