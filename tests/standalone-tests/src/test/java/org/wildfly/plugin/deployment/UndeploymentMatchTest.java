/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import static org.junit.Assert.assertEquals;

import java.util.Set;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.plugin.core.DeploymentDescription;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.tests.AbstractWildFlyServerMojoTest;

/**
 * Matcher Undeployment test case.
 *
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 */
public class UndeploymentMatchTest extends AbstractWildFlyServerMojoTest {

    @Inject
    private DeploymentManager deploymentManager;

    @Before
    public void before() throws Exception {

        deploymentManager.deploy(getDeployment().setName("test-undeploy-1.war"));
        deploymentManager.deploy(getDeployment().setName("test-undeploy-2.war"));
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

    @Test(expected = MojoDeploymentException.class)
    public void undeployFail() throws Exception {
        undeploy(MatchPatternStrategy.FAIL);
    }

    @After
    public void after() throws Exception {
        undeploy(MatchPatternStrategy.ALL);
    }

    private void undeploy(MatchPatternStrategy matchPatternStrategy) throws Exception {

        final UndeployMojo undeployMojo = lookupMojoAndVerify("undeploy", "undeploy-webarchive-match-pom.xml");

        setValue(undeployMojo, "matchPatternStrategy", matchPatternStrategy.toString());
        undeployMojo.execute();
    }

}
