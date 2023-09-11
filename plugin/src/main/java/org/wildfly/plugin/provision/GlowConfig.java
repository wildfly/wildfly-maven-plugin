/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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
    private boolean failsOnError = true;
    private boolean preview;

    public GlowConfig() {
    }

    public Arguments toArguments(Path deployment, Path inProvisioning) {
        final Set<String> profiles = profile != null ? Set.of(profile) : Set.of();
        List<Path> lst = List.of(deployment);
        Builder builder = Arguments.scanBuilder().setExecutionContext(context).setExecutionProfiles(profiles)
                .setUserEnabledAddOns(addOns).setBinaries(lst).setSuggest(suggest).setJndiLayers(getLayersForJndi())
                .setVersion(version)
                .setTechPreview(preview)
                .setOutput(OutputFormat.PROVISIONING_XML);
        if (inProvisioning != null) {
            builder.setProvisoningXML(inProvisioning);
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
}
