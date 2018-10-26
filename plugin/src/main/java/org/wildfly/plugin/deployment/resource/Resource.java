/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.deployment.resource;

import java.util.Collections;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;
import org.wildfly.plugin.cli.Commands;

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
     * Commands to run before the deployment
     *
     * @deprecated use the {@code execute-commands} goal
     */
    @Deprecated
    @Parameter(alias = "before-add")
    private Commands beforeAdd;

    /**
     * Executions to run after the deployment
     *
     * @deprecated use the {@code execute-commands} goal
     */
    @Deprecated
    @Parameter(alias = "after-add")
    private Commands afterAdd;

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

    /**
     * Checks if there are commands that should be executed before the deployment.
     *
     * @return {@code true} if there are commands that need to be executed before the deployment, otherwise {@code
     * false}
     */
    public boolean hasBeforeAddCommands() {
        return beforeAdd != null && beforeAdd.hasCommands();
    }

    /**
     * Returns the commands that should be executed before the resource is deployed.
     *
     * @return the commands to execute
     */
    public Commands getBeforeAdd() {
        return beforeAdd;
    }

    /**
     * Checks if there are commands that should be executed after the deployment.
     *
     * @return {@code true} if there are commands that need to be executed after the deployment, otherwise {@code
     * false}
     */
    public boolean hasAfterAddCommands() {
        return afterAdd != null && afterAdd.hasCommands();
    }

    /**
     * Returns the commands that should be executed after the resource is deployed.
     *
     * @return the commands to execute
     */
    public Commands getAfterAdd() {
        return afterAdd;
    }
}
