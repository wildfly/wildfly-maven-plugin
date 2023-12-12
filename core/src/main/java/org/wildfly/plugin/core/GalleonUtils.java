/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.core;

import static org.wildfly.plugin.core.Constants.FORK_EMBEDDED_PROCESS_OPTION;
import static org.wildfly.plugin.core.Constants.STANDALONE;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jboss.galleon.Constants;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.ConfigurationId;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.GalleonFeaturePack;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonConfigurationWithLayersBuilder;
import org.jboss.galleon.api.config.GalleonFeaturePackConfig;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;

/**
 * @author jdenise
 */
public class GalleonUtils {

    private static final String WILDFLY_DEFAULT_FEATURE_PACK_LOCATION = "wildfly@maven(org.jboss.universe:community-universe)";

    /**
     * Galleon provisioning of a default server.
     *
     * @param jbossHome           Server installation directory
     * @param featurePackLocation the location of the feature pack
     * @param version             WildFly version, if null latest is used.
     * @param artifactResolver    Artifact resolver used by Galleon
     * @throws ProvisioningException if there is an error provisioning the server
     */
    public static void provision(Path jbossHome, String featurePackLocation, String version, MavenRepoManager artifactResolver)
            throws ProvisioningException {
        GalleonProvisioningConfig config = buildDefaultConfig(featurePackLocation, version);
        try (Provisioning pm = new GalleonBuilder().addArtifactResolver(artifactResolver).newProvisioningBuilder(config)
                .setInstallationHome(jbossHome)
                .build()) {
            pm.provision(config);
        }
    }

    /**
     * Build a default WildFly provisioning config.
     *
     * @return
     * @throws ProvisioningException
     */
    public static GalleonProvisioningConfig buildDefaultConfig() throws ProvisioningException {
        return buildDefaultConfig(WILDFLY_DEFAULT_FEATURE_PACK_LOCATION, null);
    }

    /**
     * Build a default WildFly provisioning config.
     *
     * @param version WildFly version, if null latest is used.
     * @return
     * @throws ProvisioningException
     */
    public static GalleonProvisioningConfig buildDefaultConfig(String featurePackLocation, String version)
            throws ProvisioningException {
        String location = getWildFlyFeaturePackLocation(featurePackLocation, version);
        GalleonProvisioningConfig.Builder state = GalleonProvisioningConfig.builder();
        GalleonFeaturePackConfig.Builder fp = GalleonFeaturePackConfig.builder(FeaturePackLocation.fromString(location));
        fp.setInheritConfigs(true);
        fp.setInheritPackages(true);
        state.addFeaturePackDep(fp.build());
        Map<String, String> options = new HashMap<>();
        options.put(FORK_EMBEDDED_PROCESS_OPTION, "true");
        state.addOptions(options);
        return state.build();
    }

