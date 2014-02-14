/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.plugin.server;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Represents a module path element.
 * <p/>
 * Guarded by {@code this}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ModulesPath {

    /**
     * Defines a list of paths to be used for the modules path.
     */
    @Parameter(alias = "paths")
    private File[] paths;

    private File modulePath;

    /**
     * Returns the path or paths as a string delimited by the {@link File#pathSeparatorChar} if more than one path was
     * defined.
     *
     * @return the modules directory path
     */
    public synchronized String get() {
        if (paths == null && modulePath == null) {
            return null;
        }
        if (paths == null) {
            return modulePath.getAbsolutePath();
        }
        final StringBuilder result = new StringBuilder();
        if (modulePath != null) {
            result.append(modulePath.getAbsolutePath()).append(File.pathSeparatorChar);
        }
        for (int i = 0; i < paths.length; i++) {
            result.append(paths[i].getAbsolutePath());
            if (i + 1 < paths.length) {
                result.append(File.pathSeparatorChar);
            }
        }
        return result.toString();
    }

    /**
     * Returns a list of invalid module paths. If no paths are invalid an empty list is returned.
     *
     * @return a list of invalid module paths or an empty list
     */
    public synchronized List<String> validate() {
        if (paths == null && modulePath == null) {
            return Collections.emptyList();
        }
        final Collection<File> files = new ArrayList<>();
        if (modulePath != null) {
            files.add(modulePath);
        }
        if (paths != null) {
            Collections.addAll(files, paths);
        }
        final List<String> result = new ArrayList<>();
        for (File file : files) {
            if (!file.exists() || !file.isDirectory()) {
                result.add(file.getAbsolutePath());
            }
        }
        return result;
    }

    /**
     * Sets the modules path. Used for Maven to allow a single path to be set.
     *
     * @param modulePath the module path to set
     */
    public synchronized void set(final File modulePath) {
        this.modulePath = modulePath;
    }
}
