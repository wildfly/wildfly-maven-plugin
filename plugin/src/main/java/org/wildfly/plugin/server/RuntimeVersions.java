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

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.TreeSet;

import org.jboss.jdf.stacks.client.StacksClient;
import org.jboss.jdf.stacks.model.Runtime;
import org.jboss.jdf.stacks.model.Stacks;

/**
 * Uses {@link org.jboss.jdf.stacks.model.Stacks stacks} to determine the latest runtime version.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class RuntimeVersions {
    private final List<Runtime> availableRuntimes;

    private RuntimeVersions() {
        StacksClient client = new StacksClient();
        final Stacks stacks = client.getStacks();
        availableRuntimes = Collections.synchronizedList(stacks.getAvailableRuntimes());
    }

    private static class Holder {
        static final RuntimeVersions INSTANCE = new RuntimeVersions();
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
    public static String getLatest(final String groupId, final String artifactId) {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId cannot be null");
        }
        if (artifactId == null) {
            throw new IllegalArgumentException("artifactId cannot be null");
        }
        final NavigableSet<String> versions = Holder.INSTANCE.getVersions(groupId, artifactId);
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
    public static String getLatestFinal(final String groupId, final String artifactId) {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId cannot be null");
        }
        if (artifactId == null) {
            throw new IllegalArgumentException("artifactId cannot be null");
        }
        final NavigableSet<String> versions = Holder.INSTANCE.getVersions(groupId, artifactId);
        for (final String version : versions.descendingSet()) {
            final String l = version.toLowerCase(Locale.ENGLISH);
            if (l.endsWith("final") || l.endsWith("ga")) {
                return version;
            }
        }

        return Defaults.WILDFLY_TARGET_VERSION;
    }

    private NavigableSet<String> getVersions(final String groupId, final String artifactId) {
        final NavigableSet<String> versions = new TreeSet<>(new VersionComparator());
        synchronized (availableRuntimes) {
            for (Runtime runtime : availableRuntimes) {
                if (groupId.equalsIgnoreCase(runtime.getGroupId()) && artifactId.equalsIgnoreCase(runtime.getArtifactId())) {
                    versions.add(runtime.getVersion());
                }
            }
        }
        return versions;
    }
}
