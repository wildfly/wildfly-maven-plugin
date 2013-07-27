package org.jboss.as.plugin.deployment;

import org.apache.maven.project.MavenProject;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.plugin.AbstractItTestCase;
import org.jboss.as.plugin.common.DeploymentExecutionException;
import org.jboss.as.plugin.common.DeploymentFailureException;
import org.jboss.as.plugin.common.ServerOperations;
import org.jboss.as.plugin.deployment.Deployment.Status;
import org.jboss.as.plugin.deployment.Deployment.Type;
import org.jboss.as.plugin.deployment.standalone.StandaloneDeployment;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * deploy mojo testcase.
 *
 * @author <a href="mailto:heinz.wilming@akquinet.de">Heinz Wilming</a>
 */
public class DeployOnlyTest extends AbstractItTestCase {
    @ContainerResource
    private ManagementClient managementClient;

    @Test
    public void testDeploy() throws Exception {

        // Make sure the archive is not deployed
        if (isDeployed(DEPLOYMENT_NAME)) {
            undeploy(DEPLOYMENT_NAME);
        }

        final MavenProject mavenProject = new MavenProject();
        mavenProject.setPackaging("war");

        final File pom = getPom("deploy-only-webarchive-pom.xml");

        final AbstractDeployment deployMojo = lookupMojoAndVerify("deploy-only", pom);

        deployMojo.project = mavenProject;
        deployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", isDeployed(DEPLOYMENT_NAME));

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = executeOperation(managementClient.getControllerClient(), op);

        assertEquals("OK", ServerOperations.readResultAsString(result));
    }

    @Test
    public void testDeployWithCommands() throws Exception {

        // Make sure the archive is not deployed
        if (isDeployed(DEPLOYMENT_NAME)) {
            undeploy(DEPLOYMENT_NAME);
        }

        final MavenProject mavenProject = new MavenProject();
        mavenProject.setPackaging("war");

        final File pom = getPom("deploy-only-webarchive-with-commands-pom.xml");

        final AbstractDeployment deployMojo = lookupMojoAndVerify("deploy-only", pom);

        deployMojo.project = mavenProject;
        deployMojo.execute();

        final ModelControllerClient client = managementClient.getControllerClient();

        // /deployment=test.war :read-attribute(name=status)
        ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        ModelNode result = executeOperation(client, op);

        assertEquals("OK", ServerOperations.readResultAsString(result));

        // Ensure that org.jboss.as.logging exists and foo does not
        address = createAddress("subsystem", "logging", "logger", "foo");
        op = ServerOperations.createReadResourceOperation(address);
        result = client.execute(op);
        assertFalse("Logger foo was not removed", ServerOperations.isSuccessfulOutcome(result));

        address = createAddress("subsystem", "logging", "logger", "org.jboss.as.logging");
        op = ServerOperations.createReadResourceOperation(address);
        result = client.execute(op);
        assertTrue("Logger org.jboss.as.logging was not added", ServerOperations.isSuccessfulOutcome(result));

        // Remove the logger to clean-up
        op = ServerOperations.createRemoveOperation(address);
        executeOperation(client, op);
    }

    @Test
    public void testRedeploy() throws Exception {

        // Make sure the archive is deployed
        if (!isDeployed(DEPLOYMENT_NAME)) {
            deploy(DEPLOYMENT_NAME);
        }

        final MavenProject mavenProject = new MavenProject();
        mavenProject.setPackaging("war");

        final File pom = getPom("redeploy-only-webarchive-pom.xml");

        final AbstractDeployment deployMojo = lookupMojoAndVerify("redeploy-only", pom);

        deployMojo.project = mavenProject;
        deployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", isDeployed(DEPLOYMENT_NAME));

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = executeOperation(managementClient.getControllerClient(), op);

        assertEquals("OK", ServerOperations.readResultAsString(result));
    }

    @Test
    public void testUndeploy() throws Exception {

        // Make sure the archive is deployed
        if (!isDeployed(DEPLOYMENT_NAME)) {
            deploy(DEPLOYMENT_NAME);
        }

        final MavenProject mavenProject = new MavenProject();
        mavenProject.setPackaging("war");

        final File pom = getPom("undeploy-webarchive-pom.xml");

        final AbstractDeployment deployMojo = lookupMojoAndVerify("undeploy", pom);

        deployMojo.project = mavenProject;
        deployMojo.execute();

        // Verify deployed
        assertFalse("Deployment " + DEPLOYMENT_NAME + " was not undeployed", isDeployed(DEPLOYMENT_NAME));
    }


    protected boolean isDeployed(final String name) throws IOException {
        //
        final ModelNode address = createAddress("deployment");
        final ModelNode op = ServerOperations.createReadResourceOperation(address);
        final ModelNode result = executeOperation(managementClient.getControllerClient(), op);
        final List<ModelNode> deployments = ServerOperations.readResult(result).asList();
        for (ModelNode deployment : deployments) {
            if (name.equals(ServerOperations.readResult(deployment).get(ClientConstants.NAME).asString())) {
                return true;
            }
        }
        return false;
    }

    protected void deploy(final String name) throws IOException, DeploymentExecutionException, DeploymentFailureException {
        final StandaloneDeployment deployment = StandaloneDeployment.create(managementClient.getControllerClient(), getDeployment(), name, Type.DEPLOY, null, null);
        assertEquals(Status.SUCCESS, deployment.execute());

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", isDeployed(DEPLOYMENT_NAME));

        // Check the status
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = executeOperation(managementClient.getControllerClient(), op);

        assertEquals("OK", ServerOperations.readResultAsString(result));
    }

    protected void undeploy(final String name) throws IOException, DeploymentExecutionException, DeploymentFailureException {
        final StandaloneDeployment deployment = StandaloneDeployment.create(managementClient.getControllerClient(), null, name, Type.UNDEPLOY, null, null);
        assertEquals(Status.SUCCESS, deployment.execute());

        // Verify not deployed
        assertFalse("Deployment " + DEPLOYMENT_NAME + " was not undeployed", isDeployed(DEPLOYMENT_NAME));
    }

}
