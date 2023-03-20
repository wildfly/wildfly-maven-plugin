/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.plugin.dev;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jboss.as.controller.client.ModelControllerClient;
import org.twdata.maven.mojoexecutor.MojoExecutor;
import org.wildfly.core.launcher.CommandBuilder;
import org.wildfly.plugin.cli.CommandConfiguration;
import org.wildfly.plugin.cli.CommandExecutor;
import org.wildfly.plugin.common.Environment;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.Utils;
import org.wildfly.plugin.core.ContainerDescription;
import org.wildfly.plugin.core.Deployment;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.core.DeploymentResult;
import org.wildfly.plugin.core.ServerHelper;
import org.wildfly.plugin.core.UndeployDescription;
import org.wildfly.plugin.deployment.PackageType;
import org.wildfly.plugin.server.AbstractServerStartMojo;
import org.wildfly.plugin.server.ServerContext;
import org.wildfly.plugin.server.ServerType;
import org.wildfly.plugin.server.VersionComparator;

/**
 * Starts a standalone instance of WildFly and deploys the application to the server. The deployment type myst be a WAR.
 * Once the server is running, the source directories are monitored for changes. If required the sources will be compiled
 * and the deployment may be redeployed.
 *
 * <p>
 * Note that changes to the POM file are not monitored. If changes are made the POM file, the process will need to be
 * terminated and restarted.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @since 4.1
 */
