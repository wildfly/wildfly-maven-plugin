/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class PackageServerMojoDependenciesTestCase {

    @TempDir
    private Path directory;

    @ParameterizedTest
    @CsvSource({
            "1.0-SNAPSHOT, 1.0-20261009.180000-1, true",
            "1.0-SNAPSHOT, 1.0-SNAPSHOT, true",
            "1.0-20261009.180000-1, 1.0-20261009.180000-1, true",
            "1.0, 1.0, true",
            "1.0-SNAPSHOT, 2.0-20261009.180000-1, false",
            "1.0-20261009.180000-1, 1.0-20261009.180000-2, false",
            "1.0, 1.1, false"
    })
    public void testDependencyVersions(final String declaredVersion, final String resolvedVersion, final boolean included)
            throws Exception {
        final Dependency dependency = dependency(declaredVersion, null);
        final Artifact artifact = artifact(resolvedVersion, null);
        final Map<String, Path> deployments = deployments(List.of(dependency), artifact);
        Assertions.assertEquals(included ? Map.of(artifact.getFile().getName(), artifact.getFile().toPath()) : Map.of(),
                deployments);
    }

    @Test
    public void testTransitiveSnapshotIsExcluded() throws Exception {
        Assertions.assertTrue(deployments(List.of(), artifact("1.0-20261009.180000-1", null)).isEmpty());
    }

    @Test
    public void testSnapshotClassifierIsIncluded() throws Exception {
        final Artifact artifact = artifact("1.0-20261009.180000-1", "web");
        Assertions.assertEquals(Map.of(artifact.getFile().getName(), artifact.getFile().toPath()),
                deployments(List.of(dependency("1.0-SNAPSHOT", "web")), artifact));
    }

    @Test
    public void testDifferentClassifierIsExcluded() throws Exception {
        Assertions.assertTrue(deployments(List.of(dependency("1.0-SNAPSHOT", "other")),
                artifact("1.0-20261009.180000-1", "web")).isEmpty());
    }

    private Dependency dependency(final String version, final String classifier) {
        final Dependency dependency = new Dependency();
        dependency.setGroupId("org.example");
        dependency.setArtifactId("example-web");
        dependency.setVersion(version);
        dependency.setType("war");
        dependency.setClassifier(classifier);
        return dependency;
    }

    private Artifact artifact(final String version, final String classifier) {
        final Artifact artifact = new DefaultArtifact("org.example", "example-web", version, Artifact.SCOPE_COMPILE, "war",
                classifier, new DefaultArtifactHandler("war"));
        artifact.setFile(directory.resolve("example-web-" + version + ".war").toFile());
        return artifact;
    }

    private Map<String, Path> deployments(final List<Dependency> dependencies, final Artifact artifact) throws Exception {
        final PackageServerMojo mojo = new PackageServerMojo();
        mojo.project = new MavenProject();
        mojo.project.setDependencies(dependencies);
        mojo.project.setArtifacts(Set.of(artifact));
        final Field includes = PackageServerMojo.class.getDeclaredField("includedDependencies");
        includes.setAccessible(true);
        includes.set(mojo, Set.of("org.example::war"));
        return mojo.getDeployments();
    }
}
