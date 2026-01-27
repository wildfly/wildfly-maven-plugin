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
import org.jboss.dmr.ModelNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.tests.AbstractProjectMojoTest;
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
public class DeployOnlyTest extends AbstractProjectMojoTest {

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
    @InjectMojo(goal = "deploy-only", pom = "deploy-only-webarchive-pom.xml")
    public void testDeploy(final DeployOnlyMojo deployMojo) throws Exception {

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
    @InjectMojo(goal = "deploy-only", pom = "deploy-only-webarchive-content-url-pom.xml")
    public void testDeployUrl(final DeployOnlyMojo deployMojo) throws Exception {

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
    @InjectMojo(goal = "redeploy-only", pom = "redeploy-only-webarchive-pom.xml")
    public void testRedeploy(final RedeployOnlyMojo deployMojo) throws Exception {

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
    @InjectMojo(goal = "redeploy-only", pom = "redeploy-only-webarchive-pom.xml")
    public void testRedeployUrl(final RedeployOnlyMojo redeployMojo) throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.deploy(TestEnvironment.getDeployment());
        }

        redeployMojo.execute();

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
