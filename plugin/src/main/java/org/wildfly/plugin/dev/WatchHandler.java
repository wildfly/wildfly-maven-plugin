/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
