package org.jboss.as.plugin.deployment;


import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.as.plugin.common.PropertyNames;

/**
 * Deploys only the application to the JBoss Application Server without first invoking the
 * the execution of the lifecycle phase 'package' prior to executing itself.
 * <p/>
 * If {@code force} is set to {@code true}, the server is queried to see if the application already exists. If the
 * application already exists, the application is redeployed instead of deployed. If the application does not exist the
 * application is deployed as normal.
 * <p/>
 * If {@code force} is set to {@code false} and the application has already been deployed to the server, an error
 * will occur and the deployment will fail.
 *
 */
@Mojo(name = "deploy-only", requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public class DeployOnly extends AbstractAppDeployment {

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
        return "deploy-only";
    }

    @Override
    public Deployment.Type getType() {
        return (force ? Deployment.Type.FORCE_DEPLOY : Deployment.Type.DEPLOY);
    }

}
