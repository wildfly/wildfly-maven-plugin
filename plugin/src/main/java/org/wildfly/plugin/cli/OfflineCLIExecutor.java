/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.plugin.cli;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.plugin.logging.Log;
import org.wildfly.core.launcher.CliCommandBuilder;
import org.wildfly.plugin.core.ServerProcess;

/**
 * Executes CLI commands.
 *
 * @author Tomaz Cerar
 */
@Named
@Singleton
public class OfflineCLIExecutor {


    /**
     * Executes the commands and scripts provided.
     *
     * @param wildflyHome      the path to WildFly for setting the {@code jboss.home.dir} system property or {@code null} if
     *                         should not be set
     * @param commands         the commands to execute
     * @param log              the logger to use
     * @param stdout           the output stream to write standard output to
     * @param systemProperties the system properties to launch the CLI process with
     *
     * @throws IOException if an error occurs processing the commands
     */
    public int execute(final String wildflyHome, final Commands commands, final Log log, final OutputStream stdout,
                       final Map<String, String> systemProperties) throws IOException {
        try {
            if (commands.hasScripts()) {
                for (File f : commands.getScripts()) {
                    final Path script = f.toPath();
                    log.info("Executing script: " + script);
                    final int exitCode = executeInNewProcess(wildflyHome, script, systemProperties, stdout);
                    if (exitCode != 0) {
                        return exitCode;
                    }
                }
            }
            if (commands.hasCommands()) {
                final Path script = Files.createTempFile("wildfly-maven-plugin-cli-script", ".cli");
                try {
                    try (BufferedWriter writer = Files.newBufferedWriter(script, StandardCharsets.UTF_8)) {
                        if (commands.isBatch()) {
                            writer.write("batch");
                            writer.newLine();
                        }
                        for (String cmd : commands.getCommands()) {
                            writer.write(cmd);
                            writer.newLine();
                        }
                        if (commands.isBatch()) {
                            writer.write("run-batch");
                            writer.newLine();
                        }
                    }
                    final int exitCode = executeInNewProcess(wildflyHome, script, systemProperties, stdout);
                    if (exitCode != 0) {
                        return exitCode;
                    }
                } finally {
                    Files.deleteIfExists(script);
                }
            }
        } catch (InterruptedException e) {
            //
        }
        return 0;
    }

    private int executeInNewProcess(final String wildflyHome, final Path scriptFile, final Map<String, String> systemProperties, final OutputStream stdout) throws InterruptedException, IOException {
        final CliCommandBuilder builder = CliCommandBuilder.of(wildflyHome)
                .setScriptFile(scriptFile);
        if (systemProperties != null) {
            systemProperties.forEach((key, value) -> builder.addJavaOption(String.format("-D%s=%s", key, value)));
        }
        final Process process = ServerProcess.start(builder, Collections.singletonMap("JBOSS_HOME", wildflyHome), stdout);
        try {
            return process.waitFor();
        } finally {
            // Be safe and destroy the process to ensure we don't leave rouge processes running
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

}
