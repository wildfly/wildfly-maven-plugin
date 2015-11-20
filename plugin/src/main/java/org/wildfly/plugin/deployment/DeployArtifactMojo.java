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

package org.wildfly.plugin.deployment;

import java.io.File;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.common.DeploymentFailureException;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.deployment.Deployment.Type;

/**
 * Deploys an arbitrary artifact to the WildFly application server
 *
 * @author Stuart Douglas
 */
@Mojo(name = "deploy-artifact", requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public final class DeployArtifactMojo extends AbstractDeployment {

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

    /**
     * The artifact to deploys classifier. Note that the classifier must also be set on the dependency being deployed.
     */
    @Parameter
    private String classifier;

    /**
     * Specifies whether force mode should be used or not.
     * </p>
     * If force mode is disabled, the deploy goal will cause a build failure if the application being deployed already
     * exists.
     */
    @Parameter(defaultValue = "true", property = PropertyNames.DEPLOY_FORCE)
    private boolean force;

    /**
     * The resolved dependency file
     */
    private File file;


    @Override
    public void validate(final ModelControllerClient client, final boolean isDomain) throws DeploymentFailureException {
        super.validate(client, isDomain);
        if (artifactId == null) {
            throw new DeploymentFailureException("deploy-artifact must specify the artifactId");
        }
        if (groupId == null) {
            throw new DeploymentFailureException("deploy-artifact must specify the groupId");
        }
        final Set<Artifact> dependencies = project.getDependencyArtifacts();
        Artifact artifact = null;
        for (final Artifact a : dependencies) {
            if (a.getArtifactId().equals(artifactId) &&
                    a.getGroupId().equals(groupId) &&
                    a.getClassifier().equals(classifier == null ? "" : classifier)) {
                artifact = a;
                break;
            }
        }
        if (artifact == null) {
            throw new DeploymentFailureException("Could not resolve artifact to deploy " + groupId + ":" + artifactId);
        }
        file = artifact.getFile();
    }

    @Override
    protected File file() {
        return file;
    }

    @Override
    public String goal() {
        return "deploy-artifact";
    }

    @Override
    public Type getType() {
        return (force ? Type.FORCE_DEPLOY : Type.DEPLOY);
    }
}
