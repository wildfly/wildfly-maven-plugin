/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import java.io.IOException;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.tools.Deployment;
import org.wildfly.plugin.tools.DeploymentManager;
import org.wildfly.plugin.tools.DeploymentResult;

/**
 * Deploys the application to the WildFly Application Server.
 * <p/>
 * If {@code force} is set to {@code true}, the server is queried to see if the application already exists. If the
 * application already exists, the application is redeployed instead of deployed. If the application does not exist the
 * application is deployed as normal.
 * <p/>
 * If {@code force} is set to {@code false} and the application has already been deployed to the server, an error
 * will occur and the deployment will fail.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "deploy", requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
@Execute(phase = LifecyclePhase.PACKAGE)
public class DeployMojo extends AbstractAppDeployment {

    /**
     * Specifies whether force mode should be used or not.
     * </p>
     * If force mode is disabled, the deploy goal will cause a build failure if the application being deployed already
     * exists.
     */
    @Parameter(defaultValue = "true", property = PropertyNames.DEPLOY_FORCE)
    private boolean force;

    @Override
    public String goal() {
        return "deploy";
    }

    @Override
    protected DeploymentResult executeDeployment(final DeploymentManager deploymentManager, final Deployment deployment)
            throws IOException {
        if (force) {
            return deploymentManager.forceDeploy(deployment);
        }
        return deploymentManager.deploy(deployment);
    }

}