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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.server.DomainDeploymentManager;
import org.wildfly.plugin.tests.AbstractWildFlyServerMojoTest;

/**
 * Matcher Undeployment test case.
 *
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 */
public class UndeploymentMatchTest extends AbstractWildFlyServerMojoTest {
    private static final String DEPLOYMENT_NAME_1 = "test-undeploy-1.war";
    private static final String DEPLOYMENT_NAME_2 = "test-undeploy-2.war";

    @Inject
    private DomainDeploymentManager deploymentManager;

    @Before
    public void setup() throws Exception {
        deploymentManager.deploy(DEPLOYMENT_NAME_1, getDeployment());
        deploymentManager.deploy(DEPLOYMENT_NAME_2, getDeployment());
    }

    @After
    public void cleanup() throws Exception {
        final Collection<String> serverGroups = Arrays.asList("main-server-group", "other-server-group");
        final Collection<String> deployments = new ArrayList<>();
        deployments.addAll(deploymentManager.getDeployments("main-server-group"));
        deployments.addAll(deploymentManager.getDeployments("other-server-group"));
        for (String deployment : deployments) {
            deploymentManager.undeploy(deployment, serverGroups);
        }
    }

    @Test
    public void undeployAll() throws Exception {
        undeploy(MatchPatternStrategy.ALL);

        final Set<String> deployments = deploymentManager.getDeployments();
        assertEquals(0, deployments.size());
    }

    @Test
    public void undeployFirst() throws Exception {

        undeploy(MatchPatternStrategy.FIRST);

        final Set<String> deployments = deploymentManager.getDeployments();
        assertEquals(1, deployments.size());
    }

    @Test
    public void undeployFirstMultiServerGroup() throws Exception {
        final String serverGroup = "other-server-group";
        final Set<String> serverGroups = Collections.singleton(serverGroup);
        if (!deploymentManager.isDeployed(DEPLOYMENT_NAME_1, serverGroup)) {
            deploymentManager.deploy(DEPLOYMENT_NAME_1, serverGroups, getDeployment());
        }
        if (!deploymentManager.isDeployed(DEPLOYMENT_NAME_2, serverGroup)) {
            deploymentManager.deploy(DEPLOYMENT_NAME_2, serverGroups, getDeployment());
        }
        // Set up the other-server-group servers to ensure the full deployment process works correctly
        final ModelNode op = ServerOperations.createOperation("start-servers", ServerOperations.createAddress(ClientConstants.SERVER_GROUP, "other-server-group"));
        op.get("blocking").set(true);
        executeOperation(op);

        undeploy(MatchPatternStrategy.FIRST, "undeploy-multi-server-group-match-pom.xml");

        final Set<String> deployments = deploymentManager.getDeployments();
        assertEquals(1, deployments.size());

        assertTrue("Deployment " + DEPLOYMENT_NAME_2 + " was not found on main-server-group", deploymentManager.isDeployed(DEPLOYMENT_NAME_2, "main-server-group"));
        assertTrue("Deployment " + DEPLOYMENT_NAME_2 + " was not found on other-server-group", deploymentManager.isDeployed(DEPLOYMENT_NAME_2, "other-server-group"));
    }

    @Test(expected = DeploymentException.class)
    public void undeployFail() throws Exception {
        undeploy(MatchPatternStrategy.FAIL);
    }

    @Test
    public void testUndeployServerGroup() throws Exception {
        final String deploymentName1 = "test.war";
        final String deploymentName2 = "test-qa.war";
        if (!deploymentManager.isDeployed(deploymentName1, "main-server-group")) {
            deploymentManager.deploy(deploymentName1, Collections.singleton("main-server-group"), getDeployment());
        }
        // This deployment should stay deployed since it's only on the other-server-group
        if (!deploymentManager.isDeployed(deploymentName2, "other-server-group")) {
            deploymentManager.deploy(deploymentName2, Collections.singleton("other-server-group"), getDeployment());
        }
        undeploy(MatchPatternStrategy.ALL);

        assertEquals(0, deploymentManager.getDeployments("main-server-group").size());
        assertEquals(1, deploymentManager.getDeployments("other-server-group").size());
    }

    private void undeploy(final MatchPatternStrategy matchPatternStrategy) throws Exception {
        undeploy(matchPatternStrategy, "undeploy-webarchive-match-pom.xml");
    }

    private void undeploy(final MatchPatternStrategy matchPatternStrategy, final String pomName) throws Exception {

        final UndeployMojo undeployMojo = lookupMojoAndVerify("undeploy", pomName);

        undeployMojo.matchPatternStrategy = matchPatternStrategy.toString();
        undeployMojo.execute();
    }

}
