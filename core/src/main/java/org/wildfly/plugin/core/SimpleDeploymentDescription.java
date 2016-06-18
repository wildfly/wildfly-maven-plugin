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

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * A simple deployment description.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class SimpleDeploymentDescription implements DeploymentDescription {
    private final String name;
    private final Set<String> serverGroups;

    private SimpleDeploymentDescription(final String name, final Set<String> serverGroups) {
        this.name = name;
        this.serverGroups = serverGroups;
    }

    /**
     * Creates a simple deployment description with an empty set of server groups.
     *
     * @param name the name for the deployment
     *
     * @return the deployment description
     */
    static SimpleDeploymentDescription of(final String name) {
        return new SimpleDeploymentDescription(Assertions.requiresNotNullParameter(name, "name"), Collections.<String>emptySet());
    }

    /**
     * Creates a simple deployment description.
     *
     * @param name         the name for the deployment
     * @param serverGroups the server groups
     *
     * @return the deployment description
     */
    static SimpleDeploymentDescription of(final String name, final Set<String> serverGroups) {
        return new SimpleDeploymentDescription(Assertions.requiresNotNullParameter(name, "name"),
                Assertions.requiresNotNullParameter(serverGroups, "serverGroups"));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<String> getServerGroups() {
        return serverGroups;
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
        @SuppressWarnings("TypeMayBeWeakened")
        final SimpleDeploymentDescription other = (SimpleDeploymentDescription) obj;
        return Objects.equals(getName(), other.getName());
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder(SimpleDeploymentDescription.class.getSimpleName());
        result.append('(');
        result.append("name=").append(getName());
        if (!serverGroups.isEmpty()) {
            result.append(", serverGroups=").append(serverGroups);
        }
        return result.append(')').toString();
    }

    @Override
    public int compareTo(@SuppressWarnings("NullableProblems") final DeploymentDescription o) {
        return getName().compareTo(o.getName());
    }
}
