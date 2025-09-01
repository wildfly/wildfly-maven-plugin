/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import static org.junit.Assume.assumeNotNull;
import static org.wildfly.plugin.tests.AbstractWildFlyMojoTest.getPomFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.logging.Log;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.wildfly.plugin.tests.AbstractProvisionConfiguredMojoTestCase;
import org.wildfly.plugin.tests.AbstractWildFlyMojoTest;

abstract class AbstractImageTest extends AbstractProvisionConfiguredMojoTestCase {

    public AbstractImageTest() {
        super("wildfly-maven-plugin");
    }

    @BeforeClass
    public static void checkDockerInstallation() {
        assumeNotNull("Docker is not present in the installation, skipping the tests",
                ExecUtil.resolveImageBinary());
    }

    protected void assertConfigFileName(final String prefix, final String expectedEnvVar) throws Exception {
        final String imageName = "wildfly-" + prefix + "-maven-plugin/testing";
        final String binary = ExecUtil.resolveImageBinary();
        try {
            final Mojo imageMojo = lookupConfiguredMojo(getPomFile(prefix + "-pom.xml"), "image");
            imageMojo.execute();
            final Path jbossHome = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve(prefix);
            assertTrue(Files.exists(jbossHome));

            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            assertTrue(ExecUtil.exec(stdout, binary, "inspect", imageName));
            assertEnvironmentSet(stdout, expectedEnvVar);

        } finally {
            exec(binary, "rmi", imageName);
        }
    }

    protected static boolean exec(String command, String... args) {
        return ExecUtil.exec((Log) null, new File("."), command, args);
    }

    protected static String formatJson(final JsonStructure json) throws IOException {
        try (StringWriter writer = new StringWriter()) {
            JsonWriterFactory factory = Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));
            try (JsonWriter jsonWriter = factory.createWriter(writer)) {
                jsonWriter.write(json);
                return writer.toString();
            }
        }
    }

    protected static void assertEnvironmentSet(final ByteArrayOutputStream stdout, final String expectedEnvVar) {
        assertEnvironment(stdout, expectedEnvVar, true);
    }

    protected static void assertEnvironmentUnset(final ByteArrayOutputStream stdout, final String expectedEnvVar) {
        assertEnvironment(stdout, expectedEnvVar, false);
    }

    private static void assertEnvironment(final ByteArrayOutputStream stdout, final String expectedEnvVar,
            final boolean isSet) {

        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(stdout.toByteArray()))) {
            final JsonArray array = reader.readArray();
            Assert.assertFalse("Array was not expected to be empty: " + array, array.isEmpty());
            final JsonObject json = array.getJsonObject(0);
            final JsonObject config = json.getJsonObject("Config");
            final JsonArray env = config.getJsonArray("Env");
            boolean found = false;
            for (JsonValue value : env) {
                Assert.assertEquals(String.format("JSON value %s is not a string type: %s", value, value.getValueType()),
                        JsonValue.ValueType.STRING, value.getValueType());
                if (((JsonString) value).getString().contains(expectedEnvVar)) {
                    found = true;
                    break;
                }
            }
            if (isSet && !found) {
                Assert.fail(
                        String.format("Failed to find %s in environment.%n%s", expectedEnvVar, formatJson(env)));
            }
            if (!isSet && found) {
                Assert.fail(
                        String.format("Found %s in environment and it should not exist.%n%s", expectedEnvVar, formatJson(env)));
            }
        } catch (Exception e) {
            Assert.fail(String.format("Output from inspect is not valid JSON: %s%nOutput:%n%s", e.getMessage(), stdout));
        }
    }
}
