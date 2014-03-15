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

import java.util.Comparator;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.jboss.jdf.stacks.client.StacksClient;
import org.jboss.jdf.stacks.model.Runtime;
import org.jboss.jdf.stacks.model.Stacks;

/**
 * Uses {@link org.jboss.jdf.stacks.model.Stacks stacks} to determine the latest runtime version.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class RuntimeVersions {

    private final StacksClient client;
    private volatile Stacks stacks;

    public RuntimeVersions() {
        client = new StacksClient();
    }

    /**
     * Gets the latest version of the runtime indicated by the {@code groupId} and the {@code artifactId}.
     * <p/>
     * <strong>Note:</strong> This may not be a final version.
     *
     * @param groupId    the group id
     * @param artifactId the artifact id
     *
     * @return the latest version or {@link org.wildfly.plugin.server.Defaults#WILDFLY_TARGET_VERSION} if the runtime
     * could not be found with the {@code groupId} and {@code artifactId}
     */
    public String getLatest(final String groupId, final String artifactId) {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId cannot be null");
        }
        if (artifactId == null) {
            throw new IllegalArgumentException("artifactId cannot be null");
        }
        final NavigableSet<String> versions = getVersions(groupId, artifactId);
        return (versions.isEmpty() ? Defaults.WILDFLY_TARGET_VERSION : versions.last());
    }

    /**
     * Gets the latest final version of the runtime indicated by the {@code groupId} and the {@code artifactId}.
     *
     * @param groupId    the group id
     * @param artifactId the artifact id
     *
     * @return the latest version or {@link org.wildfly.plugin.server.Defaults#WILDFLY_TARGET_VERSION} if the runtime
     * could not be found with the {@code groupId} and {@code artifactId}
     */
    public String getLatestFinal(final String groupId, final String artifactId) {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId cannot be null");
        }
        if (artifactId == null) {
            throw new IllegalArgumentException("artifactId cannot be null");
        }
        final NavigableSet<String> versions = getVersions(groupId, artifactId);
        for (final String version : versions.descendingSet()) {
            final String l = version.toLowerCase(Locale.ENGLISH);
            if (l.endsWith("final") || l.endsWith("ga")) {
                return version;
            }
        }

        return Defaults.WILDFLY_TARGET_VERSION;
    }

    private NavigableSet<String> getVersions(final String groupId, final String artifactId) {
        if (stacks == null) {
            synchronized (this) {
                if (stacks == null) {
                    stacks = client.getStacks();
                }
            }
        }
        final NavigableSet<String> versions = new TreeSet<>(new VersionComparator());
        for (Runtime runtime : stacks.getAvailableRuntimes()) {
            if (groupId.equalsIgnoreCase(runtime.getGroupId()) && artifactId.equalsIgnoreCase(runtime.getArtifactId())) {
                versions.add(runtime.getVersion());
            }
        }
        return versions;
    }

    public static class VersionComparator implements Comparator<String> {
        private static final Pattern PATTERN = Pattern.compile(".", Pattern.LITERAL);

        @Override
        public int compare(final String o1, final String o2) {
            final String[] vs1 = PATTERN.split(o1);
            final String[] vs2 = PATTERN.split(o2);
            int result = 0;
            // TODO (jrp) probably not the best comparison, but should work well enough
            for (int i = 0; i < Math.min(vs1.length, vs2.length); i++) {
                final String s1 = vs1[i];
                final String s2 = vs2[i];
                try {
                    final Integer i1 = Integer.parseInt(s1);
                    final Integer i2 = Integer.parseInt(s2);
                    result = i1.compareTo(i2);
                } catch (Exception e) {
                    result = s1.compareTo(s2);
                }
                if (result != 0) {
                    break;
                }
            }
            return result;
        }
    }
}
