/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import static org.wildfly.plugin.core.Constants.PLUGIN_PROVISIONING_FILE;
import static org.wildfly.plugin.core.Constants.STANDALONE_XML;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.xml.stream.XMLStreamException;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.GalleonFeaturePack;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.ProvisioningBuilder;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.maven.plugin.util.MvnMessageWriter;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.Utils;
import org.wildfly.plugin.core.MavenJBossLogger;
import org.wildfly.plugin.core.MavenRepositoriesEnricher;
import org.wildfly.plugin.tools.GalleonUtils;
import org.wildfly.plugin.tools.PluginProgressTracker;

/**
 * Provision a server
 *
 * @author jfdenise
 */
abstract class AbstractProvisionServerMojo extends AbstractMojo {
    static {
        // This is odd, but if not set we should set the JBoss Logging provider to slf4j as that is what Maven uses
        final String provider = System.getProperty("org.jboss.logging.provider");
        if (provider == null || provider.isBlank()) {
            System.setProperty("org.jboss.logging.provider", "slf4j");
        }
    }

    @Inject
    RepositorySystem repoSystem;

    @Inject
    MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repositories;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    MavenSession session;

    /**
     * Arbitrary Galleon options used when provisioning the server. In case you
     * are building a large amount of server in the same maven session, it
     * is strongly advised to set 'jboss-fork-embedded' option to 'true' in
     * order to fork Galleon provisioning and CLI scripts execution in dedicated
     * processes. For example:
     *
     * <pre>
     *   &lt;galleon-options&gt;
     *     &lt;jboss-fork-embedded&gt;true&lt;/jboss-fork-embedded&gt;
     *   &lt;/galleon-options&gt;
     * </pre>
     */
    @Parameter(required = false, alias = "galleon-options")
    Map<String, String> galleonOptions = Collections.emptyMap();

    /**
     * Whether to use offline mode when the plugin resolves an artifact. In
     * offline mode the plugin will only use the local Maven repository for an
     * artifact resolution.
     */
    @Parameter(alias = "offline-provisioning", defaultValue = "false", property = PropertyNames.WILDFLY_PROVISIONING_OFFLINE)
    boolean offlineProvisioning;

    /**
     * Whether to log provisioning time at the end
     */
    @Parameter(alias = "log-provisioning-time", defaultValue = "false", property = PropertyNames.WILDFLY_PROVISIONING_LOG_TIME)
    boolean logProvisioningTime;

    /**
     * Whether to record provisioning state in .galleon directory.
     */
    @Parameter(alias = "record-provisioning-state", defaultValue = "false", property = PropertyNames.WILDFLY_PROVISIONING_RECORD_STATE)
    boolean recordProvisioningState;

    /**
     * Set to {@code true} if you want the goal to be skipped, otherwise
     * {@code false}.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.SKIP_PROVISION)
    private boolean skip;

    /**
     * The path to the directory where to provision the server. Can be an absolute path or a path relative to the buildDir.
     * By default the server is provisioned into the {@code target/server} directory.
     */
    @Parameter(alias = "provisioning-dir", property = PropertyNames.WILDFLY_PROVISIONING_DIR, defaultValue = Utils.WILDFLY_DEFAULT_DIR)
    protected String provisioningDir;

    /**
     * Set to {@code true} if you want to delete the existing server referenced from the {@code provisioningDir} and provision a
     * new one,
     * otherwise {@code false}.
     */
    @Parameter(alias = "overwrite-provisioned-server", defaultValue = "false", property = PropertyNames.WILDFLY_PROVISIONING_OVERWRITE_PROVISIONED_SERVER)
    private boolean overwriteProvisionedServer;

    /**
     * A list of feature-pack configurations to install, can be combined with layers.
     * Use the System property {@code wildfly.provisioning.feature-packs} to provide a comma separated list of feature-packs.
     */
    @Parameter(required = false, alias = "feature-packs", property = PropertyNames.WILDFLY_PROVISIONING_FEATURE_PACKS)
    List<GalleonFeaturePack> featurePacks = Collections.emptyList();

    /**
     * A list of Galleon layers to provision. Can be used when
     * feature-pack-location or feature-packs are set.
     * Use the System property {@code wildfly.provisioning.layers} to provide a comma separated list of layers.
     */
    @Parameter(alias = "layers", required = false, property = PropertyNames.WILDFLY_PROVISIONING_LAYERS)
    List<String> layers = Collections.emptyList();

