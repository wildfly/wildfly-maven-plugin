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
 * Redeploys only the application to the WildFly Application Server without first invoking the
 * the execution of the lifecycle phase 'package' prior to executing itself.
 */
@Mojo(name = "redeploy-only", threadSafe = true)
@Execute(phase = LifecyclePhase.NONE)
public class RedeployOnlyMojo extends RedeployMojo {

    /**
     * A URL representing the a path to the content to be redeployed. The server the content is being redeployed to will
     * require access to the URL.
     * <p>
     * If defined this overrides the {@code filename} and {@code targetDir} configuration parameters.
     * </p>
     */
    @Parameter(alias = "content-url", property = PropertyNames.DEPLOYMENT_CONTENT_URL)
    private URL contentUrl;

    @Override
    public String goal() {
        return "redeploy";
    }

    @Override
    protected Deployment createDeployment() {
        if (contentUrl == null) {
            return super.createDeployment();
        }
        return Deployment.of(contentUrl);
    }

}
