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
