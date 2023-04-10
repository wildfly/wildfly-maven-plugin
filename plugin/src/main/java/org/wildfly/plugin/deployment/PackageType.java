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

package org.wildfly.plugin.deployment;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.maven.project.MavenProject;

/**
 * Date: 29.06.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("WeakerAccess")
public final class PackageType implements Comparable<PackageType> {

    private static final PackageType MAVEN_PLUGIN = new PackageType("maven-project", true);
    private static final PackageType POM = new PackageType("pom", true);
    private static final PackageType EJB = new PackageType("ejb", "jar");
    private static final Map<String, PackageType> DEFAULT_TYPES;

    static {
        DEFAULT_TYPES = new HashMap<>();
        DEFAULT_TYPES.put(MAVEN_PLUGIN.packaging, MAVEN_PLUGIN);
        DEFAULT_TYPES.put(POM.packaging, POM);
        DEFAULT_TYPES.put(EJB.packaging, EJB);
    }

    private final String packaging;
    private final String fileExtension;
    private final boolean ignored;

    private PackageType(final String packaging) {
        this(packaging, packaging, false);
    }

    private PackageType(final String packaging, final boolean ignored) {
        this(packaging, packaging, ignored);
    }

    private PackageType(final String packaging, final String fileExtension) {
        this(packaging, fileExtension, false);
    }

    private PackageType(final String packaging, final String fileExtension, final boolean ignored) {
        this.packaging = packaging;
        this.fileExtension = fileExtension;
        this.ignored = ignored;
    }

    /**
     * Resolves the package type from the maven project.
     *
     * @param project the maven project
     *
     * @return the package type
     */
    public static PackageType resolve(final MavenProject project) {
        final String packaging = project.getPackaging().toLowerCase(Locale.ROOT);
        if (DEFAULT_TYPES.containsKey(packaging)) {
            return DEFAULT_TYPES.get(packaging);
        }
        return new PackageType(packaging);
    }

    /**
     * Checks the packaging type to see if it should be ignored or not.
     *
     * @return {@code true} if the package type should be ignored, otherwise {@code false}.
     */
    public boolean isIgnored() {
        return ignored;
    }

    /**
     * Returns the raw packaging type.
     *
     * @return the packaging type
     */
    public String getPackaging() {
        return packaging;
    }

    /**
     * Returns the file extension that should be used.
     *
     * @return the file extension
     */
    public String getFileExtension() {
        return fileExtension;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PackageType)) {
            return false;
        }
        final PackageType other = (PackageType) obj;
        return (packaging == null ? other.packaging == null : packaging.equals(other.packaging));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 17;
        result = prime * result + (packaging == null ? 0 : packaging.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s[packaging=%s,translatedPackaging=%s]", PackageType.class.getName(),
                packaging, fileExtension);
    }

    @Override
    public int compareTo(final PackageType o) {
        return packaging.compareTo(o.packaging);
    }
}
