/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import static org.wildfly.plugin.core.Constants.CLI_ECHO_COMMAND_ARG;
import static org.wildfly.plugin.core.Constants.STANDALONE;
import static org.wildfly.plugin.core.Constants.STANDALONE_XML;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.util.IoUtils;
import org.wildfly.plugin.cli.BaseCommandConfiguration;
import org.wildfly.plugin.cli.CliSession;
import org.wildfly.plugin.cli.OfflineCommandExecutor;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.StandardOutput;
import org.wildfly.plugin.deployment.MojoDeploymentException;
import org.wildfly.plugin.deployment.PackageType;

/**
 * Provision a server, copy extra content and deploy primary artifact if it
 * exists
 *
 * @author jfdenise
 * @since 3.0
 */
public class AbstractPackageServerMojo extends AbstractProvisionServerMojo {

    /**
     * A list of directories to copy content to the provisioned server. If a
     * directory is not absolute, it has to be relative to the project base
     * directory.
     */
    @Parameter(alias = "extra-server-content-dirs")
    protected List<String> extraServerContentDirs = Collections.emptyList();

    /**
     * List of execution of CLI scripts and commands. An embedded server is
     * started for each CLI execution. If a script file is not absolute, it has
     * to be relative to the project base directory. CLI executions are
     * configured in the following way:
     *
     * <pre>
     *   &lt;packaging-scripts&gt;
     *     &lt;packaging-script&gt;
     *       &lt;scripts&gt;
     *         &lt;script&gt;../scripts/script1.cli&lt;/script&gt;
     *       &lt;/scripts&gt;
     *       &lt;commands&gt;
     *         &lt;command&gt;/system-property=foo:add(value=bar)&lt;/command&gt;
     *       &lt;/commands&gt;
     *       &lt;properties-files&gt;
     *         &lt;property-file&gt;my-properties.properties&lt;/property-file&gt;
     *       &lt;/properties-files&gt;
     *       &lt;java-opts&gt;
     *         &lt;java-opt&gt;-Xmx256m&lt;/java-opt&gt;
     *       &lt;/java-opts&gt;
     *       &lt;!-- Expressions resolved during server execution --&gt;
     *       &lt;resolve-expressions&gt;false&lt;/resolve-expressions&gt;
     *     &lt;/packaging-script&gt;
     *   &lt;/packaging-scripts&gt;
     * </pre>
     */
    @Parameter(alias = "packaging-scripts")
    protected List<CliSession> packagingScripts = new ArrayList<>();

    /**
     * The file name of the application to be deployed.
     * <p>
     * The {@code filename} property does have a default of
     * <code>${project.build.finalName}.${project.packaging}</code>. The default
     * value is not injected as it normally would be due to packaging types like
     * {@code ejb} that result in a file with a {@code .jar} extension rather
     * than an {@code .ejb} extension.
     * </p>
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_FILENAME)
    protected String filename;

    /**
     * The name of the server configuration to use when deploying the
     * deployment. Defaults to 'standalone.xml'. If {@code layers-configuration-file-name} has been set,
     * this property is ignored and the deployment is deployed inside the configuration referenced from
     * {@code layers-configuration-file-name}.
     * <p>
     * The value of this parameter is also ignored if any layers are defined.
     * </p>
     */
    @Parameter(property = PropertyNames.SERVER_CONFIG, alias = "server-config", defaultValue = STANDALONE_XML)
    protected String serverConfig;

    /**
     * Specifies the name used for the deployment.
     * <p>
     * When the deployment is copied to the server, it is renamed with this name.
     * </p>
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_NAME)
    protected String name;

    /**
     * The runtime name for the deployment.
     * <p>
     * When the deployment is copied to the server, it is renamed with the {@code runtime-name}.
     * If both {@code name} and {@code runtime-name} are specified, {@code runtime-name} is used.
     * </p>
     *
     * @deprecated use the {@code name} property instead to change the name of the deployment.
     */
    @Deprecated(since = "4.1.O")
    @Parameter(property = PropertyNames.DEPLOYMENT_RUNTIME_NAME, alias = "runtime-name")
    protected String runtimeName;

