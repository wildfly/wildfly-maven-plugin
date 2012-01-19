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

import org.apache.maven.plugin.MojoFailureException;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;

import java.io.IOException;

import static org.jboss.as.plugin.deployment.Util.deployPlan;
import static org.jboss.as.plugin.deployment.Util.redeployPlan;

/**
 * Deploys the application to the JBoss Application Server.
 * <p/>
 * If {@code force} is set to {@code true}, the server is queried to see if the application already exists. If the
 * application already exists, the application is redeployed instead of deployed. If the application does not exist the
 * application is deployed as normal.
 * <p/>
 * If {@code force} is set to {@code false} and the application has already been deployed to the server, an error
 * will occur and the deployment will fail.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @goal deploy
 */
public class Deploy extends AbstractDeployment {

    /**
     * Specifies whether force mode should be used or not.
     * </p>
     * If force mode is disabled, the deploy goal will cause a build failure if the application being deployed already
     * exists.
     *
     * @parameter default-value="true" expression="${deploy.force}"
     */
    private boolean force;

    @Override
    public DeploymentPlan createPlan(final DeploymentPlanBuilder builder) throws IOException, MojoFailureException {
        final DeploymentPlan plan;
        if (force) {
            if (deploymentExists()) {
                getLog().debug("Deployment already exists, redeploying application.");
                plan = redeployPlan(this, builder);
            } else {
                getLog().debug("Deployment does not exist, deploying application.");
                plan = deployPlan(this, builder);
            }
        } else {
            if (deploymentExists()) {
                throw new MojoFailureException("Cannot deploy an application that already exists when force is set to false.");
            }
            getLog().debug("Deploying application.");
            plan = deployPlan(this, builder);
        }
        return plan;
    }

    @Override
    public String goal() {
        return "deploy";
    }

}
