/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.repository;

/**
 * A builder to build an artifact name.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ArtifactNameBuilder {

    /**
     * The default group id
     */
    public static final String WILDFLY_GROUP_ID = "org.wildfly";
    /**
     * The default artifact id
     */
    public static final String WILDFLY_ARTIFACT_ID = "wildfly-dist";
    /*
     * The default packaging type
     */
    public static final String WILDFLY_PACKAGING = "zip";

    private final String artifact;
    private String groupId;
    private String artifactId;
    private String classifier;
    private String packaging;
    private String version;

    private ArtifactNameBuilder(final String artifact) {
        this.artifact = artifact;
    }

    /**
     * Creates an artifact builder based on the artifact.
     *
     * @param artifact the artifact string in the {@code groupId:artifactId:version[:packaging][:classifier]} format
     *                 or {@code null}
     *
     * @return a new builder
     */
    public static ArtifactNameBuilder of(final String artifact) {
        return new ArtifactNameBuilder(artifact);
    }

    /**
     * Creates an artifact builder based on the artifact.
     * <p>
     * If the {@link #setGroupId(String) groupId}, {@link #setArtifactId(String) artifactId},
     * {@link #setPackaging(String) packaging} or {@link #setVersion(String) version} is {@code null} defaults will be
     * used.
     * </p>
     *
     * @param artifact the artifact string in the {@code groupId:artifactId:version[:packaging][:classifier]} format
     *                 or {@code null}
     *
     * @return a new builder
     */
    public static ArtifactNameBuilder forRuntime(final String artifact) {
        return new ArtifactNameBuilder(artifact) {
            @Override
            public ArtifactName build() {
                final ArtifactName delegate = super.build();
                String groupId = delegate.getGroupId();
                if (groupId == null) {
                    groupId = WILDFLY_GROUP_ID;
                }
                String artifactId = delegate.getArtifactId();
                if (artifactId == null) {
                    artifactId = WILDFLY_ARTIFACT_ID;
                }
                String packaging = delegate.getPackaging();
                if (packaging == null) {
                    packaging = WILDFLY_PACKAGING;
                }
                String version = delegate.getVersion();
                if (version == null) {
                    version = Runtimes.getLatestFinal(groupId, artifactId);
                }
                return new ArtifactNameImpl(groupId, artifactId, delegate.getClassifier(), packaging, version);
            }
        };
    }

    /**
     * Sets the group id.
     *
     * @param groupId the group id
     *
     * @return this builder
     */
    public ArtifactNameBuilder setGroupId(final String groupId) {
        this.groupId = groupId;
        return this;
    }

    /**
     * Sets the artifact id.
     *
     * @param artifactId the artifact id
     *
     * @return this builder
     */
    public ArtifactNameBuilder setArtifactId(final String artifactId) {
        this.artifactId = artifactId;
        return this;
    }

    /**
     * Sets the classifier.
     *
     * @param classifier the classifier
     *
     * @return this builder
     */
    public ArtifactNameBuilder setClassifier(final String classifier) {
        this.classifier = classifier;
        return this;
    }

    /**
     * Sets the packaging.
     *
     * @param packaging the packaging
     *
     * @return this builder
     */
    public ArtifactNameBuilder setPackaging(final String packaging) {
        this.packaging = packaging;
        return this;
    }

    /**
     * Sets the version.
     *
     * @param version the version
     *
     * @return this builder
     */
    public ArtifactNameBuilder setVersion(final String version) {
        this.version = version;
        return this;
    }

    /**
     * Creates the final artifact name.
     *
     * @return the artifact name
     */
    public ArtifactName build() {
        String groupId = this.groupId;
        String artifactId = this.artifactId;
        String classifier = this.classifier;
        String packaging = this.packaging;
        String version = this.version;
        if (artifact != null && !artifact.isEmpty()) {
            final String[] artifactSegments = artifact.split(":");
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
        return new ArtifactNameImpl(groupId, artifactId, classifier, packaging, version);
    }

    private static class ArtifactNameImpl implements ArtifactName {

        private final String groupId;
        private final String artifactId;
        private final String classifier;
        private final String packaging;
        private final String version;

        private ArtifactNameImpl(final String groupId, final String artifactId, final String classifier, final String packaging, final String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.classifier = classifier;
            this.packaging = packaging;
            this.version = version;
        }

        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @Override
        public String getClassifier() {
            return classifier;
        }

        @Override
        public String getPackaging() {
            return packaging;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public String toString() {
            final StringBuilder result = new StringBuilder()
                    .append("ArtifactName(")
                    .append(groupId)
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
            return result.append(')').toString();
        }

    }
}
