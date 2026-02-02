/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wildfly.plugin.tests.TestEnvironment;
import org.wildfly.plugin.tools.DeploymentDescription;
import org.wildfly.plugin.tools.DeploymentManager;
import org.wildfly.plugin.tools.DeploymentResult;
import org.wildfly.plugin.tools.UndeployDescription;
import org.wildfly.plugin.tools.server.ServerManager;
import org.wildfly.testing.junit.extension.annotation.ServerResource;
import org.wildfly.testing.junit.extension.annotation.WildFlyDomainTest;

/**
 * Matcher Undeployment test case.
 *
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 */
@MojoTest
@WildFlyDomainTest
@Basedir(TestEnvironment.TEST_PROJECT_PATH)
public class UndeploymentMatchTest {
    private static final String DEPLOYMENT_NAME_1 = "test-undeploy-1.war";
    private static final String DEPLOYMENT_NAME_2 = "test-undeploy-2.war";

    private final Set<String> defaultServerGroups = Collections.singleton("main-server-group");

    @ServerResource
    private ServerManager serverManager;

    @ServerResource
    private DeploymentManager deploymentManager;

    @BeforeEach
    public void setup() throws Exception {
        deploymentManager
                .deploy(TestEnvironment.getDeployment().setName(DEPLOYMENT_NAME_1).addServerGroups(defaultServerGroups));
        deploymentManager
                .deploy(TestEnvironment.getDeployment().setName(DEPLOYMENT_NAME_2).addServerGroups(defaultServerGroups));
    }

    @AfterEach
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
    @InjectMojo(goal = "undeploy", pom = "undeploy-webarchive-match-pom.xml")
    @MojoParameter(name = "match-pattern-strategy", value = "all")
    public void undeployAll(final UndeployMojo undeployMojo) throws Exception {
        undeployMojo.execute();

        final Set<DeploymentDescription> deployments = deploymentManager.getDeployments();
        assertEquals(0, deployments.size());
    }

    @Test
    @InjectMojo(goal = "undeploy", pom = "undeploy-webarchive-match-pom.xml")
    @MojoParameter(name = "match-pattern-strategy", value = "first")
    public void undeployFirst(final UndeployMojo undeployMojo) throws Exception {
        undeployMojo.execute();

        final Set<DeploymentDescription> deployments = deploymentManager.getDeployments();
        assertEquals(1, deployments.size());
    }

    @Test
    @InjectMojo(goal = "undeploy", pom = "undeploy-multi-server-group-match-pom.xml")
    @MojoParameter(name = "match-pattern-strategy", value = "first")
    public void undeployFirstMultiServerGroup(final UndeployMojo undeployMojo) throws Exception {
        final String serverGroup = "other-server-group";
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME_1, serverGroup)) {
            final DeploymentResult result = deploymentManager
                    .forceDeploy(TestEnvironment.getDeployment().setName(DEPLOYMENT_NAME_1).addServerGroup(serverGroup));
            Assertions.assertTrue(result.successful(), result.getFailureMessage());
        }
        if (!deploymentManager.hasDeployment(DEPLOYMENT_NAME_2, serverGroup)) {
            final DeploymentResult result = deploymentManager
                    .forceDeploy(TestEnvironment.getDeployment().setName(DEPLOYMENT_NAME_2).addServerGroup(serverGroup));
            Assertions.assertTrue(result.successful(), result.getFailureMessage());
        }
        // Set up the other-server-group servers to ensure the full deployment process works correctly
        final ModelNode op = Operations.createOperation("start-servers",
                new ModelNode().setEmptyList().add(ClientConstants.SERVER_GROUP, "other-server-group"));
        op.get("blocking").set(true);
        serverManager.executeOperation(op);

        undeployMojo.execute();

        final Set<DeploymentDescription> deployments = deploymentManager.getDeployments();
        assertEquals(1, deployments.size());

        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME_2, "main-server-group"),
                "Deployment " + DEPLOYMENT_NAME_2 + " was not found on main-server-group");
        assertTrue(deploymentManager.hasDeployment(DEPLOYMENT_NAME_2, "other-server-group"),
                "Deployment " + DEPLOYMENT_NAME_2 + " was not found on other-server-group");
    }

    @Test
    @InjectMojo(goal = "undeploy", pom = "undeploy-webarchive-match-pom.xml")
    @MojoParameter(name = "match-pattern-strategy", value = "fail")
    public void undeployFail(final UndeployMojo undeployMojo) throws Exception {
        assertThrows(MojoDeploymentException.class, undeployMojo::execute);
    }

    @Test
    @InjectMojo(goal = "undeploy", pom = "undeploy-webarchive-match-pom.xml")
    @MojoParameter(name = "match-pattern-strategy", value = "all")
    public void testUndeployServerGroup(final UndeployMojo undeployMojo) throws Exception {
        final String deploymentName1 = "test.war";
        final String deploymentName2 = "test-qa.war";
        if (!deploymentManager.hasDeployment(deploymentName1, "main-server-group")) {
            deploymentManager
                    .deploy(TestEnvironment.getDeployment().setName(deploymentName1).addServerGroup("main-server-group"));
        }
        // This deployment should stay deployed since it's only on the other-server-group
        if (!deploymentManager.hasDeployment(deploymentName2, "other-server-group")) {
            deploymentManager
                    .deploy(TestEnvironment.getDeployment().setName(deploymentName2).addServerGroup("other-server-group"));
        }
        undeployMojo.execute();

        assertEquals(0, deploymentManager.getDeployments("main-server-group").size());
        assertEquals(1, deploymentManager.getDeployments("other-server-group").size());
    }

}
