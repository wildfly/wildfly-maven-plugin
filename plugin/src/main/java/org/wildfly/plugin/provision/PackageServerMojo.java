/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.wildfly.glow.ScanResults;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.Utils;
import org.wildfly.plugin.tools.bootablejar.BootableJarSupport;

/**
 * Provision a server, copy extra content and deploy primary artifact if it
 * exists
 *
 * @author jfdenise
 * @since 3.0
 */
@Mojo(name = "package", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class PackageServerMojo extends AbstractPackageServerMojo {

    public static final String JAR = "jar";
    public static final String BOOTABLE_JAR_NAME_RADICAL = "server-";

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
     * <li>context: {@code bare-metal} or (@code cloud}. Default to {@code bare-metal}</li>
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
     * @since 5.0
     */
    @Parameter(alias = "discover-provisioning-info")
    protected GlowConfig discoverProvisioningInfo;

    /**
     * Package the provisioned server into a WildFly Bootable JAR.
     * <p>
     * Note that the produced fat JAR is ignored when running the {@code dev},{@code image},{@code start} or {@code run} goals.
     * </p>
     */
    @Parameter(alias = "bootable-jar", required = false, property = PropertyNames.BOOTABLE_JAR)
    protected boolean bootableJar;

    /**
     * When {@code bootable-jar} is set to true, use this parameter to name the generated jar file.
     * The jar file is named by default {@code server-bootable.jar}.
     */
    @Parameter(alias = "bootable-jar-name", required = false, property = PropertyNames.BOOTABLE_JAR_NAME)
    protected String bootableJarName;

    /**
     * When {@code bootable-jar} is set to true, the bootable JAR artifact is attached to the project with the classifier
     * 'bootable'. Use this parameter to
     * configure the classifier.
     */
    @Parameter(alias = "bootable-jar-install-artifact-classifier", property = PropertyNames.BOOTABLE_JAR_INSTALL_CLASSIFIER, defaultValue = BootableJarSupport.BOOTABLE_SUFFIX)
    protected String bootableJarInstallArtifactClassifier;

    private GalleonProvisioningConfig config;

    @Override
    protected GalleonProvisioningConfig buildGalleonConfig(GalleonBuilder pm)
            throws MojoExecutionException, ProvisioningException {
        if (discoverProvisioningInfo == null) {
            config = super.buildGalleonConfig(pm);
            return config;
        }
        try {
            try (ScanResults results = Utils.scanDeployment(discoverProvisioningInfo,
                    layers,
                    excludedLayers,
                    featurePacks,
                    dryRun,
                    getLog(),
                    getDeploymentContent(),
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
    protected void serverProvisioned(Path jbossHome) throws MojoExecutionException, MojoFailureException {
        super.serverProvisioned(jbossHome);
        try {
            if (bootableJar) {
                Utils.packageBootableJar(jbossHome,
                        config, getLog(),
                        artifactResolver,
                        project,
                        projectHelper,
                        bootableJarInstallArtifactClassifier,
                        bootableJarName);
            }
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
    }
}
