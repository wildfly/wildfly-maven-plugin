/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.core;

/**
 * Information about the running container.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({ "WeakerAccess", "unused" })
public interface ContainerDescription {

    /**
     * Returns the name of the product.
     *
     * @return the name of the product
     */
    String getProductName();

    /**
     * Returns the product version, if defined, or {@code null} if the product version was not defined.
     *
     * @return the product version or {@code null} if not defined
     */
    String getProductVersion();

    /**
     * Returns the release version, if defined, or {@code null} if the release version was not defined.
     * <p>
     * Note that in WildFly 9+ this is usually the version for WildFly Core. In WildFly 8 this is the full version.
     * </p>
     *
     * @return the release version or {@code null} if not defined
     */
    String getReleaseVersion();

    /**
     * Returns the type of the server that was launched.
     *
     * @return the type of the server that was launched or {@code null} if not defined
     */
    String getLaunchType();

    /**
     * Checks if the server is a managed domain server.
     *
     * @return {@code true} if this is a managed domain, otherwise {@code false}
     */
    boolean isDomain();
}
