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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.wildfly.plugin.common.DeploymentFailureException;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.deployment.DeployMojo;
import org.wildfly.plugin.deployment.Deployment;
import org.wildfly.plugin.deployment.standalone.StandaloneDeploymentBuilder;
import org.wildfly.plugin.server.ArtifactResolver.ArtifactNameSplitter;

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
public class RunMojo extends DeployMojo {

    public static final String WILDFLY_DIR = "wildfly-run";

    @Inject
    private ArtifactResolver artifactResolver;

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
    @Parameter(defaultValue = Defaults.WILDFLY_GROUP_ID, property = PropertyNames.WILDFLY_GROUP_ID)
    private String groupId;

    /**
     * The {@code artifactId} of the artifact to download. Ignored if {@link #artifact} {@code artifactId} portion is
     * used.
     */
    @Parameter(defaultValue = Defaults.WILDFLY_ARTIFACT_ID, property = PropertyNames.WILDFLY_ARTIFACT_ID)
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
    @Parameter(property = PropertyNames.WILDFLY_PACKAGING, defaultValue = Defaults.WILDFLY_PACKAGING)
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
     * A space delimited list of JVM arguments.
     *
     * @deprecated use {@link #javaOpts}
     */
    @Parameter(alias = "jvm-args", property = PropertyNames.JVM_ARGS)
    @Deprecated
    private String jvmArgs;

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
    @Parameter(alias = "startup-timeout", defaultValue = Defaults.TIMEOUT, property = PropertyNames.STARTUP_TIMEOUT)
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

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        final Log log = getLog();
        final File deploymentFile = file();
        // The deployment must exist before we do anything
        if (!deploymentFile.exists()) {
            throw new MojoExecutionException(String.format("The deployment '%s' could not be found.", deploymentFile.getAbsolutePath()));
        }
        // Validate the environment
        final Path jbossHome = extractIfRequired(deploymentFile.getParentFile().toPath());
        if (!Files.isDirectory(jbossHome)) {
            throw new MojoExecutionException(String.format("JBOSS_HOME '%s' is not a valid directory.", jbossHome));
        }
        final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(jbossHome)
                .setJavaHome(javaHome)
                .addModuleDirs(modulesPath.getModulePaths());

        // Set the JVM options
        if (javaOpts != null) {
            commandBuilder.setJavaOptions(javaOpts);
        } else if (jvmArgs != null) {
            commandBuilder.setJavaOptions(jvmArgs.split("\\s+"));
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

        // Print some server information
        log.info(String.format("JAVA_HOME=%s", commandBuilder.getJavaHome()));
        log.info(String.format("JBOSS_HOME=%s%n", commandBuilder.getWildFlyHome()));
        Server server = null;
        try (final ModelControllerClient client = createClient()) {
            if (addUser != null && addUser.hasUsers()) {
                log.info("Adding users: " + addUser);
                addUser.addUsers(commandBuilder.getWildFlyHome(), commandBuilder.getJavaHome());
            }
            // Create the server
            server = Server.create(commandBuilder, env, client);
            // Start the server
            log.info("Server is starting up. Press CTRL + C to stop the server.");
            server.start(startupTimeout);
            // Deploy the application
            server.checkServerState();
            if (server.isRunning()) {
                log.info(String.format("Deploying application '%s'%n", deploymentFile.getName()));
                final Deployment deployment = new StandaloneDeploymentBuilder(client)
                        .setContent(deploymentFile)
                        .setName(name)
                        .setRuntimeName(runtimeName)
                        .setType(getType())
                        .build();
                switch (executeDeployment(client, deployment, jbossHome)) {
                    case REQUIRES_RESTART: {
                        client.execute(ServerOperations.createOperation(ServerOperations.RELOAD));
                        break;
                    }
                    case SUCCESS:
                        break;
                }
            } else {
                throw new DeploymentFailureException("Cannot deploy to a server that is not running.");
            }
            while (server.isRunning()) {
                TimeUnit.SECONDS.sleep(1L);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("The server failed to start", e);
        } finally {
            if (server != null) server.stop();
        }

    }

    private Path extractIfRequired(final Path buildDir) throws MojoFailureException, MojoExecutionException {
        if (jbossHome != null) {
            //we do not need to download WildFly
            return Paths.get(jbossHome);
        }
        final String artifact = ArtifactNameSplitter.of(this.artifact)
                .setArtifactId(artifactId)
                .setClassifier(classifier)
                .setGroupId(groupId)
                .setPackaging(packaging)
                .setVersion(version)
                .asString();
        final Path result = artifactResolver.resolve(project, artifact).toPath();
        final Path target = buildDir.resolve(WILDFLY_DIR);
        // Delete the target if it exists
        if (Files.exists(target)) {
            try {
                Archives.deleteDirectory(target);
            } catch (IOException e) {
                throw new MojoFailureException("Could not delete target directory: " + target, e);
            }
        }
        try {
            Archives.unzip(result, target);
            final Iterator<Path> iterator = Files.newDirectoryStream(target).iterator();
            if (iterator.hasNext()) return iterator.next();
        } catch (IOException e) {
            throw new MojoFailureException("Artifact was not successfully extracted: " + result, e);
        }
        throw new MojoFailureException("Artifact was not successfully extracted: " + result);
    }

    @Override
    public String goal() {
        return "run";
    }
}
