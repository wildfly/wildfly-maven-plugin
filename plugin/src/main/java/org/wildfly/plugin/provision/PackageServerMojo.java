/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.plugin.provision;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.util.IoUtils;
import org.wildfly.plugin.cli.CliSession;
import org.wildfly.plugin.cli.CommandConfiguration;
import org.wildfly.plugin.cli.CommandExecutor;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.StandardOutput;
import static org.wildfly.plugin.core.Constants.CLI_ECHO_COMMAND_ARG;
import static org.wildfly.plugin.core.Constants.STANDALONE;
import static org.wildfly.plugin.core.Constants.STANDALONE_XML;
import org.wildfly.plugin.deployment.PackageType;

/**
 * Provision a server, copy extra content and deploy primary artifact if it
 * exists
 *
 * @author jfdenise
 */
@Mojo(name = "package", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class PackageServerMojo extends AbstractProvisionServerMojo {

    /**
     * A list of directories to copy content to the provisioned server. If a
     * directory is not absolute, it has to be relative to the project base
     * directory.
     */
    @Parameter(alias = "extra-server-content-dirs")
    List<String> extraServerContentDirs = Collections.emptyList();

    /**
     * List of execution of CLI scripts and commands. An embedded server is
     * started for each CLI execution. If a script file is not absolute, it has
     * to be relative to the project base directory. CLI executions are
     * configured in the following way:
     * <pre>
     *   &lt;packaging-scripts&gt;
     *     &lt;packaging-script&gt;
     *       &lt;scripts&gt;
     *         &lt;script&gt;../scripts/script1.cli&lt;/script&gt;
     *       &lt;/scripts&gt;
     *       &lt;commands&gt;
     *         &lt;command&gt;/system-property:foo:add(value=bar)&lt;/command&gt;
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
    private List<CliSession> packagingScripts = new ArrayList<>();

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
    private String filename;

    /**
     * The name of the server configuration to use when deploying the
     * deployment. Defaults to 'standalone.xml'.
     */
    @Parameter(property = PropertyNames.SERVER_CONFIG, alias = "server-config", defaultValue = STANDALONE_XML)
    private String serverConfig;

    /**
     * Specifies the name used for the deployment.
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_NAME)
    private String name;

    /**
     * The runtime name for the deployment.
     * <p>
     * In some cases users may wish to have two deployments with the same
     * {@code runtime-name} (e.g. two versions of {@code example.war}) both
     * available in the management configuration, in which case the deployments
     * would need to have distinct {@code name} values but would have the same
     * {@code runtime-name}.
     * </p>
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_RUNTIME_NAME, alias = "runtime-name")
    private String runtimeName;

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
    private String stdout;

    @Inject
    private CommandExecutor commandExecutor;

    @Override
    protected ProvisioningConfig getDefaultConfig() throws ProvisioningDescriptionException {
        return null;
    }

    @Override
    protected String getGoal() {
        return "package";
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

        final Path deploymentContent = getDeploymentContent();
        if (Files.exists(deploymentContent)) {
            getLog().info("Deploying " + deploymentContent);
            List<String> deploymentCommands = getDeploymentCommands(deploymentContent);
            final CommandConfiguration cmdConfigDeployment = CommandConfiguration.of(()-> null, ()-> null)
                    .addCommands(deploymentCommands)
                    .setJBossHome(jbossHome)
                    .addCLIArguments(CLI_ECHO_COMMAND_ARG)
                    .setFork(true)
                    .setAppend(true)
                    .setStdout(stdout)
                    .setOffline(true);
            commandExecutor.execute(cmdConfigDeployment, artifactResolver);
        }
        // CLI execution
         try {
             if (!packagingScripts.isEmpty()) {
                 getLog().info("Excuting CLI commands and scripts");
                 for (CliSession session : packagingScripts) {
                     List<File> wrappedScripts = wrapOfflineScripts(session.getScripts());
                     try {
                         final CommandConfiguration cmdConfig = CommandConfiguration.of(()-> null, ()-> null)
                                 .addCommands(wrapOfflineCommands(session.getCommands()))
                                 .addScripts(wrappedScripts)
                                 .addCLIArguments(CLI_ECHO_COMMAND_ARG)
                                 .setJBossHome(jbossHome)
                                 .setFork(true)
                                 .setAppend(true)
                                 .setStdout(stdout)
                                 .addPropertiesFiles(resolveFiles(session.getPropertiesFiles()))
                                 .addJvmOptions(session.getJavaOpts())
                                 .setResolveExpression(session.getResolveExpression())
                                 .setOffline(true);
                         commandExecutor.execute(cmdConfig, artifactResolver);
                     } finally {
                         for (File f : wrappedScripts) {
                             Files.delete(f.toPath());
                         }
                     }
                 }
             }

            cleanupServer(jbossHome);
        } catch (IOException ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
    }

    private List<File> resolveFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            return files;
        }
        List<File> resolvedFiles = new ArrayList<>();
        for(File f : files) {
            resolvedFiles.add(resolvePath(project, f.toPath()).toFile());
        }
        return resolvedFiles;
    }

    private List<String> getDeploymentCommands(Path deploymentContent) throws MojoExecutionException {
        List<String> deploymentCommands = new ArrayList<>();
        StringBuilder deploymentBuilder = new StringBuilder();
        deploymentBuilder.append("deploy  ").append(deploymentContent).append(" --name=").
                append(name == null ? deploymentContent.getFileName() : name).append(" --runtime-name=").
                append(runtimeName == null ? deploymentContent.getFileName() : runtimeName);
        deploymentCommands.add(deploymentBuilder.toString());
        return wrapOfflineCommands(deploymentCommands);
    }

    private List<String> wrapOfflineCommands(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return commands;
        }
        List<String> offlineCommands = new ArrayList<>();
        offlineCommands.add("embed-server --server-config=" + serverConfig);
        offlineCommands.addAll(commands);
        offlineCommands.add("stop-embedded-server");
         return offlineCommands;
    }

    private List<File> wrapOfflineScripts(List<File> scripts) throws IOException, MojoExecutionException {
        List<File> wrappedScripts = new ArrayList<>();
        for(File script : scripts) {
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

    private static void cleanupServer(Path jbossHome) throws IOException {
        Path history = jbossHome.resolve("standalone").resolve("configuration").resolve("standalone_xml_history");
        IoUtils.recursiveDelete(history);
        Path tmp = jbossHome.resolve("standalone").resolve("tmp");
        IoUtils.recursiveDelete(tmp);
        Path log = jbossHome.resolve("standalone").resolve("log");
        IoUtils.recursiveDelete(log);
    }

}
