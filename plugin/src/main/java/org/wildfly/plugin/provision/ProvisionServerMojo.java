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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.artifact.filter.PatternExcludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.PatternIncludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.ScopeArtifactFilter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.maven.plugin.util.MvnMessageWriter;
import org.jboss.galleon.util.IoUtils;
import org.wildfly.glow.ScanResults;
import org.wildfly.plugin.cli.BaseCommandConfiguration;
import org.wildfly.plugin.cli.OfflineCommandExecutor;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.StandardOutput;
import org.wildfly.plugin.common.Utils;
import org.wildfly.plugin.deployment.MojoDeploymentException;
import org.wildfly.plugin.deployment.PackageType;
import org.wildfly.plugin.tools.bootablejar.BootableJarSupport;

/**
 * Provisions a server optionally including a deployment or extra content. The provisioned server can be used for
 * testing or production purposes.
 * <p>
 * Provisioning a server may include configuring the server with CLI commands or adding extra content
 * </p>
 * <p>
 * Additional deployments can also be resolved from the dependencies. Use the {@code <included-dependencies/>},
 * {@code <excluded-dependencies/>}, {@code <included-dependency-scope/>} and/or {@code <excluded-dependency-scope/>}
 * to deploy additional artifacts to the packaged server.
 * </p>
 * <p>
 * Note the {@code <included-dependencies/>}, {@code <excluded-dependencies/>}, {@code <included-dependency-scope/>} and
 * {@code <excluded-ependency-scope/>} configuration properties are chained together and all checks must pass to be
 * included as additional deployments.
 * </p>
 *
 * @author jfdenise
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @since 3.0
 */
// Note we need the ResolutionScope to be "test" in order for the MavenProject.getArtifacts() to return all dependencies
@Mojo(name = "provision", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.PACKAGE)
public class ProvisionServerMojo extends AbstractProvisionServerMojo {

    /**
     * A list of directories to copy content to the provisioned server. If a directory is not absolute, it considered to
     * be relative to the project base directory.
     *
     * @since 5.2
     */
    @Parameter(alias = "extra-server-content-dirs")
    private List<String> extraServerContentDirs = Collections.emptyList();

    /**
     * The CLI commands to execute after provisioning the server. An embedded server is started before the commands
     * are executed.
     *
     * @since 5.2
     */
    @Parameter(property = PropertyNames.COMMANDS)
    private List<String> commands = new ArrayList<>();

    /**
     * The CLI script files to execute after provisioning the server. An embedded server is started before the scripts
     * are executed.
     *
     * @since 5.2
     */
    @Parameter(property = PropertyNames.SCRIPTS)
    private List<File> scripts = new ArrayList<>();

    // TODO (jrp) document this or remove if we end up using the CliSession type
    @Parameter(alias = "resolve-expressions", property = "wildfly.provision.cli.resolve.expressions")
    private boolean resolveExpressions;

    /**
     * The file name of the application to be deployed.
     * <p>
     * The {@code filename} property does have a default of
     * <code>${project.build.finalName}.${project.packaging}</code>. The default
     * value is not injected as it normally would be due to packaging types like
     * {@code ejb} that result in a file with a {@code .jar} extension rather
     * than an {@code .ejb} extension.
     * </p>
     *
     * @since 5.2
     */
    // TODO (jrp) find a way to combine this, name and bootableJarName. There is no need for 3 options here.
    @Parameter(property = PropertyNames.DEPLOYMENT_FILENAME)
    private String filename;

    /**
     * The name of the server configuration to use when deploying the
     * deployment. Defaults to 'standalone.xml'. If {@code layers-configuration-file-name} has been set,
     * this property is ignored and the deployment is deployed inside the configuration referenced from
     * {@code layers-configuration-file-name}.
     * <p>
     * The value of this parameter is also ignored if any layers are defined.
     * </p>
     *
     * @since 5.2
     */
    // TODO (jrp) do we need this? We likely do, it's effectively the same as layersConfigurationFileName
    @Parameter(property = PropertyNames.SERVER_CONFIG, alias = "server-config", defaultValue = STANDALONE_XML)
    String serverConfig;

    /**
     * Specifies the name used for the deployment.
     * <p>
     * When the deployment is copied to the server, it is renamed with this name.
     * </p>
     *
     * @since 5.2
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_NAME)
    private String name;

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
     *
     * @since 5.2
     */
    @Parameter(name = "stdout", defaultValue = "System.out", property = PropertyNames.STDOUT)
    private String stdout;

