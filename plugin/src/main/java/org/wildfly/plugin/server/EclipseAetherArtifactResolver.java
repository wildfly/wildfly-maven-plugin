/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.server;

import java.io.File;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.RepositorySystem;
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
    public File resolve(final MavenProject project, final String artifact) {
        final ArtifactResult result;
        try {
            final ProjectBuildingRequest projectBuildingRequest = project.getProjectBuildingRequest();

            final ArtifactRequest request = new ArtifactRequest();
            final ArtifactNameSplitter splitter = ArtifactNameSplitter.of(artifact).split();
            final Artifact defaultArtifact = new DefaultArtifact(splitter.getGroupId(), splitter.getArtifactId(), splitter.getClassifier(), splitter.getPackaging(), splitter.getVersion());
            request.setArtifact(defaultArtifact);
            final List<RemoteRepository> repos = project.getRemoteProjectRepositories();
            request.setRepositories(repos);
            result = repoSystem.resolveArtifact(projectBuildingRequest.getRepositorySession(), request);
        } catch (ArtifactResolutionException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return result.getArtifact().getFile();
    }
}
