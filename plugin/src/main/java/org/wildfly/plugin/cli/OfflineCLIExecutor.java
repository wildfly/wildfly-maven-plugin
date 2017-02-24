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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.wildfly.core.launcher.CliCommandBuilder;
import org.wildfly.core.launcher.Launcher;

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
     * @param wildflyHome the path to WildFly for setting the {@code jboss.home.dir} system property or {@code null} if
     *                    should not be set
     * @param scripts     the scripts to execute
     * @throws IOException if an error occurs processing the commands
     */
    public void execute(final String wildflyHome, final List<File> scripts, Log log, MavenProject project, Map<String, String> systemProperties) throws IOException {
        String target = project.getModel().getBuild().getDirectory();
        Path pluginDir = Paths.get(target, "wildfly-plugin");
        Path output = pluginDir.resolve("script-out.log");
        Path properties = null;
        Files.deleteIfExists(output);
        Files.deleteIfExists(pluginDir);
        Files.createDirectory(pluginDir);
        if (systemProperties != null && !systemProperties.isEmpty()) {
            properties = pluginDir.resolve("system.properties");
            Files.deleteIfExists(properties);
            Properties props = new Properties();
            props.putAll(systemProperties);
            props.store(Files.newBufferedWriter(properties, StandardCharsets.UTF_8), null);
        }
        try {
            for (File f : scripts) {
                final CliCommandBuilder builder = CliCommandBuilder.of(wildflyHome)
                        .setScriptFile(f.toPath());
                if (properties != null) {
                    builder.addCliArgument("--properties=" + properties.toString());
                }
                log.info("Executing script: " + f.toPath());
                //PrintStream out = new PrintStream(Files.newOutputStream(output));
                Process process = Launcher.of(builder)
                        // Redirect the output and error stream to a file
                        .redirectOutput(output)
                        .addEnvironmentVariable("JBOSS_HOME", wildflyHome)
                        .launch();
                int result = process.waitFor();
                process.destroyForcibly();
                if (result != 0){
                    List<String> l = Files.readAllLines(output, StandardCharsets.UTF_8);
                    StringBuffer err = new StringBuffer();
                    l.subList(Math.max(l.size() - 3, 0), l.size())
                            .forEach(s -> err.append(s).append("\n"));
                    throw new IOException("Script execution failed, last few lines of execution log: \n"+err);
                }
                // new Thread(new ConsoleConsumer(process.getInputStream(), out)).start();

            }
        } catch (InterruptedException e) {
            //
        }
    }

}
