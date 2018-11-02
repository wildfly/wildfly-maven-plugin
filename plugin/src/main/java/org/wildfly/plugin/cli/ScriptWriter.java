/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.plugin.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ScriptWriter {

    /**
     * Creates a script file for the commands. If {@code failOnError} is {@code false} the command will be wrapped in a
     * {@code try-catch} command.
     *
     * @param commands    the commands to write to the scripts
     * @param batch       {@code true} if the batch command should be added
     * @param failOnError {@code false} to wrap commands in a {@code try-catch}
     *
     * @return the script file
     *
     * @throws IOException if an error occurs creating the script file
     */
    static Path create(final Iterable<String> commands, final boolean batch, final boolean failOnError) throws IOException {
        final Path tempScript = Files.createTempFile("cli-scrpts", ".cli");
        try (BufferedWriter writer = Files.newBufferedWriter(tempScript, StandardCharsets.UTF_8)) {
            if (batch) {
                writer.write("batch");
                writer.newLine();
            }

            boolean inTry = false;

            for (String cmd : commands) {
                if (failOnError) {
                    writeCommand(writer, cmd);
                } else {
                    if ("try".equals(cmd.trim())) {
                        inTry = true;
                    }
                    if (inTry) {
                        writeCommand(writer, cmd);
                    } else {
                        writeCommandInTry(writer, cmd);
                    }
                    if ("end-try".equals(cmd.trim())) {
                        inTry = false;
                    }
                }
            }

            if (batch) {
                writer.write("run-batch");
                writer.newLine();
            }
        }
        return tempScript;
    }

    private static void writeCommand(final BufferedWriter writer, final String cmd) throws IOException {
        writer.write(cmd);
        writer.newLine();
    }

    private static void writeCommandInTry(final BufferedWriter writer, final String cmd) throws IOException {
        writer.write("try");
        writer.newLine();
        writeCommand(writer, cmd);
        writer.write("catch");
        writer.newLine();
        writer.write("echo Failed to execute command: \"");
        writer.write(cmd);
        writer.write('"');
        writer.newLine();
        writer.write("end-try");
        writer.newLine();
    }

}
