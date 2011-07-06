package org.jboss.as.plugin.deployment;

/**
 * Date: 29.06.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
enum IgnoredPackageTypes {

    /**
     * A maven plugin-project.
     */
    MAVEN_PLUGIN("maven-plugin"),
    /**
     * A POM project.
     */
    POM("pom");

    private final String packaging;

    IgnoredPackageTypes(final String packaging) {
        this.packaging = packaging;
    }

    /**
     * Checks the packaging type to see if it should be ignored or not.
     *
     * @param packaging the packaging type to check.
     *
     * @return {@code true} if the package type should be ignored, otherwise {@code false}.
     */
    public static boolean isIgnored(final String packaging) {
        for (IgnoredPackageTypes types : IgnoredPackageTypes.values()) {
            if (types.packaging.equalsIgnoreCase(packaging)) {
                return true;
            }
        }
        return false;
    }
}
