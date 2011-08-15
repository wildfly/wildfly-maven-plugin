package org.jboss.as.plugin.deployment;

import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;

import java.io.IOException;

/**
 * Date: 06.06.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class Util {

    private Util() {
    }

    /**
     * Creates a deployment plan for deploying the application.
     *
     * @param deployment the deployment to crate the plan for.
     * @param builder    the builder used to create the plan.
     *
     * @return the deployment plan.
     *
     * @throws IOException if the deployment plan found an error.
     */
    static DeploymentPlan deployPlan(final AbstractDeployment deployment, final DeploymentPlanBuilder builder) throws IOException {
        DeploymentPlan plan = null;
        if (deployment.name() == null) {
            deployment.getLog().debug(deployment.nameNotDefinedMessage());
            plan = builder.add(deployment.file()).deploy(deployment.file().getName()).build();
        } else {
            deployment.getLog().debug(deployment.nameDefinedMessage());
            plan = builder.add(deployment.name(), deployment.file()).deploy(deployment.name()).build();
        }
        return plan;
    }


    /**
     * Creates a deployment plan for redeploying the application.
     *
     * @param deployment the deployment to crate the plan for.
     * @param builder    the builder used to create the plan.
     *
     * @return the deployment plan.
     *
     * @throws IOException if the deployment plan found an error.
     */
    static DeploymentPlan redeployPlan(final AbstractDeployment deployment, final DeploymentPlanBuilder builder) throws IOException {
        DeploymentPlan plan = null;
        if (deployment.name() == null) {
            deployment.getLog().debug(deployment.nameNotDefinedMessage());
            plan = builder.replace(deployment.file()).redeploy(deployment.file().getName()).build();
        } else {
            deployment.getLog().debug(deployment.nameDefinedMessage());
            plan = builder.replace(deployment.name(), deployment.file()).redeploy(deployment.name()).build();
        }
        return plan;
    }


    /**
     * Creates a deployment plan for undeploying the application.
     *
     * @param deployment the deployment to crate the plan for.
     * @param builder    the builder used to create the plan.
     *
     * @return the deployment plan.
     *
     * @throws IOException if the deployment plan found an error.
     */
    static DeploymentPlan undeployPlan(final AbstractDeployment deployment, final DeploymentPlanBuilder builder) throws IOException {
        DeploymentPlan plan = null;
        if (deployment.name() == null) {
            deployment.getLog().debug(deployment.nameNotDefinedMessage());
            plan = builder.undeploy(deployment.file().getName()).remove(deployment.file().getName()).build();
        } else {
            deployment.getLog().debug(deployment.nameNotDefinedMessage());
            plan = builder.undeploy(deployment.name()).remove(deployment.name()).build();
        }
        return plan;
    }
}
