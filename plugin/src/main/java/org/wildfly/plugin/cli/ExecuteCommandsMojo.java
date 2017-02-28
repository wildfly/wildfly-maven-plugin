/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.plugin.common.PropertyNames;

/**
 * Execute commands to the running WildFly Application Server.
 * <p/>
 * Commands should be formatted in the same manor CLI commands are formatted.
 * <p/>
 * Executing commands in a batch will rollback all changes if one command fails.
 * <pre>
 *      &lt;batch&gt;true&lt;/batch&gt;
 *      &lt;fail-on-error&gt;false&lt;/fail-on-error&gt;
 *      &lt;commands&gt;
 *          &lt;command&gt;/subsystem=logging/console=CONSOLE:write-attribute(name=level,value=DEBUG)&lt;/command&gt;
 *      &lt;/commands&gt;
 * </pre>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "execute-commands", threadSafe = true)
public class ExecuteCommandsMojo extends AbstractServerConnection {

    /**
     * {@code true} if commands execution should be skipped.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.SKIP)
    private boolean skip;

    /**
     * {@code true} if commands should be executed in a batch or {@code false} if they should be executed one at a
     * time.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.BATCH)
    private boolean batch;

    /**
     * The WildFly Application Server's home directory. This is not required, but should be used for commands such as
     * {@code module add} as they are executed on the local file system.
     */
    @Parameter(alias = "jboss-home", property = PropertyNames.JBOSS_HOME)
    private String jbossHome;

    /**
     * The system properties to be set when executing CLI commands.
     */
    @Parameter(alias = "system-properties")
    private Map<String, String> systemProperties;

    /**
     * The properties files to use when executing CLI scripts or commands.
     */
    @Parameter
    private List<File> propertiesFiles = new ArrayList<>();

    /**
     * The commands to execute.
     * <p>
     * Note that if defined the {@link #commands commands}, {@link #scripts scripts} and {@link #failOnError fail-on-error}
     * parameters outside of this configuration property are ignored.
     * </p>
     *
     * @deprecated Use the {@code <commands/>}, {@code <scripts/>} and {@code <batch/>} configuration parameters
     */
    @Parameter(alias = "execute-commands")
    @Deprecated
    private Commands executeCommands;

    /**
     * The CLI commands to execute.
     */
    @Parameter(property = PropertyNames.COMMANDS)
    private List<String> commands = new ArrayList<>();

    /**
     * The CLI script files to execute.
     */
    @Parameter(property = PropertyNames.SCRIPTS)
    private List<File> scripts = new ArrayList<>();

    /**
     * Indicates whether or not subsequent commands should be executed if an error occurs executing a command. A value of
     * {@code false} will continue processing commands even if a previous command execution results in a failure.
     */
    @Parameter(alias = "fail-on-error", defaultValue = "true", property = PropertyNames.FAIL_ON_ERROR)
    private boolean failOnError = true;

    /**
     * Indicates weather or not CLI should be executed in offline mode. This is useful for embedded server / host controller scripts
     */
    @Parameter(name = "offline", defaultValue = "false", property = PropertyNames.OFFLINE)
    private boolean offline = false;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;


    @Inject
    private CommandExecutor commandExecutor;

    @Inject OfflineCLIExecutor offlineCLIExecutor;

    @Override
    public String goal() {
        return "execute-commands";
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug("Skipping commands execution");
            return;
        }
        if (offline) {
            getLog().debug("Executing offline CLI scripts");
            try {
                offlineCLIExecutor.execute(jbossHome, scripts, getLog(), project, systemProperties);
            } catch (IOException e) {
                throw new MojoFailureException("Failed to execute scripts.", e);
            }

        } else {
            final Properties currentSystemProperties = System.getProperties();
            try {
                getLog().debug("Executing commands");
                // Create new system properties with the defaults set to the current system properties
                final Properties newSystemProperties = new Properties(currentSystemProperties);

                if (propertiesFiles != null) {
                    for (File file : propertiesFiles) {
                        parseProperties(file, newSystemProperties);
                    }
                }

                if (systemProperties != null) {
                    newSystemProperties.putAll(systemProperties);
                }

                // Set the system properties for executing commands
                System.setProperties(newSystemProperties);
                try (ModelControllerClient client = createClient()) {
                    commandExecutor.execute(client, jbossHome, getCommands());
                } catch (IOException e) {
                    throw new MojoExecutionException("Could not execute commands.", e);
                }
            } catch (IOException e) {
                throw new MojoFailureException("Failed to parse properties.", e);
            } finally {
                System.setProperties(currentSystemProperties);
            }
        }
    }

    private Commands getCommands() {
        if (executeCommands != null) {
            return executeCommands;
        }
        return new Commands(batch, commands, scripts, failOnError, offline);
    }

    private static void parseProperties(final File file, final Properties properties) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
    }
}
