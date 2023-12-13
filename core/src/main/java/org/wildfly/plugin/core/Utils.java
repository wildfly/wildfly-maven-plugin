/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author jdenise@redhat.com
 */
public class Utils {
    private static final Pattern WHITESPACE_IF_NOT_QUOTED = Pattern.compile("(\\S+\"[^\"]+\")|\\S+");

    public static boolean isValidHomeDirectory(final Path path) {
        return path != null
                && Files.exists(path)
                && Files.isDirectory(path)
                && Files.exists(path.resolve("jboss-modules.jar"));
    }

    /**
     * Splits the arguments into a list. The arguments are split based on
     * whitespace while ignoring whitespace that is within quotes.
     *
     * @param arguments the arguments to split
     *
     * @return the list of the arguments
     */
    public static List<String> splitArguments(final CharSequence arguments) {
        final List<String> args = new ArrayList<>();
        final Matcher m = WHITESPACE_IF_NOT_QUOTED.matcher(arguments);
        while (m.find()) {
            final String value = m.group();
            if (!value.isEmpty()) {
                args.add(value);
            }
        }
        return args;
    }
}
