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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import javax.inject.Named;
import javax.inject.Singleton;

import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.wildfly.core.launcher.CliCommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.plugin.common.Environment;
import org.wildfly.plugin.common.StandardOutput;

/**
 * Executes CLI commands.
 *
 * @author Tomaz Cerar
 */
@Named
@Singleton
public class OfflineCLIExecutor extends AbstractLogEnabled {

    /**
     * Executes the commands and scripts provided.
     *
     * @param wildflyHome      the path to WildFly for setting the {@code jboss.home.dir} system property or {@code null} if
     *                         should not be set
     * @param commands         the commands to execute
     * @param stdout           the output stream to write standard output to
     * @param systemProperties the system properties to launch the CLI process with
     * @param javaOpts         the options to pass to the offline process
     *
     * @throws IOException if an error occurs processing the commands
     */
    public int execute(final String wildflyHome, final Commands commands, final StandardOutput stdout,
                       final Map<String, String> systemProperties, final String[] javaOpts) throws IOException {
        try {
            if (commands.hasScripts()) {
                for (File f : commands.getScripts()) {
                    final Path script = f.toPath();
                    getLogger().info("Executing script: " + script);
                    final int exitCode = executeInNewProcess(wildflyHome, script, systemProperties, stdout, javaOpts);
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
                    final int exitCode = executeInNewProcess(wildflyHome, script, systemProperties, stdout, javaOpts);
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

    private int executeInNewProcess(final String wildflyHome, final Path scriptFile, final Map<String, String> systemProperties, final StandardOutput stdout, String[] javaOpts) throws InterruptedException, IOException {

        final Logger log = getLogger();
        final CliCommandBuilder builder = CliCommandBuilder.of(wildflyHome)
                .setScriptFile(scriptFile);
        // Workaround for WFCORE-4121
        if (Environment.isModularJvm(builder.getJavaHome())) {
            builder.addJavaOptions(Environment.getModularJvmArguments());
        }
        if (systemProperties != null) {
            systemProperties.forEach((key, value) -> builder.addJavaOption(String.format("-D%s=%s", key, value)));
            if (systemProperties.containsKey("module.path")) {
                builder.setModuleDirs(systemProperties.get("module.path"));
            }
        }

        if (javaOpts != null) {
            log.debug("java opts: " + Arrays.toString(javaOpts));
            for (String opt : javaOpts) {
                opt = opt.replaceAll("\n", "").trim();
                log.debug(String.format("opt: '%s'", opt));
                if (!opt.trim().isEmpty()) {
                    builder.addJavaOption(opt);
                }
            }

        }
        if (log.isDebugEnabled()) {
            log.debug("process parameters: " + builder.build());
        }
        final Launcher launcher = Launcher.of(builder)
                .addEnvironmentVariable("JBOSS_HOME", wildflyHome)
                .setRedirectErrorStream(true);
        stdout.getRedirect().ifPresent(launcher::redirectOutput);
        final Process process = launcher.launch();
        final Optional<Thread> consoleConsumer = stdout.startConsumer(process);
        try {
            return process.waitFor();
        } finally {
            // Be safe and destroy the process to ensure we don't leave rouge processes running
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            consoleConsumer.ifPresent(Thread::interrupt);
        }
    }

}
