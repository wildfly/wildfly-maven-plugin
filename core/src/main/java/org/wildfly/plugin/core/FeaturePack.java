/*
 * Copyright 2016-2022 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.plugin.core;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.apache.maven.shared.artifact.ArtifactCoordinate;
import org.apache.maven.shared.dependencies.DependableCoordinate;
import org.jboss.galleon.util.StringUtils;

/**
 *
 * @author Alexey Loubyanssky
 */
public class FeaturePack implements DependableCoordinate, ArtifactCoordinate {

    private String groupId;
    private String artifactId;
    private String version;
    private String type = "zip";
    private String classifier;
    private String extension = "zip";

    private boolean transitiveDep;
    private String location;

    private Boolean inheritConfigs;
    private List<ConfigurationId> includedConfigs = Collections.emptyList();
    private List<ConfigurationId> excludedConfigs = Collections.emptyList();

    private Boolean inheritPackages;
    private List<String> excludedPackages = Collections.emptyList();
    private List<String> includedPackages = Collections.emptyList();

    private Path path;

    @Override
    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        assertMavenCoordinates();
        this.groupId = groupId;
    }

    @Override
    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        assertMavenCoordinates();
        this.artifactId = artifactId;
    }

    @Override
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        assertMavenCoordinates();
        this.version = version;
    }

    @Override
    public String getType() {
        return type;
    }

    public void setType(String type) {
        assertMavenCoordinates();
        this.type = type;
    }

    @Override
    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        assertMavenCoordinates();
        this.classifier = classifier;
    }

    @Override
    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        assertMavenCoordinates();
        this.extension = extension;
    }

    public boolean isTransitive() {
        return transitiveDep;
    }

    public void setTransitive(boolean transitiveDep) {
        this.transitiveDep = transitiveDep;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        assertLocation();
        if (location == null) {
            throw new IllegalStateException("Feature-pack location can't be null");
        }
        location = location.trim();
        if (location.isEmpty()) {
            throw new IllegalStateException("Feature-pack location can't be empty");
        }
        this.location = location;
    }

    public Boolean isInheritPackages() {
        return inheritPackages;
    }

    public void setInheritPackages(boolean inheritPackages) {
        this.inheritPackages = inheritPackages;
    }

    public Boolean isInheritConfigs() {
        return inheritConfigs;
    }

    public void setInheritConfigs(boolean inheritConfigs) {
        this.inheritConfigs = inheritConfigs;
    }

    public List<ConfigurationId> getIncludedConfigs() {
        return includedConfigs;
    }

    public void setIncludedConfigs(List<ConfigurationId> includedConfigs) {
        this.includedConfigs = includedConfigs;
    }

    public List<ConfigurationId> getExcludedConfigs() {
        return excludedConfigs;
    }

    public void setExcludedConfigs(List<ConfigurationId> excludedConfigs) {
        this.excludedConfigs = excludedConfigs;
    }

    public List<String> getExcludedPackages() {
        return excludedPackages;
    }

    public void setExcludedPackages(List<String> excludedPackages) {
        this.excludedPackages = excludedPackages;
    }

    public List<String> getIncludedPackages() {
        return includedPackages;
    }

    public void setIncludedPackages(List<String> includedPackages) {
        this.includedPackages = includedPackages;
    }

    public void setPath(File path) {
        assertPathLocation();
        this.path = path.toPath().normalize();
    }

    public Path getNormalizedPath() {
        return path;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append('{');
        if (transitiveDep) {
            buf.append("transitive ");
        }
        if (location != null) {
            buf.append(location);
        } else {
            buf.append(groupId).append(':').append(artifactId).append(':').append(version);
        }
        buf.append(" inherit-packages=").append(inheritPackages);
        if (!includedPackages.isEmpty()) {
            buf.append(" included-packages=");
            StringUtils.appendList(buf, includedPackages);
        }
        if (!excludedPackages.isEmpty()) {
            buf.append(" excluded-packages=");
            StringUtils.appendList(buf, excludedPackages);
        }
        buf.append(" inherit-configs=").append(inheritConfigs);
        if (!includedConfigs.isEmpty()) {
            buf.append(" included-configs=");
            StringUtils.appendList(buf, includedConfigs);
        }
        if (!excludedConfigs.isEmpty()) {
            buf.append(" excluded-configs=");
            StringUtils.appendList(buf, excludedConfigs);
        }
        return buf.append('}').toString();
    }

    private void assertPathLocation() {
        if (groupId != null || artifactId != null || version != null) {
            throw new IllegalStateException(
                    "feature-pack Path cannot be used: feature-pack Maven coordinates have already been initialized");
        }
        if (location != null) {
            throw new IllegalStateException("feature-pack Path cannot be used: location has already been initialized");
        }
    }

    private void assertLocation() {
        if (groupId != null || artifactId != null || version != null) {
            throw new IllegalStateException(
                    "Location can't bet set, feature-pack Maven coordinates have already been initialized");
        }
        if (path != null) {
            throw new IllegalStateException("Location can't bet set, feature-pack Path has already been initialized");
        }
    }

    private void assertMavenCoordinates() {
        if (location != null) {
            throw new IllegalStateException(
                    "Feature-pack Maven coordinates cannot be used: feature-pack location has already been initialized");
        }
        if (path != null) {
            throw new IllegalStateException(
                    "Feature-pack Maven coordinates cannot be used: feature-pack Path has already been initialized");
        }
    }

    public void set(String location) {
        setLocation(location);
    }
}