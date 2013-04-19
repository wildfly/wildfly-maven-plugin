package org.jboss.as.plugin.deployment;

import java.io.File;

import org.apache.maven.project.MavenProject;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.plugin.AbstractItTestCase;
import org.jboss.as.plugin.common.ServerOperations;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * deploy mojo testcase.
 *
 * @author <a href="mailto:heinz.wilming@akquinet.de">Heinz Wilming</a>
 *
 */
public class DeployTest extends AbstractItTestCase {
    @ContainerResource
    private ManagementClient managementClient;

    @Test
    public void testDeployWarArchive() throws Exception {

        final MavenProject mavenProject = new MavenProject();
        mavenProject.setPackaging("war");

        final File pom = getPom("deploy-webarchive-pom.xml");

        final AbstractDeployment deployMojo = lookupMojoAndVerify("deploy", pom);

        deployMojo.project = mavenProject;
        deployMojo.execute();

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", "test.war");
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = executeOperation(managementClient.getControllerClient(), op);

        assertEquals("OK", ServerOperations.readResultAsString(result));
    }

}
