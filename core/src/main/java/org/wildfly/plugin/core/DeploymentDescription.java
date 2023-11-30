/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.core;

import java.util.Set;

/**
 * Represents a default description for a deployment.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("WeakerAccess")
public interface DeploymentDescription {

    /**
     * Returns the name for this deployment.
     *
     * @return the name for this deployment
     */
    String getName();

    /**
     * Returns the server groups for this deployment.
     *
     * @return a set of server groups for this deployment
     */
    Set<String> getServerGroups();
}
