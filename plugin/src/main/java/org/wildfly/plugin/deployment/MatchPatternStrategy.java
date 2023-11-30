/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

/**
 * The strategy used when using a pattern to undeploy deployments.
 *
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 * @since 27.06.13 08:08
 */
public enum MatchPatternStrategy {
    /**
     * Undeploy the first matching deployment. The first deployment is determined by
     * {@linkplain Comparable natural ordering} of the deployment names.
     */
    FIRST,
    /**
     * Undeploy all deployments matching the pattern.
     */
    ALL,
    /**
     * Fail if more than one deployment matches the pattern.
     */
    FAIL
}
