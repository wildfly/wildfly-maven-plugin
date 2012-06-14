/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.plugin.deployment;

import java.io.File;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Undeploys (removes) an arbitrary artifact to the JBoss application server
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "undeploy-artifact", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.PACKAGE)
public final class UndeployArtifact extends Undeploy {

    /**
     * The artifact to deploys groupId
     */
    @Parameter
    private String groupId;


    /**
     * The artifact to deploys artifactId
     */
    @Parameter
    private String artifactId;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The resolved dependency file
     */
    private File file;


    @Override
    public void validate() throws MojoFailureException {
        if (artifactId == null) {
            throw new MojoFailureException("undeploy-artifact must specify the artifactId");
        }
        if (groupId == null) {
            throw new MojoFailureException("undeploy-artifact must specify the groupId");
        }
        @SuppressWarnings("unchecked")
        final Set<Artifact> dependencies = project.getArtifacts();
        Artifact artifact = null;
        for (final Artifact a : dependencies) {
            if (a.getArtifactId().equals(artifactId) &&
                    a.getGroupId().equals(groupId)) {
                artifact = a;
                break;
            }
        }
        if (artifact == null) {
            throw new MojoFailureException(String.format("Could not resolve artifact to deploy %s:%s", groupId, artifactId));
        }
        file = artifact.getFile();
    }

    @Override
    public File file() {
        return file;
    }

    @Override
    public String goal() {
        return "undeploy-artifact";
    }

    @Override
    protected boolean checkPackaging() {
        return false;
    }
}
