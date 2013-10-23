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

package org.jboss.as.plugin.server;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.as.plugin.common.AbstractServerConnection;
import org.jboss.as.plugin.common.Files;
import org.jboss.as.plugin.common.PropertyNames;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;

/**
 * Starts a standalone instance of JBoss Application Server 7.
 * <p/>
 * The purpose of this goal is to start a JBoss Application Server for testing during the maven lifecycle. This can
 * start a remote server, but the server will be shutdown when the maven process ends.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "start", requiresDependencyResolution = ResolutionScope.RUNTIME)
public class Start extends AbstractServerConnection {

    public static final String JBOSS_DIR = "jboss-as-run";


    /**
     * The target directory the application to be deployed is located.
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
    private File targetDir;

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
     * A string of the form groupId:artifactId:version[:packaging][:classifier]. Any missing portion of the artifact
     * will be replaced with the
     */
    @Parameter(property = PropertyNames.JBOSS_ARTIFACT)
    private String artifact;

    /**
     * The groupId of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter(defaultValue = Defaults.JBOSS_AS_GROUP_ID, property = PropertyNames.JBOSS_GROUP_ID)
    private String groupId;

    /**
     * The artifactId of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter(defaultValue = Defaults.JBOSS_AS_ARTIFACT_ID, property = PropertyNames.JBOSS_ARTIFACT_ID)
    private String artifactId;

    /**
     * The classifier of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = PropertyNames.JBOSS_CLASSIFIER)
    private String classifier;

    /**
     * The packaging of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = PropertyNames.JBOSS_PACKAGING, defaultValue = Defaults.JBOSS_AS_PACKAGING)
    private String packaging;

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
     * The path to the system properties file to load.
     */
    @Parameter(alias = "properties-file", property = PropertyNames.PROPERTIES_FILE)
    private String propertiesFile;

    /**
     * The timeout value to use when starting the server.
     */
    @Parameter(alias = "startup-timeout", defaultValue = Defaults.TIMEOUT, property = PropertyNames.STARTUP_TIMEOUT)
    private long startupTimeout;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Log log = getLog();
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
        final ServerInfo serverInfo = ServerInfo.of(this, javaHome, jbossHome, modulesPath, bundlesPath, jvmArgs, serverConfig, propertiesFile, startupTimeout);
        if (!serverInfo.getModulesDir().isDirectory()) {
            throw new MojoExecutionException(String.format("Modules path '%s' is not a valid directory.", modulesPath));
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
            log.info("Server is starting up.");
            server.start();
            server.checkServerState();
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
        final DefaultArtifact defaultArtifact = createArtifact();
        request.setArtifact(defaultArtifact);
        request.setRepositories(remoteRepos);
        getLog().info(String.format("Resolving artifact %s from %s", defaultArtifact, remoteRepos));
        final ArtifactResult result;
        try {
            result = repoSystem.resolveArtifact(repoSession, request);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        final File target = new File(buildDir, JBOSS_DIR);
        // Delete the target if it exists
        if (target.exists()) {
            Files.deleteRecursively(target);
        }
        target.mkdirs();
        try {
            Files.unzip(result.getArtifact().getFile(), target);
        } catch (IOException e) {
            throw new MojoFailureException("Artifact was not successfully extracted: " + result.getArtifact().getFile(), e);
        }
        final File[] files = target.listFiles();
        if (files == null || files.length != 1) {
            throw new MojoFailureException("Artifact was not successfully extracted: " + result.getArtifact().getFile());
        }
        // Assume the first
        return files[0];
    }

    @Override
    public String goal() {
        return "start";
    }

    private DefaultArtifact createArtifact() throws MojoFailureException {
        String groupId = this.groupId;
        String artifactId1 = this.artifactId;
        String classifier = this.classifier;
        String packaging = this.packaging;
        String version = this.version;
        // groupId:artifactId:version[:packaging][:classifier].
        if (artifact != null) {
            final String[] artifactParts = artifact.split(":");
            if (artifactParts.length == 0) {
                throw new MojoFailureException(String.format("Invalid artifact pattern: %s", artifact));
            }
            String value;
            switch (artifactParts.length) {
                case 5:
                    value = artifactParts[4];
                    if (!value.isEmpty()) {
                        classifier = value;
                    }
                case 4:
                    value = artifactParts[3];
                    if (!value.isEmpty()) {
                        packaging = value;
                    }
                case 3:
                    value = artifactParts[2];
                    if (!value.isEmpty()) {
                        version = value;
                    }
                case 2:
                    value = artifactParts[1];
                    if (!value.isEmpty()) {
                        artifactId1 = value;
                    }
                case 1:
                    value = artifactParts[0];
                    if (!value.isEmpty()) {
                        groupId = value;
                    }
            }
        }
        return new DefaultArtifact(groupId, artifactId1, classifier, packaging, version);
    }
}