    /**
     * Indicates the deployment should be included in the provisioned server.
     *
     * @since 5.2
     */
    @Parameter(alias = "include-deployment", defaultValue = "false", property = PropertyNames.INCLUDE_DEPLOYMENT)
    protected boolean includeDeployment;

    /**
     * Galleon provisioning info discovery.
     * <p>
     * By enabling this feature, the set of Galleon feature-packs
     * and layers are automatically discovered by scanning the deployed application.
     * You can configure the following items:
     * </p>
     * <div>
     * <ul>
     * <li>addOns: List of addOn to enable. An addOn brings extra galleon layers to the provisioning (eg: {@code wildfly-cli} to
     * include CLI.</li>
     * <li>context: {@code bare-metal} or {@code cloud}. Default to {@code bare-metal}. Note that if the context is set to
     * {@code cloud}
     * and the plugin option {@code bootable-jar} is set, the plugin execution will abort.</li>
     * <li>failsOnError: true|false. If errors are detected (missing datasource, missing messaging broker, ambiguous JNDI call,
     * provisioning is aborted. Default to {@code false}</li>
     * <li>layersForJndi: List of Galleon layers required by some JNDI calls located in your application.</li>
     * <li>preview: {@code true} | {@code false}. Use preview feature-packs. Default to {@code false}.</li>
     * <li>profile: {@code ha}. Default being non ha server configuration.</li>
     * <li>suggest: {@code true} | {@code false}. Display addOns that you can use to enhance discovered provisioning
     * configuration. Default to {@code false}.</li>
     * <li>excludedArchives: List of archives contained in the deployment to exclude when scanning.
     * Wildcards ({@code *}) are allowed. N.B. Just the name of the archive is matched, do not attempt
     * to specify a full path within the jar. The following examples would be valid exclusions: {@code my-jar.jar},
     * {@code *-internal.rar}.</li>
     * <li>verbose: {@code true} | {@code false}. Display more information. The set of rules that selected Galleon layers are
     * printed. Default to {@code false}.</li>
     * <li>version: server version. Default being the latest released version.</li>
     * <li>ignoreDeployment: The deployment will be not analyzed. A server based on the configured add-ons and the default base
     * layer is provisioned. Default to {@code false}.</li>
     * <li>spaces: List of spaces to enable. A space brings extra galleon feature-packs to the provisioning (eg:
     * {@code incubating} to
     * include the feature-packs that are in the incubating state.</li>
     *
     * </ul>
     * </div>
     *
     * For example, cloud, ha profile with CLI and openapi addOns enabled. mail layer being explicitly included:
     *
     * <pre>
     *   &lt;discover-provisioning-info&gt;
     *     &lt;context&gt;cloud&lt;/context&gt;
     *     &lt;profile&gt;ha&lt;/profile&gt;
     *     &lt;addOns&gt;
     *       &lt;addOn&gt;wildfly-cli&lt;/addOn&gt;
     *       &lt;addOn&gt;openapi&lt;/addOn&gt;
     *     &lt;/addOns&gt;
     *     &lt;layersForJndi&gt;
     *       &lt;layer&gt;mail&lt;/layer&gt;
     *     &lt;/layersForJndi&gt;
     *   &lt;/discover-provisioning-info&gt;
     * </pre>
     *
     * @since 5.2
     */
    @Parameter(alias = "discover-provisioning-info")
    private GlowConfig discoverProvisioningInfo;

    /**
     * Package the provisioned server into a WildFly Bootable JAR. In order to produce a hollow jar (a jar that doesn't contain
     * a deployment) set the { @code skipDeployment } parameter. A server packaged as bootable JAR is suited to run on
     * bare-metal.
     * When provisioning a server for the cloud, this option shouldn't be set.
     * <p>
     * Note that the produced fat JAR is ignored when running the {@code dev},{@code image},{@code start} or {@code run} goals.
     * </p>
     *
     * @since 5.2
     */
    @Parameter(alias = "bootable-jar", property = PropertyNames.BOOTABLE_JAR)
    private boolean bootableJar;

    /**
     * When {@code bootable-jar} is set to true, use this parameter to name the generated jar file.
     * <p>
     * Note that since 5.1 the default name changed from {@code server-bootable.jar} to
     * {@code ${project.artifactId}-bootable.jar}.
     * </p>
     *
     * @since 5.2
     */
    // TODO (jrp) can we use name for this instead of having it be definable in so many ways? It could be used with the
    // classifer
    @Parameter(alias = "bootable-jar-name", property = PropertyNames.BOOTABLE_JAR_NAME, defaultValue = "${project.artifactId}-bootable.jar")
    private String bootableJarName;