    /**
     * Indicates how {@code stdout} and {@code stderr} should be handled for the
     * spawned CLI processes. Note that {@code stderr} will be redirected to
     * {@code stdout} if the value is defined unless the value is {@code none}.
     * <div>
     * By default {@code stdout} and {@code stderr} are inherited from the
     * current process. You can change the setting to one of the follow:
     * <ul>
     * <li>{@code none} indicates the {@code stdout} and {@code stderr} stream
     * should not be consumed</li>
     * <li>{@code System.out} or {@code System.err} to redirect to the current
     * processes <em>(use this option if you see odd behavior from maven with
     * the default value)</em></li>
     * <li>Any other value is assumed to be the path to a file and the
     * {@code stdout} and {@code stderr} will be written there</li>
     * </ul>
     * </div>
     */
    @Parameter(name = "stdout", defaultValue = "System.out", property = PropertyNames.STDOUT)
    protected String stdout;

    /**
     * Set to {@code true} if you want the goal to be skipped, otherwise
     * {@code false}.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.SKIP_PACKAGE)
    protected boolean skip;

    /**
     * Skip deploying the deployment after the server is provisioned ({@code false} by default).
     */
    @Parameter(defaultValue = "false", property = PropertyNames.SKIP_PACKAGE_DEPLOYMENT)
    protected boolean skipDeployment;

    @Inject
    protected OfflineCommandExecutor commandExecutor;

    @Override
    protected GalleonProvisioningConfig getDefaultConfig() throws ProvisioningException {
        return null;
    }

    @Override
    protected String getGoal() {
        return "package";
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping " + getGoal() + " of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        super.execute();
    }

    @Override
    protected void serverProvisioned(Path jbossHome) throws MojoExecutionException, MojoFailureException {
        try {
            if (StandardOutput.isFile(stdout)) {
                // Delete it, we are appending to it.
                Files.deleteIfExists(Paths.get(stdout));
            }

            if (!extraServerContentDirs.isEmpty()) {
                getLog().info("Copying extra content to server");
                copyExtraContent(jbossHome);
            }
        } catch (IOException ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }

        if (!skipDeployment) {
            final Path deploymentContent = getDeploymentContent();
            if (Files.exists(deploymentContent)) {
                Path standaloneDeploymentDir = Path.of(provisioningDir, "standalone", "deployments");
                if (!standaloneDeploymentDir.isAbsolute()) {
                    standaloneDeploymentDir = Path.of(project.getBuild().getDirectory()).resolve(standaloneDeploymentDir);
                }
                try {
                    Path deploymentTarget = standaloneDeploymentDir.resolve(getDeploymentTargetName());
                    getLog().info("Copy deployment " + deploymentContent + " to " + deploymentTarget);
                    Files.copy(deploymentContent, deploymentTarget, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new MojoDeploymentException("Could not copy deployment in provisioned server", e);
                }
            }
        }

        // CLI execution
        try {
            if (!packagingScripts.isEmpty()) {
                getLog().info("Excuting CLI commands and scripts");
                for (CliSession session : packagingScripts) {
                    List<File> wrappedScripts = wrapOfflineScripts(session.getScripts());
                    try {
                        final BaseCommandConfiguration cmdConfig = new BaseCommandConfiguration.Builder()
                                .addCommands(wrapOfflineCommands(session.getCommands()))
                                .addScripts(wrappedScripts)
                                .addCLIArguments(CLI_ECHO_COMMAND_ARG)
                                .setJBossHome(jbossHome)
                                .setAppend(true)
                                .setStdout(stdout)
                                .addPropertiesFiles(resolveFiles(session.getPropertiesFiles()))
                                .addJvmOptions(session.getJavaOpts())
                                .setResolveExpression(session.getResolveExpression())
                                .build();
                        commandExecutor.execute(cmdConfig, artifactResolver);
                    } finally {
                        for (File f : wrappedScripts) {
                            Files.delete(f.toPath());
                        }
                    }
                }
            }

            cleanupServer(jbossHome);
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
    }

    /**
     * Return the file name of the deployment to put in the server deployment directory
     *
     * @throws MojoExecutionException
     */
    protected String getDeploymentTargetName() throws MojoExecutionException {
        String targetName;
        if (runtimeName != null) {
            targetName = runtimeName;
        } else {
            targetName = name != null ? name : getDeploymentContent().getFileName().toString();
        }
        return targetName;
    }

    private List<File> resolveFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            return files;
        }
        List<File> resolvedFiles = new ArrayList<>();
        for (File f : files) {
            resolvedFiles.add(resolvePath(project, f.toPath()).toFile());
        }
        return resolvedFiles;
    }

