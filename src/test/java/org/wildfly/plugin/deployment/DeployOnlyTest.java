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

package org.wildfly.plugin.deployment;

import org.apache.maven.project.MavenProject;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.wildfly.plugin.AbstractItTestCase;
import org.wildfly.plugin.common.DeploymentExecutionException;
import org.wildfly.plugin.common.DeploymentFailureException;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.deployment.Deployment.Status;
import org.wildfly.plugin.deployment.Deployment.Type;
import org.wildfly.plugin.deployment.standalone.StandaloneDeployment;
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
