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

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collections;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.ChannelMetadataCoordinate;

/**
 *
 */
public class ChannelConfigurationTestCase {

    @Test
    public void testInvalid() throws Exception {
        {
            ChannelConfiguration configuration = new ChannelConfiguration();
            try {
                configuration.toChannel(Collections.emptyList());
                throw new Exception("Channel with no manifest is invalid");
            } catch (MojoExecutionException ex) {
                // XXX Expected.
            }
        }
        {
            ChannelConfiguration configuration = new ChannelConfiguration();
            try {
                ChannelManifestCoordinate coordinate = new ChannelManifestCoordinate();
                configuration.setManifest(coordinate);
                configuration.toChannel(Collections.emptyList());
                throw new Exception("Channel with empty manifest is invalid");
            } catch (MojoExecutionException ex) {
                // XXX Expected.
            }
        }
        {
            ChannelConfiguration configuration = new ChannelConfiguration();
            try {
                ChannelManifestCoordinate coordinate = new ChannelManifestCoordinate();
                Field url = ChannelMetadataCoordinate.class.getDeclaredField("url");
                url.setAccessible(true);
                url.set(coordinate, new URL("http://org.example"));
                Field grpId = ChannelMetadataCoordinate.class.getDeclaredField("groupId");
                grpId.setAccessible(true);
                grpId.set(coordinate, "org.example");
                configuration.setManifest(coordinate);
                configuration.toChannel(Collections.emptyList());
                throw new Exception("Channel manifest with both url and groupId is invalid");
            } catch (MojoExecutionException ex) {
                // XXX Expected.
            }
        }
        {
            ChannelConfiguration configuration = new ChannelConfiguration();
            try {
                ChannelManifestCoordinate coordinate = new ChannelManifestCoordinate();
                Field url = ChannelMetadataCoordinate.class.getDeclaredField("url");
                url.setAccessible(true);
                url.set(coordinate, new URL("http://org.example"));
                Field artId = ChannelMetadataCoordinate.class.getDeclaredField("artifactId");
                artId.setAccessible(true);
                artId.set(coordinate, "org.example");
                configuration.setManifest(coordinate);
                configuration.toChannel(Collections.emptyList());
                throw new Exception("Channel manifest with both url and artifactId is invalid");
            } catch (MojoExecutionException ex) {
                // XXX Expected.
            }
        }
        {
            ChannelConfiguration configuration = new ChannelConfiguration();
            try {
                ChannelManifestCoordinate coordinate = new ChannelManifestCoordinate();
                Field url = ChannelMetadataCoordinate.class.getDeclaredField("url");
                url.setAccessible(true);
                url.set(coordinate, new URL("http://org.example"));
                Field version = ChannelMetadataCoordinate.class.getDeclaredField("version");
                version.setAccessible(true);
                version.set(coordinate, "1.0");
                configuration.setManifest(coordinate);
                configuration.toChannel(Collections.emptyList());
                throw new Exception("Channel manifest with both url and version is invalid");
            } catch (MojoExecutionException ex) {
                // XXX Expected.
            }
        }
        {
            ChannelConfiguration configuration = new ChannelConfiguration();
            try {
                ChannelManifestCoordinate coordinate = new ChannelManifestCoordinate();
                Field grpId = ChannelMetadataCoordinate.class.getDeclaredField("groupId");
                grpId.setAccessible(true);
                grpId.set(coordinate, "org.example");
                configuration.setManifest(coordinate);
                configuration.toChannel(Collections.emptyList());
                throw new Exception("Channel manifest with null artifactId is invalid");
            } catch (MojoExecutionException ex) {
                // XXX Expected.
            }
        }
        {
            ChannelConfiguration configuration = new ChannelConfiguration();
            try {
                ChannelManifestCoordinate coordinate = new ChannelManifestCoordinate();
                Field artId = ChannelMetadataCoordinate.class.getDeclaredField("artifactId");
                artId.setAccessible(true);
                artId.set(coordinate, "org.example");
                configuration.setManifest(coordinate);
                configuration.toChannel(Collections.emptyList());
                throw new Exception("Channel manifest with null groupId is invalid");
            } catch (MojoExecutionException ex) {
                // XXX Expected.
            }
        }
        {
            try {
                ChannelConfiguration configuration = new ChannelConfiguration();
                ChannelManifestCoordinate coordinate = new ChannelManifestCoordinate();
                Field grpId = ChannelMetadataCoordinate.class.getDeclaredField("groupId");
                grpId.setAccessible(true);
                grpId.set(coordinate, "org.example");
                Field artId = ChannelMetadataCoordinate.class.getDeclaredField("artifactId");
                artId.setAccessible(true);
                artId.set(coordinate, "org.example");
                configuration.setManifest(coordinate);
                configuration.setManifest(coordinate);
                configuration.toChannel(Collections.emptyList());
                throw new Exception("Channel with multiple manifest is invalid");
            } catch (MojoExecutionException ex) {
                // XXX Expected.
            }
        }
    }

