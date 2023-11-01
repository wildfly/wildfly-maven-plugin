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

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.core.UndeployDescription;
import org.wildfly.plugin.tests.AbstractWildFlyServerMojoTest;

import javax.inject.Inject;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
    public void testUnmanagedArchiveDeploy() throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME));
        }

        final AbstractDeployment deployMojo = lookupMojoAndVerify("deploy", "deploy-webarchive-unmanaged-pom.xml");

        deployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", deploymentManager.hasDeployment(DEPLOYMENT_NAME));

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "content");
        final ModelNode result = executeOperation(op);

        if (!ServerOperations.isSuccessfulOutcome(result)) {
            fail(ServerOperations.getFailureDescriptionAsString(result));
        }
        List<ModelNode> list = ServerOperations.readResult(result).asList();
        assertEquals(1, list.size());
        ModelNode first = list.iterator().next();

        assertNotNull(first.get("path"));
        assertTrue(first.get("archive").asBoolean());
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
