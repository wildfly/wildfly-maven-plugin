/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.inject.Inject;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.core.UndeployDescription;
import org.wildfly.plugin.tests.AbstractWildFlyServerMojoTest;

/**
 * deploy mojo testcase.
 *
 * @author <a href="mailto:heinz.wilming@akquinet.de">Heinz Wilming</a>
 */
public class DeployTest extends AbstractWildFlyServerMojoTest {

    @Inject
    private DeploymentManager deploymentManager;

    @Test
    public void testDeploy() throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME));
        }

        final AbstractDeployment deployMojo = lookupMojoAndVerify("deploy", "deploy-webarchive-pom.xml");

        deployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", deploymentManager.hasDeployment(DEPLOYMENT_NAME));

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = executeOperation(op);

        assertEquals("OK", ServerOperations.readResultAsString(result));
        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME));
    }

    @Test
    public void testForceDeploy() throws Exception {

        // Make sure the archive is not deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.deploy(getDeployment());
        }

        final AbstractDeployment deployMojo = lookupMojoAndVerify("deploy", "deploy-webarchive-pom.xml");

        deployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", deploymentManager.hasDeployment(DEPLOYMENT_NAME));

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = executeOperation(op);

        assertEquals("OK", ServerOperations.readResultAsString(result));
        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME));
    }

    @Test
    public void testDeployWithRuntimeName() throws Exception {
        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME));
        }

        final AbstractDeployment deployMojo = lookupMojoAndVerify("deploy", "deploy-webarchive-with-runtime-name-pom.xml");

        deployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", deploymentManager.hasDeployment(DEPLOYMENT_NAME));

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadResourceOperation(address);
        op.get(ClientConstants.INCLUDE_RUNTIME).set(true);
        final ModelNode result = executeOperation(op);

        if (!ServerOperations.isSuccessfulOutcome(result)) {
            fail(ServerOperations.getFailureDescriptionAsString(result));
        }

        assertEquals("OK", ServerOperations.readResult(result).get("status").asString());
        assertEquals("test-runtime.war", ServerOperations.readResult(result).get("runtime-name").asString());
        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME));
    }

    @Test
    public void testRedeploy() throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.deploy(getDeployment());
        }

        final AbstractDeployment deployMojo = lookupMojoAndVerify("redeploy", "redeploy-webarchive-pom.xml");

        deployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", deploymentManager.hasDeployment(DEPLOYMENT_NAME));

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = executeOperation(op);

        assertEquals("OK", ServerOperations.readResultAsString(result));
        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME));
    }

    @Test
    public void testUndeploy() throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.deploy(getDeployment());
        }

        final UndeployMojo deployMojo = lookupMojoAndVerify("undeploy", "undeploy-webarchive-pom.xml");

        deployMojo.execute();

        // Verify deployed
        assertFalse("Deployment " + DEPLOYMENT_NAME + " was not undeployed", deploymentManager.hasDeployment(DEPLOYMENT_NAME));
    }
}