    /**
     * A list of Galleon layers to exclude. Can be used when
     * feature-pack-location or feature-packs are set.
     * Use the System property {@code wildfly.provisioning.layers.excluded} to provide a comma separated list of layers to
     * exclude.
     */
    @Parameter(alias = "excluded-layers", required = false, property = PropertyNames.WILDFLY_PROVISIONING_LAYERS_EXCLUDED)
    List<String> excludedLayers = Collections.emptyList();

    /**
     * The path to the {@code provisioning.xml} file to use. Note that this cannot be used with the {@code feature-packs}
     * or {@code configurations}.
     * If the provisioning file is not absolute, it has to be relative to the project base directory.
     */
    @Parameter(alias = "provisioning-file", property = PropertyNames.WILDFLY_PROVISIONING_FILE, defaultValue = "${project.basedir}/galleon/provisioning.xml")
    private File provisioningFile;

    /**
     * The name of the configuration file generated from layers. Default value is {@code standalone.xml}.
     * If no {@code layers} have been configured, setting this parameter is invalid.
     */
    @Parameter(alias = "layers-configuration-file-name", property = PropertyNames.WILDFLY_LAYERS_CONFIGURATION_FILE_NAME, defaultValue = STANDALONE_XML)
    String layersConfigurationFileName;

    /**
     * A list of channels used for resolving artifacts while provisioning.
     * <p>
     * Defining a channel:
     *
     * <pre>
     * &lt;channels&gt;
     *   &lt;channel&gt;
     *       &lt;manifest&gt;
     *           &lt;groupId&gt;org.wildfly.channels&lt;/groupId&gt;
     *           &lt;artifactId&gt;wildfly-30.0&lt;/artifactId&gt;
     *       &lt;/manifest&gt;
     *   &lt;/channel&gt;
     *   &lt;channel&gt;
     *       &lt;manifest&gt;
     *           &lt;url&gt;https://example.example.org/channel/30&lt;/url&gt;
     *       &lt;/manifest&gt;
     *   &lt;/channel&gt;
     * &lt;/channels&gt;
     * </pre>
     * </p>
     * <p>
     * The {@code wildfly.channels} property can be used pass a comma delimited string for the channels. The channel
     * can be a URL or a Maven GAV. If a Maven GAV is used, the groupId and artifactId are required.
     * <br>
     * Examples:
     *
     * <pre>
     *     -Dwildfly.channels=&quot;https://channels.example.org/30&quot;
     *     -Dwildfly.channels=&quot;https://channels.example.org/30,org.example.channel:updates-30&quot;
     *     -Dwildfly.channels=&quot;https://channels.example.org/30,org.example.channel:updates-30:1.0.2&quot;
     * </pre>
     * </p>
     */
    @Parameter(alias = "channels", property = PropertyNames.CHANNELS)
    List<ChannelConfiguration> channels;

    /**
     * Do not actually provision a server but generate the Galleon provisioning configuration
     * in {@code target/.wildfly-maven-plugin-provisioning.xml} file.
     *
     * @since 5.0
     */
    @Parameter(alias = "dry-run")
    boolean dryRun;

    private Path wildflyDir;

