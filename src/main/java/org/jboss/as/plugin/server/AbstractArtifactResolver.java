/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.plugin.server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractArtifactResolver<T> implements ArtifactResolver {

    protected abstract T constructArtifact(final String groupId, final String artifactId, final String classifier, final String packaging, final String version);

    protected T createArtifact(final String artifact) {
        String groupId = null;
        String artifactId = null;
        String classifier = null;
        String packaging = null;
        String version = null;
        // groupId:artifactId:version[:packaging][:classifier].
        if (artifact != null) {
            final String[] artifactParts = artifact.split(":");
            if (artifactParts.length == 0) {
                throw new IllegalArgumentException(String.format("Invalid artifact pattern: %s", artifact));
            }
            String value;
            switch (artifactParts.length) {
                case 5:
                    value = artifactParts[4].trim();
                    if (!value.isEmpty()) {
                        classifier = value;
                    }
                case 4:
                    value = artifactParts[3].trim();
                    if (!value.isEmpty()) {
                        packaging = value;
                    }
                case 3:
                    value = artifactParts[2].trim();
                    if (!value.isEmpty()) {
                        version = value;
                    }
                case 2:
                    value = artifactParts[1].trim();
                    if (!value.isEmpty()) {
                        artifactId = value;
                    }
                case 1:
                    value = artifactParts[0].trim();
                    if (!value.isEmpty()) {
                        groupId = value;
                    }
            }
        }
        return constructArtifact(groupId, artifactId, classifier, packaging, version);
    }

    static <T> T invoke(final Object object, final String method, final Class<T> result) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        final Method m = object.getClass().getMethod(method);
        if (m == null) {
            throw new IllegalArgumentException(String.format("Method '%s' was not found on %s.", method, object.getClass()));
        }
        return result.cast(m.invoke(object));
    }

    /**
     * Checks to see if eclipse aether is being used.
     * <p/>
     * The check simply loads a known eclipse aether class and catches a {@link ClassNotFoundException class not found
     * exception}.
     *
     * @return {@code true} if eclipse aether is being used, otherwise {@code false}
     */
    static boolean isUsingEclipseAether() {
        try {
            Thread.currentThread().getContextClassLoader().loadClass("org.eclipse.aether.RepositorySystem");

            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
