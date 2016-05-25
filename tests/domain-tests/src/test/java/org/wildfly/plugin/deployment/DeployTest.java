/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.server.DomainDeploymentManager;
import org.wildfly.plugin.tests.AbstractWildFlyServerMojoTest;

/**
 * deploy mojo testcase.
 *
 * @author <a href="mailto:heinz.wilming@akquinet.de">Heinz Wilming</a>
 */
public class DeployTest extends AbstractWildFlyServerMojoTest {

    @Inject
    private DomainDeploymentManager deploymentManager;

    @Test
    public void testDeploy() throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.isDeployed(DEPLOYMENT_NAME)) {
            deploymentManager.undeploy(DEPLOYMENT_NAME);
        }
        executeAndVerifyDeploymentExists("deploy", "deploy-webarchive-pom.xml");
        deploymentManager.undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDeployWithRuntimeName() throws Exception {
        // Make sure the archive is not deployed
        if (deploymentManager.isDeployed(DEPLOYMENT_NAME)) {
            deploymentManager.undeploy(DEPLOYMENT_NAME);
        }
        executeAndVerifyDeploymentExists("deploy", "deploy-webarchive-with-runtime-name-pom.xml", "test-runtime.war");
        deploymentManager.undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDeployOnly() throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.isDeployed(DEPLOYMENT_NAME)) {
            deploymentManager.undeploy(DEPLOYMENT_NAME);
        }
        executeAndVerifyDeploymentExists("deploy-only", "deploy-webarchive-pom.xml");
        deploymentManager.undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDeployWithCommands() throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.isDeployed(DEPLOYMENT_NAME)) {
            deploymentManager.undeploy(DEPLOYMENT_NAME);
        }

        executeAndVerifyDeploymentExists("deploy", "deploy-webarchive-with-commands-pom.xml");

        // Ensure that org.jboss.as.logging exists and foo does not
        ModelNode address = ServerOperations.createAddress("profile", "full", "subsystem", "logging", "logger", "foo");
        ModelNode op = ServerOperations.createReadResourceOperation(address);
        ModelNode result = client.execute(op);
        assertFalse("Logger foo was not removed", ServerOperations.isSuccessfulOutcome(result));

        address = ServerOperations.createAddress("profile", "full", "subsystem", "logging", "logger", "org.jboss.as.logging");
        op = ServerOperations.createReadResourceOperation(address);
        result = client.execute(op);
        assertTrue("Logger org.jboss.as.logging was not added", ServerOperations.isSuccessfulOutcome(result));

        // Remove the logger to clean-up
        op = ServerOperations.createRemoveOperation(address);
        executeOperation(op);
        deploymentManager.undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDeployOnlyWithCommands() throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.isDeployed(DEPLOYMENT_NAME)) {
            deploymentManager.undeploy(DEPLOYMENT_NAME);
        }

        executeAndVerifyDeploymentExists("deploy-only", "deploy-webarchive-with-commands-pom.xml");

        // Ensure that org.jboss.as.logging exists and foo does not
        ModelNode address = ServerOperations.createAddress("profile", "full", "subsystem", "logging", "logger", "foo");
        ModelNode op = ServerOperations.createReadResourceOperation(address);
        ModelNode result = client.execute(op);
        assertFalse("Logger foo was not removed", ServerOperations.isSuccessfulOutcome(result));

        address = ServerOperations.createAddress("profile", "full", "subsystem", "logging", "logger", "org.jboss.as.logging");
        op = ServerOperations.createReadResourceOperation(address);
        result = client.execute(op);
        assertTrue("Logger org.jboss.as.logging was not added", ServerOperations.isSuccessfulOutcome(result));

        // Remove the logger to clean-up
        op = ServerOperations.createRemoveOperation(address);
        executeOperation(op);
        deploymentManager.undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDeployWithStoppedServer() throws Exception {
        // Make sure the archive is not deployed
        if (deploymentManager.isDeployed(DEPLOYMENT_NAME)) {
            deploymentManager.undeploy(DEPLOYMENT_NAME);
        }
        final ModelNode address = ServerOperations.createAddress("host", "master", "server-config", "server-one");
        try {
            // Shutdown server-one
            final ModelNode op = ServerOperations.createOperation("stop", address);
            op.get("blocking").set(true);
            executeOperation(op);

            executeAndVerifyDeploymentExists("deploy", "deploy-webarchive-pom.xml");
            deploymentManager.undeploy(DEPLOYMENT_NAME);
        } finally {
            // Restart server-twp
            final ModelNode op = ServerOperations.createOperation("start", address);
            op.get("blocking").set(true);
            executeOperation(op);
        }
    }

    @Test
    public void testDeployNewServerGroup() throws Exception {
        // Make sure the deployment is deployed to the main-server-group and not deployed to the other-server-group
        if (!deploymentManager.isDeployed(DEPLOYMENT_NAME, "main-server-group")) {
            deploymentManager.deploy(DEPLOYMENT_NAME, Collections.singleton("main-server-group"), getDeployment());
        }
        if (deploymentManager.isDeployed(DEPLOYMENT_NAME, "other-server-group")) {
            deploymentManager.undeploy(DEPLOYMENT_NAME, Collections.singleton("other-deployment-group"));
        }
        // Set up the other-server-group servers to ensure the full deployment process works correctly
        final ModelNode op = ServerOperations.createOperation("start-servers", ServerOperations.createAddress(ClientConstants.SERVER_GROUP, "other-server-group"));
        op.get("blocking").set(true);
        executeOperation(op);

        // Deploy to both server groups and ensure the deployment exists on both, it should already be on the
        // main-server-group and should have been added to the other-server-group
        final Collection<String> serverGroups = Arrays.asList("main-server-group", "other-server-group");
        executeAndVerifyDeploymentExists("deploy", "deploy-multi-server-group-pom.xml", null, serverGroups);
        deploymentManager.undeploy(DEPLOYMENT_NAME, serverGroups);
    }

    @Test
    public void testRedeploy() throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.isDeployed(DEPLOYMENT_NAME)) {
            deploymentManager.deploy(DEPLOYMENT_NAME, getDeployment());
        }

        executeAndVerifyDeploymentExists("redeploy", "deploy-webarchive-pom.xml");
        deploymentManager.undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testRedeployOnly() throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.isDeployed(DEPLOYMENT_NAME)) {
            deploymentManager.deploy(DEPLOYMENT_NAME, getDeployment());
        }

        executeAndVerifyDeploymentExists("redeploy-only", "deploy-webarchive-pom.xml");
        deploymentManager.undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testUndeploy() throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.isDeployed(DEPLOYMENT_NAME)) {
            deploymentManager.deploy(DEPLOYMENT_NAME, getDeployment());
        }

        final AbstractDeployment deployMojo = lookupMojoAndVerify("undeploy", "deploy-webarchive-pom.xml");

        deployMojo.execute();

        // Verify deployed
        assertFalse("Deployment " + DEPLOYMENT_NAME + " was not undeployed", deploymentManager.isDeployed(DEPLOYMENT_NAME));
    }

    private void executeAndVerifyDeploymentExists(final String goal, final String fileName) throws Exception {
        executeAndVerifyDeploymentExists(goal, fileName, null);
    }

    private void executeAndVerifyDeploymentExists(final String goal, final String fileName, final String runtimeName) throws Exception {
        executeAndVerifyDeploymentExists(goal, fileName, runtimeName, Collections.singleton("main-server-group"));
    }

    private void executeAndVerifyDeploymentExists(final String goal, final String fileName, final String runtimeName, final Iterable<String> serverGroups) throws Exception {

        final AbstractDeployment deployMojo = lookupMojoAndVerify(goal, fileName);

        deployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", deploymentManager.isDeployed(DEPLOYMENT_NAME));

        // Verify deployed on all server groups
        for (String serverGroup : serverGroups) {
            assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed on server group " + serverGroup, deploymentManager.isDeployed(DEPLOYMENT_NAME, serverGroup));
        }

        // /deployment=test.war :read-attribute(name=status)
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        for (String serverGroup : serverGroups) {
            final ModelNode address = ServerOperations.createAddress(ClientConstants.SERVER_GROUP, serverGroup,
                    ClientConstants.DEPLOYMENT, DEPLOYMENT_NAME);
            builder.addStep(ServerOperations.createReadAttributeOperation(address, "runtime-name"));
        }
        if (runtimeName != null) {
            final ModelNode result = client.execute(builder.build());
            assertTrue(ServerOperations.getFailureDescriptionAsString(result), ServerOperations.isSuccessfulOutcome(result));
            // Get the result of the step
            final ModelNode stepResults = ServerOperations.readResult(result);
            final Set<String> stepKeys = stepResults.keys();
            for (String stepKey : stepKeys) {
                assertEquals("Runtime name does not match", runtimeName, ServerOperations.readResultAsString(stepResults.get(stepKey)));
            }
        }
    }

}
