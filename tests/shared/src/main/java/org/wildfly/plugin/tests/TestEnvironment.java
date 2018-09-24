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

package org.wildfly.plugin.tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;
import org.wildfly.plugin.common.Environment;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class TestEnvironment extends Environment {

    /**
     * The default WildFly home directory specified by the {@code wildfly.dist} system property.
     * <p/>
     * Note that the {@code wildfly.dist} will not match the path specified here. The WildFly distribution is copied to
     * a temporary directory to keep the environment clean.
     * }
     */
    public static final Path WILDFLY_HOME;
    /**
     * The host name specified by the {@code wildfly.management.hostname} system property or {@code localhost} by
     * default.
     */
    public static final String HOSTNAME = System.getProperty("wildfly.management.hostname", "localhost");
    /**
     * The port specified by the {@code wildfly.management.port} system property or {@code 9990} by default.
     */
    public static final int PORT;

    /**
     * The default server startup timeout specified by {@code wildfly.timeout}, default is 60 seconds.
     */
    public static final long TIMEOUT;
    private static final boolean IS_MODULAR_JVM;

    static {
        final Logger logger = Logger.getLogger(TestEnvironment.class);

        // Get the WildFly home directory and copy to the temp directory
        final String wildflyDist = System.getProperty("jboss.home");
        assert wildflyDist != null : "WildFly home property, jboss.home, was not set";
        Path wildflyHome = Paths.get(wildflyDist);
        validateWildFlyHome(wildflyHome);
        WILDFLY_HOME = wildflyHome;

        final String port = System.getProperty("wildfly.management.port", "9990");
        try {
            PORT = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            logger.debugf(e, "Invalid port: %s", port);
            throw new RuntimeException("Invalid port: " + port, e);
        }

        final String timeout = System.getProperty("wildfly.timeout", "60");
        try {
            TIMEOUT = Long.parseLong(timeout);
        } catch (NumberFormatException e) {
            logger.debugf(e, "Invalid timeout: %s", timeout);
            throw new RuntimeException("Invalid timeout: " + timeout, e);
        }
        final String javaVersion = System.getProperty("java.specification.version");
        int vmVersion;
        try {
            final Matcher matcher = Pattern.compile("^(?:1\\.)?(\\d+)$").matcher(javaVersion);
            if (matcher.find()) {
                vmVersion = Integer.valueOf(matcher.group(1));
            } else {
                throw new RuntimeException("Unknown version of jvm " + javaVersion);
            }
        } catch (Exception e) {
            vmVersion = 8;
        }
        IS_MODULAR_JVM = vmVersion > 8;
    }

    public static boolean isModularJvm() {
        return IS_MODULAR_JVM;
    }

    public static boolean isValidWildFlyHome(final Path wildflyHome) {
        return Files.exists(wildflyHome) && Files.isDirectory(wildflyHome) && Files.exists(wildflyHome.resolve("jboss-modules.jar"));
    }

    public static void validateWildFlyHome(final Path wildflyHome) {
        if (!isValidWildFlyHome(wildflyHome)) {
            throw new RuntimeException("Invalid WildFly home directory: " + wildflyHome);
        }
    }
}
