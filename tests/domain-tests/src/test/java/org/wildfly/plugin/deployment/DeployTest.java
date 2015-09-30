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

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.wildfly.plugin.common.DeploymentExecutionException;
import org.wildfly.plugin.common.DeploymentFailureException;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.deployment.Deployment.Status;
import org.wildfly.plugin.deployment.Deployment.Type;
import org.wildfly.plugin.deployment.domain.Domain;
import org.wildfly.plugin.deployment.domain.DomainDeploymentBuilder;
import org.wildfly.plugin.tests.AbstractWildFlyServerMojoTest;

/**
 * deploy mojo testcase.
 *
 * @author <a href="mailto:heinz.wilming@akquinet.de">Heinz Wilming</a>
 */
public class DeployTest extends AbstractWildFlyServerMojoTest {

    private static final Domain DOMAIN = new Domain() {
        @Override
        public List<String> getProfiles() {
            return Collections.singletonList("full");
        }

        @Override
        public List<String> getServerGroups() {
            return Collections.singletonList("main-server-group");
        }
    };

    @Test
    public void testDeploy() throws Exception {

        // Make sure the archive is not deployed
        if (isDeployed(DEPLOYMENT_NAME)) {
            undeploy(DEPLOYMENT_NAME);
        }
        executeAndVerifyDeploymentExists("deploy", "deploy-webarchive-pom.xml");
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDeployWithRuntimeName() throws Exception {
        // Make sure the archive is not deployed
        if (isDeployed(DEPLOYMENT_NAME)) {
            undeploy(DEPLOYMENT_NAME);
        }
        executeAndVerifyDeploymentExists("deploy", "deploy-webarchive-with-runtime-name-pom.xml", "test-runtime.war");
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDeployOnly() throws Exception {

        // Make sure the archive is not deployed
        if (isDeployed(DEPLOYMENT_NAME)) {
            undeploy(DEPLOYMENT_NAME);
        }
        executeAndVerifyDeploymentExists("deploy-only", "deploy-webarchive-pom.xml");
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDeployWithCommands() throws Exception {

        // Make sure the archive is not deployed
        if (isDeployed(DEPLOYMENT_NAME)) {
            undeploy(DEPLOYMENT_NAME);
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
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testDeployOnlyWithCommands() throws Exception {

        // Make sure the archive is not deployed
        if (isDeployed(DEPLOYMENT_NAME)) {
            undeploy(DEPLOYMENT_NAME);
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
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testRedeploy() throws Exception {

        // Make sure the archive is deployed
        if (!isDeployed(DEPLOYMENT_NAME)) {
            deploy(DEPLOYMENT_NAME);
        }

        executeAndVerifyDeploymentExists("redeploy", "deploy-webarchive-pom.xml");
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testRedeployOnly() throws Exception {

        // Make sure the archive is deployed
        if (!isDeployed(DEPLOYMENT_NAME)) {
            deploy(DEPLOYMENT_NAME);
        }

        executeAndVerifyDeploymentExists("redeploy-only", "deploy-webarchive-pom.xml");
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void testUndeploy() throws Exception {

        // Make sure the archive is deployed
        if (!isDeployed(DEPLOYMENT_NAME)) {
            deploy(DEPLOYMENT_NAME);
        }

        final AbstractDeployment deployMojo = lookupMojoAndVerify("undeploy", "deploy-webarchive-pom.xml");

        deployMojo.execute();

        // Verify deployed
        assertFalse("Deployment " + DEPLOYMENT_NAME + " was not undeployed", isDeployed(DEPLOYMENT_NAME));
    }


    protected boolean isDeployed(final String name) throws IOException {
        //
        final ModelNode address = ServerOperations.createAddress("deployment");
        final ModelNode op = ServerOperations.createReadResourceOperation(address);
        final ModelNode result = executeOperation(op);
        final List<ModelNode> deployments = ServerOperations.readResult(result).asList();
        for (ModelNode deployment : deployments) {
            if (name.equals(ServerOperations.readResult(deployment).get(ClientConstants.NAME).asString())) {
                return true;
            }
        }
        return false;
    }

    protected void deploy(final String name) throws IOException, DeploymentExecutionException, DeploymentFailureException {
        final Deployment deployment = new DomainDeploymentBuilder(client, DOMAIN)
                .setContent(getDeployment())
                .setName(name)
                .setType(Type.DEPLOY)
                .build();
        assertEquals(Status.SUCCESS, deployment.execute());

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", isDeployed(DEPLOYMENT_NAME));

        // Check the status
        final ModelNode address = ServerOperations.createAddress("server-group", "main-server-group", "deployment", DEPLOYMENT_NAME);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "enabled");
        final ModelNode result = executeOperation(op);

        assertTrue(ServerOperations.readResult(result).asBoolean());
    }

    protected void undeploy(final String name) throws IOException, DeploymentExecutionException, DeploymentFailureException {
        final Deployment deployment = new DomainDeploymentBuilder(client, DOMAIN)
                .setName(name)
                .setType(Type.UNDEPLOY)
                .build();
        assertEquals(Status.SUCCESS, deployment.execute());

        // Verify not deployed
        assertFalse("Deployment " + DEPLOYMENT_NAME + " was not undeployed", isDeployed(DEPLOYMENT_NAME));
    }

    private void executeAndVerifyDeploymentExists(final String goal, final String fileName) throws Exception {
        executeAndVerifyDeploymentExists(goal, fileName, null);
    }

    private void executeAndVerifyDeploymentExists(final String goal, final String fileName, final String runtimeName) throws Exception {

        final AbstractDeployment deployMojo = lookupMojoAndVerify(goal, fileName);

        deployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", isDeployed(DEPLOYMENT_NAME));

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
