/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.plugin.tests;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.util.PathsUtils;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Before;

/**
 * A class to construct a properly configured MOJO.
 *
 */
@RunWith(JUnit4.class)
public abstract class AbstractProvisionConfiguredMojoTestCase extends AbstractMojoTestCase {
    private final String artifactId;

    protected AbstractProvisionConfiguredMojoTestCase(String artifactId) {
        this.artifactId = artifactId;
    }

    protected MavenSession newMavenSession() {
        try {
            MavenExecutionRequest request = new DefaultMavenExecutionRequest();
            MavenExecutionResult result = new DefaultMavenExecutionResult();

            MavenExecutionRequestPopulator populator;
            populator = getContainer().lookup(MavenExecutionRequestPopulator.class);
            populator.populateDefaults(request);
            // Required otherwise WARNING:The POM for org.wildfly.core:wildfly-jar-boot:jar:
            // is invalid, transitive dependencies (if any)
            //request.setSystemProperties(System.getProperties());

            DefaultMaven maven = (DefaultMaven) getContainer().lookup(Maven.class);
            DefaultRepositorySystemSession repoSession
                    = (DefaultRepositorySystemSession) maven.newRepositorySession(request);

            // Add remote repositories required to resolve provisioned artifacts.
            ArtifactRepositoryPolicy snapshot = new ArtifactRepositoryPolicy(false, ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE);
            ArtifactRepositoryPolicy release = new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_DAILY, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE);

            // Take into account maven.repo.local
            String path = System.getProperty("maven.repo.local", request.getLocalRepository().getBasedir());
            repoSession.setLocalRepositoryManager(
                    new SimpleLocalRepositoryManagerFactory().newInstance(repoSession,
                            new LocalRepository(path)));
            request.addRemoteRepository(new MavenArtifactRepository("jboss", "https://repository.jboss.org/nexus/content/groups/public/",
                    new DefaultRepositoryLayout(), snapshot, release));
            request.addRemoteRepository(new MavenArtifactRepository("redhat-ga", "https://maven.repository.redhat.com/ga/",
                    new DefaultRepositoryLayout(), snapshot, release));

            @SuppressWarnings("deprecation")
            MavenSession session = new MavenSession(getContainer(),
                    repoSession,
                    request, result);
            return session;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected MavenSession newMavenSession(MavenProject project) {
        MavenSession session = newMavenSession();
        session.setCurrentProject(project);
        session.setProjects(Arrays.asList(project));
        return session;
    }

    protected Mojo lookupConfiguredMojo(File pom, String goal) throws Exception {
        assertNotNull(pom);
        assertTrue(pom.exists());

        ProjectBuildingRequest buildingRequest = newMavenSession().getProjectBuildingRequest();
        // Need to resolve artifacts for tests that upgrade server components
        buildingRequest.setResolveDependencies(true);
        ProjectBuilder projectBuilder = lookup(ProjectBuilder.class);
        MavenProject project = projectBuilder.build(pom, buildingRequest).getProject();


        Mojo mojo = lookupConfiguredMojo(project, goal);

        // For some reasons, the configuration item gets ignored in lookupConfiguredMojo
        // explicitly configure it
        configureMojo(mojo, artifactId, pom);

        return mojo;
    }

    @Before
    public void before() throws Exception {
        super.setUp();
    }

     public void checkStandaloneWildFlyHome(Path wildflyHome, int numDeployments,
            String[] layers, String[] excludedLayers, boolean stateRecorded, String... configTokens) throws Exception {
        Assert.assertTrue(TestEnvironment.isValidWildFlyHome(wildflyHome));
        if (numDeployments > 0) {
            // Must retrieve all content directories.
            Path rootDir = wildflyHome.resolve("standalone/data/content");
            List<Path> deployments = new ArrayList<>();
            Files.walkFileTree(rootDir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        if ("content".equals(file.getFileName().toString())) {
                            deployments.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            assertEquals(numDeployments, deployments.size());
        } else {
            // The directory should be empty if no deployment is expected, however in some cases it may not even be
            // created.
            if (Files.exists(wildflyHome.resolve("standalone/data/content"))) {
                assertEquals(0, Files.list(wildflyHome.resolve("standalone/data/content")).count());
            }
        }
        Path history = wildflyHome.resolve("standalone").resolve("configuration").resolve("standalone_xml_history");
        assertFalse(Files.exists(history));

        Path configFile = wildflyHome.resolve("standalone/configuration/standalone.xml");
        assertTrue(Files.exists(configFile));
        if (layers != null) {
            Path provisioning = PathsUtils.getProvisioningXml(wildflyHome);
            assertTrue(Files.exists(provisioning));
            ProvisioningConfig config = ProvisioningXmlParser.parse(provisioning);
            ConfigModel cm = config.getDefinedConfig(new ConfigId("standalone", "standalone.xml"));
            assertNotNull(config.getDefinedConfigs().toString(), cm);
            assertEquals(layers.length, cm.getIncludedLayers().size());
            for (String layer : layers) {
                assertTrue(cm.getIncludedLayers().contains(layer));
            }
            if (excludedLayers != null) {
                for (String layer : excludedLayers) {
                    assertTrue(cm.getExcludedLayers().contains(layer));
                }
            }
        }
        if (configTokens != null) {
            String str = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
            for (String token : configTokens) {
                assertTrue(str, str.contains(token));
            }
        }
         assertEquals(Files.exists(wildflyHome.resolve(".galleon")), stateRecorded);
         assertEquals(Files.exists(wildflyHome.resolve(".wildfly-maven-plugin-provisioning.xml")), !stateRecorded);
    }

     public void checkDomainWildFlyHome(Path wildflyHome, int numDeployments,
            boolean stateRecorded, String... configTokens) throws Exception {
        Assert.assertTrue(TestEnvironment.isValidWildFlyHome(wildflyHome));
        if (numDeployments > 0) {
            // Must retrieve all content directories.
            Path rootDir = wildflyHome.resolve("domain/data/content");
            List<Path> deployments = new ArrayList<>();
            Files.walkFileTree(rootDir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        if ("content".equals(file.getFileName().toString())) {
                            deployments.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            assertEquals(numDeployments, deployments.size());
        } else {
            // The directory should be empty if no deployment is expected, however in some cases it may not even be
            // created.
            if (Files.exists(wildflyHome.resolve("domain/data/content"))) {
                assertEquals(0, Files.list(wildflyHome.resolve("domain/data/content")).count());
            }
        }
        Path history = wildflyHome.resolve("domain").resolve("configuration").resolve("domain_xml_history");
        assertFalse(Files.exists(history));

        Path configFile = wildflyHome.resolve("domain/configuration/domain.xml");
        assertTrue(Files.exists(configFile));

        if (configTokens != null) {
            String str = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
            for (String token : configTokens) {
                assertTrue(str, str.contains(token));
            }
        }
         assertEquals(Files.exists(wildflyHome.resolve(".galleon")), stateRecorded);
         assertEquals(Files.exists(wildflyHome.resolve(".wildfly-maven-plugin-provisioning.xml")), !stateRecorded);
    }
}
