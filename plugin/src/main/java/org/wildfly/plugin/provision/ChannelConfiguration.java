/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.plugin.provision;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.repository.RemoteRepository;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;

/**
 * A channel configuration. Contains a {@code manifest} composed of a {@code groupId}, an {@code artifactId}
 * an optional {@code version} or a {@code url}.
 *
 * @author jdenise
 */
public class ChannelConfiguration {

    private ChannelManifestCoordinate manifest;

    /**
     * @return the manifest
     */
    public ChannelManifestCoordinate getManifest() {
        return manifest;
    }

    void setManifest(ChannelManifestCoordinate manifest) {
        this.manifest = manifest;
    }

    private void validate() throws MojoExecutionException {
        if (getManifest() == null) {
            throw new MojoExecutionException("Invalid Channel. No manifest specified.");
        }
        ChannelManifestCoordinate coordinates = getManifest();
        if (coordinates.getUrl() == null && coordinates.getGroupId() == null && coordinates.getArtifactId() == null) {
            throw new MojoExecutionException(
                    "Invalid Channel. Manifest must contain a groupId, artifactId and (optional) version or an url.");
        }
        if (coordinates.getUrl() == null) {
            if (coordinates.getGroupId() == null) {
                throw new MojoExecutionException("Invalid Channel. Manifest groupId is null.");
            }
            if (coordinates.getArtifactId() == null) {
                throw new MojoExecutionException("Invalid Channel. Manifest artifactId is null.");
            }
        } else {
            if (coordinates.getGroupId() != null) {
                throw new MojoExecutionException("Invalid Channel. Manifest groupId is set although an URL is provided.");
            }
            if (coordinates.getArtifactId() != null) {
                throw new MojoExecutionException("Invalid Channel. Manifest artifactId is set although an URL is provided.");
            }
            if (coordinates.getVersion() != null) {
                throw new MojoExecutionException("Invalid Channel. Manifest version is set although an URL is provided.");
            }
        }
    }

    public Channel toChannel(List<RemoteRepository> repositories) throws MojoExecutionException {
        validate();
        List<Repository> repos = new ArrayList<>();
        for (RemoteRepository r : repositories) {
            repos.add(new Repository(r.getId(), r.getUrl()));
        }
        return new Channel(null, null, null, repos, getManifest(), null, null);
    }
}
