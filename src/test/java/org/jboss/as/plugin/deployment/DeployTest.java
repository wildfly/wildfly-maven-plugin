package org.jboss.as.plugin.deployment;

import java.io.File;

import org.apache.maven.project.MavenProject;
import org.jboss.as.plugin.AbstractItTestCase;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * deploy mojo testcase.
 * 
 * @author <a href="mailto:heinz.wilming@akquinet.de">Heinz Wilming</a>
 * 
 */
public class DeployTest extends AbstractItTestCase {

    @Test
    public void testDeployWarArchive() throws Exception {

        MavenProject mavenProject = new MavenProject();
        mavenProject.setPackaging("war");

        File pom = getTestFile("src/test/resources/unit/common/deploy-webarchive-pom.xml");

        AbstractDeployment deployMojo = (AbstractDeployment) lookupMojo("deploy", pom);

        deployMojo.project = mavenProject;
        deployMojo.execute();

        // /deployment=test.war :read-attribute(name=status)

        ModelNode operation = new ModelNode();
        operation.get("operation").set("read-attribute");
        operation.get("name").set("status");
        ModelNode address = operation.get("address");
        address.add("deployment", "test.war");

        ModelNode result = execute(operation);

        String status = result.get("result").asString();

        assertEquals("OK", status);
    }

}
