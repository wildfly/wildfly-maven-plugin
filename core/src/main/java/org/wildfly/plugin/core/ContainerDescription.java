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

/**
 * Information about the running container.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"WeakerAccess", "unused"})
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

