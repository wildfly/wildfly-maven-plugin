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

import java.util.Collection;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class Assertions {

    /**
     * Checks if the parameter is {@code null} and throws an {@link IllegalArgumentException} if it is.
     *
     * @param value the value to check
     * @param name  the name of the parameter
     * @param <T>   the parameter type
     *
     * @return the parameter value
     *
     * @throws IllegalArgumentException if the object representing the parameter is {@code null}
     */
    static <T> T requiresNotNullParameter(final T value, final String name) throws IllegalArgumentException {
        if (value == null) {
            throw new IllegalArgumentException(String.format("Parameter %s is required and cannot be null.", name));
        }
        return value;
    }

    /**
     * Checks if the parameter is {@code null} or empty and throws an {@link IllegalArgumentException} if it is.
     *
     * @param value the value to check
     * @param name  the name of the parameter
     *
     * @return the parameter value
     *
     * @throws IllegalArgumentException if the object representing the parameter is {@code null}
     */
    static String requiresNotNullOrNotEmptyParameter(final String value, final String name) throws IllegalArgumentException {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format("Parameter %s is required and cannot be null or empty.", name));
        }
        return value;
    }

    /**
     * Checks if the parameter is {@code null} or empty and throws an {@link IllegalArgumentException} if it is.
     *
     * @param value the value to check
     * @param name  the name of the parameter
     *
     * @return the parameter value
     *
     * @throws IllegalArgumentException if the object representing the parameter is {@code null}
     */
    static <E, T extends Collection<E>> T requiresNotNullOrNotEmptyParameter(final T value, final String name) throws IllegalArgumentException {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(String.format("Parameter %s is required and cannot be null or empty.", name));
        }
        return value;
    }
}
