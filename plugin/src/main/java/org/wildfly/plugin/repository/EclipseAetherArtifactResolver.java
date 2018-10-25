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

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Named
class EclipseAetherArtifactResolver implements ArtifactResolver {

    @Inject
    private RepositorySystem repoSystem;

    @Override
    public Path resolve(final RepositorySystemSession session, final List<RemoteRepository> repositories, final ArtifactName name) {
        final ArtifactResult result;
        try {
            final ArtifactRequest request = new ArtifactRequest();
            final Artifact defaultArtifact = new DefaultArtifact(name.getGroupId(), name.getArtifactId(), name.getClassifier(), name.getPackaging(), name.getVersion());
            request.setArtifact(defaultArtifact);
            request.setRepositories(repositories);
            result = repoSystem.resolveArtifact(session, request);
        } catch (ArtifactResolutionException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        if (!result.isResolved()) {
            throw new RuntimeException("Failed to resolve artifact " + name);
        }
        final Artifact artifact = result.getArtifact();
        final File artifactFile;
        if (artifact == null || (artifactFile = artifact.getFile()) == null) {
            throw new RuntimeException("Failed to resolve artifact " + name);
        }
        return artifactFile.toPath();
    }
}
