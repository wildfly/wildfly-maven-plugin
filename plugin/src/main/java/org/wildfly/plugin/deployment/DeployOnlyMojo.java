/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import java.net.URL;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.tools.Deployment;

/**
 * Deploys only the application to the WildFly Application Server without first invoking the
 * the execution of the lifecycle phase 'package' prior to executing itself.
 * <p/>
 * If {@code force} is set to {@code true}, the server is queried to see if the application already exists. If the
 * application already exists, the application is redeployed instead of deployed. If the application does not exist the
 * application is deployed as normal.
 * <p/>
 * If {@code force} is set to {@code false} and the application has already been deployed to the server, an error
 * will occur and the deployment will fail.
 */
@Mojo(name = "deploy-only", threadSafe = true)
@Execute(phase = LifecyclePhase.NONE)
public class DeployOnlyMojo extends DeployMojo {

    /**
     * A URL representing the a path to the content to be deployed. The server the content is being deployed to will
     * require access to the URL.
     * <p>
     * If defined this overrides the {@code filename} and {@code targetDir} configuration parameters.
     * </p>
     */
    @Parameter(alias = "content-url", property = PropertyNames.DEPLOYMENT_CONTENT_URL)
    private URL contentUrl;

    @Override
    public String goal() {
        return "deploy-only";
    }

    @Override
    protected Deployment createDeployment() {
        if (contentUrl == null) {
            return super.createDeployment();
        }
        return Deployment.of(contentUrl);
    }
}
