/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wildfly.plugin.tests.TestEnvironment;
import org.wildfly.plugin.tools.DeploymentDescription;
import org.wildfly.plugin.tools.DeploymentManager;
import org.wildfly.plugin.tools.UndeployDescription;
import org.wildfly.plugin.tools.server.ServerManager;
import org.wildfly.testing.junit.extension.annotation.ServerResource;
import org.wildfly.testing.junit.extension.annotation.WildFlyTest;

/**
 * Matcher Undeployment test case.
 *
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 */
@MojoTest
@WildFlyTest
@Basedir(TestEnvironment.TEST_PROJECT_PATH)
public class UndeploymentMatchTest {

    @ServerResource
    private ServerManager serverManager;

    @ServerResource
    private DeploymentManager deploymentManager;

    @BeforeEach
    public void setup() throws Exception {
        deploymentManager.deploy(TestEnvironment.getDeployment().setName("test-undeploy-1.war"));
        deploymentManager.deploy(TestEnvironment.getDeployment().setName("test-undeploy-2.war"));
    }

    @AfterEach
    public void cleanup() throws Exception {
        deploymentManager.undeploy(UndeployDescription.of("test-undeploy-1.war"));
        deploymentManager.undeploy(UndeployDescription.of("test-undeploy-2.war"));
    }

    @Test
    @InjectMojo(goal = "undeploy", pom = "undeploy-webarchive-match-pom.xml")
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
    @InjectMojo(goal = "undeploy", pom = "undeploy-webarchive-match-pom.xml")
    @MojoParameter(name = "match-pattern-strategy", value = "fail")
    public void undeployFail(final UndeployMojo undeployMojo) {
        assertThrows(MojoDeploymentException.class, undeployMojo::execute);
    }

}
