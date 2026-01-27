/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.jboss.dmr.ModelNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.tests.TestEnvironment;
import org.wildfly.plugin.tools.Deployment;
import org.wildfly.plugin.tools.DeploymentManager;
import org.wildfly.plugin.tools.UndeployDescription;
import org.wildfly.plugin.tools.server.ServerManager;
import org.wildfly.testing.junit.extension.annotation.ServerResource;
import org.wildfly.testing.junit.extension.annotation.WildFlyTest;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@MojoTest
@WildFlyTest
@Basedir(TestEnvironment.TEST_PROJECT_PATH)
public class ArtifactDeploymentTest {

    private final String artifactName = "dummy.jar";

    @ServerResource
    private ServerManager serverManager;

    @ServerResource
    private DeploymentManager deploymentManager;

    @AfterEach
    public void cleanup() throws Exception {
        if (deploymentManager.hasDeployment(artifactName)) {
            deploymentManager.undeploy(UndeployDescription.of(artifactName));
        }
    }

    @Test
    @InjectMojo(goal = "deploy-artifact", pom = "deploy-artifact-pom.xml")
    public void testDeploy(final DeployArtifactMojo mojo) throws Exception {
        testDeploy(mojo, null);
    }

    @Test
    @InjectMojo(goal = "deploy-artifact", pom = "deploy-artifact-classifier-pom.xml")
    public void testDeployWithClassifier(final DeployArtifactMojo mojo) throws Exception {
        testDeploy(mojo, "classifier");
    }

    @Test
    @InjectMojo(goal = "undeploy-artifact", pom = "deploy-artifact-pom.xml")
    public void testUndeploy(final UndeployArtifactMojo mojo) throws Exception {
        testUndeploy(mojo, null);
    }

    @Test
    @InjectMojo(goal = "undeploy-artifact", pom = "deploy-artifact-classifier-pom.xml")
    public void testUndeployWithClassifier(final UndeployArtifactMojo mojo) throws Exception {
        testUndeploy(mojo, "classifier");
    }

    private void testDeploy(final DeployArtifactMojo mojo, final String classifier) throws Exception {
        if (deploymentManager.hasDeployment(artifactName)) {
            deploymentManager.undeploy(UndeployDescription.of(artifactName));
        }
        mojo.project.setArtifacts(Collections.singleton(createArtifact(classifier)));

        mojo.execute();

        // Verify deployed
        assertTrue(deploymentManager.hasDeployment(artifactName), "Deployment " + artifactName + " was not deployed");

        // /deployment=test.war :read-attribute(name=status)
        final ModelNode address = ServerOperations.createAddress("deployment", artifactName);
        final ModelNode op = ServerOperations.createReadAttributeOperation(address, "status");
        final ModelNode result = serverManager.executeOperation(op);

        assertEquals("OK", result.asString());
    }

    private void testUndeploy(final UndeployArtifactMojo mojo, final String classifier) throws Exception {
        if (!deploymentManager.hasDeployment(artifactName)) {
            deploymentManager.deploy(Deployment.of(Paths.get(TestEnvironment.TEST_PROJECT_PATH, artifactName)));
        }
        mojo.project.setArtifacts(Collections.singleton(createArtifact(classifier)));

        mojo.execute();

        // Verify undeployed
        assertFalse(deploymentManager.hasDeployment(artifactName), "Deployment " + artifactName + " was not undeployed");
    }

    private Artifact createArtifact(final String classifier) {
        final Artifact artifact = new DefaultArtifact("dummy", "dummy", "1.0.0", "provided", "jar", classifier,
                new DefaultArtifactHandler());
        artifact.setFile(new File(TestEnvironment.TEST_PROJECT_PATH, artifactName));
        return artifact;
    }
}
