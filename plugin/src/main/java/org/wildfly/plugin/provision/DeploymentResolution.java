/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

/**
 * Resolve possible deployments from the Maven project dependencies.
 */
class DeploymentResolution {

    private static final String WAR = "war";
    private static final String EAR = "ear";
    private static final String RAR = "rar";
    private static final String JAR = "jar";
    private static final Set<String> SUPPORTED_DEPLOYMENT_TYPES = Set.of(EAR, JAR, RAR, WAR);

    static DeploymentResolution getInstance(MavenProject project) throws MojoExecutionException {
        return new DeploymentResolution(project);
    }

    private final Map<String, File> files = new HashMap<>();

    private DeploymentResolution(MavenProject project) throws MojoExecutionException {
        for (Artifact artifact : project.getArtifacts()) {
            if (SUPPORTED_DEPLOYMENT_TYPES.contains(artifact.getType())) {
                files.put(buildKey(artifact.getGroupId(), artifact.getArtifactId(),
                        artifact.getClassifier(), artifact.getType()), artifact.getFile());
            }
        }
    }

    File getFile(final String groupId, final String artifactId, final String classifier,
            final String type) throws MojoExecutionException {
        return files.get(buildKey(groupId, artifactId, classifier, type));
    }

    private String buildKey(String groupId, String artifactId, String classifier, String type) throws MojoExecutionException {
        if (groupId == null) {
            throw new MojoExecutionException("Deployment groupId can't be null");
        }
        if (artifactId == null) {
            throw new MojoExecutionException("Deployment artifactId can't be null");
        }
        if (type == null) {
            throw new MojoExecutionException("Deployment type can't be null");
        }
        if (!SUPPORTED_DEPLOYMENT_TYPES.contains(type)) {
            throw new MojoExecutionException(
                    "Deployment type " + type + " is not supported. Supported types are " + SUPPORTED_DEPLOYMENT_TYPES);
        }
        return groupId + ":" + artifactId + ":" + (classifier == null ? "" : classifier) + ":" + type;
    }
}
