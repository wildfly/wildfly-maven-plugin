/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.util.PathsUtils;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.jboss.logging.Logger;
import org.wildfly.plugin.tools.Deployment;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class TestEnvironment {

    /**
     * The default WildFly home directory specified by the {@code wildfly.dist} system property.
     * <p/>
     * Note that the {@code wildfly.dist} will not match the path specified here. The WildFly distribution is copied to
     * a temporary directory to keep the environment clean.
     * }
     */
    public static final Path WILDFLY_HOME;
    /**
     * The default host, {@code localhost}.
     */
    public static final String HOSTNAME = "localhost";

    /**
     * The default port of 9990.
     */
    public static final int PORT = 9990;

    /**
     * The default HTTP port of 8080.
     */
    public static final int HTTP_PORT = 8080;

    /**
     * The default server startup timeout specified by {@code wildfly.timeout}, default is 60 seconds.
     */
    public static final long TIMEOUT;

    public static final String TEST_PROJECT_PATH = "target/test-classes/test-project";
    public static final String TEST_PROJECT_TARGET_PATH = TEST_PROJECT_PATH + "/target";
    public static final String DEPLOYMENT_NAME = "test.war";

    static {
        final Logger logger = Logger.getLogger(TestEnvironment.class);
        Path wildflyHome = null;
        if (!Boolean.getBoolean("wildfly.test.bootable")) {
            // Get the WildFly home directory and copy to the temp directory
            final String wildflyDist = System.getProperty("jboss.home");
            assert wildflyDist != null : "WildFly home property, jboss.home, was not set";
            wildflyHome = Paths.get(wildflyDist);
            validateWildFlyHome(wildflyHome);
        }
        WILDFLY_HOME = wildflyHome;

        final String timeout = System.getProperty("wildfly.timeout", "60");
        try {
            TIMEOUT = Long.parseLong(timeout);
        } catch (NumberFormatException e) {
            logger.debugf(e, "Invalid timeout: %s", timeout);
            throw new RuntimeException("Invalid timeout: " + timeout, e);
        }
    }

    public static void checkDomainWildFlyHome(final Path wildflyHome, final int numDeployments,
            final boolean stateRecorded, final String... configTokens) throws Exception {
        assertTrue(isValidWildFlyHome(wildflyHome));
        final List<Path> deployments = resolveDeployments(wildflyHome.resolve("domain/data/content"));
        // Check that the number of deployments matches the expected number of deployments
        assertEquals(Math.max(numDeployments, 0), deployments.size(), () -> "Deployments %s".formatted(deployments));
        final Path history = wildflyHome.resolve("domain").resolve("configuration").resolve("domain_xml_history");
        assertFalse(Files.exists(history));

        Path configFile = wildflyHome.resolve("domain/configuration/domain.xml");
        assertTrue(Files.exists(configFile));

        if (configTokens != null) {
            final String str = Files.readString(configFile);
            for (String token : configTokens) {
                assertTrue(str.contains(token), str);
            }
        }
        assertEquals(Files.exists(wildflyHome.resolve(".galleon")), stateRecorded);
        assertEquals(Files.exists(wildflyHome.resolve(".wildfly-maven-plugin-provisioning.xml")), !stateRecorded);
    }

    public static void checkStandaloneWildFlyHome(final Path wildflyHome, final int numDeployments,
            final String[] layers, final String[] excludedLayers, final boolean stateRecorded, final String... configTokens)
            throws Exception {
        assertTrue(isValidWildFlyHome(wildflyHome));
        final List<Path> deployments = resolveDeployments(wildflyHome.resolve("standalone/deployments"));
        // Check that the number of deployments matches the expected number of deployments
        assertEquals(Math.max(numDeployments, 0), deployments.size(), () -> "Deployments %s".formatted(deployments));

        final Path history = wildflyHome.resolve("standalone").resolve("configuration").resolve("standalone_xml_history");
        assertFalse(Files.exists(history));

        final Path configFile = wildflyHome.resolve("standalone/configuration/standalone.xml");
        assertTrue(Files.exists(configFile));
        if (layers != null) {
            final Path provisioning = PathsUtils.getProvisioningXml(wildflyHome);
            assertTrue(Files.exists(provisioning));
            final ProvisioningConfig config = ProvisioningXmlParser.parse(provisioning);
            final ConfigModel cm = config.getDefinedConfig(new ConfigId("standalone", "standalone.xml"));
            assertNotNull(cm, config.getDefinedConfigs().toString());
            assertEquals(layers.length, cm.getIncludedLayers().size());
            for (String layer : layers) {
                assertTrue(cm.getIncludedLayers().contains(layer));
            }
            if (excludedLayers != null) {
                for (String layer : excludedLayers) {
                    assertTrue(cm.getExcludedLayers().contains(layer));
                }
            }
        }
        if (configTokens != null) {
            final String str = Files.readString(configFile);
            for (String token : configTokens) {
                assertTrue(str.contains(token), str);
            }
        }
        assertEquals(Files.exists(wildflyHome.resolve(".galleon")), stateRecorded);
        assertEquals(Files.exists(wildflyHome.resolve(".wildfly-maven-plugin-provisioning.xml")), !stateRecorded);
    }

    public static boolean isValidWildFlyHome(final Path wildflyHome) {
        return Files.exists(wildflyHome) && Files.isDirectory(wildflyHome)
                && Files.exists(wildflyHome.resolve("jboss-modules.jar"));
    }

    public static void validateWildFlyHome(final Path wildflyHome) {
        if (!isValidWildFlyHome(wildflyHome)) {
            throw new RuntimeException("Invalid WildFly home directory: " + wildflyHome);
        }
    }

    /**
     * Returns the deployment {@code test.war} from the test resources.
     *
     * @return the deployment
     */
    public static Deployment getDeployment() {
        return Deployment.of(Path.of(TEST_PROJECT_PATH, "target", DEPLOYMENT_NAME));
    }

    public static Path resolveProjectTarget(final String... pathElements) {
        return Path.of(TEST_PROJECT_TARGET_PATH, pathElements);
    }

    private static List<Path> resolveDeployments(final Path deploymentDir) throws IOException {
        if (Files.notExists(deploymentDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(deploymentDir)) {
            return stream
                    .filter(f -> !f.getFileName().toString().equals("README.txt"))
                    .toList();
        }
    }
}
