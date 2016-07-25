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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A simple deployment description.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class SimpleDeploymentDescription implements DeploymentDescription, Comparable<SimpleDeploymentDescription> {

    private final String name;
    private final Set<String> serverGroups;

    private SimpleDeploymentDescription(final String name) {
        this.name = name;
        serverGroups = new LinkedHashSet<>();
    }

    /**
     * Creates a simple deployment description with an empty set of server groups.
     *
     * @param name the name for the deployment
     *
     * @return the deployment description
     */
    public static SimpleDeploymentDescription of(final String name) {
        return new SimpleDeploymentDescription(Assertions.requiresNotNullOrNotEmptyParameter(name, "name"));
    }

    /**
     * Creates a simple deployment description.
     *
     * @param name         the name for the deployment
     * @param serverGroups the server groups
     *
     * @return the deployment description
     */
    public static SimpleDeploymentDescription of(final String name, @SuppressWarnings("TypeMayBeWeakened") final Set<String> serverGroups) {
        final SimpleDeploymentDescription result = of(name);
        if (serverGroups != null) {
            result.addServerGroups(serverGroups);
        }
        return result;
    }

    /**
     * Adds a server group for the deployment description.
     *
     * @param serverGroup the server group to add
     *
     * @return this deployment description
     */
    public SimpleDeploymentDescription addServerGroup(final String serverGroup) {
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
    public SimpleDeploymentDescription addServerGroups(final String... serverGroups) {
        return addServerGroups(Arrays.asList(serverGroups));
    }

    /**
     * Adds the server groups for the deployment description.
     *
     * @param serverGroups the server groups to add
     *
     * @return this deployment description
     */
    public SimpleDeploymentDescription addServerGroups(final Collection<String> serverGroups) {
        this.serverGroups.addAll(serverGroups);
        return this;
    }

    @Override
    public Set<String> getServerGroups() {
        return Collections.unmodifiableSet(serverGroups);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int compareTo(@SuppressWarnings("NullableProblems") final SimpleDeploymentDescription o) {
        return name.compareTo(o.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SimpleDeploymentDescription)) {
            return false;
        }
        final SimpleDeploymentDescription other = (SimpleDeploymentDescription) obj;
        return Objects.equals(name, other.name);
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder(SimpleDeploymentDescription.class.getSimpleName());
        result.append('(');
        result.append("name=").append(name);
        if (!serverGroups.isEmpty()) {
            result.append(", serverGroups=").append(serverGroups);
        }
        return result.append(')').toString();
    }
}
