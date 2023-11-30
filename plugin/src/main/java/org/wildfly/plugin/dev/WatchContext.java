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
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class WatchContext implements Comparable<WatchContext> {
    private final Path directory;
    private final WatchHandler handler;

    private WatchContext(final Path directory, final WatchHandler handler) {
        this.directory = directory;
        this.handler = handler;
    }

    static WatchContext of(final Path directory, final WatchHandler handler) {
        return new WatchContext(directory, handler);
    }

    Path directory() {
        return directory;
    }

    WatchHandler handler() {
        return handler;
    }

    // TODO (jrp) should this just go away?
    WatchHandler.Result handle(final WatchEvent<Path> event, final Path file) throws MojoExecutionException, IOException {
        return handler.handle(this, event, directory.resolve(file));
    }

    @Override
    public int compareTo(final WatchContext o) {
        return directory().compareTo(o.directory());
    }
}
