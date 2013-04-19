/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.plugin.server;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.plugin.common.DeploymentFailureException;
import org.jboss.as.plugin.common.ServerOperations;
import org.jboss.as.plugin.common.PropertyNames;
import org.jboss.as.plugin.common.Streams;
import org.jboss.as.plugin.deployment.Deploy;
import org.jboss.as.plugin.deployment.Deployment;
import org.jboss.as.plugin.deployment.standalone.StandaloneDeployment;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;

/**
 * Starts a standalone instance of JBoss Application Server 7 and deploys the application to the server.
 * <p/>
 * This goal will block until cancelled or a shutdown is invoked from a management client.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.PACKAGE)
public class Run extends Deploy {

    public static final String JBOSS_DIR = "jboss-as-run";

    /**
     * The entry point to Aether, i.e. the component doing all the work.
     */
    @Component
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories
     */
    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepos;

    /**
     * The JBoss Application Server's home directory. If not used, JBoss Application Server will be downloaded.
     */
    @Parameter(alias = "jboss-home", property = PropertyNames.JBOSS_HOME)
    private String jbossHome;

    /**
     * The version of the JBoss Application Server to run.
     */
    @Parameter(alias = "jboss-as-version", defaultValue = Defaults.JBOSS_AS_TARGET_VERSION, property = PropertyNames.JBOSS_VERSION)
    private String version;

    /**
     * The modules path to use.
     */
    @Parameter(alias = "modules-path", property = PropertyNames.MODULES_PATH)
    private String modulesPath;

    /**
     * The bundles path to use.
     */
    @Parameter(alias = "bundles-path", property = PropertyNames.BUNDLES_PATH)
    private String bundlesPath;

    /**
     * A space delimited list of JVM arguments.
     */
    @Parameter(alias = "jvm-args", property = PropertyNames.JVM_ARGS, defaultValue = Defaults.DEFAULT_JVM_ARGS)
    private String jvmArgs;

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
     * The timeout value to use when starting the server.
     */
    @Parameter(alias = "startup-timeout", defaultValue = Defaults.TIMEOUT, property = PropertyNames.STARTUP_TIMEOUT)
    private long startupTimeout;

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        final Log log = getLog();
        final File deploymentFile = file();
        final String deploymentName = deploymentFile.getName();
        final File targetDir = deploymentFile.getParentFile();
        // The deployment must exist before we do anything
        if (!deploymentFile.exists()) {
            throw new MojoExecutionException(String.format("The deployment '%s' could not be found.", deploymentFile.getAbsolutePath()));
        }
        // Validate the environment
        final File jbossHome = extractIfRequired(targetDir);
        if (!jbossHome.isDirectory()) {
            throw new MojoExecutionException(String.format("JBOSS_HOME '%s' is not a valid directory.", jbossHome));
        }
        // JVM arguments should be space delimited
        final String[] jvmArgs = (this.jvmArgs == null ? null : this.jvmArgs.split("\\s+"));
        final String javaHome;
        if (this.javaHome == null) {
            javaHome = SecurityActions.getEnvironmentVariable("JAVA_HOME");
        } else {
            javaHome = this.javaHome;
        }
        final ServerInfo serverInfo = ServerInfo.of(this, javaHome, jbossHome, modulesPath, bundlesPath, jvmArgs, serverConfig, startupTimeout);
        if (!serverInfo.getModulesDir().isDirectory()) {
            throw new MojoExecutionException(String.format("Modules path '%s' is not a valid directory.", modulesPath));
        }
        if (!serverInfo.getBundlesDir().isDirectory()) {
            throw new MojoExecutionException(String.format("Bundles path '%s' is not a valid directory.", bundlesPath));
        }
        // Print some server information
        log.info(String.format("JAVA_HOME=%s", javaHome));
        log.info(String.format("JBOSS_HOME=%s%n", jbossHome));
        try {
            // Create the server
            final Server server = new StandaloneServer(serverInfo);
            // Add the shutdown hook
            SecurityActions.registerShutdown(server);
            // Start the server
            log.info("Server is starting up. Press CTRL + C to stop the server.");
            server.start();
            // Deploy the application
            server.checkServerState();
            if (server.isRunning()) {
                log.info(String.format("Deploying application '%s'%n", deploymentFile.getName()));
                final ModelControllerClient client = server.getClient();
                final Deployment deployment = StandaloneDeployment.create(client, deploymentFile, deploymentName, getType());
                switch (executeDeployment(client, deployment)) {
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
        }

    }

    private File extractIfRequired(final File buildDir) throws MojoFailureException, MojoExecutionException {
        if (jbossHome != null) {
            //we do not need to download JBoss
            return new File(jbossHome);
        }
        final ArtifactRequest request = new ArtifactRequest();
        final String jbossAsArtifact = String.format("org.jboss.as:jboss-as-dist:zip:%s", version);
        request.setArtifact(new DefaultArtifact(jbossAsArtifact));
        request.setRepositories(remoteRepos);
        getLog().info(String.format("Resolving artifact %s from %s", jbossAsArtifact, remoteRepos));
        final ArtifactResult result;
        try {
            result = repoSystem.resolveArtifact(repoSession, request);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        final File target = new File(buildDir, JBOSS_DIR);
        Streams.unzip(result.getArtifact().getFile(), target);

        return new File(target.getAbsoluteFile(), String.format("jboss-as-%s", version));
    }

    @Override
    public String goal() {
        return "run";
    }
}