    private List<String> wrapOfflineCommands(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return commands;
        }
        List<String> offlineCommands = new ArrayList<>();
        String serverConfigName = serverConfig;
        if (!layersConfigurationFileName.equals(STANDALONE_XML)) {
            serverConfigName = layersConfigurationFileName;
        }
        offlineCommands.add("embed-server --server-config=" + serverConfigName);
        offlineCommands.addAll(commands);
        offlineCommands.add("stop-embedded-server");
        return offlineCommands;
    }

    private List<File> wrapOfflineScripts(List<File> scripts) throws IOException, MojoExecutionException {
        List<File> wrappedScripts = new ArrayList<>();
        for (File script : scripts) {
            if (script == null) {
                continue;
            }
            wrappedScripts.add(wrapScript(script).toFile());
        }
        return wrappedScripts;
    }

    private Path wrapScript(File script) throws IOException, MojoExecutionException {
        final Path tempScript = Files.createTempFile("offline-cli-script", ".cli");
        Path resolvedScript = resolvePath(project, script.toPath());
        if (!Files.exists(resolvedScript)) {
            throw new MojoExecutionException("CLI script " + resolvedScript + " doesn't exist");
        }
        List<String> cmds = Files.readAllLines(resolvedScript, StandardCharsets.UTF_8);
        List<String> wrappedCommands = wrapOfflineCommands(cmds);
        Files.write(tempScript, wrappedCommands, StandardCharsets.UTF_8);
        return tempScript;
    }

    public void copyExtraContent(Path target) throws MojoExecutionException, IOException {
        for (String path : extraServerContentDirs) {
            Path extraContent = Paths.get(path);
            extraContent = resolvePath(project, extraContent);
            if (Files.notExists(extraContent)) {
                throw new MojoExecutionException("Extra content dir " + extraContent + " doesn't exist");
            }
            if (!Files.isDirectory(extraContent)) {
                throw new MojoExecutionException("Extra content dir " + extraContent + " is not a directory");
            }
            // Check for the presence of a standalone.xml file
            warnExtraConfig(extraContent);
            IoUtils.copy(extraContent, target);
        }

    }

    private void warnExtraConfig(Path extraContentDir) {
        Path config = extraContentDir.resolve(STANDALONE).resolve("configurations").resolve(STANDALONE_XML);
        if (Files.exists(config)) {
            getLog().warn("The file " + config + " overrides the Galleon generated configuration, "
                    + "un-expected behavior can occur when starting the server");
        }
    }

    protected Path getDeploymentContent() throws MojoExecutionException {
        final PackageType packageType = PackageType.resolve(project);
        final String filename;
        if (this.filename == null) {
            filename = String.format("%s.%s", project.getBuild().getFinalName(), packageType.getFileExtension());
        } else {
            filename = this.filename;
        }
        Path deployment = Paths.get(project.getBuild().getDirectory()).resolve(filename);
        if (Files.notExists(deployment)) {
            if (this.filename != null) {
                throw new MojoExecutionException("No deployment found with name " + this.filename);
            }
            if (this.runtimeName != null) {
                throw new MojoExecutionException("No deployment found with name " + filename +
                        ". A runtime-name has been set that indicates that a deployment is expected. "
                        + "A custom file name can be set with the <filename> parameter.");
            }
            getLog().warn("The project doesn't define a deployment artifact to deploy to the server.");
        }
        return deployment;
    }

    private static void cleanupServer(Path jbossHome) throws IOException {
        Path history = jbossHome.resolve("standalone").resolve("configuration").resolve("standalone_xml_history");
        IoUtils.recursiveDelete(history);
        Path tmp = jbossHome.resolve("standalone").resolve("tmp");
        IoUtils.recursiveDelete(tmp);
        Path log = jbossHome.resolve("standalone").resolve("log");
        IoUtils.recursiveDelete(log);
    }

}
