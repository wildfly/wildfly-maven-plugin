/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;

import javax.inject.Inject;

import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.tests.AbstractWildFlyServerMojoTest;
import org.wildfly.plugin.tools.DeploymentManager;
import org.wildfly.plugin.tools.UndeployDescription;

/**
 * deploy mojo testcase.
 *
 * @author <a href="mailto:heinz.wilming@akquinet.de">Heinz Wilming</a>
 */
public class DeployOnlyTest extends AbstractWildFlyServerMojoTest {

    @Inject
    private DeploymentManager deploymentManager;

    @Test
    public void testDeploy() throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME));
        }

        final AbstractDeployment deployMojo = lookupMojoAndVerify("deploy-only", "deploy-only-webarchive-pom.xml");

        deployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", deploymentManager.hasDeployment(DEPLOYMENT_NAME));

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = executeOperation(op);

        assertEquals("OK", ServerOperations.readResultAsString(result));
    }

    @Test
    public void testDeployUrl() throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME));
        }

        final AbstractDeployment deployMojo = getUrlDeploymentMojo("deploy-only", "deploy-only-webarchive-pom.xml");

        deployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", deploymentManager.hasDeployment(DEPLOYMENT_NAME));

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = executeOperation(op);

        assertEquals("OK", ServerOperations.readResultAsString(result));
    }

    @Test
    public void testRedeploy() throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.deploy(getDeployment());
        }

        final AbstractDeployment deployMojo = lookupMojoAndVerify("redeploy-only", "redeploy-only-webarchive-pom.xml");

        deployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", deploymentManager.hasDeployment(DEPLOYMENT_NAME));

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = executeOperation(op);

        assertEquals("OK", ServerOperations.readResultAsString(result));
    }

    @Test
    public void testRedeployUrl() throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.deploy(getDeployment());
        }

        final AbstractDeployment redeployMojo = getUrlDeploymentMojo("redeploy-only", "redeploy-only-webarchive-pom.xml");

        redeployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", deploymentManager.hasDeployment(DEPLOYMENT_NAME));

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = executeOperation(op);

        assertEquals("OK", ServerOperations.readResultAsString(result));
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

    private AbstractDeployment getUrlDeploymentMojo(final String goal, final String pomName) throws Exception {
        final AbstractDeployment mojo = lookupMojoAndVerify(goal, pomName);
        setValue(mojo, "contentUrl", getContentUrl());
        // Clear the target and filename to ensure they're not getting picked up
        setValue(mojo, "filename", null);
        setValue(mojo, "targetDir", null);
        return mojo;
    }

    private URL getContentUrl() throws MalformedURLException {
        return Paths.get(BASE_CONFIG_DIR, "target", DEPLOYMENT_NAME).toUri().toURL();
    }
}
