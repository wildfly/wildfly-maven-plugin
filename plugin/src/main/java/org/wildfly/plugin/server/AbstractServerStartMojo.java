/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.galleon.ProvisioningException;
import org.wildfly.core.launcher.DomainCommandBuilder;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.wildfly.plugin.common.Environment;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.Utils;
import org.wildfly.plugin.tools.GalleonUtils;
import org.wildfly.plugin.tools.server.ServerManager;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractServerStartMojo extends AbstractStartMojo {

    /**
     * The target directory the application to be deployed is located.
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
    protected File targetDir;

    /**
     * The WildFly Application Server's home directory. If not used, WildFly will be downloaded.
     */
    @Parameter(alias = "jboss-home", property = PropertyNames.JBOSS_HOME)
    protected String jbossHome;

    /**
     * The feature pack location. See the <a href="https://docs.wildfly.org/galleon/#_feature_pack_location">documentation</a>
     * for details on how to format a feature pack location.
     * <p>
     * Note that if you define the version in the feature pack location, e.g. {@code #26.1.1.Final}, the {@code version}
     * configuration parameter should be left blank.
     * </p>
     */
    @Parameter(alias = "feature-pack-location", property = PropertyNames.WILDFLY_FEATURE_PACK_LOCATION)
    private String featurePackLocation;

    /**
     * The version of the WildFly default server to install in case no jboss-home has been set
     * and no server has previously been provisioned.
     * <p>
     * The latest stable version is resolved if left blank.
     * </p>
     */
    @Parameter(property = PropertyNames.WILDFLY_VERSION)
    private String version;

    /**
     * The directory name inside the buildDir where to provision the default server.
     * By default the server is provisioned into the 'server' directory.
     *
     * @since 3.0
     */
    @Parameter(alias = "provisioning-dir", property = PropertyNames.WILDFLY_PROVISIONING_DIR, defaultValue = Utils.WILDFLY_DEFAULT_DIR)
    private String provisioningDir;

    /**
     * The modules path or paths to use. A single path can be used or multiple paths by enclosing them in a paths
     * element.
     */
    @Parameter(alias = "modules-path", property = PropertyNames.MODULES_PATH)
    private ModulesPath modulesPath;

    /**
     * Options passed to JBoss Modules. This is useful for things like Java Agents where you need to start the server
     * with an agent.
     */
    @Parameter(alias = "module-options", property = PropertyNames.MODULE_OPTS)
    protected String[] moduleOptions;

    /**
     * The users to add to the server.
     */
    @Parameter(alias = "add-user", property = "wildfly.add-user")
    private AddUser addUser;

    @Override
    protected Path getServerHome() throws MojoExecutionException, MojoFailureException {
        // Validate the environment
        final Path jbossHome = provisionIfRequired(targetDir.toPath().resolve(provisioningDir));
        if (!ServerManager.isValidHomeDirectory(jbossHome)) {
            throw new MojoExecutionException(String.format("JBOSS_HOME '%s' is not a valid directory.", jbossHome));
        }
        return jbossHome;
    }

    /**
     * Allows the {@link #moduleOptions} to be set as a string. The string is assumed to be space delimited.
     *
     * @param value a spaced delimited value of JBoss Modules options
     */
    @SuppressWarnings("unused")
    public void setModulesOptions(final String value) {
        if (value != null) {
            moduleOptions = value.split("\\s+");
        }
    }

    protected StandaloneCommandBuilder createStandaloneCommandBuilder(final Path jbossHome, final String serverConfig)
            throws MojoExecutionException {
        final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(jbossHome)
                .setJavaHome(javaHome)
                .addModuleDirs(modulesPath.getModulePaths());

        // Set the JVM options
        if (Utils.isNotNullOrEmpty(javaOpts)) {
            commandBuilder.setJavaOptions(javaOpts);
        }
        if (debug) {
            commandBuilder.addJavaOptions(String.format("-agentlib:jdwp=transport=dt_socket,server=y,suspend=%s,address=%s:%d",
                    debugSuspend ? "y" : "n", debugHost, debugPort));
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

        if (Utils.isNotNullOrEmpty(moduleOptions)) {
            commandBuilder.setModuleOptions(moduleOptions);
        }

        final Path javaHomePath = (this.javaHome == null ? Paths.get(System.getProperty("java.home"))
                : Paths.get(this.javaHome));
        if (Environment.isModularJvm(javaHomePath)) {
            commandBuilder.addJavaOptions(Environment.getModularJvmArguments());
        }

        // Print some server information
        final Log log = getLog();
        log.info("JAVA_HOME : " + commandBuilder.getJavaHome());
        log.info("JBOSS_HOME: " + commandBuilder.getWildFlyHome());
        log.info("JAVA_OPTS : " + Utils.toString(commandBuilder.getJavaOptions(), " "));
        try {
            addUsers(commandBuilder.getWildFlyHome(), commandBuilder.getJavaHome());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to add users", e);
        }
        return commandBuilder;
    }

    protected DomainCommandBuilder createDomainCommandBuilder(final Path jbossHome, final String domainConfig,
            final String hostConfig) throws MojoExecutionException {
        final Path javaHome = (this.javaHome == null ? Paths.get(System.getProperty("java.home")) : Paths.get(this.javaHome));
        final DomainCommandBuilder commandBuilder = DomainCommandBuilder.of(jbossHome, javaHome)
                .addModuleDirs(modulesPath.getModulePaths());

        // Set the JVM options
        if (Utils.isNotNullOrEmpty(javaOpts)) {
            commandBuilder.setProcessControllerJavaOptions(javaOpts)
                    .setHostControllerJavaOptions(javaOpts);
        }

        if (domainConfig != null) {
            commandBuilder.setDomainConfiguration(domainConfig);
        }

        if (hostConfig != null) {
            commandBuilder.setHostConfiguration(hostConfig);
        }

        if (propertiesFile != null) {
            commandBuilder.setPropertiesFile(propertiesFile);
        }

        if (serverArgs != null) {
            commandBuilder.addServerArguments(serverArgs);
        }

        // Workaround for WFCORE-4121
        if (Environment.isModularJvm(javaHome)) {
            commandBuilder.addHostControllerJavaOptions(Environment.getModularJvmArguments());
            commandBuilder.addProcessControllerJavaOptions(Environment.getModularJvmArguments());
        }

        // Print some server information
        final Log log = getLog();
        log.info("JAVA_HOME : " + commandBuilder.getJavaHome());
        log.info("JBOSS_HOME: " + commandBuilder.getWildFlyHome());
        log.info("JAVA_OPTS : " + Utils.toString(commandBuilder.getHostControllerJavaOptions(), " "));
        try {
            addUsers(commandBuilder.getWildFlyHome(), commandBuilder.getJavaHome());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to add users", e);
        }
        return commandBuilder;
    }

    protected Path provisionIfRequired(final Path installDir) throws MojoFailureException, MojoExecutionException {
        if (jbossHome != null) {
            // we do not need to download WildFly
            return Paths.get(jbossHome);
        }
        try {
            if (!Files.exists(installDir)) {
                getLog().info("Provisioning default server in " + installDir);
                GalleonUtils.provision(installDir, resolveFeaturePackLocation(), version, mavenRepoManager);
            }
            return installDir;
        } catch (ProvisioningException ex) {
            throw new MojoFailureException(ex.getLocalizedMessage(), ex);
        }
    }

    private void addUsers(final Path wildflyHome, final Path javaHome) throws IOException {
        if (addUser != null && addUser.hasUsers()) {
            getLog().info("Adding users: " + addUser);
            addUser.addUsers(wildflyHome, javaHome);
        }
    }

    private String resolveFeaturePackLocation() {
        return featurePackLocation == null ? getDefaultFeaturePackLocation() : featurePackLocation;
    }

    /**
     * Returns the default feature pack location if not defined in the configuration.
     *
     * @return the default feature pack location
     */
    protected String getDefaultFeaturePackLocation() {
        return "wildfly@maven(org.jboss.universe:community-universe)";
    }
}