    protected MavenRepoManager artifactResolver;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping " + getGoal() + " of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        if (dryRun) {
            getLog().info("Dry run execution, no server will be provisioned.");
        }
        Path targetPath = Paths.get(project.getBuild().getDirectory());
        wildflyDir = targetPath.resolve(provisioningDir).normalize();
        if (!overwriteProvisionedServer && Files.exists(wildflyDir)) {
            getLog().info(String.format("A server already exists in " + wildflyDir + ", skipping " + getGoal() +
                    " of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        enrichRepositories();
        if (channels == null || channels.isEmpty()) {
            artifactResolver = offlineProvisioning ? new MavenArtifactRepositoryManager(repoSystem, repoSession)
                    : new MavenArtifactRepositoryManager(repoSystem, repoSession, repositories);
        } else {
            try {
                artifactResolver = new ChannelMavenArtifactRepositoryManager(channels,
                        repoSystem, repoSession, repositories,
                        getLog(), offlineProvisioning);
            } catch (MalformedURLException | UnresolvedMavenArtifactException ex) {
                throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
            }
        }
        if (!Paths.get(provisioningDir).isAbsolute() && (targetPath.equals(wildflyDir) || !wildflyDir.startsWith(targetPath))) {
            throw new MojoExecutionException("provisioning-dir " + provisioningDir
                    + " must be an absolute path or a child directory relative to the project build directory.");
        }
        IoUtils.recursiveDelete(wildflyDir);
        try {
            try {
                provisionServer(wildflyDir);
                if (!dryRun) {
                    if (artifactResolver instanceof ChannelMavenArtifactRepositoryManager) {
                        ((ChannelMavenArtifactRepositoryManager) artifactResolver).done(wildflyDir);
                    }
                    serverProvisioned(wildflyDir);
                }
            } catch (ProvisioningException | IOException | XMLStreamException ex) {
                throw new MojoExecutionException("Provisioning failed", ex);
            }
        } finally {
            // Although cli and embedded are run in their own classloader,
            // the module.path system property has been set and needs to be cleared for
            // in same JVM next execution.
            System.clearProperty("module.path");
        }
    }

    protected void enrichRepositories() throws MojoExecutionException {
        MavenRepositoriesEnricher.enrich(session, project, repositories);
    }

    protected abstract String getGoal();

    protected abstract void serverProvisioned(Path jbossHome) throws MojoExecutionException, MojoFailureException;

    private void provisionServer(Path home) throws ProvisioningException,
            MojoExecutionException, IOException, XMLStreamException {
        GalleonBuilder galleonBuilder = new GalleonBuilder();
        galleonBuilder.addArtifactResolver(artifactResolver);
        GalleonProvisioningConfig config = buildGalleonConfig(galleonBuilder);
        ProvisioningBuilder builder = galleonBuilder.newProvisioningBuilder(config);
        try (Provisioning pm = builder
                .setInstallationHome(home)
                .setMessageWriter(new MvnMessageWriter(getLog()))
                .setLogTime(logProvisioningTime)
                .setRecordState(recordProvisioningState)
                .build()) {
            if (dryRun) {
                Path targetPath = Paths.get(project.getBuild().getDirectory());
                Path file = targetPath.resolve(PLUGIN_PROVISIONING_FILE);
                getLog().info("Dry-run execution, generating provisioning.xml file: " + file);
                pm.storeProvisioningConfig(config, file);
                return;
            }
            getLog().info("Provisioning server in " + home);
            PluginProgressTracker.initTrackers(pm, new MavenJBossLogger(getLog()));
            pm.provision(config);
            // Check that at least the standalone or domain directories have been generated.
            if (!Files.exists(home.resolve("standalone")) && !Files.exists(home.resolve("domain"))) {
                getLog().error("Invalid galleon provisioning, no server provisioned in " + home + ". Make sure "
                        + "that the list of Galleon feature-packs and Galleon layers are properly configured.");
                throw new MojoExecutionException("Invalid plugin configuration, no server provisioned.");
            }
            if (!recordProvisioningState) {
                Path file = home.resolve(PLUGIN_PROVISIONING_FILE);
                pm.storeProvisioningConfig(config, file);
            }
        }
    }

    protected GalleonProvisioningConfig buildGalleonConfig(GalleonBuilder galleonBuilder)
            throws MojoExecutionException, ProvisioningException {
        GalleonProvisioningConfig config;
        Path resolvedProvisioningFile = resolvePath(project, provisioningFile.toPath());
        boolean provisioningFileExists = Files.exists(resolvedProvisioningFile);
        if (featurePacks.isEmpty()) {
            if (provisioningFileExists) {
                getLog().info("Provisioning server using " + resolvedProvisioningFile + " file.");
                try (Provisioning p = galleonBuilder.newProvisioningBuilder(resolvedProvisioningFile).build()) {
                    config = p.loadProvisioningConfig(resolvedProvisioningFile);
                }
            } else {
                config = getDefaultConfig();
                if (config == null) {
                    throw new MojoExecutionException("No feature-pack has been configured, can't provision a server.");
                }
            }
        } else {
            if (provisioningFileExists) {
                getLog().warn(
                        "Galleon provisioning file " + provisioningFile + " is ignored, plugin configuration is used.");
            }
            if (layers.isEmpty() && !STANDALONE_XML.equals(layersConfigurationFileName)) {
                throw new MojoExecutionException(
                        "layers-configuration-file-name has been set although no layers are defined.");
            }
            config = GalleonUtils.buildConfig(galleonBuilder, featurePacks, layers, excludedLayers, galleonOptions,
                    layersConfigurationFileName);
        }
        return config;
    }

    protected GalleonProvisioningConfig getDefaultConfig() throws ProvisioningException {
        return GalleonUtils.buildDefaultConfig();
    }

    static Path resolvePath(MavenProject project, Path path) {
        if (!path.isAbsolute()) {
            path = Paths.get(project.getBasedir().getAbsolutePath()).resolve(path);
        }
        return path;
    }
}
