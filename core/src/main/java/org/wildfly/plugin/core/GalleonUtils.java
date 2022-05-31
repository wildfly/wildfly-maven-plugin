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
package org.wildfly.plugin.core;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.galleon.Constants;
import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import static org.wildfly.plugin.core.Constants.FORK_EMBEDDED_PROCESS_OPTION;
import static org.wildfly.plugin.core.Constants.STANDALONE;

/**
 * @author jdenise
 */
public class GalleonUtils {

    private static final String WILDFLY_DEFAULT_FEATURE_PACK_LOCATION = "wildfly@maven(org.jboss.universe:community-universe)";

    /**
     * Galleon provisioning of a default server.
     *
     * @param jbossHome Server installation directory
     * @param version WildFly version, if null latest is used.
     * @param artifactResolver Artifact resolver used by Galleon
     * @throws ProvisioningException
     */
    public static void provision(Path jbossHome, String version, MavenRepoManager artifactResolver) throws ProvisioningException {
        try (ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(artifactResolver)
                .setInstallationHome(jbossHome)
                .build()) {
            pm.provision(buildDefaultConfig(version));
        }
    }

    /**
     * Build a default WildFly provisioning config.
     *
     * @return
     * @throws ProvisioningDescriptionException
     */
    public static ProvisioningConfig buildDefaultConfig() throws ProvisioningDescriptionException {
        return buildDefaultConfig(null);
    }

    /**
     * Build a default WildFly provisioning config.
     *
     * @param version WildFly version, if null latest is used.
     * @return
     * @throws ProvisioningDescriptionException
     */
    public static ProvisioningConfig buildDefaultConfig(String version) throws ProvisioningDescriptionException {
        String location = getWildFlyFeaturePackLocation(version);
        ProvisioningConfig.Builder state = ProvisioningConfig.builder();
        FeaturePackLocation fpl = FeaturePackLocation.fromString(location);
        FeaturePackConfig.Builder fpConfig = FeaturePackConfig.builder(fpl);
        fpConfig.setInheritConfigs(true);
        fpConfig.setInheritPackages(true);
        state.addFeaturePackDep(fpConfig.build());
        Map<String, String> options = new HashMap<>();
        options.put(FORK_EMBEDDED_PROCESS_OPTION, "true");
        state.addOptions(options);
        return state.build();
    }

    /**
     * Build a Galleon provisioning configuration based on a provisioning.xml
     * file.
     *
     * @param provisioningFile
     * @return The provisioning config.
     * @throws ProvisioningException
     */
    public static ProvisioningConfig buildConfig(Path provisioningFile) throws ProvisioningException {
        return ProvisioningXmlParser.parse(provisioningFile);
    }

    /**
     * Build a Galleon provisioning configuration.
     *
     * @param pm The Galleon provisioning runtime.
     * @param featurePacks The list of feature-packs.
     * @param layers Layers to include.
     * @param excludedLayers Layers to exclude.
     * @param pluginOptions Galleon plugin options.
     * @param layersConfigFileName The name of the configuration generated from layers
     * @return The provisioning config.
     * @throws ProvisioningException
     */
    public static ProvisioningConfig buildConfig(ProvisioningManager pm,
            List<FeaturePack> featurePacks,
            List<String> layers,
            List<String> excludedLayers,
            Map<String, String> pluginOptions, String layersConfigFileName) throws ProvisioningException, IllegalArgumentException {
        final ProvisioningConfig.Builder state = ProvisioningConfig.builder();
        boolean hasLayers = !layers.isEmpty();
        boolean fpWithDefaults = true;
        if (!hasLayers) {
            // Check we have all feature-packs with default values only.
            for (FeaturePack fp : featurePacks) {
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

        for (FeaturePack fp : featurePacks) {
            if (fp.getLocation() == null && (fp.getGroupId() == null || fp.getArtifactId() == null)
                    && fp.getNormalizedPath() == null) {
                throw new IllegalArgumentException("Feature-pack location, Maven GAV or feature pack path is missing");
            }

            final FeaturePackLocation fpl;
            if (fp.getNormalizedPath() != null) {
                fpl = pm.getLayoutFactory().addLocal(fp.getNormalizedPath(), false);
            } else if (fp.getGroupId() != null && fp.getArtifactId() != null) {
                String coords = getMavenCoords(fp);
                fpl = FeaturePackLocation.fromString(coords);
            } else {
                //Special case for G:A that conflicts with producer:channel that we can't have in the plugin.
                String location = fp.getLocation();
                if (!FeaturePackLocation.fromString(location).hasUniverse()) {
                    long numSeparators = location.chars().filter(ch -> ch == ':').count();
                    if (numSeparators <= 1) {
                        location += ":";
                    }
                }
                fpl = FeaturePackLocation.fromString(location);
            }

            final FeaturePackConfig.Builder fpConfig = fp.isTransitive() ? FeaturePackConfig.transitiveBuilder(fpl)
                    : FeaturePackConfig.builder(fpl);
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
            ConfigModel.Builder configBuilder = ConfigModel.
                    builder(STANDALONE, layersConfigFileName);
            for (String layer : layers) {
                configBuilder.includeLayer(layer);
            }
            for (String layer : excludedLayers) {
                configBuilder.excludeLayer(layer);
            }
            state.addConfig(configBuilder.build());
            if (pluginOptions.isEmpty()) {
                pluginOptions = Collections.
                        singletonMap(Constants.OPTIONAL_PACKAGES, Constants.PASSIVE_PLUS);
            } else if (!pluginOptions.containsKey(Constants.OPTIONAL_PACKAGES)) {
                pluginOptions.put(Constants.OPTIONAL_PACKAGES, Constants.PASSIVE_PLUS);
            }
        }

        state.addOptions(pluginOptions);

        return state.build();
    }

    private static String getMavenCoords(FeaturePack fp) {
        StringBuilder builder = new StringBuilder();
        builder.append(fp.getGroupId()).append(":").append(fp.getArtifactId());
        String type = fp.getExtension() == null ? fp.getType() : fp.getExtension();
        if (fp.getClassifier() != null || type != null) {
            builder.append(":").append(fp.getClassifier() == null ? "" : fp.getClassifier()).append(":").append(type == null ? "" : type);
        }
        if (fp.getVersion() != null) {
            builder.append(":").append(fp.getVersion());
        }
        return builder.toString();
    }

    private static String getWildFlyFeaturePackLocation(String version) {
        StringBuilder fplBuilder = new StringBuilder();
        fplBuilder.append(WILDFLY_DEFAULT_FEATURE_PACK_LOCATION);
        if (version != null) {
            fplBuilder.append("#").append(version);
        }
        return fplBuilder.toString();
    }
}
