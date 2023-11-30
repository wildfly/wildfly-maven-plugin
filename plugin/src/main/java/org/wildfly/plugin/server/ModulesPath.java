/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
