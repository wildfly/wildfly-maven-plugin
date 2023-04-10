/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class VersionComparator implements Comparator<String> {
    @Override
    public int compare(final String o1, final String o2) {
        final Version v1 = Version.parse(o1);
        final Version v2 = Version.parse(o2);
        return v1.compareTo(v2);
    }

    private enum ReleaseType {
        UNKNOWN(null),
        SNAPSHOT("snapshot"),
        ALPHA("alpha", "a"),
        BETA("beta", "b"),
        MILESTONE("milestone", "m"),
        RELEASE_CANDIDATE("rc", "cr"),
        FINAL("final", "", "ga"),;

        private static final Map<String, ReleaseType> ENTRIES;

        static {
            final Map<String, ReleaseType> map = new HashMap<>();
            for (ReleaseType r : values()) {
                if (r == UNKNOWN)
                    continue;
                map.put(r.type, r);
                map.put("-" + r.type, r);
                for (String alias : r.aliases) {
                    map.put(alias, r);
                }
            }
            ENTRIES = Collections.unmodifiableMap(map);
        }

        private final String type;
        private final List<String> aliases;

        ReleaseType(final String type, final String... aliases) {
            this.type = type;
            this.aliases = Collections.unmodifiableList(Arrays.asList(aliases));
        }

        static ReleaseType find(final String s) {
            if (ENTRIES.containsKey(s)) {
                return ENTRIES.get(s);
            }
            return UNKNOWN;
        }
    }

    private static class Version implements Comparable<Version> {
        private final List<Part> parts;
        private final String original;

        private Version(final String original, final List<Part> parts) {
            this.original = original;
            this.parts = parts;
        }

        public static Version parse(final String version) {
            final List<Part> parts = new ArrayList<>();
            final StringBuilder sb = new StringBuilder();
            boolean isDigit = false;
            for (char c : version.toCharArray()) {
                switch (c) {
                    case '-':
                    case '.': {
                        if (isDigit) {
                            parts.add(new IntegerPart(Integer.parseInt(sb.toString())));
                        } else {
                            parts.add(new StringPart(sb.toString()));
                        }
                        sb.setLength(0);
                        isDigit = false;
                        continue;
                    }
                    default: {
                        if (Character.isDigit(c)) {
                            if (!isDigit && sb.length() > 0) {
                                parts.add(new StringPart(sb.toString()));
                                sb.setLength(0);
                            }
                            isDigit = true;
                        } else {
                            if (isDigit && sb.length() > 0) {
                                parts.add(new IntegerPart(Integer.parseInt(sb.toString())));
                                sb.setLength(0);
                            }
                            isDigit = false;
                        }
                        sb.append(c);
                    }
                }
            }
            if (sb.length() > 0) {
                if (isDigit) {
                    parts.add(new IntegerPart(Integer.parseInt(sb.toString())));
                } else {
                    parts.add(new StringPart(sb.toString()));
                }
            }
            return new Version(version, parts);
        }

        @Override
        public int compareTo(final Version o) {
            final Iterator<Part> left = parts.iterator();
            final Iterator<Part> right = o.parts.iterator();
            int result = 0;
            while (left.hasNext() || right.hasNext()) {
                if (left.hasNext() && right.hasNext()) {
                    result = left.next().compareTo(right.next());
                } else if (left.hasNext()) {
                    result = left.next().compareTo(NULL_PART);
                } else if (right.hasNext()) {
                    // Need the inverse of the comparison
                    result = (-1 * right.next().compareTo(NULL_PART));
                }
                if (result != 0) {
                    break;
                }
            }
            return result;
        }

        @Override
        public int hashCode() {
            return 33 * (17 + original.hashCode());
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this)
                return true;
            if (!(obj instanceof Version)) {
                return false;
            }
            final Version other = (Version) obj;
            return original.equals(other.original);
        }

        @Override
        public String toString() {
            return original;
        }

        private interface Part extends Comparable<Part> {
        }

        private static final Part NULL_PART = new Part() {
            @Override
            public int compareTo(final Part o) {
                throw new UnsupportedOperationException();
            }
        };

        private static class IntegerPart implements Part {
            private static final Integer DEFAULT_INTEGER = 0;
            private final Integer value;

            private IntegerPart(final Integer value) {
                this.value = value;
            }

            @Override
            public int compareTo(final Part o) {
                if (o == NULL_PART) {
                    return value.compareTo(DEFAULT_INTEGER);
                }
                if (o instanceof IntegerPart) {
                    return value.compareTo(((IntegerPart) o).value);
                }
                return 1;
            }

            @Override
            public String toString() {
                return value.toString();
            }
        }

        private static class StringPart implements Part {
            private final String originalValue;
            private final String value;
            private final ReleaseType releaseType;

            private StringPart(final String value) {
                originalValue = value;
                this.value = value.toLowerCase(Locale.ROOT);
                releaseType = ReleaseType.find(this.value);
            }

            @Override
            public int compareTo(final Part o) {
                if (o == NULL_PART) {
                    return releaseType.compareTo(ReleaseType.FINAL);
                }
                if (o instanceof StringPart) {
                    if (releaseType == ReleaseType.UNKNOWN && ((StringPart) o).releaseType == ReleaseType.UNKNOWN) {
                        return value.compareTo(((StringPart) o).value);
                    }
                    return releaseType.compareTo(((StringPart) o).releaseType);
                }
                return -1;
            }

            @Override
            public String toString() {
                return originalValue;
            }
        }
    }
}