    @Test
    public void testValid() throws Exception {
        {
            ChannelConfiguration configuration = new ChannelConfiguration();
            ChannelManifestCoordinate coordinate = new ChannelManifestCoordinate();
            Field grpId = ChannelMetadataCoordinate.class.getDeclaredField("groupId");
            grpId.setAccessible(true);
            grpId.set(coordinate, "org.example");
            Field artId = ChannelMetadataCoordinate.class.getDeclaredField("artifactId");
            artId.setAccessible(true);
            artId.set(coordinate, "org.example");
            configuration.setManifest(coordinate);
            configuration.toChannel(Collections.emptyList());
        }

        {
            ChannelConfiguration configuration = new ChannelConfiguration();
            ChannelManifestCoordinate coordinate = new ChannelManifestCoordinate();
            Field grpId = ChannelMetadataCoordinate.class.getDeclaredField("groupId");
            grpId.setAccessible(true);
            grpId.set(coordinate, "org.example");
            Field artId = ChannelMetadataCoordinate.class.getDeclaredField("artifactId");
            artId.setAccessible(true);
            artId.set(coordinate, "org.example");
            Field vers = ChannelMetadataCoordinate.class.getDeclaredField("version");
            vers.setAccessible(true);
            vers.set(coordinate, "1.0");
            configuration.setManifest(coordinate);
            configuration.toChannel(Collections.emptyList());
        }

        {
            ChannelConfiguration configuration = new ChannelConfiguration();
            ChannelManifestCoordinate coordinate = new ChannelManifestCoordinate();
            Field url = ChannelMetadataCoordinate.class.getDeclaredField("url");
            url.setAccessible(true);
            url.set(coordinate, new URL("http://org.example"));
            configuration.setManifest(coordinate);
            configuration.toChannel(Collections.emptyList());
        }
    }

    @Test
    public void testFileUrl() throws Exception {
        {
            // Make sure that the notation "file:relative/path" is handled like a file URL, rather than a Maven G:A.

            String url = "file:path/to/manifest.yaml";
            ChannelConfiguration configuration = new ChannelConfiguration();
            configuration.set(url);
            Channel channel = configuration.toChannel(Collections.emptyList());

            Assert.assertNotNull(channel.getManifestCoordinate().getUrl());
            Assert.assertEquals(url, channel.getManifestCoordinate().getUrl().toExternalForm());
        }

        {
            // The notation "file://relative/path" should still be handled like a file URL too.

            String url = "file://path/to/manifest.yaml";
            ChannelConfiguration configuration = new ChannelConfiguration();
            configuration.set(url);
            Channel channel = configuration.toChannel(Collections.emptyList());

            Assert.assertNotNull(channel.getManifestCoordinate().getUrl());
            Assert.assertEquals(url, channel.getManifestCoordinate().getUrl().toExternalForm());
        }
    }
}
