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
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.maven.plugin.util.MvnMessageWriter;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.xml.ProvisioningXmlWriter;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.maven.ChannelCoordinate;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.Utils;
import org.wildfly.plugin.core.GalleonUtils;
import static org.wildfly.plugin.core.Constants.PLUGIN_PROVISIONING_FILE;
import static org.wildfly.plugin.core.Constants.STANDALONE_XML;
import org.wildfly.plugin.core.FeaturePack;
import org.wildfly.plugin.core.MavenRepositoriesEnricher;


/**
 * Provision a server
 *
 * @author jfdenise
 */
abstract class AbstractProvisionServerMojo extends AbstractMojo {

    @Component
    RepositorySystem repoSystem;

    @Component
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
    private String provisioningDir;

     /**
     * Set to {@code true} if you want to delete the existing server referenced from the {@code provisioningDir} and provision a new one,
     * otherwise {@code false}.
     */
    @Parameter(alias="overwrite-provisioned-server", defaultValue = "false", property = PropertyNames.WILDFLY_PROVISIONING_OVERWRITE_PROVISIONED_SERVER)
    private boolean overwriteProvisionedServer;

    /**
     * A list of feature-pack configurations to install, can be combined with layers.
     * Use the System property {@code wildfly.provisioning.feature-packs} to provide a comma separated list of feature-packs.
     */
    @Parameter(required = false, alias= "feature-packs", property = PropertyNames.WILDFLY_PROVISIONING_FEATURE_PACKS)
    List<FeaturePack> featurePacks = Collections.emptyList();

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
     * Use the System property {@code wildfly.provisioning.layers.excluded} to provide a comma separated list of layers to exclude.
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

    @Parameter(alias = "channels", required = false)
    List<ChannelCoordinate> channels;

    private Path wildflyDir;

    protected MavenRepoManager artifactResolver;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping " + getGoal() + " of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        Path targetPath = Paths.get(project.getBuild().getDirectory());
        wildflyDir = targetPath.resolve(provisioningDir).normalize();
        if (!overwriteProvisionedServer && Files.exists(wildflyDir)) {
            getLog().info(String.format("A server already exists in " + wildflyDir + ", skipping " + getGoal() +
                    " of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        enrichRepositories();
        if (channels == null) {
            artifactResolver = offlineProvisioning ? new MavenArtifactRepositoryManager(repoSystem, repoSession)
                    : new MavenArtifactRepositoryManager(repoSystem, repoSession, repositories);
        } else {
            try {
                artifactResolver = offlineProvisioning ? new ChannelMavenArtifactRepositoryManager(channels, repoSystem, repoSession)
                        : new ChannelMavenArtifactRepositoryManager(channels, repoSystem, repoSession, repositories);
            } catch (MalformedURLException | UnresolvedMavenArtifactException ex) {
                throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
            }
        }
        if (!Paths.get(provisioningDir).isAbsolute() && (targetPath.equals(wildflyDir) || !wildflyDir.startsWith(targetPath))) {
            throw new  MojoExecutionException("provisioning-dir " + provisioningDir + " must be an absolute path or a child directory relative to the project build directory.");
        }
        IoUtils.recursiveDelete(wildflyDir);
        try {
            try {
                provisionServer(wildflyDir);
                if (artifactResolver instanceof ChannelMavenArtifactRepositoryManager) {
                    ((ChannelMavenArtifactRepositoryManager)artifactResolver).done(wildflyDir);
                }
            } catch (ProvisioningException | IOException | XMLStreamException ex) {
                throw new MojoExecutionException("Provisioning failed", ex);
            }
            serverProvisioned(wildflyDir);
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
        try (ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(artifactResolver)
                .setInstallationHome(home)
                .setMessageWriter(new MvnMessageWriter(getLog()))
                .setLogTime(logProvisioningTime)
                .setRecordState(recordProvisioningState)
                .build()) {
            ProvisioningConfig config = null;
            Path resolvedProvisioningFile = resolvePath(project, provisioningFile.toPath());
            boolean provisioningFileExists = Files.exists(resolvedProvisioningFile);
            if (featurePacks.isEmpty()) {
                if (provisioningFileExists) {
                    getLog().info("Provisioning server using " + resolvedProvisioningFile + " file.");
                    config = GalleonUtils.buildConfig(resolvedProvisioningFile);
                } else {
                    config = getDefaultConfig();
                    if (config == null) {
                        throw new MojoExecutionException("No feature-pack has been configured, can't provision a server.");
                    }
                }
            } else {
                if (provisioningFileExists) {
                    getLog().warn("Galleon provisioning file " + provisioningFile + " is ignored, plugin configuration is used.");
                }
                if (layers.isEmpty() && !STANDALONE_XML.equals(layersConfigurationFileName)) {
                    throw new MojoExecutionException("layers-configuration-file-name has been set although no layers are defined.");
                }
                config = GalleonUtils.buildConfig(pm, featurePacks, layers, excludedLayers, galleonOptions, layersConfigurationFileName);
            }
            getLog().info("Provisioning server in " + home);
            pm.provision(config);
            // Check that at least the standalone or domain directories have been generated.
            if (!Files.exists(home.resolve("standalone")) && !Files.exists(home.resolve("domain"))) {
                getLog().error("Invalid galleon provisioning, no server provisioned in " + home + ". Make sure "
                        + "that the list of Galleon feature-packs and Galleon layers are properly configured.");
                throw new MojoExecutionException("Invalid plugin configuration, no server provisioned.");
            }
            if (!recordProvisioningState) {
                Path file = home.resolve(PLUGIN_PROVISIONING_FILE);
                try (FileWriter writer = new FileWriter(file.toFile())) {
                    ProvisioningXmlWriter.getInstance().write(config, writer);
                }
            }
        }
    }

    protected ProvisioningConfig getDefaultConfig() throws ProvisioningDescriptionException {
        return GalleonUtils.buildDefaultConfig();
    }

    static Path resolvePath(MavenProject project, Path path) {
        if (!path.isAbsolute()) {
            path = Paths.get(project.getBasedir().getAbsolutePath()).resolve(path);
        }
        return path;
    }
}
