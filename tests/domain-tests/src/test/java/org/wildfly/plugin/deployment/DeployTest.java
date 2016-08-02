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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

import org.apache.maven.plugin.Mojo;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
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
    private static final String DEFAULT_SERVER_GROUP = "main-server-group";
    private static final Set<String> DEFAULT_SERVER_GROUPS = Collections.singleton(DEFAULT_SERVER_GROUP);

    @Inject
    private DeploymentManager deploymentManager;

    @Test
    public void testDeploy() throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
        }
        executeAndVerifyDeploymentExists("deploy", "deploy-webarchive-pom.xml");
        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));

        executeAndVerifyDeploymentExists("deploy", "legacy-deploy-webarchive-pom.xml");
        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
    }

    @Test
    public void testDeployWithRuntimeName() throws Exception {
        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
        }
        executeAndVerifyDeploymentExists("deploy", "deploy-webarchive-with-runtime-name-pom.xml", "test-runtime.war");
        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
    }

    @Test
    public void testDeployOnly() throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
        }
        executeAndVerifyDeploymentExists("deploy-only", "deploy-webarchive-pom.xml");
        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
    }

    @Test
    public void testDeployWithCommands() throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
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
        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
    }

    @Test
    public void testDeployOnlyWithCommands() throws Exception {

        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
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
        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
    }

    @Test
    public void testDeployWithStoppedServer() throws Exception {
        // Make sure the archive is not deployed
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
        }
        final ModelNode address = ServerOperations.createAddress("host", "master", "server-config", "server-one");
        try {
            // Shutdown server-one
            final ModelNode op = ServerOperations.createOperation("stop", address);
            op.get("blocking").set(true);
            executeOperation(op);

            executeAndVerifyDeploymentExists("deploy", "deploy-webarchive-pom.xml");
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
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
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME, "main-server-group")) {
            deploymentManager.deploy(getDeployment().addServerGroup("main-server-group"));
        }
        if (deploymentManager.hasDeployment(DEPLOYMENT_NAME, "other-server-group")) {
            deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroup("other-deployment-group"));
        }
        // Set up the other-server-group servers to ensure the full deployment process works correctly
        final ModelNode op = ServerOperations.createOperation("start-servers", ServerOperations.createAddress(ClientConstants.SERVER_GROUP, "other-server-group"));
        op.get("blocking").set(true);
        executeOperation(op);

        // Deploy to both server groups and ensure the deployment exists on both, it should already be on the
        // main-server-group and should have been added to the other-server-group
        final Set<String> serverGroups = new HashSet<>(Arrays.asList("main-server-group", "other-server-group"));
        executeAndVerifyDeploymentExists("deploy", "deploy-multi-server-group-pom.xml", null, serverGroups);
        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(serverGroups));
    }

    @Test
    public void testRedeploy() throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.deploy(getDeployment().addServerGroup(DEFAULT_SERVER_GROUP));
        }

        executeAndVerifyDeploymentExists("redeploy", "deploy-webarchive-pom.xml");
        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
    }

    @Test
    public void testRedeployOnly() throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.deploy(getDeployment().addServerGroup(DEFAULT_SERVER_GROUP));
        }

        executeAndVerifyDeploymentExists("redeploy-only", "deploy-webarchive-pom.xml");
        deploymentManager.undeploy(UndeployDescription.of(DEPLOYMENT_NAME).addServerGroups(DEFAULT_SERVER_GROUPS));
    }

    @Test
    public void testUndeploy() throws Exception {

        // Make sure the archive is deployed
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP)) {
            deploymentManager.deploy(getDeployment().addServerGroup(DEFAULT_SERVER_GROUP));
        }

        final UndeployMojo deployMojo = lookupMojoAndVerify("undeploy", "undeploy-webarchive-pom.xml");

        deployMojo.execute();

        // Verify deployed
        assertFalse("Deployment " + DEPLOYMENT_NAME + " was not undeployed", deploymentManager.hasDeployment(DEPLOYMENT_NAME, DEFAULT_SERVER_GROUP));
    }

    @Override
    public <T extends Mojo> T lookupMojoAndVerify(final String goal, final String fileName) throws Exception {
        final Mojo mojo = super.lookupMojoAndVerify(goal, fileName);
        setValue(mojo, "serverGroups", Collections.singletonList("main-server-group"));
        return (T) mojo;
    }

    private void executeAndVerifyDeploymentExists(final String goal, final String fileName) throws Exception {
        executeAndVerifyDeploymentExists(goal, fileName, null);
    }

    private void executeAndVerifyDeploymentExists(final String goal, final String fileName, final String runtimeName) throws Exception {
        executeAndVerifyDeploymentExists(goal, fileName, runtimeName, Collections.singleton("main-server-group"));
    }

    private void executeAndVerifyDeploymentExists(final String goal, final String fileName, final String runtimeName, final Collection<String> serverGroups) throws Exception {

        final AbstractDeployment deployMojo = lookupMojoAndVerify(goal, fileName);

        // Server groups are required to be set and when there is a property defined on an attribute parameter the
        // test harness does not set the fields
        setValue(deployMojo, "serverGroups", new ArrayList<>(serverGroups));

        deployMojo.execute();

        // Verify deployed
        assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed", deploymentManager.hasDeployment(DEPLOYMENT_NAME));

        // Verify deployed on all server groups
        for (String serverGroup : serverGroups) {
            assertTrue("Deployment " + DEPLOYMENT_NAME + " was not deployed on server group " + serverGroup, deploymentManager.hasDeployment(DEPLOYMENT_NAME, serverGroup));
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