    /**
     * When {@code bootable-jar} is set to true, the bootable JAR artifact is attached to the project with the classifier
     * 'bootable'. Use this parameter to configure the classifier.
     *
     * @since 5.2
     */
    @Parameter(alias = "bootable-jar-classifier", property = PropertyNames.BOOTABLE_JAR_INSTALL_CLASSIFIER, defaultValue = BootableJarSupport.BOOTABLE_SUFFIX)
    private String bootableJarClassifier;

    /**
     * A list of the dependencies to include as deployments. These dependencies must be defined as dependencies in the
     * project.
     *
     * <p>
     * The pattern is {@code groupId:artifactId:type:classifier:version}. Each type may be left blank. A pattern can
     * be prefixed with a {@code !} to negatively match the pattern. Note that it is best practice to place negative
     * checks first.
     * </p>
     *
     * <pre>
     *     &lt;included-dependencies&gt;
     *         &lt;included&gt;!org.wildfly.examples:*test*&lt;/included&gt;
     *         &lt;included&gt;::war&lt;/included&gt;
     *         &lt;included&gt;org.wildfly.examples&lt;/included&gt;
     *     &lt;/included-dependencies&gt;
     * </pre>
     *
     * @since 5.2
     */
    @Parameter(alias = "included-dependencies", property = "wildfly.included.dependencies")
    private Set<String> includedDependencies = Set.of();

    /**
     * A list of the dependencies to exclude as deployments.
     *
     * <p>
     * The pattern is {@code groupId:artifactId:type:classifier:version}. Each type may be left blank. A pattern can
     * be prefixed with a {@code !} to negatively match the pattern. Note that it is best practice to place negative
     * checks first.
     * </p>
     *
     * <pre>
     *     &lt;excluded-dependencies&gt;
     *         &lt;excluded&gt;!org.wildfly.examples:*test*&lt;/excluded&gt;
     *         &lt;excluded&gt;::jar&lt;/excluded&gt;
     *     &lt;/excluded-dependencies&gt;
     * </pre>
     *
     * @since 5.2
     */
    @Parameter(alias = "excluded-dependencies", property = "wildfly.excluded.dependencies")
    private Set<String> excludedDependencies = Set.of();

    /**
     * Defines the scope of the dependencies to be included as deployments. This will deploy all dependencies defined
     * in the scope to the packaged server. However, this does assume the dependency passes the
     * {@code <included-dependencies/>}, {@code <excluded-dependencies/>} and {@code <excluded-dependency-scope/>} checks.
     *
     * @since 5.2
     */
    @Parameter(alias = "included-dependency-scope", property = "wildfly.included.dependency.scope")
    private String includedDependencyScope;

    /**
     * Defines the scope of the dependencies to be excluded as deployments. This will deploy all dependencies
     * <em>not</em> defined in the scope to the packaged server. However, this does assume the dependency passes the
     * {@code <included-dependencies/>}, {@code <excluded-dependencies/>} and {@code <included-dependency-scope/>} checks.
     *
     * @since 5.2
     */
    @Parameter(alias = "excluded-dependency-scope", property = "wildfly.excluded.dependency.scope")
    private String excludedDependencyScope;

    @Inject
    private OfflineCommandExecutor commandExecutor;

    private GalleonProvisioningConfig config;

    // Used to only collect additional deployments once
    private Map<String, Path> deployments;

    @Override
    protected GalleonProvisioningConfig getDefaultConfig() throws ProvisioningException {
        return null;
    }

