/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.wildfly.core.launcher.BootableJarCommandBuilder;
import org.wildfly.core.launcher.CommandBuilder;
import org.wildfly.plugin.common.Environment;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.StandardOutput;
import org.wildfly.plugin.common.Utils;
import org.wildfly.plugin.provision.PackageServerMojo;
import org.wildfly.plugin.tools.bootablejar.BootableJarSupport;

/**
 * Starts a WildFly Application Server packaged as Bootable JAR.
 * <p/>
 * The purpose of this goal is to start a WildFly Application Server packaged as a Bootable JAR for testing during the maven
 * lifecycle.
 */
@Mojo(name = "start-jar", requiresDependencyResolution = ResolutionScope.RUNTIME)
public class StartJarMojo extends AbstractStartMojo {

    /**
     * Indicates how {@code stdout} and {@code stderr} should be handled for the spawned server process. Note that
     * {@code stderr} will be redirected to {@code stdout} if the value is defined unless the value is {@code none}.
     * <div>
     * By default {@code stdout} and {@code stderr} are inherited from the current process. You can change the setting
     * to one of the follow:
     * <ul>
     * <li>{@code none} indicates the {@code stdout} and {@code stderr} stream should not be consumed. This should
     * generally only be used if the {@code shutdown} goal is used in the same maven process.</li>
     * <li>{@code System.out} or {@code System.err} to redirect to the current processes <em>(use this option if you
     * see odd behavior from maven with the default value)</em></li>
     * <li>Any other value is assumed to be the path to a file and the {@code stdout} and {@code stderr} will be
     * written there</li>
     * </ul>
     * </div>
     * <div>
     * Note that if this goal is not later followed by a {@code shutdown} goal in the same maven process you should use
     * a file to redirect the {@code stdout} and {@code stderr} to. Both output streams will be redirected to the same
     * file.
     * </div>
     */
    @Parameter(property = PropertyNames.STDOUT)
    private String stdout;

    /**
     * When {@code bootable-jar} is set to true, use this parameter to name the generated jar file.
     * The jar file is named by default {@code server-bootable.jar}.
     */
    @Parameter(alias = "bootable-jar-name", required = false, property = PropertyNames.BOOTABLE_JAR_NAME)
    private String bootableJarName;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Log log = getLog();
        if (skip) {
            log.debug("Skipping server start");
            return;
        }

        // Determine how stdout should be consumed
        try {
            startServer(ServerType.STANDALONE);
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("The server failed to start", e);
        }
    }

    @Override
    protected CommandBuilder createCommandBuilder(final Path server) throws MojoExecutionException {
        BootableJarCommandBuilder commandBuilder = BootableJarCommandBuilder.of(server).setJavaHome(javaHome);

        // Set the JVM options
        if (Utils.isNotNullOrEmpty(javaOpts)) {
            commandBuilder.setJavaOptions(javaOpts);
        }
        if (debug) {
            commandBuilder.addJavaOptions(String.format("-agentlib:jdwp=transport=dt_socket,server=y,suspend=%s,address=%s:%d",
                    debugSuspend ? "y" : "n", debugHost, debugPort));
        }

        if (propertiesFile != null) {
            commandBuilder.addServerArgument("-P" + propertiesFile);
        }

        if (serverArgs != null) {
            commandBuilder.addServerArguments(serverArgs);
        }

        final Path javaHomePath = (this.javaHome == null ? Paths.get(System.getProperty("java.home"))
                : Paths.get(this.javaHome));
        if (Environment.isModularJvm(javaHomePath)) {
            commandBuilder.addJavaOptions(Environment.getModularJvmArguments());
        }

        // Print some server information
        final Log log = getLog();
        log.info("JAVA_HOME : " + commandBuilder.getJavaHome());
        log.info("JAVA_OPTS : " + Utils.toString(commandBuilder.getJavaOptions(), " "));
        return commandBuilder;
    }

    @Override
    protected StandardOutput standardOutput() throws IOException {
        return StandardOutput.parse(stdout, true);
    }

    @Override
    public String goal() {
        return "start-jar";
    }

    @Override
    protected Path getServerHome() throws MojoExecutionException, MojoFailureException {
        String jarName = bootableJarName == null ? PackageServerMojo.BOOTABLE_JAR_NAME_RADICAL +
                BootableJarSupport.BOOTABLE_SUFFIX + "." + PackageServerMojo.JAR
                : bootableJarName;
        Path targetPath = Paths.get(project.getBuild().getDirectory());
        Path jarFile = targetPath.toAbsolutePath()
                .resolve(jarName);
        if (!Files.exists(jarFile)) {
            throw new MojoExecutionException(String.format("Bootable JAR file '%s' doesn't exist.", jarFile));
        }
        return jarFile;
    }
}