    /**
     * Build a Galleon provisioning configuration.
     *
     * @param pm                   The Galleon provisioning runtime.
     * @param featurePacks         The list of feature-packs.
     * @param layers               Layers to include.
     * @param excludedLayers       Layers to exclude.
     * @param pluginOptions        Galleon plugin options.
     * @param layersConfigFileName The name of the configuration generated from layers
     * @return The provisioning config.
     * @throws ProvisioningException
     */
    public static GalleonProvisioningConfig buildConfig(GalleonBuilder pm,
            List<GalleonFeaturePack> featurePacks,
            List<String> layers,
            List<String> excludedLayers,
            Map<String, String> pluginOptions, String layersConfigFileName)
            throws ProvisioningException, IllegalArgumentException {
        final GalleonProvisioningConfig.Builder state = GalleonProvisioningConfig.builder();
        boolean hasLayers = !layers.isEmpty();
        boolean fpWithDefaults = true;
        if (!hasLayers) {
            // Check we have all feature-packs with default values only.
            for (GalleonFeaturePack fp : featurePacks) {
                if (fp.isInheritConfigs() != null ||
                        fp.isInheritPackages() != null ||
                        !fp.getIncludedConfigs().isEmpty() ||
                        !fp.getExcludedConfigs().isEmpty() ||
                        fp.isTransitive() ||
                        !fp.getExcludedPackages().isEmpty() ||
                        !fp.getIncludedPackages().isEmpty()) {
                    fpWithDefaults = false;
                    break;
                }
            }
        }

        for (GalleonFeaturePack fp : featurePacks) {
            if (fp.getLocation() == null && (fp.getGroupId() == null || fp.getArtifactId() == null)
                    && fp.getNormalizedPath() == null) {
                throw new IllegalArgumentException("Feature-pack location, Maven GAV or feature pack path is missing");
            }

            final FeaturePackLocation fpl;
            if (fp.getNormalizedPath() != null) {
                fpl = pm.addLocal(fp.getNormalizedPath(), false);
            } else if (fp.getGroupId() != null && fp.getArtifactId() != null) {
                String coords = getMavenCoords(fp);
                fpl = FeaturePackLocation.fromString(coords);
            } else {
                // Special case for G:A that conflicts with producer:channel that we can't have in the plugin.
                String location = fp.getLocation();
                if (!FeaturePackLocation.fromString(location).hasUniverse()) {
                    long numSeparators = location.chars().filter(ch -> ch == ':').count();
                    if (numSeparators <= 1) {
                        location += ":";
                    }
                }
                fpl = FeaturePackLocation.fromString(location);
            }

            final GalleonFeaturePackConfig.Builder fpConfig = fp.isTransitive()
                    ? GalleonFeaturePackConfig.transitiveBuilder(fpl)
                    : GalleonFeaturePackConfig.builder(fpl);
            if (fp.isInheritConfigs() == null) {
                if (hasLayers) {
                    fpConfig.setInheritConfigs(false);
                } else {
                    if (fpWithDefaults) {
                        fpConfig.setInheritConfigs(true);
                    }
                }
            } else {
                fpConfig.setInheritConfigs(fp.isInheritConfigs());
            }

            if (fp.isInheritPackages() == null) {
                if (hasLayers) {
                    fpConfig.setInheritPackages(false);
                } else {
                    if (fpWithDefaults) {
                        fpConfig.setInheritConfigs(true);
                    }
                }
            } else {
                fpConfig.setInheritPackages(fp.isInheritPackages());
            }

            if (!fp.getExcludedConfigs().isEmpty()) {
                for (ConfigurationId configId : fp.getExcludedConfigs()) {
                    if (configId.isModelOnly()) {
                        fpConfig.excludeConfigModel(configId.getId().getModel());
                    } else {
                        fpConfig.excludeDefaultConfig(configId.getId());
                    }
                }
            }
            if (!fp.getIncludedConfigs().isEmpty()) {
                for (ConfigurationId configId : fp.getIncludedConfigs()) {
                    if (configId.isModelOnly()) {
                        fpConfig.includeConfigModel(configId.getId().getModel());
                    } else {
                        fpConfig.includeDefaultConfig(configId.getId());
                    }
                }
            }

            if (!fp.getIncludedPackages().isEmpty()) {
                for (String includedPackage : fp.getIncludedPackages()) {
                    fpConfig.includePackage(includedPackage);
                }
            }
            if (!fp.getExcludedPackages().isEmpty()) {
                for (String excludedPackage : fp.getExcludedPackages()) {
                    fpConfig.excludePackage(excludedPackage);
                }
            }

            state.addFeaturePackDep(fpConfig.build());
        }

        if (!layers.isEmpty()) {
            GalleonConfigurationWithLayersBuilder config = GalleonConfigurationWithLayersBuilder.builder(STANDALONE,
                    layersConfigFileName);
            for (String l : layers) {
                config.includeLayer(l);
            }
            for (String l : excludedLayers) {
                config.excludeLayer(l);
            }
            state.addConfig(config.build());
            if (pluginOptions.isEmpty()) {
                pluginOptions = Collections.singletonMap(Constants.OPTIONAL_PACKAGES, Constants.PASSIVE_PLUS);
            } else if (!pluginOptions.containsKey(Constants.OPTIONAL_PACKAGES)) {
                pluginOptions.put(Constants.OPTIONAL_PACKAGES, Constants.PASSIVE_PLUS);
            }
        }

        state.addOptions(pluginOptions);

        return state.build();
    }

    private static String getMavenCoords(GalleonFeaturePack fp) {
        StringBuilder builder = new StringBuilder();
        builder.append(fp.getGroupId()).append(":").append(fp.getArtifactId());
        String type = fp.getExtension() == null ? fp.getType() : fp.getExtension();
        if (fp.getClassifier() != null || type != null) {
            builder.append(":").append(fp.getClassifier() == null ? "" : fp.getClassifier()).append(":")
                    .append(type == null ? "" : type);
        }
        if (fp.getVersion() != null) {
            builder.append(":").append(fp.getVersion());
        }
        return builder.toString();
    }

    private static String getWildFlyFeaturePackLocation(String featurePackLocation, String version) {
        StringBuilder fplBuilder = new StringBuilder();
        fplBuilder.append(Objects.requireNonNull(featurePackLocation, "The feature pack location is required."));
        if (version != null) {
            fplBuilder.append("#").append(version);
        }
        return fplBuilder.toString();
    }
}