    @Override
    protected GalleonProvisioningConfig buildGalleonConfig(GalleonBuilder pm)
            throws MojoExecutionException, ProvisioningException {
        if (discoverProvisioningInfo == null) {
            config = super.buildGalleonConfig(pm);
            return config;
        }
        if (discoverProvisioningInfo.getContext() != null &&
                GlowConfig.CLOUD_CONTEXT.equals(discoverProvisioningInfo.getContext()) &&
                bootableJar) {
            throw new MojoExecutionException("The option 'bootableJar' must not be set when "
                    + "discovering provisioning information for the 'cloud' execution context.");
        }
        try {
            List<Path> allDeployments = new ArrayList<>();
            Path primaryDeployment = getDeploymentContent();
            if (primaryDeployment != null) {
                allDeployments.add(primaryDeployment);
            }
            Map<String, Path> extraDeps = getDeployments();
            if (!extraDeps.isEmpty()) {
                allDeployments.addAll(extraDeps.values());
            }
            try (
                    ScanResults results = Utils.scanDeployment(discoverProvisioningInfo,
                            layers,
                            excludedLayers,
                            featurePacks,
                            dryRun,
                            getLog(),
                            allDeployments,
                            artifactResolver,
                            Paths.get(project.getBuild().getDirectory()),
                            pm,
                            galleonOptions,
                            layersConfigurationFileName)) {
                config = results.getProvisioningConfig();
                return config;
            }
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
    }

    @Override
    protected String getGoal() {
        return "provision";
    }

    private void deploy(Path deploymentContent, String targetName) throws MojoDeploymentException {
        if (Files.exists(deploymentContent)) {
            Path standaloneDeploymentDir = Path.of(provisioningDir, "standalone", "deployments");
            if (!standaloneDeploymentDir.isAbsolute()) {
                standaloneDeploymentDir = Path.of(project.getBuild().getDirectory()).resolve(standaloneDeploymentDir);
            }
            try {
                Path deploymentTarget = standaloneDeploymentDir.resolve(targetName);
                getLog().info("Copy deployment " + deploymentContent + " to " + deploymentTarget);
                Files.copy(deploymentContent, deploymentTarget, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new MojoDeploymentException("Could not copy deployment in provisioned server", e);
            }
        } else {
            getLog().warn("The file " + deploymentContent + " doesn't exist, it will be not deployed.");
        }
    }

    private Map<String, Path> getDeployments() throws MojoExecutionException {
        // Check if we've already processed the deployments
        if (deployments != null) {
            return deployments;
        }
        // If no filters are defined, we do not want to include any additional deployments.
        if (includedDependencies.isEmpty() && excludedDependencies.isEmpty() && includedDependencyScope == null
                && excludedDependencyScope == null) {
            return Map.of();
        }
        final List<ArtifactFilter> filters = new ArrayList<>();
        // Map the dependencies to a known key format
        final Set<String> dependenciesIds = project.getDependencies()
                .stream()
                .map(ProvisionServerMojo::createKey)
                .collect(Collectors.toSet());
        // Create a filter to only allow artifacts which are included as dependencies
        final ArtifactFilter dependencyFilter = artifact -> dependenciesIds.contains(createKey(artifact));
        filters.add(dependencyFilter);
        if (!includedDependencies.isEmpty()) {
            filters.add(new PatternIncludesArtifactFilter(includedDependencies));
        }
        if (!excludedDependencies.isEmpty()) {
            filters.add(new PatternExcludesArtifactFilter(excludedDependencies));
        }
        if (includedDependencyScope != null) {
            filters.add(createScopeFilter(includedDependencyScope, true));
        }
        if (excludedDependencyScope != null) {
            filters.add(createScopeFilter(excludedDependencyScope, false));
        }
        final ArtifactFilter filter = new AndArtifactFilter(filters);
        final Set<Artifact> projectArtifacts = project.getArtifacts();
        final Set<Artifact> deployments = projectArtifacts.stream()
                .filter(filter::include)
                .collect(Collectors.toSet());
        final Map<String, Path> deploymentPaths = new LinkedHashMap<>();
        for (var artifact : deployments) {
            final File f = artifact.getFile();
            if (f == null) {
                throw new MojoExecutionException("Deployment not found " + artifact);
            }
            final Path p = f.toPath();
            deploymentPaths.put(p.getFileName().toString(), p);
        }
        return this.deployments = Map.copyOf(deploymentPaths);
    }

    @Override
    protected void serverProvisioned(Path jbossHome) throws MojoExecutionException {
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

        if (includeDeployment) {
            Path primaryDeployment = getDeploymentContent();
            if (primaryDeployment != null) {
                deploy(primaryDeployment, getDeploymentTargetName());
            }
            // Handle extra deployments
            try {
                Map<String, Path> extraPaths = getDeployments();
                for (Map.Entry<String, Path> p : extraPaths.entrySet()) {
                    deploy(p.getValue(), p.getKey());
                }
            } catch (Exception ex) {
                throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
            }
        }

        // CLI execution
        try {
            if (!commands.isEmpty()) {
                List<File> wrappedScripts = wrapOfflineScripts(scripts);
                final BaseCommandConfiguration cmdConfig = new BaseCommandConfiguration.Builder()
                        .addCommands(wrapOfflineCommands(commands))
                        .addScripts(wrappedScripts)
                        .addCLIArguments(CLI_ECHO_COMMAND_ARG)
                        .setJBossHome(jbossHome)
                        .setAppend(true)
                        .setStdout(stdout)
                        // TODO (jrp) should we support this? We currently do, but is it even used?
                        // .addPropertiesFiles(resolveFiles(session.getPropertiesFiles()))
                        .setResolveExpression(resolveExpressions)
                        .build();
                commandExecutor.execute(cmdConfig, artifactResolver);
            }

            cleanupServer(jbossHome);
            if (bootableJar) {
                packageBootableJar(jbossHome, config);
            }
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
    }

    private void attachJar(Path jarFile) {
        if (getLog().isDebugEnabled()) {
            getLog().debug("Attaching bootable jar " + jarFile + " as a project artifact with classifier "
                    + bootableJarClassifier);
        }
        projectHelper.attachArtifact(project, "jar", bootableJarClassifier, jarFile.toFile());
    }

    private void packageBootableJar(Path jbossHome, GalleonProvisioningConfig activeConfig) throws Exception {
        String jarName = bootableJarName;
        Path targetPath = Paths.get(project.getBuild().getDirectory());
        Path targetJarFile = targetPath.toAbsolutePath()
                .resolve(jarName);
        Files.deleteIfExists(targetJarFile);
        BootableJarSupport.packageBootableJar(targetJarFile, targetPath,
                activeConfig, jbossHome,
                artifactResolver,
                new MvnMessageWriter(getLog()));
        attachJar(targetJarFile);
        getLog().info("Bootable JAR packaging DONE. To run the server: java -jar " + targetJarFile);

    }

    /**
     * Return the file name of the deployment to put in the server deployment directory
     *
     * @throws MojoExecutionException if an error occurs getting the deployment content
     */
    protected String getDeploymentTargetName() throws MojoExecutionException {
        return name != null ? name : getDeploymentContent().getFileName().toString();
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
        if (packageType.isIgnored()) {
            return null;
        }
        final String filename = Objects.requireNonNullElse(this.filename,
                String.format("%s.%s", project.getBuild().getFinalName(), packageType.getFileExtension()));
        final Path deployment = Paths.get(project.getBuild().getDirectory()).resolve(filename);
        if (Files.notExists(deployment)) {
            if (this.filename != null) {
                throw new MojoExecutionException("No deployment found with name " + this.filename);
            }
            getLog().warn("The project doesn't define a deployment artifact to deploy to the server.");
        }
        return deployment;
    }

    private static void cleanupServer(Path jbossHome) {
        final Path history = jbossHome.resolve("standalone").resolve("configuration").resolve("standalone_xml_history");
        IoUtils.recursiveDelete(history);
        final Path tmp = jbossHome.resolve("standalone").resolve("tmp");
        IoUtils.recursiveDelete(tmp);
        final Path log = jbossHome.resolve("standalone").resolve("log");
        IoUtils.recursiveDelete(log);
    }

    private static ArtifactFilter createScopeFilter(final String scope, final boolean includeScope) {
        final ScopeArtifactFilter filter = new ScopeArtifactFilter();
        switch (scope) {
            case Artifact.SCOPE_COMPILE:
                filter.setIncludeCompileScope(true);
                break;
            case Artifact.SCOPE_PROVIDED:
                filter.setIncludeProvidedScope(true);
                break;
            case Artifact.SCOPE_RUNTIME:
                filter.setIncludeRuntimeScope(true);
                break;
            case Artifact.SCOPE_TEST:
                filter.setIncludeTestScope(true);
                break;
            case Artifact.SCOPE_SYSTEM:
                filter.setIncludeSystemScope(true);
                break;
        }
        return includeScope ? filter : artifact -> !filter.include(artifact);
    }

    private static String createKey(final Dependency dependency) {
        final StringBuilder key = new StringBuilder()
                .append(dependency.getGroupId())
                .append(':')
                .append(dependency.getArtifactId())
                .append(':')
                .append(dependency.getType());
        if (dependency.getClassifier() != null) {
            key.append(':')
                    .append(dependency.getClassifier());
        }
        key.append(':')
                .append(dependency.getVersion());
        return key.toString();
    }

    private static String createKey(final Artifact artifact) {
        final StringBuilder key = new StringBuilder()
                .append(artifact.getGroupId())
                .append(':')
                .append(artifact.getArtifactId())
                .append(':')
                .append(artifact.getType());
        if (artifact.getClassifier() != null) {
            key.append(':')
                    .append(artifact.getClassifier());
        }
        key.append(':')
                .append(artifact.getVersion());
        return key.toString();
    }

}
