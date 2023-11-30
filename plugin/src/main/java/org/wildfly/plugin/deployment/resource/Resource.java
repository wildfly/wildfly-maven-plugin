/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment.resource;

import java.util.Collections;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Defines a resource.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Resource {

    /**
     * The operation address.
     */
    @Parameter
    private String address;

    /**
     * Only adds the resource if the resource does not already exist. If the resource already exists, the resource is
     * skipped.
     */
    @Parameter
    private boolean addIfAbsent;

    /**
     * The operation properties for the resource.
     */
    @Parameter
    private Map<String, String> properties;

    /**
     * An array of resources that rely on this resource.
     * <p/>
     * Note all resources will be ignored if the {@code <addIfAbsent/>} is defined and his resource is already defined.
     */
    @Parameter
    private Resource[] resources;

    /**
     * Default constructor.
     */
    public Resource() {

    }

    /**
     * The address for the resource.
     *
     * @return the address.
     */
    public String getAddress() {
        return address;
    }

    /**
     * Whether or not we should add only if the resource is absent.
     *
     * @return {@code true} if the resource should only be added if it does not already exist, otherwise {@code false}.
     */
    public boolean isAddIfAbsent() {
        return addIfAbsent;
    }

    /**
     * The properties for the resource. If no properties were defined an empty map is returned.
     *
     * @return the properties.
     */
    public Map<String, String> getProperties() {
        if (properties == null) {
            return Collections.emptyMap();
        }
        return properties;
    }

    /**
     * Returns an array of resources that depend on this resource.
     * <p/>
     * Note all sub-resources will be ignored if the {@link #isAddIfAbsent()} is defined and his resource is already
     * defined.
     *
     * @return an array of resources that depend on this resource or {@code null} if there are no child resources.
     */
    public Resource[] getResources() {
        return resources;
    }
}
