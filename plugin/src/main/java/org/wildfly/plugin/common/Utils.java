/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple utility class.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Utils {
    private static final Pattern EMPTY_STRING = Pattern.compile("^$|\\s+");

    private static final Pattern WHITESPACE_IF_NOT_QUOTED = Pattern.compile("(\\S+\"[^\"]+\")|\\S+");

    public static final String WILDFLY_DEFAULT_DIR = "server";

    /**
     * Tests if the character sequence is not {@code null} and not empty.
     *
     * @param seq the character sequence to test
     *
     * @return {@code true} if the character sequence is not {@code null} and not empty
     */
    public static boolean isNotNullOrEmpty(final CharSequence seq) {
        return seq != null && !EMPTY_STRING.matcher(seq).matches();
    }

    /**
     * Tests if the arrays is not {@code null} and not empty.
     *
     * @param array the array to test
     *
     * @return {@code true} if the array is not {@code null} and not empty
     */
    public static boolean isNotNullOrEmpty(final Object[] array) {
        return array != null && array.length > 0;
    }

    /**
     * Converts an iterable to a delimited string.
     *
     * @param iterable  the iterable to convert
     * @param delimiter the delimiter
     *
     * @return a delimited string of the iterable
     */
    public static String toString(final Iterable<?> iterable, final CharSequence delimiter) {
        final StringBuilder result = new StringBuilder();
        final Iterator<?> iterator = iterable.iterator();
        while (iterator.hasNext()) {
            result.append(iterator.next());
            if (iterator.hasNext()) {
                result.append(delimiter);
            }
        }
        return result.toString();
    }

    /**
     * Splits the arguments into a list. The arguments are split based on whitespace while ignoring whitespace that is
     * within quotes.
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
