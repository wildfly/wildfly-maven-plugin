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

package org.jboss.as.plugin.deployment.standalone;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.standalone.DeploymentAction;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentActionResult;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;
import org.jboss.as.controller.client.helpers.standalone.ServerUpdateActionResult;
import org.jboss.as.plugin.common.DeploymentExecutionException;
import org.jboss.as.plugin.common.DeploymentFailureException;
import org.jboss.as.plugin.common.DeploymentInspector;
import org.jboss.as.plugin.deployment.Deployment;
import org.jboss.as.plugin.deployment.MatchPatternStrategy;

/**
 * A deployment for standalone servers.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StandaloneDeployment implements Deployment {

    private final File content;
    private final ModelControllerClient client;
    private final String name;
    private final Type type;
    private final String matchPattern;
    private final MatchPatternStrategy matchPatternStrategy;

    /**
     * Creates a new deployment.
     *
     * @param client  the client that is connected.
     * @param content the content for the deployment.
     * @param name    the name of the deployment, if {@code null} the name of the content file is used.
     * @param type    the deployment type.
     * @param matchPattern            the pattern for matching multiple artifacts, if {@code null} the name is used.
     * @param matchPatternStrategy    the strategy for handling multiple artifacts.
     */
    public StandaloneDeployment(final ModelControllerClient client, final File content, final String name, final Type type,
                                final String matchPattern, final MatchPatternStrategy matchPatternStrategy) {
        this.content = content;
        this.client = client;
        this.name = (name == null ? content.getName() : name);
        this.type = type;
        this.matchPattern = matchPattern;
        this.matchPatternStrategy = matchPatternStrategy;
    }

    /**
     * Creates a new deployment.
     *
     * @param client  the client that is connected.
     * @param content the content for the deployment.
     * @param name    the name of the deployment, if {@code null} the name of the content file is used.
     * @param type    the deployment type.
     * @param matchPattern            the pattern for matching multiple artifacts, if {@code null} the name is used.
     * @param matchPatternStrategy    the strategy for handling multiple artifacts.
     *
     * @return the new deployment
     */
    public static StandaloneDeployment create(final ModelControllerClient client, final File content, final String name, final Type type,
                                              final String matchPattern, final MatchPatternStrategy matchPatternStrategy) {
        return new StandaloneDeployment(client, content, name, type, matchPattern, matchPatternStrategy);
    }

    private DeploymentPlan createPlan(final DeploymentPlanBuilder builder) throws IOException, DeploymentFailureException {
        DeploymentPlanBuilder planBuilder = builder;

        List<String> existingDeployments = DeploymentInspector.getDeployments(client, name, matchPattern);
        validateExistingDeployments(existingDeployments);

        switch (type) {
            case DEPLOY: {
                planBuilder = builder.add(name, content).andDeploy();
                break;
            }
            case FORCE_DEPLOY:
            case REDEPLOY: {
                if(existingDeployments.contains(name)) {
                    existingDeployments.remove(name);
                    planBuilder = undeployAndRemove(builder, existingDeployments);
                    planBuilder = planBuilder.replace(name, content).redeploy(name);
                }
                else {
                    planBuilder = undeployAndRemove(builder, existingDeployments);
                    planBuilder = planBuilder.add(name, content).andDeploy();
                }
                break;
            }
            case UNDEPLOY: {
                planBuilder = undeployAndRemove(builder, existingDeployments);
                break;
            }
            case UNDEPLOY_IGNORE_MISSING: {
                if (!existingDeployments.isEmpty()) {
                    planBuilder = undeployAndRemove(builder, existingDeployments);
                } else {
                    return null;
                }
                break;
            }
        }
        return planBuilder.build();
    }

    private DeploymentPlanBuilder undeployAndRemove(final DeploymentPlanBuilder builder, final List<String> deploymentNames) {

        DeploymentPlanBuilder planBuilder = builder;

        for (String deploymentName : deploymentNames) {
            planBuilder = planBuilder.undeploy(deploymentName).andRemoveUndeployed();

            if(matchPatternStrategy == MatchPatternStrategy.first) {
                break;
            }
        }

        return planBuilder;
    }

    private void validateExistingDeployments(List<String> existingDeployments) throws DeploymentFailureException {
        if(matchPattern == null) {
            return;
        }

        if(matchPatternStrategy == MatchPatternStrategy.fail && existingDeployments.size() > 1) {
            throw new DeploymentFailureException(String.format("Deployment failed, found %d deployed artifacts for pattern '%s' (%s)",
                                                               existingDeployments.size(), matchPattern, existingDeployments));
        }
    }

    @Override
    public Status execute() throws DeploymentExecutionException, DeploymentFailureException {
        Status resultStatus = Status.SUCCESS;
        try {
            final ServerDeploymentManager manager = ServerDeploymentManager.Factory.create(client);
            final DeploymentPlanBuilder builder = manager.newDeploymentPlan();
            final DeploymentPlan plan = createPlan(builder);
            if (plan != null) {
                if (plan.getDeploymentActions().size() > 0) {
                    final ServerDeploymentPlanResult planResult = manager.execute(plan).get();
                    // Check the results
                    for (DeploymentAction action : plan.getDeploymentActions()) {
                        final ServerDeploymentActionResult actionResult = planResult.getDeploymentActionResult(action.getId());
                        final ServerUpdateActionResult.Result result = actionResult.getResult();
                        switch (result) {
                            case FAILED:
                                throw new DeploymentExecutionException("Deployment failed.", actionResult.getDeploymentException());
                            case NOT_EXECUTED:
                                throw new DeploymentExecutionException("Deployment not executed.", actionResult.getDeploymentException());
                            case ROLLED_BACK:
                                throw new DeploymentExecutionException("Deployment failed and was rolled back.", actionResult.getDeploymentException());
                            case CONFIGURATION_MODIFIED_REQUIRES_RESTART:
                                resultStatus = Status.REQUIRES_RESTART;
                                break;
                        }
                    }
                }
            }
        } catch (DeploymentExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new DeploymentExecutionException(e, "Error executing %s", type);
        }
        return resultStatus;
    }

    @Override
    public Type getType() {
        return type;
    }

}
