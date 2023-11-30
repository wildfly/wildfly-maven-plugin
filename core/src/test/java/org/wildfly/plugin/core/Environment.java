/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.core;

import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.logging.Logger;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({ "WeakerAccess", "Duplicates" })
class Environment {

    /**
     * The default WildFly home directory specified by the {@code jboss.home} system property.
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

    static {
        final Logger logger = Logger.getLogger(Environment.class);

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
    }

    public static ModelControllerClient createClient() throws UnknownHostException {
        return ModelControllerClient.Factory.create(HOSTNAME, PORT);
    }

    private static void validateWildFlyHome(final Path wildflyHome) {
        if (!ServerHelper.isValidHomeDirectory(wildflyHome)) {
            throw new RuntimeException("Invalid WildFly home directory: " + wildflyHome);
        }
    }
}
