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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.jboss.as.plugin.common.ConnectionInfo;
import org.jboss.as.plugin.common.Streams;
import org.jboss.as.plugin.deployment.Deployments;
import org.jboss.as.plugin.deployment.domain.Domain;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;

/**
 * @author Stuart Douglas
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @goal run
 * @requiresDependencyResolution runtime
 * @execute phase="package"
 */
public class Run extends AbstractMojo {

    public static final String JBOSS_DIR = "jboss-as-run";

    /**
     * @parameter default-value="${project}"
     * @readonly
     * @required
     */
    private MavenProject project;

    /**
     * The entry point to Aether, i.e. the component doing all the work.
     *
     * @component
     */
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     *
     * @parameter default-value="${repositorySystemSession}"
     * @readonly
     */
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories
     *
     * @parameter default-value="${project.remotePluginRepositories}"
     * @readonly
     */
    private List<RemoteRepository> remoteRepos;

    /**
     * The JBoss Application Server's home directory. If not used, JBoss Application Server will be downloaded.
     *
     * @parameter alias="jboss-home" expression="${jboss-as.home}"
     */
    private String jbossHome;

    /**
     * The group id for the JBoss Application Server.
     *
     * @parameter alias="jboss-as-groupId" default-value="org.jboss.as" expression="${jboss-as.groupId}"
     */
    private String jbossAsGroupId;

    /**
     * The artifact id for the JBoss Application Server.
     *
     * @parameter alias="jboss-as-artifactId" default-value="jboss-as-dist" expression="${jboss-as.artifactId}"
     */
    private String jbossAsArtifactId;

    /**
     * The type of the archive.
     *
     * @parameter alias="jboss-as-archive-type" default-value="zip" expression="${jboss-as.archiveType}"
     */
    private String jbossAsArchiveType;

    /**
     * The version of the JBoss Application Server to run.
     *
     * @parameter alias="jboss-as.version" default-value="7.1.1.Final" expression="${jboss-as.version}"
     */
    private String jbossAsVersion;

    /**
     * The modules path to use.
     *
     * @parameter alias="modules-path" expression="${jboss-as.modulesPath}"
     */
    private String modulesPath;

    /**
     * The bundles path to use.
     *
     * @parameter alias="bundles-path" expression="${jboss-as.bundlesPath}"
     */
    private String bundlesPath;

    /**
     * A space delimited list of JVM arguments.
     *
     * @parameter alias="jvm-args" expression="${jboss-as.jvmArgs}"
     */
    private String jvmArgs;

    /**
     * The {@code JAVA_HOME} to use for launching the server.
     *
     * @parameter alias="java-home" expression="${java.home}"
     */
    private String javaHome;

    /**
     * The path to the server configuration to use.
     *
     * @parameter alias="server-config" expression="${jboss-as.serverConfig}"
     */
    private String serverConfig;

    /**
     * The timeout value to use when starting the server.
     *
     * @parameter alias="startup-timeout" default-value=60 expression="${jboss-as.startupTimeout}"
     */
    private long startupTimeout;

    /**
     * The target directory the application to be deployed is located.
     *
     * @parameter
     */
    private File targetDir;

    /**
     * The file name of the application to be deployed.
     *
     * @parameter
     */
    private String filename;


    private final Domain domain = null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final MavenProject project = Deployments.resolveProject(this.project);
        final String deploymentName = Deployments.resolveFileName(project, this.filename);
        final File targetDir = Deployments.resolveTargetDir(project, this.targetDir);
        final File file = new File(targetDir, deploymentName);
        // The deployment must exist before we do anything
        if (!file.exists()) {
            throw new MojoExecutionException(String.format("The deployment '%s' could not be found.", file.getAbsolutePath()));
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
        final ServerInfo serverInfo = ServerInfo.of(ConnectionInfo.DEFAULT, javaHome, jbossHome, modulesPath, bundlesPath, jvmArgs, serverConfig, startupTimeout);
        if (!serverInfo.getModulesPath().isDirectory()) {
            throw new MojoExecutionException(String.format("Modules path '%s' is not a valid directory.", modulesPath));
        }
        if (!serverInfo.getBundlesPath().isDirectory()) {
            throw new MojoExecutionException(String.format("Bundles path '%s' is not a valid directory.", bundlesPath));
        }
        try {
            // Create the server
            final Server server;
            // Currently this will never be true, see comments in DomainServer for details
            if (domain != null) {
                server = new DomainServer(serverInfo, domain);
            } else {
                server = new StandaloneServer(serverInfo);
            }
            final Thread shutdownThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    server.stop();
                    // Bad hack to get maven to complete it's message output
                    try {
                        TimeUnit.MILLISECONDS.sleep(500L);
                    } catch (InterruptedException ignore) {
                        // no-op
                    }
                }
            });
            SecurityActions.addShutdownHook(shutdownThread);
            getLog().info("Server is starting up. Press CTRL + C to stop the server.");
            server.start();
            getLog().info(String.format("Deploying application '%s'%n", file.getName()));
            server.deploy(file, deploymentName);
            while (server.isStarted()) {
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
        final String jbossAsArtifact = String.format("%s:%s:%s:%s", jbossAsGroupId, jbossAsArtifactId, jbossAsArchiveType, jbossAsVersion);
        request.setArtifact(new DefaultArtifact(jbossAsArtifact));
        request.setRepositories(remoteRepos);
        getLog().info("Resolving artifact " + jbossAsArtifact + " from " + remoteRepos);
        final ArtifactResult result;
        try {
            result = repoSystem.resolveArtifact(repoSession, request);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        final byte buff[] = new byte[1024];
        final File target = new File(buildDir, JBOSS_DIR);
        if (target.exists()) {
            target.delete();
        }
        ZipFile file = null;
        try {
            file = new ZipFile(result.getArtifact().getFile());
            final Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                final File extractTarget = new File(target.getAbsolutePath(), entry.getName());
                if (entry.isDirectory()) {
                    extractTarget.mkdirs();
                } else {
                    final File parent = new File(extractTarget.getParent());
                    parent.mkdirs();
                    final BufferedInputStream in = new BufferedInputStream(file.getInputStream(entry));
                    try {
                        final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(extractTarget));
                        try {
                            int read = 0;
                            while ((read = in.read(buff)) != -1) {
                                out.write(buff, 0, read);
                            }
                        } finally {
                            Streams.safeClose(out);
                        }
                    } finally {
                        Streams.safeClose(in);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Error extracting '%s'", (file == null ? "null file" : file.getName())), e);
        } finally {
            Streams.safeClose(file);
        }
        return new File(target.getAbsoluteFile(), String.format("jboss-as-%s", jbossAsVersion));
    }
}
