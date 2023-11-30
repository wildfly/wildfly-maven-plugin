/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import java.io.IOException;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.wildfly.plugin.core.Deployment;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.core.DeploymentResult;

/**
 * Redeploys the application to the WildFly Application Server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "redeploy", requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
@Execute(phase = LifecyclePhase.PACKAGE)
public class RedeployMojo extends AbstractAppDeployment {

    @Override
    public String goal() {
        return "redeploy";
    }

    @Override
    protected DeploymentResult executeDeployment(final DeploymentManager deploymentManager, final Deployment deployment)
            throws IOException, MojoDeploymentException {
        // Ensure the deployment exists before attempting to redeploy it
        if (!deploymentManager.hasDeployment(deployment.getName())) {
            throw new MojoDeploymentException(
                    "The deployment %s does not exist in the content repository and cannot be redeployed.",
                    deployment.getName());
        }
        return deploymentManager.redeploy(deployment);
    }

}
