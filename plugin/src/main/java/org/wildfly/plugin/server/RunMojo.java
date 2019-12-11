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

package org.wildfly.plugin.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.core.launcher.CommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.wildfly.plugin.cli.CommandConfiguration;
import org.wildfly.plugin.cli.CommandExecutor;
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.plugin.common.Archives;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.Utils;
import org.wildfly.plugin.core.Deployment;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.core.ServerHelper;
import org.wildfly.plugin.deployment.PackageType;
import org.wildfly.plugin.repository.ArtifactName;
import org.wildfly.plugin.repository.ArtifactNameBuilder;
import org.wildfly.plugin.repository.ArtifactResolver;

/**
 * Starts a standalone instance of WildFly and deploys the application to the server.
 * <p/>
 * This goal will block until cancelled or a shutdown is invoked from a management client.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.PACKAGE)
public class RunMojo extends AbstractServerConnection {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession session;

    @Inject
    private ArtifactResolver artifactResolver;

    @Inject
    private CommandExecutor commandExecutor;

    /**
     * The WildFly Application Server's home directory. If not used, WildFly will be downloaded.
     */
    @Parameter(alias = "jboss-home", property = PropertyNames.JBOSS_HOME)
    private String jbossHome;

    /**
     * A string of the form groupId:artifactId:version[:packaging][:classifier]. Any missing portion of the artifact
     * will be replaced with the it's appropriate default property value
     */
    @Parameter(property = PropertyNames.WILDFLY_ARTIFACT)
    private String artifact;

    /**
     * The {@code groupId} of the artifact to download. Ignored if {@link #artifact} {@code groupId} portion is used.
     */
    @Parameter(defaultValue = ArtifactNameBuilder.WILDFLY_GROUP_ID, property = PropertyNames.WILDFLY_GROUP_ID)
    private String groupId;

    /**
     * The {@code artifactId} of the artifact to download. Ignored if {@link #artifact} {@code artifactId} portion is
     * used.
     */
    @Parameter(defaultValue = ArtifactNameBuilder.WILDFLY_ARTIFACT_ID, property = PropertyNames.WILDFLY_ARTIFACT_ID)
    private String artifactId;

    /**
     * The {@code classifier} of the artifact to download. Ignored if {@link #artifact} {@code classifier} portion is
     * used.
     */
    @Parameter(property = PropertyNames.WILDFLY_CLASSIFIER)
    private String classifier;

    /**
     * The {@code packaging} of the artifact to download. Ignored if {@link #artifact} {@code packing} portion is used.
     */
    @Parameter(property = PropertyNames.WILDFLY_PACKAGING, defaultValue = ArtifactNameBuilder.WILDFLY_PACKAGING)
    private String packaging;

    /**
     * The {@code version} of the artifact to download. Ignored if {@link #artifact} {@code version} portion is used.
     * The default version is resolved if left blank.
     */
    @Parameter(property = PropertyNames.WILDFLY_VERSION)
    private String version;

    /**
     * The modules path or paths to use. A single path can be used or multiple paths by enclosing them in a paths
     * element.
     */
    @Parameter(alias = "modules-path", property = PropertyNames.MODULES_PATH)
    private ModulesPath modulesPath;

    /**
     * The JVM options to use.
     */
    @Parameter(alias = "java-opts", property = PropertyNames.JAVA_OPTS)
    private String[] javaOpts;

    /**
     * The {@code JAVA_HOME} to use for launching the server.
     */
    @Parameter(alias = "java-home", property = PropertyNames.JAVA_HOME)
    private String javaHome;

    /**
     * The CLI commands to execute before the deployment is deployed.
     */
    @Parameter(property = PropertyNames.COMMANDS)
    private List<String> commands = new ArrayList<>();

    /**
     * The CLI script files to execute before the deployment is deployed.
     */
    @Parameter(property = PropertyNames.SCRIPTS)
    private List<File> scripts = new ArrayList<>();

    /**
     * The path to the server configuration to use.
     */
    @Parameter(alias = "server-config", property = PropertyNames.SERVER_CONFIG)
    private String serverConfig;

    /**
     * The path to the system properties file to load.
     */
    @Parameter(alias = "properties-file", property = PropertyNames.PROPERTIES_FILE)
    private String propertiesFile;

    /**
     * The timeout value to use when starting the server.
     */
    @Parameter(alias = "startup-timeout", defaultValue = "60", property = PropertyNames.STARTUP_TIMEOUT)
    private long startupTimeout;

    /**
     * The arguments to be passed to the server.
     */
    @Parameter(alias = "server-args", property = PropertyNames.SERVER_ARGS)
    private String[] serverArgs;

    /**
     * The users to add to the server.
     */
    @Parameter(alias = "add-user", property = "wildfly.add-user")
    private AddUser addUser;

    /**
     * Specifies the environment variables to be passed to the process being started.
     * <div>
     * <pre>
     * &lt;env&gt;
     *     &lt;HOME&gt;/home/wildfly/&lt;/HOME&gt;
     * &lt;/env&gt;
     * </pre>
     * </div>
     */
    @Parameter
    private Map<String, String> env;

    /**
     * Specifies the name used for the deployment.
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_NAME)
    private String name;

    /**
     * The runtime name for the deployment.
     * <p>
     * In some cases users may wish to have two deployments with the same {@code runtime-name} (e.g. two versions of
     * {@code example.war}) both available in the management configuration, in which case the deployments would need to
     * have distinct {@code name} values but would have the same {@code runtime-name}.
     * </p>
     */
    @Parameter(alias = "runtime-name", property = PropertyNames.DEPLOYMENT_RUNTIME_NAME)
    private String runtimeName;


    /**
     * The target directory the application to be deployed is located.
     */
    @Parameter(defaultValue = "${project.build.directory}/", property = PropertyNames.DEPLOYMENT_TARGET_DIR)
    private File targetDir;

    /**
     * The file name of the application to be deployed.
     * <p>
     * The {@code filename} property does have a default of <code>${project.build.finalName}.${project.packaging}</code>.
     * The default value is not injected as it normally would be due to packaging types like {@code ejb} that result in
     * a file with a {@code .jar} extension rather than an {@code .ejb} extension.
     * </p>
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_FILENAME)
    private String filename;

    /**
     * By default certain package types are ignored when processing, e.g. {@code maven-project} and {@code pom}. Set
     * this value to {@code false} if this check should be bypassed.
     */
    @Parameter(alias = "check-packaging", property = PropertyNames.CHECK_PACKAGING, defaultValue = "true")
    private boolean checkPackaging;

    /**
     * Set to {@code true} if you want the deployment to be skipped, otherwise {@code false}.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.SKIP)
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }
        final Log log = getLog();
        final Path deploymentContent = getDeploymentContent();
        // The deployment must exist before we do anything
        if (Files.notExists(deploymentContent)) {
            throw new MojoExecutionException(String.format("The deployment '%s' could not be found.", deploymentContent.toAbsolutePath()));
        }
        // Validate the environment
        final Path wildflyPath = extractIfRequired(deploymentContent.getParent());
        if (!ServerHelper.isValidHomeDirectory(wildflyPath)) {
            throw new MojoExecutionException(String.format("JBOSS_HOME '%s' is not a valid directory.", wildflyPath));
        }
        final StandaloneCommandBuilder commandBuilder = createCommandBuilder(wildflyPath);

        // Print some server information
        log.info("JAVA_HOME : " + commandBuilder.getJavaHome());
        log.info("JBOSS_HOME: " + commandBuilder.getWildFlyHome());
        log.info("JAVA_OPTS : " + Utils.toString(commandBuilder.getJavaOptions(), " "));
        try {
            if (addUser != null && addUser.hasUsers()) {
                log.info("Adding users: " + addUser);
                addUser.addUsers(commandBuilder.getWildFlyHome(), commandBuilder.getJavaHome());
            }
            // Start the server
            log.info("Server is starting up. Press CTRL + C to stop the server.");
            Process process = startContainer(commandBuilder);
            try (ModelControllerClient client = createClient()) {
                // Execute commands before the deployment is done
                final CommandConfiguration cmdConfig = CommandConfiguration.of(this::createClient, this::getClientConfiguration)
                        .addCommands(commands)
                        .addScripts(scripts)
                        .setJBossHome(commandBuilder.getWildFlyHome())
                        .setFork(true)
                        .setStdout("none")
                        .setTimeout(timeout);
                commandExecutor.execute(cmdConfig);
                // Create the deployment and deploy
                final Deployment deployment = Deployment.of(deploymentContent)
                        .setName(name)
                        .setRuntimeName(runtimeName);
                final DeploymentManager deploymentManager = DeploymentManager.Factory.create(client);
                deploymentManager.forceDeploy(deployment);
            } catch (MojoExecutionException | MojoFailureException e) {
                if (process != null) {
                    process.destroyForcibly().waitFor(10L, TimeUnit.SECONDS);
                }
                throw e;
            }
            try {
                // Wait for the process to die
                boolean keepRunning = true;
                while (keepRunning) {
                    final int exitCode = process.waitFor();
                    // 10 is the magic code used in the scripts to survive a :shutdown(restart=true) operation
                    if (exitCode == 10) {
                        // Ensure the current process is destroyed and restart a new one
                        process.destroy();
                        process = startContainer(commandBuilder);
                    } else {
                        keepRunning = false;
                    }
                }
            } catch (Exception e) {
                throw new MojoExecutionException("The server failed to start", e);
            } finally {
                if (process != null) process.destroy();
            }
        } catch (Exception e) {
            throw new MojoExecutionException("The server failed to start", e);
        }
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

    private StandaloneCommandBuilder createCommandBuilder(final Path wildflyPath) {
        final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(wildflyPath)
                .setJavaHome(javaHome)
                .addModuleDirs(modulesPath.getModulePaths());

        // Set the JVM options
        if (Utils.isNotNullOrEmpty(javaOpts)) {
            commandBuilder.setJavaOptions(javaOpts);
        }

        if (serverConfig != null) {
            commandBuilder.setServerConfiguration(serverConfig);
        }

        if (propertiesFile != null) {
            commandBuilder.setPropertiesFile(propertiesFile);
        }

        if (serverArgs != null) {
            commandBuilder.addServerArguments(serverArgs);
        }
        return commandBuilder;
    }

    private Path extractIfRequired(final Path buildDir) throws MojoFailureException {
        if (jbossHome != null) {
            //we do not need to download WildFly
            return Paths.get(jbossHome);
        }
        final ArtifactName artifact = ArtifactNameBuilder.forRuntime(this.artifact)
                .setArtifactId(artifactId)
                .setClassifier(classifier)
                .setGroupId(groupId)
                .setPackaging(packaging)
                .setVersion(version)
                .build();
        final Path result = artifactResolver.resolve(session, project.getRemotePluginRepositories(), artifact);
        try {
            return Archives.uncompress(result, buildDir);
        } catch (IOException e) {
            throw new MojoFailureException("Artifact was not successfully extracted: " + result, e);
        }
    }

    @Override
    public String goal() {
        return "run";
    }

    private Process startContainer(final CommandBuilder commandBuilder) throws IOException, InterruptedException, TimeoutException {
        final Launcher launcher = Launcher.of(commandBuilder)
                .inherit();
        if (env != null) {
            launcher.addEnvironmentVariables(env);
        }
        final Process process = launcher.launch();
        try (ModelControllerClient client = createClient()) {
            ServerHelper.waitForStandalone(process, client, startupTimeout);
        }
        return process;
    }

    private Path getDeploymentContent() {
        final PackageType packageType = PackageType.resolve(project);
        final String filename;
        if (this.filename == null) {
            filename = String.format("%s.%s", project.getBuild().getFinalName(), packageType.getFileExtension());
        } else {
            filename = this.filename;
        }
        return targetDir.toPath().resolve(filename);
    }
}
