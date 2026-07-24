/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.jboss.galleon.api.MavenStreamResolver;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.ZipUtils;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.ArtifactTransferException;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.NoStreamFoundException;
import org.wildfly.channel.Repository;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.VersionResult;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.ChannelResolvable;
import org.wildfly.prospero.metadata.ManifestVersionRecord;
import org.wildfly.prospero.metadata.ManifestVersionResolver;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;

public class ChannelMavenArtifactRepositoryManager implements MavenRepoManager, ChannelResolvable, MavenStreamResolver {

    private static final String REQUIRE_CHANNEL_FOR_ALL_ARTIFACT = "org.wildfly.plugins.galleon.all.artifact.requires.channel.resolution";

    private final ChannelSession channelSession;
    private final List<Channel> channels = new ArrayList<>();
    private final Log log;
    private final RepositorySystem system;
    private final DefaultRepositorySystemSession session;
    private final List<RemoteRepository> repositories;

    public ChannelMavenArtifactRepositoryManager(List<ChannelConfiguration> channels,
            RepositorySystem system,
            RepositorySystemSession contextSession,
            List<RemoteRepository> repositories, Log log, boolean offline)
            throws MalformedURLException, UnresolvedMavenArtifactException, MojoExecutionException {
        if (channels.isEmpty()) {
            throw new MojoExecutionException("No channel specified.");
        }
        this.log = log;
        session = new DefaultRepositorySystemSession(contextSession);
        this.repositories = repositories;
        session.setOffline(offline);
        for (ChannelConfiguration channelConfiguration : channels) {
            this.channels.add(channelConfiguration.toChannel(offline ? Collections.emptyList() : repositories));
        }
        VersionResolverFactory factory;
        if (offline) {
            factory = new VersionResolverFactory(system, session);
        } else {
            Map<String, RemoteRepository> mapping = new HashMap<>();
            for (RemoteRepository r : repositories) {
                mapping.put(r.getId(), r);
            }
            Function<Repository, RemoteRepository> mapper = r -> {
                RemoteRepository rep = mapping.get(r.getId());
                if (rep == null) {
                    rep = DEFAULT_REPOSITORY_MAPPER.apply(r);
                }
                return rep;
            };
            factory = new VersionResolverFactory(system, session, mapper);
        }
        channelSession = new ChannelSession(this.channels, factory);
        this.system = system;
    }

    /**
     * Bulk artifact pre-fetch. The default {@link MavenRepoManager#resolveAll}
     * iterates {@link #resolve(MavenArtifact)} sequentially: one HTTP round-trip
     * per artifact, dominating cold-cache provisioning time. This override pre-fetches
     * all artifacts that already have a concrete version in one call to Aether's
     * {@link RepositorySystem#resolveArtifacts(RepositorySystemSession, java.util.Collection)},
     * which uses Aether's connector thread pool (default 5 threads) to download in
     * parallel. After the pre-fetch every {@link #resolve(MavenArtifact)} call hits
     * the local cache.
     *
     * <p>
     * This path is only triggered when the Galleon provisioning option
     * {@code jboss-bulk-resolve-artifacts} is set to {@code true} (see
     * {@code WfInstallPlugin.resolveArtifactsInCache}). Without that option the
     * upstream plugin keeps calling {@link #resolve(MavenArtifact)} per artifact
     * and this override is unused.
     *
     * <p>
     * The set of artifacts must be split in 2 groups, the ones that require resolution from the channel,
     * the ones that can be resolved directly. The failure handling is different in the two groups.
     */
    @Override
    public void resolveAll(Collection<MavenArtifact> artifacts) throws MavenUniverseException {
        if (artifacts == null || artifacts.isEmpty()) {
            return;
        }
        // split the artifacts into requiring channels and not requiring channels
        final List<MavenArtifact> artifactsRequiringChannels = artifacts.stream()
                .filter(a -> requiresChannel(a))
                .collect(Collectors.toList());
        final List<MavenArtifact> artifactsNotRequiringChannels = artifacts.stream()
                .filter(a -> !requiresChannel(a))
                .collect(Collectors.toList());
        // bulk resolve artifacts requiring channels - if any fail, throw exception
        MavenArtifactMapper mapper = new MavenArtifactMapper(artifactsRequiringChannels);
        List<org.wildfly.channel.MavenArtifact> channelArtifacts = channelSession
                .resolveMavenArtifacts(mapper.toChannelArtifacts());
        mapper.applyResolution(channelArtifacts);

        // bulk resolve other artifacts, failure are expected and resolution occurs with the original version
        final MavenArtifactMapper mapperNotRequiringChannels = new MavenArtifactMapper(artifactsNotRequiringChannels);
        resolveArtifactsWithFallbackVersions(mapperNotRequiringChannels, mapperNotRequiringChannels.toChannelArtifacts());
    }

