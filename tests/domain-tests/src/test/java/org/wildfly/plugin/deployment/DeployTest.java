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

import javax.inject.Inject;

import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.server.Deployments;
import org.wildfly.plugin.tests.AbstractWildFlyServerMojoTest;

/**
 * deploy mojo testcase.
 *
 * @author <a href="mailto:heinz.wilming@akquinet.de">Heinz Wilming</a>
 */
public class DeployTest extends AbstractWildFlyServerMojoTest {

    @Inject
    private Deployments deployments;

    @Test
    public void testDeploy() throws Exception {

        // Make sure the archive is not deployed
        if (deployments.isDeployed(DEPLOYMENT_NAME)) {
            deployments.undeploy(DEPLOYMENT_NAME);
        }
        executeAndVerifyDeploymentExists("deploy", "deploy-webarchive-pom.xml");
        deployments.undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDeployWithRuntimeName() throws Exception {
        // Make sure the archive is not deployed
        if (deployments.isDeployed(DEPLOYMENT_NAME)) {
            deployments.undeploy(DEPLOYMENT_NAME);
        }
        executeAndVerifyDeploymentExists("deploy", "deploy-webarchive-with-runtime-name-pom.xml", "test-runtime.war");
        deployments.undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDeployOnly() throws Exception {

        // Make sure the archive is not deployed
        if (deployments.isDeployed(DEPLOYMENT_NAME)) {
            deployments.undeploy(DEPLOYMENT_NAME);
        }
        executeAndVerifyDeploymentExists("deploy-only", "deploy-webarchive-pom.xml");
        deployments.undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDeployWithCommands() throws Exception {

        // Make sure the archive is not deployed
        if (deployments.isDeployed(DEPLOYMENT_NAME)) {
            deployments.undeploy(DEPLOYMENT_NAME);
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
        deployments.undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDeployOnlyWithCommands() throws Exception {

        // Make sure the archive is not deployed
        if (deployments.isDeployed(DEPLOYMENT_NAME)) {
            deployments.undeploy(DEPLOYMENT_NAME);
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
        deployments.undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDeployWithStoppedServer() throws Exception {
        // Make sure the archive is not deployed
        if (deployments.isDeployed(DEPLOYMENT_NAME)) {
            deployments.undeploy(DEPLOYMENT_NAME);
        }
        final ModelNode address = ServerOperations.createAddress("host", "master", "server-config", "server-one");
        try {
            // Shutdown server-one
            final ModelNode op = ServerOperations.createOperation("stop", address);
            op.get("blocking").set(true);
            executeOperation(op);

            executeAndVerifyDeploymentExists("deploy", "deploy-webarchive-pom.xml");
            deployments.undeploy(DEPLOYMENT_NAME);
        } finally {
            // Restart server-twp
            final ModelNode op = ServerOperations.createOperation("start", address);
            op.get("blocking").set(true);
            executeOperation(op);
        }
    }

    @Test
    public void testRedeploy() throws Exception {

        // Make sure the archive is deployed
        if (!deployments.isDeployed(DEPLOYMENT_NAME)) {
            deployments.deploy(DEPLOYMENT_NAME, getDeployment());
        }

        executeAndVerifyDeploymentExists("redeploy", "deploy-webarchive-pom.xml");
        deployments.undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testRedeployOnly() throws Exception {

        // Make sure the archive is deployed
        if (!deployments.isDeployed(DEPLOYMENT_NAME)) {
            deployments.deploy(DEPLOYMENT_NAME, getDeployment());
        }

        executeAndVerifyDeploymentExists("redeploy-only", "deploy-webarchive-pom.xml");
        deployments.undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testUndeploy() throws Exception {

        // Make sure the archive is deployed
        if (!deployments.isDeployed(DEPLOYMENT_NAME)) {
            deployments.deploy(DEPLOYMENT_NAME, getDeployment());
        }

        final AbstractDeployment deployMojo = lookupMojoAndVerify("undeploy", "deploy-webarchive-pom.xml");

        deployMojo.execute();

        // Verify deployed
        assertFalse("Deployment " + DEPLOYMENT_NAME + " was not undeployed", deployments.isDeployed(DEPLOYMENT_NAME));
    }

    private void executeAndVerifyDeploymentExists(final String goal, final String fileName) throws Exception {
        executeAndVerifyDeploymentExists(goal, fileName, null);
    }

    private void executeAndVerifyDeploymentExists(final String goal, final String fileName, final String runtimeName) throws Exception {

        final AbstractDeployment deployMojo = lookupMojoAndVerify(goal, fileName);

        deployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", deployments.isDeployed(DEPLOYMENT_NAME));

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("server-group", "main-server-group", "deployment", DEPLOYMENT_NAME);
        ModelNode op = ServerOperations.createReadAttributeOperation(address, "enabled");
        ModelNode result = executeOperation(op);

        assertTrue("Deployment was not enabled", ServerOperations.readResult(result).asBoolean());

        if (runtimeName != null) {
            op = ServerOperations.createReadAttributeOperation(address, "runtime-name");
            result = executeOperation(op);
            assertEquals("Runtime name does not match", runtimeName, ServerOperations.readResultAsString(result));
        }
    }

}
