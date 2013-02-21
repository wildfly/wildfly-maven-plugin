package org.jboss.as.plugin.cli;

import java.io.File;

import org.apache.maven.plugin.Mojo;
import org.jboss.as.plugin.AbstractItTestCase;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

public class ExecuteCommandsTest extends AbstractItTestCase {

    @Test
    public void testExecuteCommandsFromScript() throws Exception {

        File pom = getTestFile("src/test/resources/unit/common/execute-script-pom.xml");

        Mojo executeCommandsMojo = lookupMojo("execute-commands", pom);

        executeCommandsMojo.execute();

        ModelNode operation = new ModelNode();
        operation.get("operation").set("read-attribute");
        operation.get("name").set("default-timeout");
        ModelNode address = operation.get("address");
        address.add("subsystem", "transactions");

        ModelNode result = execute(operation);

        int timeout = result.get("result").asInt();

        assertEquals(600, timeout);

    }

    @Test
    public void testExecuteCommands() throws Exception {

        File pom = getTestFile("src/test/resources/unit/common/execute-commands-pom.xml");

        Mojo executeCommandsMojo = lookupMojo("execute-commands", pom);

        executeCommandsMojo.execute();

        ModelNode operation = new ModelNode();
        operation.get("operation").set("read-attribute");
        operation.get("name").set("default-timeout");
        ModelNode address = operation.get("address");
        address.add("subsystem", "transactions");

        ModelNode result = execute(operation);

        int timeout = result.get("result").asInt();

        assertEquals(600, timeout);
    }
}
