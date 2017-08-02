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

    public Collection<String> getModulePaths() {
        if (paths == null && modulePath == null) {
            return Collections.emptyList();
        }
        final Collection<String> result = new ArrayList<>();
        if (modulePath != null) {
            result.add(modulePath.getAbsolutePath());
        }
        if (paths != null) {
            for (File path : paths) {
                result.add(path.getAbsolutePath());
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
