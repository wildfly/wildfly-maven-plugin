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

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.repository.RemoteRepository;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.InvalidChannelMetadataException;
import org.wildfly.channel.Repository;

/**
 * A channel configuration. One of url or manifest coordinates.
 *
 * @author jdenise
 */
public class ChannelConfiguration {

    private URL url;
    private ChannelManifestCoordinate manifestCoordinate;

    /**
     * @return the url
     */
    public URL getUrl() {
        return url;
    }

    /**
     * @return the manifestCoordinate
     */
    public ChannelManifestCoordinate getManifestCoordinate() {
        return manifestCoordinate;
    }

    private void validate() throws MojoExecutionException {
        if (getUrl() != null) {
            if (getManifestCoordinate() != null) {
                throw new MojoExecutionException("Invalid Channel. A manifest-coordinate is specified although an URL is provided.");
            }
        } else {
            if (getManifestCoordinate() == null) {
                throw new MojoExecutionException("Invalid Channel. No manifest-coordinate or URL specified.");
            } else {
                ChannelManifestCoordinate coordinates = getManifestCoordinate();
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
                }
            }
        }
    }

    public Channel toChannel(Set<String> remoteRepositories, List<RemoteRepository> repositories, Log log) throws MojoExecutionException {
        validate();
        Channel channel;
        if (getUrl() == null) {
            List<Repository> repos = new ArrayList<>();
            for (RemoteRepository r : repositories) {
                repos.add(new Repository(r.getId(), r.getUrl()));
            }
            channel = new Channel(null, null, null, repos, getManifestCoordinate(), null, null);
        } else {
            try {
                channel = ChannelMapper.from(getUrl());
                for (Repository r : channel.getRepositories()) {
                    if (!remoteRepositories.contains(r.getId())) {
                        log.warn("Repository id " + r.getId() + " defined in channel " + getUrl()
                                + " is not found in the configured Maven "
                                + "repositories. Will create a new repository.");
                    }
                }
            } catch (InvalidChannelMetadataException ex) {
                throw new MojoExecutionException("Invalid Channel: "
                        + (ex.getValidationMessages() == null ? "" : ex.getValidationMessages()), ex);
            }
        }
        return channel;
    }
}
