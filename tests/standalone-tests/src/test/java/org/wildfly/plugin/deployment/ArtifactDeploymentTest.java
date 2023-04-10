/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.core.Deployment;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.core.UndeployDescription;
import org.wildfly.plugin.tests.AbstractWildFlyServerMojoTest;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ArtifactDeploymentTest extends AbstractWildFlyServerMojoTest {

    private final String artifactName = "dummy.jar";

    @Inject
    private DeploymentManager deploymentManager;

    @After
    public void cleanup() throws Exception {
        if (deploymentManager.hasDeployment(artifactName)) {
            deploymentManager.undeploy(UndeployDescription.of(artifactName));
        }
    }

    @Test
    public void testDeploy() throws Exception {
        final DeployArtifactMojo mojo = lookupMojoAndVerify("deploy-artifact", "deploy-artifact-pom.xml");
        testDeploy(mojo, null);
    }

    @Test
    public void testDeployWithClassifier() throws Exception {
        final DeployArtifactMojo mojo = lookupMojoAndVerify("deploy-artifact", "deploy-artifact-classifier-pom.xml");
        testDeploy(mojo, "classifier");
    }

    @Test
    public void testUndeploy() throws Exception {
        final UndeployArtifactMojo mojo = lookupMojoAndVerify("undeploy-artifact", "deploy-artifact-pom.xml");
        testUndeploy(mojo, null);
    }

    @Test
    public void testUndeployWithClassifier() throws Exception {
        final UndeployArtifactMojo mojo = lookupMojoAndVerify("undeploy-artifact", "deploy-artifact-classifier-pom.xml");
        testUndeploy(mojo, "classifier");
    }

    private void testDeploy(final DeployArtifactMojo mojo, final String classifier) throws Exception {
        if (deploymentManager.hasDeployment(artifactName)) {
            deploymentManager.undeploy(UndeployDescription.of(artifactName));
        }
        mojo.project.setDependencyArtifacts(Collections.singleton(createArtifact(classifier)));

        mojo.execute();

        // Verify deployed
        assertTrue("Deployment " + artifactName + " was not deployed", deploymentManager.hasDeployment(artifactName));

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", artifactName);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = executeOperation(op);

        assertEquals("OK", ServerOperations.readResultAsString(result));
    }

    private void testUndeploy(final UndeployArtifactMojo mojo, final String classifier) throws Exception {
        if (!deploymentManager.hasDeployment(artifactName)) {
            deploymentManager.deploy(Deployment.of(Paths.get(BASE_CONFIG_DIR, artifactName)));
        }
        mojo.project.setDependencyArtifacts(Collections.singleton(createArtifact(classifier)));

        mojo.execute();

        // Verify undeployed
        assertFalse("Deployment " + artifactName + " was not undeployed", deploymentManager.hasDeployment(artifactName));
    }

    private Artifact createArtifact(final String classifier) {
        final Artifact artifact = new DefaultArtifact("dummy", "dummy", "1.0.0", "provided", "jar", classifier,
                new DefaultArtifactHandler());
        artifact.setFile(new File(BASE_CONFIG_DIR, artifactName));
        return artifact;
    }
}
