/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wildfly.plugin.tests.TestEnvironment.DEPLOYMENT_NAME;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.tests.TestEnvironment;
import org.wildfly.plugin.tools.DeploymentManager;
import org.wildfly.plugin.tools.UndeployDescription;
import org.wildfly.plugin.tools.server.ServerManager;
import org.wildfly.testing.junit.extension.annotation.ServerResource;
import org.wildfly.testing.junit.extension.annotation.WildFlyTest;

/**
 * deploy mojo testcase.
 *
 * @author <a href="mailto:heinz.wilming@akquinet.de">Heinz Wilming</a>
 */
@MojoTest
@WildFlyTest
@Basedir(TestEnvironment.TEST_PROJECT_PATH)
public class DeployTest {

    @ServerResource
    private ServerManager serverManager;

    @ServerResource
    private DeploymentManager deploymentManager;

    @AfterEach
    public void cleanup() throws Exception {
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME));
        }
    }

    @Test
    @InjectMojo(goal = "deploy", pom = "deploy-webarchive-pom.xml")
    public void testDeploy(final DeployMojo deployMojo) throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME));
        }

        deployMojo.execute();

        // Verify deployed
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME), "Deployment " + DEPLOYMENT_NAME + " was not deployed");

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = serverManager.executeOperation(op);

        assertEquals("OK", result.asString());
    }

    @Test
    @InjectMojo(goal = "deploy", pom = "deploy-webarchive-pom.xml")
    public void testForceDeploy(final DeployMojo deployMojo) throws Exception {

        // Make sure the archive is not deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.deploy(TestEnvironment.getDeployment());
        }

        deployMojo.execute();

        // Verify deployed
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME), "Deployment " + DEPLOYMENT_NAME + " was not deployed");

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = serverManager.executeOperation(op);

        assertEquals("OK", result.asString());
    }

    @Test
    @InjectMojo(goal = "deploy", pom = "deploy-webarchive-with-runtime-name-pom.xml")
    public void testDeployWithRuntimeName(final DeployMojo deployMojo) throws Exception {
        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME));
        }

        deployMojo.execute();

        // Verify deployed
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME), "Deployment " + DEPLOYMENT_NAME + " was not deployed");

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadResourceOperation(address);
        op.get(ClientConstants.INCLUDE_RUNTIME).set(true);
        final ModelNode result = serverManager.executeOperation(op);

        assertEquals("OK", result.get("status").asString());
        assertEquals("test-runtime.war", result.get("runtime-name").asString());
    }

    @Test
    @InjectMojo(goal = "redeploy", pom = "redeploy-webarchive-pom.xml")
    public void testRedeploy(final RedeployMojo deployMojo) throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.deploy(TestEnvironment.getDeployment());
        }

        deployMojo.execute();

        // Verify deployed
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME), "Deployment " + DEPLOYMENT_NAME + " was not deployed");

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = serverManager.executeOperation(op);

        assertEquals("OK", result.asString());
    }

    @Test
    @InjectMojo(goal = "undeploy", pom = "undeploy-webarchive-pom.xml")
    public void testUndeploy(final UndeployMojo deployMojo) throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.deploy(TestEnvironment.getDeployment());
        }

        deployMojo.execute();

        // Verify deployed
        assertFalse(deploymentManager.hasDeployment(DEPLOYMENT_NAME), "Deployment " + DEPLOYMENT_NAME + " was not undeployed");
    }
}
