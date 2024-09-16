/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.repository.RemoteRepository;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;

/**
 * A channel configuration. Contains a {@code manifest} composed of a {@code groupId}, an {@code artifactId}
 * an optional {@code version} or a {@code url}.
 *
 * Optionally can declare if the channel requires GPG signature validation ({@code gpgCheck}) and a list of GPG public
 * keys used to verify them ({@code gpgUrls}).
 *
 * @author jdenise
 */
public class ChannelConfiguration {
    private static final Pattern FILE_MATCHER = Pattern.compile("^(file:|http://|https://).*");

    private ChannelManifestCoordinate manifest;

    private boolean multipleManifest;
    private String name;

    private boolean gpgCheck;

    private List<URL> gpgUrls;

    /**
     * @return the manifest
     */
    public ChannelManifestCoordinate getManifest() {
        return manifest;
    }

    public void set(final String channel) {
        // Is this a URL?
        if (FILE_MATCHER.matcher(channel).matches()) {
            try {
                this.manifest = new ChannelManifestCoordinate(new URL(channel));
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Failed to parse URL for " + channel, e);
            }
        } else {
            // Treat as a Maven GAV
            final String[] coords = channel.split(":");
            if (coords.length > 2) {
                this.manifest = new ChannelManifestCoordinate(coords[0], coords[1], coords[2]);
            } else if (coords.length == 2) {
                this.manifest = new ChannelManifestCoordinate(coords[0], coords[1]);
            } else {
                throw new IllegalArgumentException(
                        "A channel must be a Maven GAV in the format groupId:artifactId:version. The groupId and artifactId are both required.");
            }
        }
    }

    /**
     * Set the name of the channel.
     * This information is stored in the .installation directory of the provisioned server.
     *
     * @param name The name of the channel. Can be {@code null}.
     */
    public void setName(final String name) {
        this.name = name;
    }

    public void setManifest(ChannelManifestCoordinate manifest) {
        if (this.manifest != null) {
            multipleManifest = true;
        }
        this.manifest = manifest;
    }

    private void validate() throws MojoExecutionException {
        if (getManifest() == null) {
            throw new MojoExecutionException("Invalid Channel. No manifest specified.");
        }
        if (multipleManifest) {
            throw new MojoExecutionException("Invalid Channel. More than one manifest has been set.");
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
        final Channel.Builder builder = new Channel.Builder()
                .setManifestCoordinate(getManifest())
                .setGpgCheck(gpgCheck);

        if (gpgUrls != null) {
            gpgUrls.stream().map(URL::toExternalForm).forEach(builder::addGpgUrl);
        }

        for (RemoteRepository r : repositories) {
            builder.addRepository(r.getId(), r.getUrl());
        }

        return builder.build();
    }
}
