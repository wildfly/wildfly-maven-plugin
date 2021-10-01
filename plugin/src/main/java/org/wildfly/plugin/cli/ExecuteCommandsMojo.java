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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.plugin.common.Archives;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.repository.ArtifactNameBuilder;
import org.wildfly.plugin.repository.ArtifactResolver;

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
     * The WildFly Application Server's home directory.
     * <p>
     * This parameter is required when {@code offline} is set to {@code true}. Otherwise this is not required, but
     * should be used for commands such as {@code module add} as they are executed on the local file system.
     * </p>
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
     * <p>
     * Note that this value is ignored if {@code offline} is set to {@code true}.
     * </p>
     */
    @Parameter(alias = "fail-on-error", defaultValue = "true", property = PropertyNames.FAIL_ON_ERROR)
    private boolean failOnError = true;

    /**
     * Indicates the commands should be run in a new process. If the {@code jboss-home} property is not set an attempt
     * will be made to download a version of WildFly to execute commands on. However it's generally considered best
     * practice to set the {@code jboss-home} property if setting this value to {@code true}.
     * <p>
     * Note that if {@code offline} is set to {@code true} this setting really has no effect.
     * </p>
     * <p>
     * <strong>WARNING: </strong> In 3.0.0 you'll be required to set the {@code jboss-home}. An error will occur if
     * this option is {@code true} and the {@code jboss-home} is not set.
     * </p>
     *
     * @since 2.0.0
     */
    @Parameter(defaultValue = "false", property = "wildfly.fork")
    private boolean fork;

    /**
     * Indicates whether or not CLI scrips or commands should be executed in an offline mode. This is useful for using
     * an embedded server or host controller.
     *
     * <p>This does not start an embedded server it instead skips checking if a server is running.</p>
     */
    @Parameter(name = "offline", defaultValue = "false", property = PropertyNames.OFFLINE)
    private boolean offline = false;

    /**
     * Indicates how {@code stdout} and {@code stderr} should be handled for the spawned CLI process. Currently a new
     * process is only spawned if {@code offline} is set to {@code true} or {@code fork} is set to {@code true}. Note
     * that {@code stderr} will be redirected to {@code stdout} if the value is defined unless the value is
     * {@code none}.
     * <div>
     * By default {@code stdout} and {@code stderr} are inherited from the current process. You can change the setting
     * to one of the follow:
     * <ul>
     * <li>{@code none} indicates the {@code stdout} and {@code stderr} stream should not be consumed</li>
     * <li>{@code System.out} or {@code System.err} to redirect to the current processes <em>(use this option if you
     * see odd behavior from maven with the default value)</em></li>
     * <li>Any other value is assumed to be the path to a file and the {@code stdout} and {@code stderr} will be
     * written there</li>
     * </ul>
     * </div>
     */
    @Parameter(name = "stdout", defaultValue = "System.out", property = PropertyNames.STDOUT)
    private String stdout;

    /**
     * The JVM options to pass to the offline process if the {@code offline} configuration parameter is set to
     * {@code true}.
     */
    @Parameter(alias = "java-opts", property = PropertyNames.JAVA_OPTS)
    private String[] javaOpts;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession session;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> repositories;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
    private File buildDir;

    @Inject
    private ArtifactResolver artifactResolver;

    @Inject
    private CommandExecutor commandExecutor;

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
        final CommandConfiguration cmdConfig = CommandConfiguration.of(this::createClient, this::getClientConfiguration)
                .addCommands(commands)
                .addJvmOptions(javaOpts)
                .addPropertiesFiles(propertiesFiles)
                .addScripts(scripts)
                .addSystemProperties(systemProperties)
                .setBatch(batch)
                .setFailOnError(failOnError)
                .setFork(fork)
                .setJBossHome(jbossHome)
                .setOffline(offline)
                .setStdout(stdout)
                .setTimeout(timeout);
        if (fork) {
            cmdConfig.setJBossHome(extractIfRequired());
        }
        commandExecutor.execute(cmdConfig);
    }

    /**
     * Allows the {@link #javaOpts} to be set as a string. The string is assumed to be space delimited.
     *
     * @param value a spaced delimited value of JVM options
     */
    @SuppressWarnings("unused")
    public void setJavaOpts(final String value) {
        if (value != null) {
            javaOpts = value.split("\\s+");
        }
    }

    private Path extractIfRequired() throws MojoFailureException {
        if (jbossHome != null) {
            //we do not need to download WildFly
            return Paths.get(jbossHome);
        }
        getLog().warn("The jboss-home parameter was not set. In 3.0.0 this parameter will be required. " +
                "Downloading a server via Maven artifact will not longer be supported.");
        final Path result = artifactResolver.resolve(session, repositories, ArtifactNameBuilder.forRuntime(null).build());
        try {
            return Archives.uncompress(result, buildDir.toPath());
        } catch (IOException e) {
            throw new MojoFailureException("Artifact was not successfully extracted: " + result, e);
        }
    }
}
