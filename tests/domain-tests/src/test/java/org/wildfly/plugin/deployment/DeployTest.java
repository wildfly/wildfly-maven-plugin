/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wildfly.plugin.tests.TestEnvironment.DEPLOYMENT_NAME;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.jupiter.api.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.tests.TestEnvironment;
import org.wildfly.plugin.tools.DeploymentManager;
import org.wildfly.plugin.tools.OperationExecutionException;
import org.wildfly.plugin.tools.UndeployDescription;
import org.wildfly.plugin.tools.server.ServerManager;
import org.wildfly.testing.junit.extension.annotation.ServerResource;
import org.wildfly.testing.junit.extension.annotation.WildFlyDomainTest;

/**
 * deploy mojo testcase.
 *
 * @author <a href="mailto:heinz.wilming@akquinet.de">Heinz Wilming</a>
 */
@MojoTest
@WildFlyDomainTest
@Basedir(TestEnvironment.TEST_PROJECT_PATH)
public class DeployTest {
    private static final String DEFAULT_SERVER_GROUP = "main-server-group";
    private static final Set<String> DEFAULT_SERVER_GROUPS = Collections.singleton(DEFAULT_SERVER_GROUP);

    @ServerResource
    private ServerManager serverManager;

    @ServerResource
    private DeploymentManager deploymentManager;

