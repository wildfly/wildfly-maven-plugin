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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.plugin.common.Files;
import org.wildfly.plugin.common.PropertyNames;

/**
 * Starts a standalone instance of WildFly Application Server.
 * <p/>
 * The purpose of this goal is to start a WildFly Application Server for testing during the maven lifecycle. This can
 * start a remote server, but the server will be shutdown when the maven process ends.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "start", requiresDependencyResolution = ResolutionScope.RUNTIME)
public class StartMojo extends AbstractServerConnection {

    public static final String WILDFLY_DIR = "wildfly-run";

    /**
     * The project
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /**
     * The target directory the application to be deployed is located.
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
    private File targetDir;

    @Component
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
        final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(jbossHome.toPath())
                .setJavaHome(javaHome)
                .addModuleDirs(modulesPath.getModulePaths());

        // JVM arguments should be space delimited
        if (jvmArgs != null) {
            commandBuilder.addJavaOptions(jvmArgs.split("\\s+"));
        }

        if (serverConfig != null) {
            commandBuilder.setServerConfiguration(serverConfig);
        }

        if (propertiesFile != null) {
            commandBuilder.setPropertiesFile(propertiesFile);
        }
        // Print some server information
        log.info(String.format("JAVA_HOME=%s", commandBuilder.getJavaHome()));
        log.info(String.format("JBOSS_HOME=%s%n", commandBuilder.getWildFlyHome()));
        try {
            // Create the server
            final Server server = Server.create(commandBuilder, getClient());
            // Start the server
            log.info("Server is starting up.");
            server.start(startupTimeout);
            server.checkServerState();
        } catch (Exception e) {
            throw new MojoExecutionException("The server failed to start", e);
        }

    }

    private File extractIfRequired(final File buildDir) throws MojoFailureException, MojoExecutionException {
        if (jbossHome != null) {
            //we do not need to download WildFly
            return new File(jbossHome);
        }
        final File result = artifactResolver.resolve(project, createArtifact());
        final File target = new File(buildDir, WILDFLY_DIR);
        // Delete the target if it exists
        if (target.exists()) {
            Files.deleteRecursively(target);
        }
        target.mkdirs();
        try {
            Files.unzip(result, target);
        } catch (IOException e) {
            throw new MojoFailureException("Artifact was not successfully extracted: " + result, e);
        }
        final File[] files = target.listFiles();
        if (files == null || files.length != 1) {
            throw new MojoFailureException("Artifact was not successfully extracted: " + result);
        }
        // Assume the first
        return files[0];
    }

    @Override
    public String goal() {
        return "start";
    }

    private String createArtifact() throws MojoFailureException {
        String groupId = this.groupId;
        String artifactId = this.artifactId;
        String classifier = this.classifier;
        String packaging = this.packaging;
        String version = this.version == null ? RuntimeVersions.getLatestFinal(groupId, artifactId) : this.version;
        // groupId:artifactId:version[:packaging][:classifier].
        if (artifact != null) {
            final String[] artifactParts = artifact.split(":");
            if (artifactParts.length == 0) {
                throw new MojoFailureException(String.format("Invalid artifact pattern: %s", artifact));
            }
            String value;
            switch (artifactParts.length) {
                case 5:
                    value = artifactParts[4].trim();
                    if (!value.isEmpty()) {
                        classifier = value;
                    }
                case 4:
                    value = artifactParts[3].trim();
                    if (!value.isEmpty()) {
                        packaging = value;
                    }
                case 3:
                    value = artifactParts[2].trim();
                    if (!value.isEmpty()) {
                        version = value;
                    }
                case 2:
                    value = artifactParts[1].trim();
                    if (!value.isEmpty()) {
                        artifactId = value;
                    }
                case 1:
                    value = artifactParts[0].trim();
                    if (!value.isEmpty()) {
                        groupId = value;
                    }
            }
        }
        // Validate the groupId, artifactId and version are not null
        if (groupId == null || artifactId == null || version == null) {
            throw new IllegalStateException("The groupId, artifactId and version parameters are required");
        }
        final StringBuilder result = new StringBuilder();
        result.append(groupId)
                .append(':')
                .append(artifactId)
                .append(':')
                .append(version)
                .append(':');
        if (packaging != null) {
            result.append(packaging);
        }
        result.append(':');
        if (classifier != null) {
            result.append(classifier);
        }
        return result.toString();
    }
}
