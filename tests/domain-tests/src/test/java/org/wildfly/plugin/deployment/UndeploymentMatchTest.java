/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.plugin.core.DeploymentDescription;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.core.DeploymentResult;
import org.wildfly.plugin.core.UndeployDescription;
import org.wildfly.plugin.tests.AbstractWildFlyServerMojoTest;

/**
 * Matcher Undeployment test case.
 *
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 */
public class UndeploymentMatchTest extends AbstractWildFlyServerMojoTest {
    private static final String DEPLOYMENT_NAME_1 = "test-undeploy-1.war";
    private static final String DEPLOYMENT_NAME_2 = "test-undeploy-2.war";

    private final Set<String> defaultServerGroups = Collections.singleton("main-server-group");

    @Inject
    private DeploymentManager deploymentManager;

    @Before
    public void setup() throws Exception {
        deploymentManager.deploy(getDeployment().setName(DEPLOYMENT_NAME_1).addServerGroups(defaultServerGroups));
        deploymentManager.deploy(getDeployment().setName(DEPLOYMENT_NAME_2).addServerGroups(defaultServerGroups));
    }

    @After
    public void cleanup() throws Exception {
        final Set<UndeployDescription> deployments = new HashSet<>();
        for (DeploymentDescription deployment : deploymentManager.getDeployments()) {
            deployments.add(UndeployDescription.of(deployment));
        }
        if (!deployments.isEmpty()) {
            deploymentManager.undeploy(deployments).assertSuccess();
        }
    }

    @Test
    public void undeployAll() throws Exception {
        undeploy(MatchPatternStrategy.ALL);

        final Set<DeploymentDescription> deployments = deploymentManager.getDeployments();
        assertEquals(0, deployments.size());
    }

    @Test
    public void undeployFirst() throws Exception {

        undeploy(MatchPatternStrategy.FIRST);

        final Set<DeploymentDescription> deployments = deploymentManager.getDeployments();
        assertEquals(1, deployments.size());
    }

    @Test
    public void undeployFirstMultiServerGroup() throws Exception {
        final String serverGroup = "other-server-group";
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME_1, serverGroup)) {
            final DeploymentResult result = deploymentManager
                    .forceDeploy(getDeployment().setName(DEPLOYMENT_NAME_1).addServerGroup(serverGroup));
            Assert.assertTrue(result.getFailureMessage(), result.successful());
        }
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME_2, serverGroup)) {
            final DeploymentResult result = deploymentManager
                    .forceDeploy(getDeployment().setName(DEPLOYMENT_NAME_2).addServerGroup(serverGroup));
            Assert.assertTrue(result.getFailureMessage(), result.successful());
        }
        // Set up the other-server-group servers to ensure the full deployment process works correctly
        final ModelNode op = Operations.createOperation("start-servers",
                new ModelNode().setEmptyList().add(ClientConstants.SERVER_GROUP, "other-server-group"));
        op.get("blocking").set(true);
        executeOperation(op);

        undeploy(MatchPatternStrategy.FIRST, "undeploy-multi-server-group-match-pom.xml",
                Arrays.asList("main-server-group", "other-server-group"));

        final Set<DeploymentDescription> deployments = deploymentManager.getDeployments();
        assertEquals(1, deployments.size());

        assertTrue("Deployment " + DEPLOYMENT_NAME_2 + " was not found on main-server-group",
                deploymentManager.hasDeployment(DEPLOYMENT_NAME_2, "main-server-group"));
        assertTrue("Deployment " + DEPLOYMENT_NAME_2 + " was not found on other-server-group",
                deploymentManager.hasDeployment(DEPLOYMENT_NAME_2, "other-server-group"));
    }

    @Test(expected = MojoDeploymentException.class)
    public void undeployFail() throws Exception {
        undeploy(MatchPatternStrategy.FAIL);
    }

    @Test
    public void testUndeployServerGroup() throws Exception {
        final String deploymentName1 = "test.war";
        final String deploymentName2 = "test-qa.war";
        if (!deploymentManager.hasDeployment(deploymentName1, "main-server-group")) {
            deploymentManager.deploy(getDeployment().setName(deploymentName1).addServerGroup("main-server-group"));
        }
        // This deployment should stay deployed since it's only on the other-server-group
        if (!deploymentManager.hasDeployment(deploymentName2, "other-server-group")) {
            deploymentManager.deploy(getDeployment().setName(deploymentName2).addServerGroup("other-server-group"));
        }
        undeploy(MatchPatternStrategy.ALL);

        assertEquals(0, deploymentManager.getDeployments("main-server-group").size());
        assertEquals(1, deploymentManager.getDeployments("other-server-group").size());
    }

    private void undeploy(final MatchPatternStrategy matchPatternStrategy) throws Exception {
        undeploy(matchPatternStrategy, "undeploy-webarchive-match-pom.xml");
    }

    private void undeploy(final MatchPatternStrategy matchPatternStrategy, final String pomName) throws Exception {
        undeploy(matchPatternStrategy, pomName, Collections.singletonList("main-server-group"));
    }

    private void undeploy(final MatchPatternStrategy matchPatternStrategy, final String pomName,
            final List<String> serverGroups) throws Exception {

        final UndeployMojo undeployMojo = lookupMojoAndVerify("undeploy", pomName);
        // Server groups are required to be set and when there is a property defined on an attribute parameter the
        // test harness does not set the fields
        setValue(undeployMojo, "serverGroups", serverGroups);

        setValue(undeployMojo, "matchPatternStrategy", matchPatternStrategy.toString());
        undeployMojo.execute();
    }

}
