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

package org.jboss.as.plugin.deployment;

import java.io.File;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.plugin.AbstractItTestCase;
import org.jboss.as.plugin.common.DeploymentInspector;
import org.jboss.as.plugin.common.ServerOperations;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Matcher Deployment test case.
 * 
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 */
public class RedeploymentMatchTest extends AbstractItTestCase {

    @ContainerResource
    private ManagementClient managementClient;

    @Before
    public void before() throws Exception {
        deploy("test-redeploy-1.war");
        deploy("test-redeploy-2.war");
    }

    @Test
    public void redeployDifferentName() throws Exception {

        final MavenProject mavenProject = createProject();

        final File pom = getPom("redeploy-webarchive-match-pom.xml");

        final AbstractDeployment redeployMojo = lookupMojoAndVerify("redeploy", pom);

        redeployMojo.project = mavenProject;
        redeployMojo.execute();

        List<String> deployments = DeploymentInspector.getDeployments(managementClient.getControllerClient(), "", ".*.war");
        assertEquals(1, deployments.size());
        assertTrue(deployments.contains("test.war"));

    }

    @Test
    public void redeployWithExistingName() throws Exception {

        final MavenProject mavenProject = createProject();

        final File pom = getPom("redeploy-webarchive-match-pom.xml");

        final AbstractDeployment redeployMojo = lookupMojoAndVerify("redeploy", pom);

        redeployMojo.project = mavenProject;
        redeployMojo.name = "test-redeploy-1.war";
        redeployMojo.execute();

        List<String> deployments = DeploymentInspector.getDeployments(managementClient.getControllerClient(), "", ".*.war");
        assertEquals(1, deployments.size());
        assertTrue(deployments.contains("test-redeploy-1.war"));

    }

    @After
    public void after() throws Exception {
        undeploy();
    }

    private void deploy(String deploymentName) throws Exception {
        final MavenProject mavenProject = createProject();

        final File pom = getPom("deploy-webarchive-pom.xml");

        final AbstractDeployment deployMojo = lookupMojoAndVerify("deploy", pom);

        deployMojo.project = mavenProject;
        deployMojo.name = deploymentName;
        deployMojo.execute();

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", deploymentName);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = executeOperation(managementClient.getControllerClient(), op);

        assertEquals("OK", ServerOperations.readResultAsString(result));
    }

    private void undeploy() throws Exception {
        final MavenProject mavenProject = createProject();

        final File pom = getPom("undeploy-webarchive-match-pom.xml");

        final AbstractDeployment redeployMojo = lookupMojoAndVerify("undeploy", pom);

        redeployMojo.project = mavenProject;
        redeployMojo.execute();
    }

    private MavenProject createProject() {
        final MavenProject mavenProject = new MavenProject();
        mavenProject.setPackaging("war");
        return mavenProject;
    }

}
