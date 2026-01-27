/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.tools;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
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

import org.junit.jupiter.api.Assertions;

/**
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
public class TestSupport {

    public static void deleteRecursively(final Path path) throws IOException {
        if (Files.exists(path)) {
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                        Files.deleteIfExists(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.deleteIfExists(path);
            }
        }
    }

    public static String formatJson(final JsonStructure json) throws IOException {
        try (StringWriter writer = new StringWriter()) {
            final JsonWriterFactory factory = Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));
            try (JsonWriter jsonWriter = factory.createWriter(writer)) {
                jsonWriter.write(json);
                return writer.toString();
            }
        }
    }

    public static void assertEnvironmentSet(final ByteArrayOutputStream stdout, final String expectedEnvVar) {
        assertEnvironment(stdout, expectedEnvVar, true);
    }

    public static void assertEnvironmentUnset(final ByteArrayOutputStream stdout, final String expectedEnvVar) {
        assertEnvironment(stdout, expectedEnvVar, false);
    }

    private static void assertEnvironment(final ByteArrayOutputStream stdout, final String expectedEnvVar,
            final boolean isSet) {

        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(stdout.toByteArray()))) {
            final JsonArray array = reader.readArray();
            Assertions.assertFalse(array.isEmpty(), "Array was not expected to be empty: " + array);
            final JsonObject json = array.getJsonObject(0);
            final JsonObject config = json.getJsonObject("Config");
            final JsonArray env = config.getJsonArray("Env");
            boolean found = false;
            for (JsonValue value : env) {
                Assertions.assertEquals(JsonValue.ValueType.STRING,
                        value.getValueType(),
                        String.format("JSON value %s is not a string type: %s", value, value.getValueType()));
                if (((JsonString) value).getString().contains(expectedEnvVar)) {
                    found = true;
                    break;
                }
            }
            if (isSet && !found) {
                fail(
                        String.format("Failed to find %s in environment.%n%s", expectedEnvVar, formatJson(env)));
            }
            if (!isSet && found) {
                fail(
                        String.format("Found %s in environment and it should not exist.%n%s", expectedEnvVar, formatJson(env)));
            }
        } catch (Exception e) {
            fail(String.format("Output from inspect is not valid JSON: %s%nOutput:%n%s", e.getMessage(), stdout));
        }
    }
}
