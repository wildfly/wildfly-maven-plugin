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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the description for undeploying content from a running container.
 * <p>
 * Instances of this are not thread-safe.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("unused")
public class UndeployDescription implements DeploymentDescription {

    private final String name;
    private final Set<String> serverGroups;
    private boolean failOnMissing;

    /**
     * Creates a new undeploy description.
     *
     * @param name the name of the deployment
     */
    public UndeployDescription(final String name) {
        this.name = Assertions.requiresNotNullParameter(name, "name");
        serverGroups = new LinkedHashSet<>();
    }

    /**
     * Creates a new undeploy description.
     *
     * @param name the name of the deployment
     *
     * @return the description
     */
    public static UndeployDescription of(final String name) {
        return new UndeployDescription(name);
    }

    /**
     * Creates a new undeploy description.
     *
     * @param deploymentDescription the deployment description to copy
     *
     * @return the description
     */
    public static UndeployDescription of(final DeploymentDescription deploymentDescription) {
        return of(deploymentDescription.getName()).addServerGroups(deploymentDescription.getServerGroups());
    }

    /**
     * Adds a server group for the deployment description.
     *
     * @param serverGroup the server group to add
     *
     * @return this deployment description
     */
    public UndeployDescription addServerGroup(final String serverGroup) {
        serverGroups.add(serverGroup);
        return this;
    }

    /**
     * Adds the server groups for the deployment description.
     *
     * @param serverGroups the server groups to add
     *
     * @return this deployment description
     */
    public UndeployDescription addServerGroups(final String... serverGroups) {
        return addServerGroups(Arrays.asList(serverGroups));
    }

    /**
     * Adds the server groups for the deployment description.
     *
     * @param serverGroups the server groups to add
     *
     * @return this deployment description
     */
    public UndeployDescription addServerGroups(final Collection<String> serverGroups) {
        this.serverGroups.addAll(serverGroups);
        return this;
    }

    @Override
    public Set<String> getServerGroups() {
        return new LinkedHashSet<>(serverGroups);
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Indicates whether or not a failure should occur if the deployment does not exist on the container. A value of
     * {@code true} indicates the deployment should fail.
     *
     * @return {@code true} if the undeploy should fail if not found on the container, otherwise {@code false}
     */
    public boolean isFailOnMissing() {
        return failOnMissing;
    }

    /**
     * Sets whether or not a failure should occur if the deployment does exist on the container.
     *
     * @param failOnMissing {@code true} if the undeploy should fail if the deployment was not found on the server,
     *                      {@code false} if the deployment does not exist and the undeploy should be ignored
     *
     * @return the deployment description
     */
    public UndeployDescription setFailOnMissing(final boolean failOnMissing) {
        this.failOnMissing = failOnMissing;
        return this;
    }

    @Override
    public int compareTo(@SuppressWarnings("NullableProblems") final DeploymentDescription o) {
        return getName().compareTo(o.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName());
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UndeployDescription)) {
            return false;
        }
        @SuppressWarnings("TypeMayBeWeakened")
        final UndeployDescription other = (UndeployDescription) obj;
        return Objects.equals(getName(), other.getName());
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder(UndeployDescription.class.getSimpleName());
        result.append('(');
        result.append("name=").append(getName());
        result.append(", failOnMissing=").append(failOnMissing);
        if (!serverGroups.isEmpty()) {
            result.append(", serverGroups=").append(serverGroups);
        }
        return result.append(')').toString();
    }
}
