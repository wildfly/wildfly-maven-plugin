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

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.maven.ChannelCoordinate;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.ChannelResolvable;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class ChannelMavenArtifactRepositoryManager implements MavenRepoManager, ChannelResolvable {

    private final ChannelSession channelSession;
    private final MavenVersionsResolver resolver;

    public ChannelMavenArtifactRepositoryManager(List<ChannelCoordinate> channelCoords,
                                                 RepositorySystem system, RepositorySystemSession contextSession) throws MalformedURLException, UnresolvedMavenArtifactException {
        this(channelCoords, system, contextSession, null);
    }

    public ChannelMavenArtifactRepositoryManager(List<ChannelCoordinate> channelCoords,
                                                 RepositorySystem system,
                                                 RepositorySystemSession contextSession,
                                                 List<RemoteRepository> repositories) throws MalformedURLException, UnresolvedMavenArtifactException {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(contextSession.getLocalRepositoryManager());
        VersionResolverFactory factory = new VersionResolverFactory(system, session, repositories);
        resolver = factory.create();
        List<Channel> channels = factory.resolveChannels(channelCoords);
        channelSession = new ChannelSession(channels, factory);
    }

    @Override
    public void resolve(MavenArtifact artifact) throws MavenUniverseException {
        try {
            resolveFromChannels(artifact);
        } catch (UnresolvedMavenArtifactException ex) {
            // unable to resolve the artifact through the channel.
            // if the version is defined, let's resolve it directly
            if (artifact.getVersion() == null) {
                throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
            }
            try {
                org.wildfly.channel.MavenArtifact mavenArtifact = channelSession.resolveDirectMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier(), artifact.getVersion());
                artifact.setPath(mavenArtifact.getFile().toPath());
            } catch (UnresolvedMavenArtifactException e) {
                throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
            }
        }
    }

    private void resolveFromChannels(MavenArtifact artifact) throws UnresolvedMavenArtifactException {
        org.wildfly.channel.MavenArtifact result = channelSession.resolveMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier());
        artifact.setVersion(result.getVersion());
        artifact.setPath(result.getFile().toPath());
    }

    public void done(Path home) throws MavenUniverseException, IOException {
        Channel channel = channelSession.getRecordedChannel();
        Files.write(home.resolve(".channel.yaml"), ChannelMapper.toYaml(channel).getBytes());
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
    public void resolveLatestVersion(MavenArtifact artifact, String lowestQualifier, boolean locallyAvailable) throws MavenUniverseException {
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
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier, Pattern includeVersion, Pattern excludeVersion) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public List<String> getAllVersions(MavenArtifact artifact) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public List<String> getAllVersions(MavenArtifact artifact, Pattern includeVersion, Pattern excludeVersion) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public void install(MavenArtifact artifact, Path path) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

}