    private void resolveArtifactsWithFallbackVersions(MavenArtifactMapper mapperNotRequiringChannels,
            List<ArtifactCoordinate> coordinates) throws MavenUniverseException {
        List<org.wildfly.channel.MavenArtifact> channelArtifacts;
        try {
            channelArtifacts = channelSession.resolveMavenArtifacts(coordinates);
            mapperNotRequiringChannels.applyResolution(channelArtifacts);
        } catch (ArtifactTransferException e) {
            throw new MavenUniverseException(e.getLocalizedMessage(), e);
        } catch (NoStreamFoundException e) {
            handleMissingStreams(mapperNotRequiringChannels, coordinates, e);
        } catch (UnresolvedMavenArtifactException e) {
            throw new MavenUniverseException(e.getLocalizedMessage(), e);
        }
    }

    /**
     * The failed artifacts are resolved directly, the others are resolved in bulk.
     *
     * @param mapperNotRequiringChannels
     * @param coordinates
     * @param e
     * @throws MavenUniverseException
     */
    private void handleMissingStreams(MavenArtifactMapper mapperNotRequiringChannels, List<ArtifactCoordinate> coordinates,
            UnresolvedMavenArtifactException e) throws MavenUniverseException {
        final Set<ArtifactCoordinate> unresolvedArtifacts = e.getUnresolvedArtifacts();
        // resolve unresolvedArtifacts directly
        for (ArtifactCoordinate a : unresolvedArtifacts) {
            final List<MavenArtifact> missingArtifacts = mapperNotRequiringChannels.get(
                    new ArtifactCoordinate(a.getGroupId(), a.getArtifactId(),
                            a.getExtension(), a.getClassifier(), a.getVersion()));
            for (MavenArtifact missingArtifact : missingArtifacts) {
                if (missingArtifact.getVersion() == null) {
                    throw new MavenUniverseException(e.getLocalizedMessage(), e);
                }
                final org.wildfly.channel.MavenArtifact mavenArtifact = channelSession.resolveDirectMavenArtifact(
                        missingArtifact.getGroupId(), missingArtifact.getArtifactId(),
                        missingArtifact.getExtension(), missingArtifact.getClassifier(), missingArtifact.getVersion());
                missingArtifact.setPath(mavenArtifact.getFile().toPath());
            }
        }
        // remove unresolvedArtifacts from the list of artifact to resolve
        final List<ArtifactCoordinate> requests = new ArrayList<>();
        for (ArtifactCoordinate a : coordinates) {
            if (!unresolvedArtifacts.contains(
                    new ArtifactCoordinate(a.getGroupId(), a.getArtifactId(), a.getExtension(), a.getClassifier(), ""))) {
                requests.add(a);
            }
        }
        // try resolving the new list, handle missing artifacts (e.g. wrong versions)
        resolveArtifactsWithFallbackVersions(mapperNotRequiringChannels, requests);
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

    private boolean requiresChannel(MavenArtifact artifact) {
        // if the Galleon pack hasn't defined the version, it needs to come from channel
        if (artifact.getVersion() == null || artifact.getVersion().isEmpty()) {
            return true;
        }
        boolean requireChannel = Boolean.parseBoolean(artifact.getMetadata().get(REQUIRE_CHANNEL_FOR_ALL_ARTIFACT));
        try {
            return requireChannel || fpRequireChannel(artifact);
        } catch (Exception e) {
            throw new RuntimeException(e);
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
            if (!Provisioning.isFeaturePack(mavenArtifact.getFile().toPath())) {
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
        final ManifestVersionRecord currentVersions = ManifestVersionResolver.getCurrentVersions(channelSession);
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
        resolveLatestVersion(artifact, null, false);
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact, String lowestQualifier, boolean locallyAvailable)
            throws MavenUniverseException {
        artifact.setVersion(getLatestVersion(artifact));
        resolve(artifact);
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact) throws MavenUniverseException {
        return getLatestVersion(artifact, null, null, null);
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier) throws MavenUniverseException {
        return getLatestVersion(artifact, lowestQualifier, null, null);
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier, Pattern includeVersion,
            Pattern excludeVersion) throws MavenUniverseException {
        try {
            return channelSession.resolveMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                    artifact.getClassifier(), null).getVersion();
        } catch (UnresolvedMavenArtifactException e) {
            VersionRangeResult res = getVersionRange(new DefaultArtifact(artifact.getGroupId(),
                    artifact.getArtifactId(), artifact.getExtension(), artifact.getVersionRange()));
            return res.getHighestVersion().toString();
        }
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

    @Override
    public String getLatestVersion(String groupId, String artifactId, String extension, String classifier, String baseVersion) {
        VersionResult res = channelSession.findLatestMavenArtifactVersion(groupId, artifactId, extension, classifier,
                baseVersion);
        return res.getVersion();
    }

    private VersionRangeResult getVersionRange(Artifact artifact) throws MavenUniverseException {
        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact(artifact);
        rangeRequest.setRepositories(repositories);
        VersionRangeResult rangeResult;
        try {
            rangeResult = system.resolveVersionRange(session, rangeRequest);
        } catch (VersionRangeResolutionException ex) {
            throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
        }
        return rangeResult;
    }
}