@Mojo(name = "dev", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class DevMojo extends AbstractServerStartMojo {
    private static final String ORG_APACHE_MAVEN_PLUGINS = "org.apache.maven.plugins";
    private static final String MAVEN_COMPILER_PLUGIN = "maven-compiler-plugin";
    private static final String MAVEN_COMPILER_GOAL = "compile";
    private static final String MAVEN_WAR_PLUGIN = "maven-war-plugin";
    private static final String MAVEN_EXPLODED_GOAL = "exploded";
    private static final String MAVEN_RESOURCES_PLUGIN = "maven-resources-plugin";
    private static final String MAVEN_RESOURCES_GOAL = "resources";

    /**
     * Executing any one of these phases means the compile phase will have been run, if these have not been run we
     * manually run compile.
     */
    private static final Set<String> POST_COMPILE_PHASES = Set.of(
            "compile",
            "process-classes",
            "generate-test-sources",
            "process-test-sources",
            "generate-test-resources",
            "process-test-resources",
            "test-compile",
            "process-test-classes",
            "test",
            "prepare-package",
            "package",
            "pre-integration-test",
            "integration-test",
            "post-integration-test",
            "verify",
            "install",
            "deploy");

    // A list of configuration parameters of the exploded goal as of 3.3.2
    private static final Map<String, String> EXPLODED_WAR_PARAMETERS = Map.ofEntries(
            Map.entry("archive", ""),
            Map.entry("archiveClasses", "2.0.1"),
            Map.entry("containerConfigXML", ""),
            Map.entry("delimiters", "3.0.0"),
            Map.entry("dependentWarExcludes", ""),
            Map.entry("dependentWarIncludes", ""),
            Map.entry("escapeString", "2.1-beta-1"),
            Map.entry("escapedBackslashesInFilePath", "2.1-alpha-2"),
            // Only added to the exploded goal since 3.3.0, see
            // https://github.com/apache/maven-war-plugin/commit/d98aee4b2c0e58c89f610f4fa1c613861c4cdcd1
            Map.entry("failOnMissingWebXml", "3.3.0"),
            Map.entry("filteringDeploymentDescriptors", "2.1-alpha-2"),
            Map.entry("filters", ""),
            Map.entry("includeEmptyDirectories", "2.4"),
            Map.entry("nonFilteredFileExtensions", "2.1-alpha-2"),
            Map.entry("outdatedCheckPath", "3.3.1"),
            Map.entry("outputFileNameMapping", "2.1-alpha-1"),
            Map.entry("outputTimestamp", "3.3.0"),
            Map.entry("overlays", "2.1-alpha-1"),
            Map.entry("recompressZippedFiles", "2.3"),
            Map.entry("resourceEncoding", "2.3"),
            Map.entry("supportMultiLineFiltering", "2.4"),
            Map.entry("useDefaultDelimiters", "3.0.0"),
            Map.entry("useJvmChmod", "2.4"),
            Map.entry("warSourceDirectory", ""),
            Map.entry("warSourceExcludes", ""),
            Map.entry("warSourceIncludes", ""),
            Map.entry("webappDirectory", ""),
            Map.entry("webResources", ""),
            Map.entry("webXml", ""),
            Map.entry("workDirectory", "")
    );
    private final Map<WatchKey, WatchContext> watchedDirectories = new HashMap<>();

    @Component
    private BuildPluginManager pluginManager;

    @Inject
    private CommandExecutor commandExecutor;

    /**
     * The CLI commands to execute before the deployment is deployed.
     */
    @Parameter(property = PropertyNames.COMMANDS)
    private List<String> commands = new ArrayList<>();

    /**
     * The CLI script files to execute before the deployment is deployed.
     */
    @Parameter(property = PropertyNames.SCRIPTS)
    private List<File> scripts = new ArrayList<>();

    /**
     * The path to the server configuration to use.
     */
    @Parameter(alias = "server-config", property = PropertyNames.SERVER_CONFIG)
    private String serverConfig;

    /**
     * Additional extensions of files located in {@code src/webapp} directory and its sub-directories that don't
     * require a redeployment on update.
     * <p>
     * The builtin list is {@code html, xhtml, jsp, js, css}.
     * </p>
     * <p>
     * You can set the system property {@code wildfly.dev.web.extensions} to a white space separated list of file
     * extensions.
     * </p>
     */
    @Parameter(property = "wildfly.dev.web.extensions", alias = "web-extensions")
    private List<String> webExtensions = new ArrayList<>();

    /**
     * File patterns that we should ignore during watch.
     * <p>
     * Hidden files and files ending with '~' are ignored.
     * </p>
     * <p>
     * You can set the system property {@code wildfly.dev.ignore.patterns} to a white space separated list of file
     * patterns.
     * </p>
     */
    @Parameter(property = "wildfly.dev.ignore.patterns", alias = "ignore-patterns")
    private List<String> ignorePatterns = new ArrayList<>();

    /**
     * If set to {@code true} a server will not be provisioned or started and the application will be deployed to a
     * remote server.
     */
    @Parameter(property = "wildfly.dev.remote", defaultValue = "false")
    private boolean remote;

    // Lazily loaded list of patterns based on the ignorePatterns
    private final List<Pattern> ignoreUpdatePatterns = new ArrayList<>();
    // Lazy loaded
    private final Set<String> allowedWarPluginParams = new HashSet<>();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final var packageType = PackageType.resolve(project);
        if (!"war".equalsIgnoreCase(packageType.getPackaging())) {
            throw new MojoExecutionException("The dev goal only works for WAR deployments");
        }
        ServerContext context = null;
        if (remote) {
            init();
        } else {
            // Start the container
            context = startServer(ServerType.STANDALONE);
        }
        try {
            // Do we need to build first?
            if (needsCompile()) {
                triggerResources();
                triggerCompile();
                triggerExplodeWar();
            }
            try (final WatchService watcher = FileSystems.getDefault().newWatchService()) {
                final CompiledSourceHandler sourceHandler = new CompiledSourceHandler();
                registerDir(watcher, Path.of(project.getBuild().getSourceDirectory()), sourceHandler);
                for (Resource resource : project.getResources()) {
                    registerDir(watcher, Path.of(resource.getDirectory()), new ResourceHandler());
                }
                registerDir(watcher, resolveWebAppSourceDir(), new WebAppResourceHandler(webExtensions));
                try (ModelControllerClient client = createClient()) {
                    if (!ServerHelper.isStandaloneRunning(client)) {
                        throw new MojoExecutionException("No standalone server appears to be running.");
                    }
                    if (remote) {
                        final ContainerDescription description = ServerHelper.getContainerDescription(client);
                        getLog().info(String.format("Deploying to remote %s container.", description));
                    }
                    // Execute commands before the deployment is done
                    final CommandConfiguration.Builder builder = CommandConfiguration.of(this::createClient, this::getClientConfiguration)
                            .addCommands(commands)
                            .addScripts(scripts)
                            .setStdout("none")
                            .setTimeout(timeout);
                    if (context == null) {
                        builder.setOffline(false)
                                .setFork(false);
                    } else {
                        builder.setJBossHome(context.jbossHome())
                                .setFork(true);
                    }
                    commandExecutor.execute(builder.build(), mavenRepoManager);
                    final DeploymentManager deploymentManager = DeploymentManager.Factory.create(client);
                    final Deployment deployment = getDeploymentContent();
                    try {
                        final DeploymentResult result = deploymentManager.forceDeploy(deployment);
                        if (!result.successful()) {
                            throw new MojoExecutionException("Failed to deploy content: " + result.getFailureMessage());
                        }
                        if (remote) {
                            getLog().info(String.format("Deployed %s", deployment.toString()));
                        }
                        watch(watcher, deploymentManager, deployment);
                    } finally {
                        deploymentManager.undeploy(UndeployDescription.of(deployment));
                        ServerHelper.shutdownStandalone(client);
                    }
                }
            } catch (IOException e) {
                throw new MojoExecutionException(e.getLocalizedMessage(), e);
            }
        } finally {
            if (context != null) {
                context.process().destroyForcibly();
            }
        }
    }

    @Override
    public String goal() {
        return "dev";
    }

    @Override
    protected CommandBuilder createCommandBuilder(final Path jbossHome) throws MojoExecutionException {
        return createStandaloneCommandBuilder(jbossHome, serverConfig);
    }

    /**
     * Allows the {@linkplain #webExtensions} to be set as a string.
     *
     * @param webExtensions a whitespace delimited string for the web file extensions
     */
    @SuppressWarnings("unused")
    public void setWebExtensions(final String webExtensions) {
        this.webExtensions = Utils.splitArguments(webExtensions);
    }

    /**
     * Allows the {@linkplain #ignorePatterns} to be set as a string.
     *
     * @param ignorePatterns a whitespace delimited string for the file patterns
     */
    @SuppressWarnings("unused")
    public void setIgnorePatterns(final String ignorePatterns) {
        this.ignorePatterns = Utils.splitArguments(ignorePatterns);
    }

    private boolean registerDir(final WatchService watcher, final Path dir, final WatchHandler handler) throws IOException {
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            final int currentSize = watchedDirectories.size();
            final Set<Path> registered = watchedDirectories.values()
                    .stream()
                    .map(WatchContext::directory)
                    .collect(Collectors.toCollection(HashSet::new));
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!project.getBuild().getOutputDirectory().equals(dir.toString())) {
                        if (registered.add(dir)) {
                            final WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                            watchedDirectories.put(key, WatchContext.of(dir, handler));
                            debug("Watching for changes in %s", dir);
                        }
                        return FileVisitResult.CONTINUE;
                    } else {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
            });
            return currentSize == watchedDirectories.size();
        }
        return false;
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private void watch(final WatchService watcher, final DeploymentManager deploymentManager, final Deployment deployment) {
        final var projectDir = project.getBasedir().toPath();
        try {
            for (; ; ) {
                WatchKey key = watcher.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    final WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    debug("File changed [%s]: %s", ev.kind().name(), ev.context());
                    final Path absolutePath = getPath(key, ev.context());
                    if (absolutePath == null) {
                        continue;
                    }
                    final var eventPath = absolutePath.getFileName();
                    try {
                        if (isIgnoredChange(eventPath)) {
                            debug("Ignoring change for %s", eventPath);
                            continue;
                        }
                    } catch (IOException ex) {
                        debug("Failed checking %s for ignored state: %s", eventPath, ex);
                    }
                    try {
                        final var context = watchedDirectories.get(key);
                        if (context == null) {
                            getLog().warn(String.format("Failed to find context for %s", ev.context()));
                            continue;
                        }
                        final var relativePath = projectDir.relativize(absolutePath);
                        if (ev.kind() == ENTRY_DELETE) {
                            // Undeploy application as Windows won't be able to delete the directory
                            DeploymentResult deploymentResult = deploymentManager.undeploy(UndeployDescription.of(deployment));
                            if (!deploymentResult.successful()) {
                                getLog().warn(String.format("Failed to undeploy application. Unexpected results may occur. Failure: %s",
                                        deploymentResult.getFailureMessage()));
                            } else {
                                // Clean the deployment directory
                                final Path path = resolveWarDir();
                                deleteRecursively(path);
                                triggerResources();
                                triggerCompile();
                                triggerExplodeWar();
                                deploymentResult = deploymentManager.deploy(deployment);
                                if (!deploymentResult.successful()) {
                                    throw new MojoExecutionException("Failed to deploy content: " + deploymentResult.getFailureMessage());
                                }
                                if (Files.notExists(context.directory())) {
                                    watchedDirectories.remove(key);
                                    key.cancel();
                                }
                                continue;
                            }
                        } else if (ev.kind() == ENTRY_CREATE) {
                            // If this is a directory we need to add the directory
                            if (Files.isDirectory(eventPath)) {
                                if (registerDir(watcher, eventPath, context.handler())) {
                                    debug("New directory registered: %s", relativePath);
                                }
                            } else {
                                final Path parent = absolutePath.getParent();
                                if (parent != null) {
                                    if (registerDir(watcher, parent, context.handler())) {
                                        debug("New directory registered: %s", relativePath);
                                    }
                                }
                                debug("A new source file has been created: %s", relativePath);
                            }
                        } else if (ev.kind() == ENTRY_MODIFY) {
                            debug("Source file modified: %s", relativePath);
                        }
                        // Handle the file
                        final var result = context.handle(ev, absolutePath);
                        if (result.requiresRecompile()) {
                            triggerCompile();
                        }
                        if (result.requiresCopyResources()) {
                            triggerResources();
                        }
                        if (result.requiresRepackage()) {
                            triggerExplodeWar();
                        }
                        if (result.requiresRedeploy()) {
                            final DeploymentResult deploymentResult = deploymentManager.redeployToRuntime(deployment);
                            if (!deploymentResult.successful()) {
                                throw new MojoExecutionException("Failed to deploy content: " + deploymentResult.getFailureMessage());
                            }
                        }
                    } catch (Exception ex) {
                        getLog().error("Exception handling file change: " + ex);
                    }
                }
                key.reset();
            }
        } catch (ClosedWatchServiceException ex) {
            // OK Can ignore, we have been closed by shutdown hook.
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted during watch.", e);
        }
    }

    private void triggerCompile() throws MojoExecutionException {
        // Compile the Java sources if needed
        final String compilerPluginKey = ORG_APACHE_MAVEN_PLUGINS + ":" + MAVEN_COMPILER_PLUGIN;
        final Plugin compilerPlugin = project.getPlugin(compilerPluginKey);
        if (compilerPlugin != null) {
            executeGoal(project, compilerPlugin, ORG_APACHE_MAVEN_PLUGINS, MAVEN_COMPILER_PLUGIN, MAVEN_COMPILER_GOAL, getPluginConfig(compilerPlugin, MAVEN_COMPILER_GOAL));
        }
    }

    private void triggerExplodeWar() throws MojoExecutionException {
        // Compile the Java sources if needed
        final String warPluginKey = ORG_APACHE_MAVEN_PLUGINS + ":" + MAVEN_WAR_PLUGIN;
        final Plugin warPlugin = project.getPlugin(warPluginKey);
        if (warPlugin != null) {
            executeGoal(project, warPlugin, ORG_APACHE_MAVEN_PLUGINS, MAVEN_WAR_PLUGIN, MAVEN_EXPLODED_GOAL, getWarPluginConfig(warPlugin));
        } else {
            getLog().warn("Can't package war application, war plugin not found");
        }
    }

    private void triggerResources() throws MojoExecutionException {
        List<Resource> resources = project.getResources();
        if (resources.isEmpty()) {
            return;
        }
        Plugin resourcesPlugin = project.getPlugin(ORG_APACHE_MAVEN_PLUGINS + ":" + MAVEN_RESOURCES_PLUGIN);
        if (resourcesPlugin == null) {
            return;
        }
        executeGoal(project, resourcesPlugin, ORG_APACHE_MAVEN_PLUGINS, MAVEN_RESOURCES_PLUGIN, MAVEN_RESOURCES_GOAL, getPluginConfig(resourcesPlugin, MAVEN_RESOURCES_GOAL));
    }

    private Path getPath(final WatchKey key, final Path fileName) {
        final WatchContext context = watchedDirectories.get(key);
        if (context == null) {
            getLog().debug("No more watching key, ignoring change done to " + fileName);
            return null;
        } else {
            final Path resolved = context.directory().resolve(fileName);
            // Fully ignore target dir
            if (Path.of(project.getBuild().getDirectory()).equals(resolved)) {
                return null;
            }
            return resolved;
        }

    }

    private boolean needsCompile() {
        // Check if compiling is going to be done by a previous goal
        boolean compileNeeded = true;
        for (String goal : mavenSession.getGoals()) {
            if (POST_COMPILE_PHASES.contains(goal)) {
                compileNeeded = false;
                break;
            }
            if (goal.endsWith("wildfly:" + goal())) {
                break;
            }
        }
        return compileNeeded;
    }

    private void executeGoal(final MavenProject project, final Plugin plugin, final String groupId, final String artifactId, final String goal, final Xpp3Dom config) throws MojoExecutionException {
        executeMojo(plugin(groupId(groupId), artifactId(artifactId), version(plugin.getVersion()), plugin.getDependencies()), MojoExecutor.goal(goal), config, executionEnvironment(project, mavenSession, pluginManager));
    }

    private Xpp3Dom getPluginConfig(final Plugin plugin, final String goal) throws MojoExecutionException {
        Xpp3Dom mergedConfig = null;
        if (!plugin.getExecutions().isEmpty()) {
            for (PluginExecution exec : plugin.getExecutions()) {
                if (exec.getConfiguration() != null && exec.getGoals().contains(goal)) {
                    mergedConfig = mergedConfig == null ? (Xpp3Dom) exec.getConfiguration() : Xpp3Dom.mergeXpp3Dom(mergedConfig, (Xpp3Dom) exec.getConfiguration(), true);
                }
            }
        }

        if (plugin.getConfiguration() != null) {
            mergedConfig = mergedConfig == null ? (Xpp3Dom) plugin.getConfiguration() : Xpp3Dom.mergeXpp3Dom(mergedConfig, (Xpp3Dom) plugin.getConfiguration(), true);
        }

        final Xpp3Dom configuration = configuration();

        if (mergedConfig != null) {
            Set<String> supportedParams = null;
            // Filter out `test*` configurations
            for (Xpp3Dom child : mergedConfig.getChildren()) {
                if (child.getName().startsWith("test")) {
                    continue;
                }
                if (supportedParams == null) {
                    supportedParams = getMojoDescriptor(plugin, goal).getParameterMap().keySet();
                }
                if (supportedParams.contains(child.getName())) {
                    configuration.addChild(child);
                }
            }
        }

        return configuration;
    }

    private Xpp3Dom getWarPluginConfig(final Plugin plugin) {

        final Xpp3Dom configuration = configuration();

        // Load the allowed configuration params if not yet loaded
        if (allowedWarPluginParams.isEmpty()) {
            final String pluginVersion = plugin.getVersion();
            final VersionComparator comparator = new VersionComparator();
            allowedWarPluginParams.addAll(EXPLODED_WAR_PARAMETERS.entrySet().stream()
                    .filter(e -> e.getValue().isEmpty() || comparator.compare(e.getValue(), pluginVersion) <= 0)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet()));
        }

        final Xpp3Dom pluginConfiguration = (Xpp3Dom) plugin.getConfiguration();
        if (pluginConfiguration != null) {
            for (Xpp3Dom child : pluginConfiguration.getChildren()) {
                if (allowedWarPluginParams.contains(child.getName())) {
                    configuration.addChild(child);
                }
            }
        }

        final MojoExecutor.Element e = new MojoExecutor.Element("webappDirectory", resolveWarDir().toAbsolutePath()
                .toString());
        configuration.addChild(e.toDom());
        return configuration;
    }

    // Required to retrieve the actual set of supported configuration items.
    private MojoDescriptor getMojoDescriptor(Plugin plugin, String goal) throws MojoExecutionException {
        try {
            return pluginManager.getMojoDescriptor(plugin, goal, repositories, session);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to obtain descriptor for Maven plugin " + plugin.getId() + " goal " + goal, e);
        }
    }

    private boolean isIgnoredChange(final Path file) throws IOException {
        if (isHiddenFile(file) || file.getFileName().toString().endsWith("~")) {
            return true;
        }
        for (Pattern pattern : getPatterns()) {
            if (pattern.matcher(file.getFileName().toString()).matches()) {
                return true;
            }
        }
        return false;
    }

    private boolean isHiddenFile(final Path p) throws IOException {
        if (Environment.isWindows()) {
            final DosFileAttributes dosAttrs = Files.readAttributes(p, DosFileAttributes.class);
            return dosAttrs.isHidden();
        } else {
            return Files.isHidden(p);
        }
    }

    private List<Pattern> getPatterns() {
        if (!ignorePatterns.isEmpty()) {
            if (ignoreUpdatePatterns.isEmpty()) {
                for (String p : ignorePatterns) {
                    Pattern pattern = Pattern.compile(p);
                    ignoreUpdatePatterns.add(pattern);
                }
            }
        }
        return ignoreUpdatePatterns;
    }

    private Deployment getDeploymentContent() {
        final PackageType packageType = PackageType.resolve(project);
        final Deployment deployment = Deployment.of(resolveWarDir());
        final String filename;
        //if (this.filename == null) {
        filename = String.format("%s.%s", project.getBuild().getFinalName(), packageType.getFileExtension());
        //} else {
        //filename = this.filename;
        //}
        return deployment.setRuntimeName(filename);
    }

    private Path resolveWebAppSourceDir() {
        final String warPluginKey = ORG_APACHE_MAVEN_PLUGINS + ":" + MAVEN_WAR_PLUGIN;
        final Plugin warPlugin = project.getPlugin(warPluginKey);
        Xpp3Dom dom = getWarPluginConfig(warPlugin);
        final Xpp3Dom warSourceDirectory = dom.getChild("warSourceDirectory");
        if (warSourceDirectory == null) {
            return project.getBasedir().toPath().resolve("src").resolve("main").resolve("webapp");
        }
        return Path.of(warSourceDirectory.getValue());
    }

    private Path resolveWarDir() {
        return Path.of(project.getBuild().getDirectory()).resolve(project.getBuild().getFinalName());
    }

    private void debug(final String format, final Object... args) {
        getLog().debug(String.format("[WATCH] " + format, args));
    }

    private static void deleteRecursively(final Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else if (Files.exists(path)) {
            Files.delete(path);
        }
    }
}
