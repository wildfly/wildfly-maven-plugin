/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.core;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 *
 * @author jdenise@redhat.com
 */
public class Utils {

    public static boolean isValidHomeDirectory(final Path path) {
        return path != null
                && Files.exists(path)
                && Files.isDirectory(path)
                && Files.exists(path.resolve("jboss-modules.jar"));
    }
}
