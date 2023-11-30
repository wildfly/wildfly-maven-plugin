/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.dev;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * A handler for changes that happen to source files.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface WatchHandler {

    Result handle(WatchContext context, WatchEvent<Path> event, Path file) throws IOException, MojoExecutionException;

    /**
     * The result of handling the changed source file.
     */
    interface Result {

        /**
         * Indicates whether a recompile is required.
         *
         * @return {@code true} if a recompile is required
         */
        default boolean requiresRecompile() {
            return false;
        }

        /**
         * Indicates whether the deployment should be redeployed is required.
         *
         * @return {@code true} if the deployment should be redeployed is required
         */
        default boolean requiresRedeploy() {
            return false;
        }

        /**
         * Indicates whether the resources should be copied.
         *
         * @return {@code true} if the resources should be copied
         */
        default boolean requiresCopyResources() {
            return false;
        }

        /**
         * Indicates whether the deployment should be repackaged.
         *
         * @return {@code true} if the deployment should be repackaged
         */
        default boolean requiresRepackage() {
            return false;
        }
    }
}
