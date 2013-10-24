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

package org.jboss.as.plugin.server;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Component(role = ArtifactResolver.class, hint = "eclipse")
class EclipseAetherArtifactResolver extends AbstractArtifactResolver<DefaultArtifact> implements ArtifactResolver {

    @Requirement
    private RepositorySystem repoSystem;

    @Override
    public File resolve(final MavenProject project, final String artifact) {
        final ArtifactResult result;
        try {
            ProjectBuildingRequest projectBuildingRequest = invoke(project, "getProjectBuildingRequest", ProjectBuildingRequest.class);

            final ArtifactRequest request = new ArtifactRequest();
            final DefaultArtifact defaultArtifact = createArtifact(artifact);
            request.setArtifact(defaultArtifact);
            @SuppressWarnings("unchecked")
            final List<RemoteRepository> repos = invoke(project, "getRemoteProjectRepositories", List.class);
            request.setRepositories(repos);
            result = repoSystem.resolveArtifact(invoke(projectBuildingRequest, "getRepositorySession", RepositorySystemSession.class), request);
        } catch (ArtifactResolutionException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return result.getArtifact().getFile();
    }

    @Override
    protected DefaultArtifact constructArtifact(final String groupId, final String artifactId, final String classifier, final String packaging, final String version) {
        return new DefaultArtifact(groupId, artifactId, classifier, packaging, version);
    }
}
