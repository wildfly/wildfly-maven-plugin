/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.wildfly.glow.Arguments;
import org.wildfly.glow.OutputFormat;
import org.wildfly.glow.ScanArguments.Builder;

/**
 *
 * @author jdenise
 */
@SuppressWarnings("unused")
public class GlowConfig {

    private String context = "bare-metal";
    private String profile;
    private Set<String> addOns = Set.of();
    private String version;
    private boolean suggest;
    private Set<String> layersForJndi = Set.of();
    private Set<String> excludedArchives = Set.of();
    private boolean failsOnError = true;
    private boolean preview;
    private boolean verbose;

    public GlowConfig() {
    }

    public Arguments toArguments(Path deployment, Path inProvisioning, String layersConfigurationFileName) {
        final Set<String> profiles = profile != null ? Set.of(profile) : Set.of();
        List<Path> lst = List.of(deployment);
        Builder builder = Arguments.scanBuilder().setExecutionContext(context).setExecutionProfiles(profiles)
                .setUserEnabledAddOns(addOns).setBinaries(lst).setSuggest(suggest).setJndiLayers(getLayersForJndi())
                .setVersion(version)
                .setTechPreview(preview)
                .setExcludeArchivesFromScan(excludedArchives)
                .setVerbose(verbose)
                .setOutput(OutputFormat.PROVISIONING_XML);
        if (inProvisioning != null) {
            builder.setProvisoningXML(inProvisioning);
        }
        if (layersConfigurationFileName != null) {
            builder.setConfigName(layersConfigurationFileName);
        }
        return builder.build();
    }

    /**
     * @return the execution context
     */
    public String getContext() {
        return context;
    }

    /**
     * @param context the execution context to set
     */
    public void setContext(String context) {
        this.context = context;
    }

    /**
     * @return the profile
     */
    public String getProfile() {
        return profile;
    }

    /**
     * @param profile the profile to set
     */
    public void setProfile(String profile) {
        this.profile = profile;
    }

    /**
     * @return the userEnabledAddOns
     */
    public Set<String> getAddOns() {
        return addOns;
    }

    /**
     * @param addOns the userEnabledAddOns to set
     */
    public void setAddOns(Set<String> addOns) {
        this.addOns = Set.copyOf(addOns);
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * @return the suggest
     */
    public boolean isSuggest() {
        return suggest;
    }

    /**
     * @param suggest the suggest to set
     */
    public void setSuggest(boolean suggest) {
        this.suggest = suggest;
    }

    /**
     * @return the layersForJndi
     */
    public Set<String> getLayersForJndi() {
        return layersForJndi;
    }

    /**
     * @param layersForJndi the layersForJndi to set
     */
    public void setLayersForJndi(Set<String> layersForJndi) {
        this.layersForJndi = Set.copyOf(layersForJndi);
    }

    /**
     * @return the failsOnError
     */
    public boolean isFailsOnError() {
        return failsOnError;
    }

    /**
     * @param failsOnError the failsOnError to set
     */
    public void setFailsOnError(boolean failsOnError) {
        this.failsOnError = failsOnError;
    }

    /**
     * @param preview the preview to set
     */
    public void setPreview(boolean preview) {
        this.preview = preview;
    }

    /**
     * @return the preview
     */
    public boolean isPreview() {
        return preview;
    }

    /**
     * @return the excludedArchives
     */
    public Set<String> getExcludedArchives() {
        return excludedArchives;
    }

    /**
     * @param excludedArchives the excludedArchives to set
     */
    public void setExcludedArchives(Set<String> excludedArchives) {
        this.excludedArchives = Set.copyOf(excludedArchives);
    }

    /**
     * @param verbose the verbose to set
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * @return the verbose
     */
    public boolean isVerbose() {
        return verbose;
    }
}
