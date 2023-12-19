/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import org.wildfly.plugin.tests.AbstractWildFlyServerMojoTest;
import org.wildfly.plugin.tools.Deployment;
import org.wildfly.plugin.tools.DeploymentManager;
import org.wildfly.plugin.tools.UndeployDescription;

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
