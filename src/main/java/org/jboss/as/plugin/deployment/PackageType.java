package org.jboss.as.plugin.deployment;

import java.util.HashMap;
import java.util.Map;

import org.apache.maven.project.MavenProject;

/**
 * Date: 29.06.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class PackageType implements Comparable<PackageType> {

    private static final PackageType MAVEN_PLUGIN = new PackageType("maven-project");
    private static final PackageType POM = new PackageType("pom");
    private static final PackageType EJB = new PackageType("ejb", "jar");
    private static final Map<String, PackageType> DEFAULT_TYPES;

    static {
        DEFAULT_TYPES = new HashMap<String, PackageType>();
        DEFAULT_TYPES.put(MAVEN_PLUGIN.packaging, MAVEN_PLUGIN);
        DEFAULT_TYPES.put(POM.packaging, MAVEN_PLUGIN);
        DEFAULT_TYPES.put(EJB.packaging, MAVEN_PLUGIN);
    }

    private final String packaging;
    private final String fileExtension;

    private PackageType(final String packaging) {
        this.packaging = packaging;
        this.fileExtension = packaging;
    }

    private PackageType(final String packaging, final String fileExtension) {
        this.packaging = packaging;
        this.fileExtension = fileExtension;
    }

    /**
     * Resolves the package type from the maven project.
     *
     * @param project the maven project
     *
     * @return the package type
     */
    public static PackageType resolve(final MavenProject project) {
        final String packaging = project.getPackaging();
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
        return false;
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