    @Test
    @InjectMojo(goal = "deploy", pom = "deploy-webarchive-pom.xml")
    public void testDeploy(final DeployMojo deployMojo) throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
        }

        deployMojo.execute();

        // Verify deployed
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME), "Deployment " + DEPLOYMENT_NAME + " was not deployed");
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP),
                "Deployment " + DEPLOYMENT_NAME + " was not deployed on server group " + DEFAULT_SERVER_GROUP);

        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
    }

    @Test
    @InjectMojo(goal = "deploy", pom = "deploy-webarchive-with-runtime-name-pom.xml")
    public void testDeployWithRuntimeName(final DeployMojo deployMojo) throws Exception {
        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
        }

        deployMojo.execute();

        // Verify deployed
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME), "Deployment " + DEPLOYMENT_NAME + " was not deployed");
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP),
                "Deployment " + DEPLOYMENT_NAME + " was not deployed on server group " + DEFAULT_SERVER_GROUP);

        // Verify runtime name
        final ModelNode address = ServerOperations.createAddress(ClientConstants.SERVER_GROUP, DEFAULT_SERVER_GROUP,
                ClientConstants.DEPLOYMENT, DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "runtime-name");
        final ModelNode result = serverManager.executeOperation(op);
        assertEquals("test-runtime.war", result.asString(), "Runtime name does not match");

        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
    }

    @Test
    @InjectMojo(goal = "deploy-only", pom = "deploy-webarchive-pom.xml")
    public void testDeployOnly(final DeployOnlyMojo deployMojo) throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
        }

        deployMojo.execute();

        // Verify deployed
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME), "Deployment " + DEPLOYMENT_NAME + " was not deployed");
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP),
                "Deployment " + DEPLOYMENT_NAME + " was not deployed on server group " + DEFAULT_SERVER_GROUP);

        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
    }

    @Test
    @InjectMojo(goal = "deploy", pom = "deploy-webarchive-pom.xml")
    public void testDeployWithStoppedServer(final DeployMojo deployMojo) throws Exception {
        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
        }
        final ModelNode address = determineHostAddress(serverManager.client()).add("server-config")
                .add("server-one");
        try {
            // Shutdown server-one
            final ModelNode op = ServerOperations.createOperation("stop", address);
            op.get("blocking").set(true);
            serverManager.executeOperation(op);

            deployMojo.execute();

            // Verify deployed
            assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME), "Deployment " + DEPLOYMENT_NAME + " was not deployed");
            assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP),
                    "Deployment " + DEPLOYMENT_NAME + " was not deployed on server group " + DEFAULT_SERVER_GROUP);

            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
        } finally {
            // Restart server-twp
            final ModelNode op = ServerOperations.createOperation("start", address);
            op.get("blocking").set(true);
            serverManager.executeOperation(op);
        }
    }

    @Test
    @InjectMojo(goal = "deploy", pom = "deploy-multi-server-group-pom.xml")
    public void testDeployNewServerGroup(final DeployMojo deployMojo) throws Exception {
        // Make sure the deployment is deployed to the main-server-group and not deployed to the other-server-group
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME, "main-server-group")) {
            deploymentManager.deploy(TestEnvironment.getDeployment().addServerGroup("main-server-group"));
        }
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME, "other-server-group")) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroup("other-deployment-group"));
        }
        // Set up the other-server-group servers to ensure the full deployment process works correctly
        final ModelNode op = ServerOperations.createOperation("start-servers",
                ServerOperations.createAddress(ClientConstants.SERVER_GROUP, "other-server-group"));
        op.get("blocking").set(true);
        serverManager.executeOperation(op);

        // Deploy to both server groups and ensure the deployment exists on both, it should already be on the
        // main-server-group and should have been added to the other-server-group
        final Set<String> serverGroups = new HashSet<>(Arrays.asList("main-server-group", "other-server-group"));

        deployMojo.execute();

        // Verify deployed
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME), "Deployment " + DEPLOYMENT_NAME + " was not deployed");

        // Verify deployed on all server groups
        for (String serverGroup : serverGroups) {
            assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME, serverGroup),
                    "Deployment " + DEPLOYMENT_NAME + " was not deployed on server group " + serverGroup);
        }

        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(serverGroups));
    }

    @Test
    @InjectMojo(goal = "redeploy", pom = "deploy-webarchive-pom.xml")
    public void testRedeploy(final RedeployMojo deployMojo) throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.deploy(TestEnvironment.getDeployment().addServerGroup(DEFAULT_SERVER_GROUP));
        }

        deployMojo.execute();

        // Verify deployed
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME), "Deployment " + DEPLOYMENT_NAME + " was not deployed");
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP),
                "Deployment " + DEPLOYMENT_NAME + " was not deployed on server group " + DEFAULT_SERVER_GROUP);

        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
    }

    @Test
    @InjectMojo(goal = "redeploy-only", pom = "deploy-webarchive-pom.xml")
    public void testRedeployOnly(final RedeployOnlyMojo deployMojo) throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.deploy(TestEnvironment.getDeployment().addServerGroup(DEFAULT_SERVER_GROUP));
        }

        deployMojo.execute();

        // Verify deployed
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME), "Deployment " + DEPLOYMENT_NAME + " was not deployed");
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP),
                "Deployment " + DEPLOYMENT_NAME + " was not deployed on server group " + DEFAULT_SERVER_GROUP);

        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
    }

    @Test
    @InjectMojo(goal = "undeploy", pom = "undeploy-webarchive-pom.xml")
    public void testUndeploy(final UndeployMojo deployMojo) throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.deploy(TestEnvironment.getDeployment().addServerGroup(DEFAULT_SERVER_GROUP));
        }

        deployMojo.execute();

        // Verify deployed
        assertFalse(deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP),
                "Deployment " + DEPLOYMENT_NAME + " was not undeployed");
    }

    /**
     * Determines the address for the host being used.
     *
     * @return the address of the host
     *
     * @throws IOException                 if an error occurs communicating with the server
     * @throws OperationExecutionException if the operation used to determine the host name fails
     */
    private static ModelNode determineHostAddress(final ModelControllerClient client)
            throws IOException, OperationExecutionException {
        final ModelNode op = Operations.createReadAttributeOperation(new ModelNode().setEmptyList(), "local-host-name");
        ModelNode response = client.execute(op);
        if (Operations.isSuccessfulOutcome(response)) {
            return Operations.createAddress("host", Operations.readResult(response).asString());
        }
        throw new OperationExecutionException(op, response);
    }

}
