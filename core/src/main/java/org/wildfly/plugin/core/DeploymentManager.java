/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.plugin.core;

import java.io.IOException;
import java.util.Set;

import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Allows deployment operations to be executed on a running server. This will work with both standalone servers and
 * managed domain servers.
 * <p>
 * The {@linkplain DeploymentResult#asModelNode() server result} for each deployment operation will be the result of a
 * composite operation.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("unused")
public interface DeploymentManager {

    /**
     * Deploys the content to the server.
     * <p>
     * If the deployment is not {@linkplain Deployment#setEnabled(boolean) enabled} a {@code deploy} operation will
     * need to be invoked. This can also be achieved by invoking the {@link #deployToRuntime(DeploymentDescription)}
     * method.
     * </p>
     *
     * @param deployment the deployment to deploy
     *
     * @return the result of the deployment
     *
     * @throws IOException if a failure occurs communicating with the server
     * @see #deployToRuntime(DeploymentDescription)
     */
    DeploymentResult deploy(Deployment deployment) throws IOException;

    /**
     * Deploys the content of each deployment to the server.
     * <p>
     * If the deployment is not {@linkplain Deployment#setEnabled(boolean) enabled} a {@code deploy} operation will
     * need to be invoked. This can also be achieved by invoking the {@link #deployToRuntime(Set)} method.
     * </p>
     * <p>
     * Also note that it is safe to trigger a {@code deploy} operation on already deployed deployments.
     * </p>
     *
     * @param deployments a set of deployments to deploy
     *
     * @return the result of the deployment
     *
     * @throws IOException if a failure occurs communicating with the server
     * @see #deployToRuntime(Set)
     */
    DeploymentResult deploy(Set<Deployment> deployments) throws IOException;

    /**
     * Deploys the content to the server if it does not already exist on the server. If the deployment already exist the
     * deployment is replaced.
     * <p>
     * If the deployment is not {@linkplain Deployment#setEnabled(boolean) enabled} a {@code deploy} operation will
     * need to be invoked. This can also be achieved by invoking the {@link #deployToRuntime(DeploymentDescription)}
     * method.
     * </p>
     *
     * @param deployment the deployment to deploy
     *
     * @return the result of the deployment
     *
     * @throws IOException if a failure occurs communicating with the server
     * @see #deployToRuntime(DeploymentDescription)
     */
    DeploymentResult forceDeploy(Deployment deployment) throws IOException;

    /**
     * Deploys the content to the server if it does not already exist on the server. If the deployment already exist the
     * deployment is replaced.
     * <p>
     * If the deployment is not {@linkplain Deployment#setEnabled(boolean) enabled} a {@code deploy} operation will
     * need to be invoked. This can also be achieved by invoking the {@link #deployToRuntime(Set)} method.
     * </p>
     * <p>
     * Also note that it is safe to trigger a {@code deploy} operation on already deployed deployments.
     * </p>
     *
     * @param deployments a set of deployments to deploy
     *
     * @return the result of the deployment
     *
     * @throws IOException if a failure occurs communicating with the server
     * @see #deployToRuntime(Set)
     */
    DeploymentResult forceDeploy(Set<Deployment> deployments) throws IOException;

    /**
     * Deploys existing deployment content to the runtime.
     *
     * @param deployment the deployment description to deploy
     *
     * @return the result of the deployment
     *
     * @throws IOException if a failure occurs communicating with the server
     */
    DeploymentResult deployToRuntime(DeploymentDescription deployment) throws IOException;

    /**
     * Deploys existing deployment content to the runtime for each deployment description.
     *
     * @param deployments the deployment descriptions to deploy
     *
     * @return the result of the deployment
     *
     * @throws IOException if a failure occurs communicating with the server
     */
    DeploymentResult deployToRuntime(Set<DeploymentDescription> deployments) throws IOException;

    /**
     * Redeploys the content to the server. Uses a {@code full-replace-deployment} operation to upload the new content,
     * undeploy the old content, deploy the new content and then remove the old content.
     * <p>
     * If the deployment is not {@linkplain Deployment#setEnabled(boolean) enabled} a {@code deploy} or {@code redeploy}
     * operation will need to be invoked. This can also be achieved by invoking the
     * {@link #deployToRuntime(DeploymentDescription)} method or the {@link #redeployToRuntime(DeploymentDescription)}
     * method.
     * </p>
     *
     * @param deployment the deployment to redeploy
     *
     * @return the result of the deployment
     *
     * @throws IOException if a failure occurs communicating with the server
     * @see #redeployToRuntime(DeploymentDescription)
     */
    DeploymentResult redeploy(Deployment deployment) throws IOException;

    /**
     * Redeploys the content to the server. Uses a {@code full-replace-deployment} operation to upload the new content,
     * undeploy the old content, deploy the new content and then remove the old content.
     * <p>
     * If the deployment is not {@linkplain Deployment#setEnabled(boolean) enabled} a {@code deploy} or {@code redeploy}
     * operation will need to be invoked. This can also be achieved by invoking the {@link #deployToRuntime(Set)}
     * method or the {@link #redeployToRuntime(Set)} method.
     * </p>
     * <p>
     * Also note that it is safe to trigger a {@code deploy} or operation on already deployed deployments.
     * </p>
     *
     * @param deployments a set of deployments to redeploy
     *
     * @return the result of the deployment
     *
     * @throws IOException if a failure occurs communicating with the server
     * @see #redeployToRuntime(Set)
     */
    DeploymentResult redeploy(Set<Deployment> deployments) throws IOException;

    /**
     * Redeploys existing deployment content to the runtime.
     *
     * @param deployment the deployment description to redeploy
     *
     * @return the result of the deployment
     *
     * @throws IOException if a failure occurs communicating with the server
     */
    DeploymentResult redeployToRuntime(DeploymentDescription deployment) throws IOException;

    /**
     * Redeploys existing deployment content to the runtime for each deployment description.
     *
     * @param deployments the deployment descriptions to redeploy
     *
     * @return the result of the deployment
     *
     * @throws IOException if a failure occurs communicating with the server
     */
    DeploymentResult redeployToRuntime(Set<DeploymentDescription> deployments) throws IOException;

    /**
     * Undeploys the deployment from the server.
     *
     * @param undeployDescription the description for undeploying the content
     *
     * @return the result of the deployment
     *
     * @throws IOException if a failure occurs communicating with the server
     */
    DeploymentResult undeploy(UndeployDescription undeployDescription) throws IOException;

    /**
     * Undeploys the deployment from the server.
     *
     * @param undeployDescriptions the descriptions for undeploying the content
     *
     * @return the result of the deployment
     *
     * @throws IOException if a failure occurs communicating with the server
     */
    DeploymentResult undeploy(Set<UndeployDescription> undeployDescriptions) throws IOException;

    /**
     * Returns the available deployments.
     *
     * @return the deployments
     *
     * @throws IOException if a failure occurs communicating with the server
     */
    Set<DeploymentDescription> getDeployments() throws IOException;

    /**
     * Returns all the deployments on the specified server-group. These deployments may also belong to other server
     * groups.
     *
     * @param serverGroup the server group to get the deployments for
     *
     * @return the deployments
     *
     * @throws IOException           if a failure occurs communicating with the server
     * @throws IllegalStateException if the running server is not a managed domain
     */
    Set<DeploymentDescription> getDeployments(String serverGroup) throws IOException;

    /**
     * Returns the names of deployed content.
     *
     * @return the names of deployed content
     *
     * @throws IOException if a failure occurs communicating with the server
     */
    Set<String> getDeploymentNames() throws IOException;

    /**
     * Checks if the deployment is on the server.
     *
     * @param name the name of the deployment
     *
     * @return {@code true} if the deployment exists otherwise {@code false}
     *
     * @throws IOException if a failure occurs communicating with the server
     */
    boolean hasDeployment(String name) throws IOException;

    /**
     * Checks if the deployment is on the server.
     *
     * @param name        the name of the deployment
     * @param serverGroup the server group to check for the deployment on
     *
     * @return {@code true} if the deployment exists otherwise {@code false}
     */
    boolean hasDeployment(String name, String serverGroup) throws IOException;

    /**
     * A factory to create a new deployment manager
     */
    class Factory {

        /**
         * Creates a new deployment manager.
         *
         * @param client the client used to communicate with the server
         *
         * @return a new deployment manager
         */
        public static DeploymentManager create(final ModelControllerClient client) {
            return new DefaultDeploymentManager(client);
        }
    }
}
