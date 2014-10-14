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

package org.wildfly.plugin.server;

import java.io.File;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;

/**
 * Resolves artifacts downloading the artifact if necessary.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Component(role = ArtifactResolver.class)
public interface ArtifactResolver {

    /**
     * Resolves the artifact downloading it if necessary.
     *
     * @param project  the maven project
     * @param artifact the artifact string in the {@code groupId:artifactId:version[:packaging][:classifier]} format
     *
     * @return the file associated with the artifact
     */
    File resolve(MavenProject project, String artifact);

    static class ArtifactNameSplitter {

        private final String artifact;
        private String groupId;
        private String artifactId;
        private String classifier;
        private String packaging;
        private String version;

        public ArtifactNameSplitter(final String artifact) {
            this.artifact = artifact;
        }

        public static ArtifactNameSplitter of(final String artifact) {
            return new ArtifactNameSplitter(artifact);
        }

        public String getGroupId() {
            return groupId;
        }

        public ArtifactNameSplitter setGroupId(final String groupId) {
            this.groupId = groupId;
            return this;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public ArtifactNameSplitter setArtifactId(final String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public String getClassifier() {
            return classifier;
        }

        public ArtifactNameSplitter setClassifier(final String classifier) {
            this.classifier = classifier;
            return this;
        }

        public String getPackaging() {
            return packaging;
        }

        public ArtifactNameSplitter setPackaging(final String packaging) {
            this.packaging = packaging;
            return this;
        }

        public String getVersion() {
            return version;
        }

        public ArtifactNameSplitter setVersion(final String version) {
            this.version = version;
            return this;
        }

        public ArtifactNameSplitter split() {
            if (artifact != null) {
                final String[] artifactSegments = artifact.split(":");
                if (artifactSegments.length == 0) {
                    throw new IllegalArgumentException(String.format("Invalid artifact pattern: %s", artifact));
                }
                // groupId:artifactId:version[:packaging][:classifier].
                String value;
                switch (artifactSegments.length) {
                    case 5:
                        value = artifactSegments[4].trim();
                        if (!value.isEmpty()) {
                            classifier = value;
                        }
                    case 4:
                        value = artifactSegments[3].trim();
                        if (!value.isEmpty()) {
                            packaging = value;
                        }
                    case 3:
                        value = artifactSegments[2].trim();
                        if (!value.isEmpty()) {
                            version = value;
                        }
                    case 2:
                        value = artifactSegments[1].trim();
                        if (!value.isEmpty()) {
                            artifactId = value;
                        }
                    case 1:
                        value = artifactSegments[0].trim();
                        if (!value.isEmpty()) {
                            groupId = value;
                        }
                }
            }
            return this;
        }

        public String asString() {
            split();
            if (version == null) {
                version = RuntimeVersions.getLatestFinal(groupId, artifactId);
            }
            // Validate the groupId, artifactId and version are not null
            if (groupId == null || artifactId == null || version == null) {
                throw new IllegalStateException("The groupId, artifactId and version parameters are required");
            }
            final StringBuilder result = new StringBuilder();
            result.append(groupId)
                    .append(':')
                    .append(artifactId)
                    .append(':')
                    .append(version)
                    .append(':');
            if (packaging != null) {
                result.append(packaging);
            }
            result.append(':');
            if (classifier != null) {
                result.append(classifier);
            }
            return result.toString();
        }
    }
}
