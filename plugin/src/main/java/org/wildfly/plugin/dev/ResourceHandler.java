/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.dev;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ResourceHandler implements WatchHandler {

    @Override
    public Result handle(final WatchContext context, final WatchEvent<Path> event, final Path file) throws IOException {
        return new Result() {
            @Override
            public boolean requiresCopyResources() {
                return true;
            }

            @Override
            public boolean requiresRepackage() {
                return true;
            }

            @Override
            public boolean requiresRedeploy() {
                return true;
            }
        };
    }
}
