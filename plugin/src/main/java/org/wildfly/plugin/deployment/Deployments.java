/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.SERVER_GROUP;
import static org.wildfly.plugin.common.ServerOperations.createAddress;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.wildfly.plugin.common.ServerOperations;

/**
 * Represents existing deployments in the container. Deployments are cached during creation time and the cache is not
 * mutated.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class Deployments {
    private final Set<String> deployments;
    private final ServerGroupDeployments serverGroupDeployments;
    private final boolean isDomain;

    private Deployments(final Set<String> deployments, final ServerGroupDeployments serverGroupDeployments, final boolean isDomain) {
        this.deployments = Collections.unmodifiableSet(deployments);
        this.serverGroupDeployments = serverGroupDeployments;
        this.isDomain = isDomain;
    }

    /**
     * Creates a new deployment helper.
     *
     * @param client       the client used to communicate with the running container
     * @param serverGroups the server groups or {@code null} for standalone server deployments
     *
     * @return the deployment helper
     */
    static Deployments create(final ModelControllerClient client, final Collection<String> serverGroups) {
        final Set<String> deployments = getDeployments(client);
        if (serverGroups == null || serverGroups.isEmpty()) {
            return new Deployments(deployments, ServerGroupDeployments.EMPTY, false);
        }
        final ServerGroupDeployments serverGroupDeployments = getServerGroupDeployments(client);
        return new Deployments(deployments, serverGroupDeployments, true);
    }

    /**
     * Filters the current deployments by a name match or the pattern match.
     * <p>
     * If the {@code matchPattern} is not {@code null} the pattern will be used to filter the existing deployments to a
     * collection of deployments that match the pattern.
     * </p>
     * <p>
     * If the {@code name} is not {@code null} and the {@code matchPattern} <em>is</em> {@code null} a collection with a
     * single entry will be returned if the name matches an existing deployment. If the name does not match an existing
     * deployment the returned collection will be empty.
     * </p>
     *
     * @param name         the name of the deployment to exactly match or {@code null} if using a pattern
     * @param matchPattern the pattern used to match one or more existing deployments or {@code null} if an exact match
     *                     is being used
     *
     * @return a collection of the matching deployments
     *
     * @throws IllegalArgumentException if both {@code name} and {@code matchPattern} parameters are {@code null}
     */
    Set<String> filter(final String name, final String matchPattern) {
        return filter(name, matchPattern, null);
    }

    /**
     * Filters the current deployments by a name match or the pattern match.
     * <p>
     * If the {@code matchPattern} is not {@code null} the pattern will be used to filter the existing deployments to a
     * collection of deployments that match the pattern.
     * </p>
     * <p>
     * If the {@code name} is not {@code null} and the {@code matchPattern} <em>is</em> {@code null} a collection with a
     * single entry will be returned if the name matches an existing deployment. If the name does not match an existing
     * deployment the returned collection will be empty.
     * </p>
     *
     * @param name         the name of the deployment to exactly match or {@code null} if using a pattern
     * @param matchPattern the pattern used to match one or more existing deployments or {@code null} if an exact match
     *                     is being used
     * @param serverGroup  the server group used to match deployments
     *
     * @return a collection of the matching deployments
     *
     * @throws IllegalArgumentException if both {@code name} and {@code matchPattern} parameters are {@code null}
     */
    Set<String> filter(final String name, final String matchPattern, final String serverGroup) {

        if (name == null && matchPattern == null) {
            throw new IllegalArgumentException("deploymentName and matchPattern are null. One of them must "
                    + "be set in order to find an existing deployment.");
        }

        final Set<String> result = new TreeSet<>();
        final Collection<String> deployments;
        if (isDomain && serverGroup != null) {
            deployments = serverGroupDeployments.getDeploymentsByServerGroup(serverGroup);
        } else {
            deployments = this.deployments;
        }
        if (matchPattern == null) {
            for (String deployment : deployments) {
                if (deployment.equals(name)) {
                    result.add(deployment);
                    break;
                }
            }
        } else {
            final Pattern pattern = Pattern.compile(matchPattern);
            for (String deployment : deployments) {

                if (pattern.matcher(deployment).matches()) {
                    result.add(deployment);
                }
            }
        }
        return result;
    }

    /**
     * Checks to see if the deployment exists.
     * <p>
     * For domain servers this will return {@code true} if the deployment content exists even if the content is not
     * deployed to a server group.
     * </p>
     *
     * @param name the name of the deployment
     *
     * @return {@code true} if the deployment exists on the server, otherwise {@code false}
     */
    boolean hasDeployment(final String name) {
        return deployments.contains(name);
    }

    /**
     * Checks to see if the deployment exists in a server group.
     *
     * @param serverGroup the server group name to check
     * @param name        the name of the deployment
     *
     * @return {@code true} if the deployment exists on the server, otherwise {@code false}
     */
    boolean hasDeployment(final String serverGroup, final String name) {
        if (isDomain) {
            final Set<String> serverGroups = serverGroupDeployments.getServerGroupsByDeployment(name);
            return serverGroups != null && serverGroups.contains(serverGroup);
        }
        return false;
    }

    private static Set<String> getDeployments(final ModelControllerClient client) {
        // CLI :read-children-names(child-type=deployment)
        final ModelNode op = ServerOperations.createListDeploymentsOperation();
        final ModelNode listDeploymentsResult;
        final Set<String> result = new HashSet<>();
        try {
            listDeploymentsResult = client.execute(op);
            // Check to make sure there is an outcome
            if (ServerOperations.isSuccessfulOutcome(listDeploymentsResult)) {
                final List<ModelNode> deployments = ServerOperations.readResult(listDeploymentsResult).asList();
                for (ModelNode deployment : deployments) {
                    result.add(deployment.asString());
                }
            } else {
                throw new IllegalStateException(ServerOperations.getFailureDescriptionAsString(listDeploymentsResult));
            }
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Could not execute operation '%s'", op), e);
        }
        return result;

    }

    private static ServerGroupDeployments getServerGroupDeployments(final ModelControllerClient client) {
        /* Keyed by the deployment name with a value of each server-group the deployment belongs to */
        final Map<String, Set<String>> deploymentServerGroups = new LinkedHashMap<>();
        /* Keyed by the server group name with a value of each deployment on that server group */
        final Map<String, Set<String>> serverGroupDeployments = new LinkedHashMap<>();
        final ModelNode address = createAddress(SERVER_GROUP, "*", DEPLOYMENT, "*");
        try {
            final ModelNode outcome = client.execute(Operations.createReadAttributeOperation(address, "enabled"));
            if (Operations.isSuccessfulOutcome(outcome)) {
                final List<ModelNode> results = Operations.readResult(outcome).asList();
                for (ModelNode r : results) {
                    final List<Property> resultAddress = Operations.getOperationAddress(r).asPropertyList();
                    String serverGroup = null;
                    String deployment = null;
                    for (Property property : resultAddress) {
                        if (SERVER_GROUP.equals(property.getName())) {
                            serverGroup = property.getValue().asString();
                        } else if (DEPLOYMENT.equals(property.getName())) {
                            deployment = property.getValue().asString();
                        }
                    }
                    // Add the server-group to the map of deployments
                    final Set<String> serverGroups = computeIfAbsent(deploymentServerGroups, deployment);
                    serverGroups.add(serverGroup);
                    // Add the deployment to the map of the server-groups
                    final Set<String> deployments = computeIfAbsent(serverGroupDeployments, serverGroup);
                    deployments.add(deployment);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not retrieve deployments for server-groups", e);
        }
        return new ServerGroupDeployments(deploymentServerGroups, serverGroupDeployments);
    }

    private static Set<String> computeIfAbsent(final Map<String, Set<String>> map, final String key) {
        Set<String> result = map.get(key);
        if (result == null) {
            result = new HashSet<>();
            map.put(key, result);
        }
        return result;
    }

    private static class ServerGroupDeployments {
        static final ServerGroupDeployments EMPTY = new ServerGroupDeployments(Collections.<String, Set<String>>emptyMap(),
                Collections.<String, Set<String>>emptyMap());
        /* Keyed by the deployment name with a value of each server-group the deployment belongs to */
        private final Map<String, Set<String>> deploymentServerGroups;
        /* Keyed by the server group name with a value of each deployment on that server group */
        private final Map<String, Set<String>> serverGroupDeployments;

        private ServerGroupDeployments(final Map<String, Set<String>> deploymentServerGroups, final Map<String, Set<String>> serverGroupDeployments) {
            this.deploymentServerGroups = Collections.unmodifiableMap(deploymentServerGroups);
            this.serverGroupDeployments = Collections.unmodifiableMap(serverGroupDeployments);
        }

        /**
         * Returns all the deployments on the specified server group.
         *
         * @param serverGroup the server group to get the deployments for
         *
         * @return the deployments
         */
        Set<String> getDeploymentsByServerGroup(final String serverGroup) {
            final Set<String> deployments = serverGroupDeployments.get(serverGroup);
            return deployments == null ? Collections.<String>emptySet() : Collections.unmodifiableSet(deployments);
        }

        /**
         * Gets all the server groups a deployment belongs to.
         *
         * @param deploymentName the deployment name
         *
         * @return the server groups
         */
        Set<String> getServerGroupsByDeployment(final String deploymentName) {
            final Set<String> serverGroups = deploymentServerGroups.get(deploymentName);
            return serverGroups == null ? Collections.<String>emptySet() : Collections.unmodifiableSet(serverGroups);
        }
    }
}
