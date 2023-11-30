/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.dev;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class WebAppResourceHandler implements WatchHandler {
    private static final Set<String> NO_DEPLOYMENT_WEB_FILE_EXTENSIONS = Set.of(
            ".xhtml",
            ".html",
            ".jsp",
            ".js",
            ".css");

    private final Set<String> ignoredFileExtensions;

    WebAppResourceHandler(final Collection<String> ignoredFileExtensions) {
        this.ignoredFileExtensions = ignoredFileExtensions.stream()
                .map((value) -> value.charAt(0) == '.' ? value : "." + value)
                .collect(Collectors.toCollection(HashSet::new));
        this.ignoredFileExtensions.addAll(NO_DEPLOYMENT_WEB_FILE_EXTENSIONS);
    }

    @Override
    public Result handle(final WatchContext context, final WatchEvent<Path> event, final Path file) throws IOException {
        // Check the file extension to see if a redeploy should be ignored
        final String fileName = file.getFileName().toString();
        final int dot = fileName.lastIndexOf('.');
        final boolean requiresRedeploy = dot <= 0 ||
                !ignoredFileExtensions.contains(fileName.substring(dot).toLowerCase(Locale.ROOT));
        return new Result() {

            @Override
            public boolean requiresRepackage() {
                return true;
            }

            @Override
            public boolean requiresRedeploy() {
                return requiresRedeploy;
            }
        };
    }
}
