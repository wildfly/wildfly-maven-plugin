/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.provision;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.MavenArtifact;

class MavenArtifactMapper {

    private final Collection<org.jboss.galleon.universe.maven.MavenArtifact> galleonArtifacts;
    private final HashMap<String, List<org.jboss.galleon.universe.maven.MavenArtifact>> artifactMap = new HashMap<>();

    public MavenArtifactMapper(Collection<org.jboss.galleon.universe.maven.MavenArtifact> galleonArtifacts) {
        this.galleonArtifacts = galleonArtifacts;

        for (org.jboss.galleon.universe.maven.MavenArtifact a : galleonArtifacts) {
            final String key = coordString(a.getGroupId(), a.getArtifactId(), a.getExtension(), a.getClassifier());
            if (!artifactMap.containsKey(key)) {
                artifactMap.put(key, new ArrayList<>());
            }

            artifactMap.get(key).add(a);
        }
    }

    private String coordString(String groupId, String artifactId, String extension, String classifier) {
        return String.format("%s:%s:%s:%s", groupId, artifactId, wrapNull(extension), wrapNull(classifier));
    }

    private String wrapNull(String value) {
        return value == null ? "" : value;
    }

    public List<ArtifactCoordinate> toChannelArtifacts() {
        return galleonArtifacts.stream()
                .map(a -> new ArtifactCoordinate(a.getGroupId(), a.getArtifactId(), a.getExtension(), a.getClassifier(),
                        a.getVersion() == null ? "" : a.getVersion()))
                .collect(Collectors.toList());
    }

    public static boolean isSameArtifact(MavenArtifact channelArtifact,
            org.jboss.galleon.universe.maven.MavenArtifact galleonArtifact) {
        return channelArtifact.getGroupId().equals(galleonArtifact.getGroupId()) &&
                channelArtifact.getArtifactId().equals(galleonArtifact.getArtifactId()) &&
                channelArtifact.getClassifier().equals(galleonArtifact.getClassifier()) &&
                channelArtifact.getExtension().equals(galleonArtifact.getExtension());
    }

    public Collection<org.jboss.galleon.universe.maven.MavenArtifact> applyResolution(List<MavenArtifact> channelArtifacts)
            throws MavenUniverseException {
        for (MavenArtifact channelArtifact : channelArtifacts) {
            String key = coordString(channelArtifact.getGroupId(), channelArtifact.getArtifactId(),
                    channelArtifact.getExtension(), channelArtifact.getClassifier());
            if (!artifactMap.containsKey(key)) {
                throw new MavenUniverseException("Unknown artifact " + key);
            }
            for (org.jboss.galleon.universe.maven.MavenArtifact a : artifactMap.get(key)) {
                resolve(a, channelArtifact);
            }
        }
        return galleonArtifacts;
    }

    /**
     * gets a list of artifact matching required {@code ArtifactCoordinate}
     *
     * @param coord
     * @return
     * @throws IllegalArgumentException if the artifact coordinates cannot be found
     */
    public List<org.jboss.galleon.universe.maven.MavenArtifact> get(ArtifactCoordinate coord) {
        String key = coordString(coord.getGroupId(), coord.getArtifactId(), coord.getExtension(), coord.getClassifier());
        if (!artifactMap.containsKey(key)) {
            throw new IllegalArgumentException("Artifact " + key + " not found.");
        }
        return artifactMap.get(key);
    }

    public static void resolve(org.jboss.galleon.universe.maven.MavenArtifact artifact, MavenArtifact resolvedArtifact) {
        Objects.requireNonNull(artifact);
        Objects.requireNonNull(resolvedArtifact);
        Objects.requireNonNull(resolvedArtifact.getFile());
        Objects.requireNonNull(resolvedArtifact.getVersion());

        artifact.setVersion(resolvedArtifact.getVersion());
        artifact.setPath(resolvedArtifact.getFile().toPath());
    }
}
