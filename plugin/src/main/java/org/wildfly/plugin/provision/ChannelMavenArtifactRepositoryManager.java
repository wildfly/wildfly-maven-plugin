/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

import static org.wildfly.channel.maven.VersionResolverFactory.DEFAULT_REPOSITORY_MAPPER;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.layout.FeaturePackDescriber;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.ZipUtils;
import org.wildfly.channel.ArtifactTransferException;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.NoStreamFoundException;
import org.wildfly.channel.Repository;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.ChannelResolvable;
import org.wildfly.prospero.metadata.ManifestVersionRecord;
import org.wildfly.prospero.metadata.ManifestVersionResolver;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;

public class ChannelMavenArtifactRepositoryManager implements MavenRepoManager, ChannelResolvable {

    private static final String REQUIRE_CHANNEL_FOR_ALL_ARTIFACT = "org.wildfly.plugins.galleon.all.artifact.requires.channel.resolution";

    private final ChannelSession channelSession;
    private final List<Channel> channels = new ArrayList<>();
    private final Log log;
    private final Path localCachePath;
    private final RepositorySystem system;

    public ChannelMavenArtifactRepositoryManager(List<ChannelConfiguration> channels,
            RepositorySystem system,
            RepositorySystemSession contextSession,
            List<RemoteRepository> repositories, Log log, boolean offline)
            throws MalformedURLException, UnresolvedMavenArtifactException, MojoExecutionException {
        if (channels.isEmpty()) {
            throw new MojoExecutionException("No channel specified.");
        }
        this.log = log;
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(contextSession.getLocalRepositoryManager());
        session.setOffline(offline);
        Map<String, RemoteRepository> mapping = new HashMap<>();
        for (RemoteRepository r : repositories) {
            mapping.put(r.getId(), r);
        }
        for (ChannelConfiguration channelConfiguration : channels) {
            this.channels.add(channelConfiguration.toChannel(repositories));
        }
        Function<Repository, RemoteRepository> mapper = r -> {
            RemoteRepository rep = mapping.get(r.getId());
            if (rep == null) {
                rep = DEFAULT_REPOSITORY_MAPPER.apply(r);
            }
            return rep;
        };
        VersionResolverFactory factory = new VersionResolverFactory(system, session, mapper);
        channelSession = new ChannelSession(this.channels, factory);
        localCachePath = contextSession.getLocalRepositoryManager().getRepository().getBasedir().toPath();
        this.system = system;
    }

    @Override
    public void resolve(MavenArtifact artifact) throws MavenUniverseException {
        try {
            resolveFromChannels(artifact);
        } catch (ArtifactTransferException ex) {
            throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
        } catch (NoStreamFoundException ex) {
            boolean requireChannel = Boolean.parseBoolean(artifact.getMetadata().get(REQUIRE_CHANNEL_FOR_ALL_ARTIFACT));
            if (!requireChannel) {
                // Could be a feature-pack that could require to be resolved from a channel.
                try {
                    requireChannel = fpRequireChannel(artifact);
                } catch (Exception exception) {
                    log.error("Error attempting to read artifact as a feature-pack", exception);
                    ex.addSuppressed(exception);
                    throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
                }
            }
            if (!requireChannel) {
                if (log.isDebugEnabled()) {
                    log.debug("Resolution of artifact " + artifact.getGroupId() + ":" +
                            artifact.getArtifactId() + " failed using configured channels. Using original version.");
                }
                // unable to resolve the artifact through the channel.
                // if the version is defined, let's resolve it directly
                if (artifact.getVersion() == null) {
                    log.error("No version provided.");
                    throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
                }
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Using version " + artifact.getVersion() +
                                " to resolve artifact " + artifact.getGroupId() + ":" +
                                artifact.getArtifactId());
                    }
                    org.wildfly.channel.MavenArtifact mavenArtifact = channelSession.resolveDirectMavenArtifact(
                            artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier(),
                            artifact.getVersion());
                    artifact.setPath(mavenArtifact.getFile().toPath());
                } catch (UnresolvedMavenArtifactException e) {
                    // if the artifact can not be resolved directly either, we abort
                    throw new MavenUniverseException(e.getLocalizedMessage(), e);
                }
            } else {
                throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
            }
        }
    }

    private boolean fpRequireChannel(MavenArtifact artifact) throws Exception {
        boolean requireChannel = false;
        if (artifact.getVersion() != null && artifact.getExtension() != null
                && artifact.getExtension().equalsIgnoreCase("zip")) {
            org.wildfly.channel.MavenArtifact mavenArtifact = channelSession.resolveDirectMavenArtifact(artifact.getGroupId(),
                    artifact.getArtifactId(),
                    artifact.getExtension(),
                    artifact.getClassifier(),
                    artifact.getVersion());
            try {
                FeaturePackDescriber.readSpec(mavenArtifact.getFile().toPath());
            } catch (ProvisioningException ex) {
                // Not a feature-pack
                return requireChannel;
            }
            try (FileSystem fs = ZipUtils.newFileSystem(mavenArtifact.getFile().toPath())) {
                Path resPath = fs.getPath("resources");
                final Path wfRes = resPath.resolve("wildfly");
                final Path channelPropsPath = wfRes.resolve("wildfly-channel.properties");
                if (Files.exists(channelPropsPath)) {
                    Properties props = new Properties();
                    try (BufferedReader reader = Files.newBufferedReader(channelPropsPath)) {
                        props.load(reader);
                    }
                    String resolution = props.getProperty("resolution");
                    if (resolution != null) {
                        requireChannel = "REQUIRED".equals(resolution) || "REQUIRED_FP_ONLY".equals(resolution);
                    }
                }
            }
        }
        return requireChannel;
    }

    private void resolveFromChannels(MavenArtifact artifact) throws UnresolvedMavenArtifactException {
        org.wildfly.channel.MavenArtifact result = channelSession.resolveMavenArtifact(artifact.getGroupId(),
                artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier(), artifact.getVersion());
        artifact.setVersion(result.getVersion());
        artifact.setPath(result.getFile().toPath());
    }

    public void done(Path home) throws MavenUniverseException, IOException {
        ChannelManifest channelManifest = channelSession.getRecordedChannel();
        final ManifestVersionRecord currentVersions = new ManifestVersionResolver(localCachePath, system)
                .getCurrentVersions(channels);
        ProsperoMetadataUtils.generate(home, channels, channelManifest, currentVersions);
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public boolean isResolved(MavenArtifact artifact) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public boolean isLatestVersionResolved(MavenArtifact artifact, String lowestQualifier) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact, String lowestQualifier, Pattern includeVersion,
            Pattern excludeVersion) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact, String lowestQualifier, boolean locallyAvailable)
            throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier, Pattern includeVersion,
            Pattern excludeVersion) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public List<String> getAllVersions(MavenArtifact artifact) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public List<String> getAllVersions(MavenArtifact artifact, Pattern includeVersion, Pattern excludeVersion)
            throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public void install(MavenArtifact artifact, Path path) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

}
