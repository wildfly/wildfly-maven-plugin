/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Deploys an arbitrary artifact to the JBoss application server
 *
 * @author Stuart Douglas
 * @goal deploy-artifact
 */
public final class DeployArtifact extends Deploy {

    /**
     * The artifact to deploys groupId
     *
     * @parameter
     */
    private String groupId;


    /**
     * The artifact to deploys artifactId
     *
     * @parameter
     */
    private String artifactId;

    /**
     * @parameter default-value="${project}"
     * @readonly
     * @required
     */
    private MavenProject project;

    /**
     * The resolved dependency file
     */
    private File file;

    /**
     * The deployment name
     *
     * @parameter
     */
    private String name;


    @Override
    public DeploymentPlan createPlan(final DeploymentPlanBuilder builder) throws IOException, MojoFailureException {
        if (artifactId == null) {
            throw new MojoFailureException("deploy-artifact must specify the artifactId");
        }
        if (groupId == null) {
            throw new MojoFailureException("deploy-artifact must specify the groupId");
        }

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
            throw new MojoFailureException("Could not resolve artifact to deploy " + groupId + ":" + artifactId);
        }
        file = artifact.getFile();
        return super.createPlan(builder);
    }

    @Override
    public File file() {
        return file;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String goal() {
        return "deploy";
    }

}
