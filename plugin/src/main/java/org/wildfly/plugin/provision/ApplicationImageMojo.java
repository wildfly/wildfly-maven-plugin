/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import static com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer.DEFAULT_MODIFICATION_TIME_PROVIDER;
import static java.lang.String.format;
import static java.lang.String.join;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.core.Constants;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.FilePermissionsProvider;
import com.google.cloud.tools.jib.api.buildplan.OwnershipProvider;

/**
 * Build (and push) an application image containing the provisioned server and the deployment.
 * <p>
 * The {@code image} goal extends the {@code package} goal, building and pushing the image occurs after the server
 * is provisioned and the deployment deployed in it.
 * <p>
 *
 * <p>
 * Note that if a WildFly Bootable JAR is packaged, it is ignored when building the image.
 * </p>
 *
 * @since 4.0
 */
@Mojo(name = "image", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
@SuppressWarnings({ "deprecated", "removal" })
public class ApplicationImageMojo extends PackageServerMojo {

    /**
     * Provides a reference to the settings file.
     */
    @Parameter(property = "settings", readonly = true, required = true, defaultValue = "${settings}")
    private Settings settings;

    @Inject
    private SettingsDecrypter settingsDecrypter;

    /**
     * <p>
     * <strong>NOTE: If this is used the other parameters for this goal will be ignored.</strong>
     * </p>
     * The configuration of the application image.
     * <p>
     * The {@code image} goal accepts the following configuration:
     * <p>
     *
     * <pre>
     * &lt;image&gt;
     *   &lt;!-- (optional) set it to false to skip build the application image (true by default) --&gt;
     *   &lt;build&gt;true&lt;/build&gt;
     *
     *   &lt;!-- (optional) set it to true to (login and) push the application image to the container registry (false by default).
     *
     *     If user and password are not specified, the image goal will not attempt to login to the container
     *     registry prior to pushing the image.
     *     The login to the container registry must then be performed before Maven is run.
     *     --&gt;
     *   &lt;push&gt;true&lt;/push&gt;
     *
     *   &lt;!-- (optional) the JDK version used by the application. Allowed values are "11", "17", and "21". If unspecified, the "latest" tag is used to determine the JDK version used by WildFly runtime image --&gt;
     *   &lt;jdk-version&gt;11&lt;/jdk-version&gt;
     *
     *   &lt;!-- (optional) The group part of the name of the application image --&gt;
     *   &lt;group&gt;${user.name}&lt;/group&gt;
     *
     *   &lt;!-- (optional) The name part of the application image. If not set, the value of the artifactId (in lower case) is used --&gt;
     *   &lt;name&gt;${project.artifactId}&lt;/name&gt;
     *
     *   &lt;!-- (optional) The tag part of the application image (default is "latest") --&gt;
     *   &lt;tag&gt;latest&lt;/tag&gt;
     *
     *   &lt;!-- (optional) The container registry. If set, the registry is added to the application name.
     *     If the image is pushed and the registry is not set, it defaults to "docker.io" to login to the registry
     *     --&gt;
     *   &lt;registry&gt;quay.io&lt;/registry&gt;
     *
     *   &lt;!-- (optional) The user name to login to the container registry (if push is enabled). --&gt;
     *   &lt;user&gt;${user.name}&lt;/user&gt;
     *
     *   &lt;!-- (optional) The password login to the container registry (if push is enabled) --&gt;
     *   &lt;password&gt;${my.secret.password}&lt;/password&gt;
     * &lt;/image&gt;
     * </pre>
     *
     * @deprecated 5.0.1 use the matching configuration parameters instead of this complex object. The simple migration is
     *                 to remove the surrounding {@code <image>} tags. The one exception is {@code <name>} needs to
     *                 change to {@code <image-name>}
     */
    @Parameter(alias = "image")
    @Deprecated(forRemoval = true, since = "5.0.1")
    private ApplicationImageInfo image;

    /**
     * Whether the application image should be built (default is {@code true}).
     *
     * @since 5.0.1
     */
    @Parameter(defaultValue = "true", property = "wildfly.image.build")
    private boolean build;

    /**
     * Whether the application image should be pushed.
     *
     * @since 5.0.1
     */
    @Parameter(defaultValue = "false", property = "wildfly.image.push")
    private boolean push;

    /**
     * Determine which WildFly runtime image to use so that the application runs with the specified JDK. Note this must
     * be a valid JDK version.
     *
     * @since 5.0.1
     */
    @Parameter(alias = "jdk-version", property = "wildfly.image.jdk.version")
    private String jdkVersion;

    /**
     * The group part of the name of the application image.
     *
     * @since 5.0.1
     */
    @Parameter(property = "wildfly.image.group")
    private String group;

    /**
     * The name part of the application image. If not set, the value of the artifactId (in lower case) is used.
     *
     * @since 5.0.1
     */
    @Parameter(alias = "image-name", property = "wildfly.image.name")
    private String imageName;

    /**
     * The tag part of the application image (default is @{code latest}.
     *
     * @since 5.0.1
     */
    @Parameter(defaultValue = "latest", property = "wildfly.image.tag")
    private String tag;

    /**
     * The container registry.
     * <p>
     * If set, the registry is added to the application name. If the image is pushed and the registry is not set, it
     * defaults to the registry defined in your docker configuration.
     * </p>
     *
     * @since 5.0.1
     */
    @Parameter(property = "wildfly.image.registry")
    private String registry;

    /**
     * Specifies the id of the registry server for the username and password to be retrieved from the {@code settings.xml}
     * file.
     *
     * @since 5.0.1
     */
    @Parameter(alias = "registry-id", property = "wildfly.image.registry.id")
    private String registryId;

    /**
     * The username to login to the container registry.
     *
     * @since 5.0.1
     */
    @Parameter(property = "wildfly.image.user")
    private String user;

    /**
     * The user password to login to the container registry.
     *
     * @since 5.0.1
     */
    @Parameter
    private String password;

    /**
     * The binary used to build and push images. If not explicitly set, there will be an attempt to determine the binary
     * to use. The first attempt will be to check the {@code docker} command. If that command is not available,
     * {@code podman} is attempted. If neither is available an error will occur if attempting to build or push an image.
     *
     * @since 5.0.1
     */
    @Deprecated(forRemoval = true, since = "5.0.2")
    @Parameter(alias = "docker-binary", property = PropertyNames.WILDFLY_IMAGE_BINARY)
    private String dockerBinary;

    /**
     * The list of architectures of the images (If not set, it defaults to {@code amd64,arm64}).
     *
     * Multiple architectures does not work with Docker or Podman.
     * To build multiarch images, you must specify a registry and push the image ({@code registry} must be set and {@code push}
     * must be true).
     * Only `linux` operating system is supported.
     *
     * @since 5.0.1
     */
    @Parameter(alias = "archs", property = PropertyNames.WILDFLY_IMAGE_ARCHS)
    private String archs = "amd64,arm64";

    /**
     * Adds labels to the generated Dockerfile. Each label will be added as a new line with the prefix of {@code LABEL}.
     * For example:
     *
     * <pre>
     * &lt;labels&gt;
     *     &lt;version&gt;1.0&lt;/version&gt;
     *     &lt;description&gt;This is only for testing purposes, \
     * do not deploy&lt;/description&gt;
     * &lt;/labels&gt;
     * </pre>
     *
     * Will generate:
     *
     * <pre>
     * LABEL description="This is only for testing purposes, \
     * do not deploy"
     * LABEL version="1.0"
     * </pre>
     * <p>
     * The map is always sorted by the key and the values are always wrapped in quotes and quotes within the value are
     * escaped.
     * </p>
     *
     * @since 5.0.1
     */
    @Parameter(property = "wildfly.image.labels")
    private Map<String, String> labels;

    @Override
    protected String getGoal() {
        return "image";
    }

    private static final FilePermissionsProvider PERMISSIONS_PROVIDER = (sourcePath, destinationPath) -> FilePermissions
            .fromOctalString("775");

    private static final OwnershipProvider JBOSS_ROOT_OWNER = (sourcePath, destinationPath) -> "jboss:root";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // when the application image is built, the deployment step is skipped.
        // This allows to create 2 different Docker layers (1 for the server and 1 for the deployments)
        this.skipDeployment = true;

        super.execute();

        if (image == null) {
            image = new ApplicationImageInfo();
            image.build = this.build;
            image.dockerBinary = this.dockerBinary;
            image.group = this.group;
            image.jdkVersion = this.jdkVersion;
            image.name = this.imageName;
            image.push = this.push;
            image.registry = this.registry;
            image.tag = this.tag;
            if (user == null && settings != null && registryId != null) {
                final Server server = settings.getServer(registryId);
                if (server != null) {
                    image.user = server.getUsername();
                    image.password = decrypt(server);
                } else {
                    getLog().warn(String.format("No server %s found in settings.", registryId));
                    image.user = this.user;
                    image.password = this.password;
                }
            } else {
                image.user = this.user;
                image.password = this.password;
            }
        } else {
            getLog().warn(
                    "You are using the deprecated image parameter. The image parameters will be ignored if defined outside of the image tag.");
        }

        try {
            if (!image.build) {
                return;
            }

            String runtimeImage = this.image.getWildFlyRuntimeImage();
            String image = this.image.getApplicationImageName(project.getArtifactId());

            Path serverDir = Paths.get(project.getBuild().getDirectory(), provisioningDir);
            // FIXME we should continue to support images built with docker/podman
            boolean buildSuccess = buildApplicationImageWithJib(image, runtimeImage, archs, serverDir, getDeploymentContent());
            if (!buildSuccess) {
                throw new MojoExecutionException(String.format("Unable to build application image %s", image));
            }
            getLog().info(String.format("Successfully built application image %s", image));
        } catch (IOException e) {
            throw new MojoExecutionException(e.getLocalizedMessage(), e);
        }
    }

    private boolean buildApplicationImageWithDocker(String image) throws IOException {
        getLog().info(format("Building application image %s using %s.", image, this.image.getDockerBinary()));
        String[] dockerArgs = new String[] { "build", "-t", image, "." };

        getLog().info(format("Executing the following command to build application image: '%s %s'",
                this.image.getDockerBinary(), join(" ", dockerArgs)));
        return ExecUtil.exec(getLog(), Paths.get(project.getBuild().getDirectory()).toFile(), this.image.getDockerBinary(),
                dockerArgs);

    }

    private boolean buildApplicationImageWithJib(String image, String runtimeImage, String archs, Path wildflyDirectory,
            Path deployment)
            throws IOException {

        getLog().info(format("Building application image %s.", image));
        try {
            ImageReference imageRef = ImageReference.parse(image);
            Containerizer containerizer;

            if (push) {
                RegistryImage registryImage = RegistryImage.named(image);
                if (user != null && password != null) {
                    registryImage.addCredential(user, password);
                }
                containerizer = Containerizer.to(registryImage);
            } else {
                DockerDaemonImage dockerDaemon = DockerDaemonImage.named(imageRef);
                containerizer = Containerizer.to(dockerDaemon);
            }

            FileEntriesLayer serverLayer = FileEntriesLayer.builder()
                    .setName("wildfly")
                    .addEntryRecursive(wildflyDirectory, AbsoluteUnixPath.get("/opt/server/"),
                            PERMISSIONS_PROVIDER,
                            DEFAULT_MODIFICATION_TIME_PROVIDER,
                            JBOSS_ROOT_OWNER)
                    .build();

            FileEntriesLayer deploymentLayer = FileEntriesLayer.builder()
                    .setName("deployment")
                    .addEntryRecursive(deployment, AbsoluteUnixPath.get("/opt/server/standalone/deployments"),
                            PERMISSIONS_PROVIDER,
                            DEFAULT_MODIFICATION_TIME_PROVIDER,
                            JBOSS_ROOT_OWNER)
                    .build();

            JibContainerBuilder builder = Jib.from(runtimeImage);

            final List<String> serverArgs = new ArrayList<>();
            if (!layers.isEmpty() && !layersConfigurationFileName.equals(Constants.STANDALONE_XML)) {
                serverArgs.add("-c=" + layersConfigurationFileName);
            } else if (!serverConfig.equals(Constants.STANDALONE_XML)) {
                serverArgs.add("-c=" + serverConfig);
            }

            if (!serverArgs.isEmpty()) {
                builder.addEnvironmentVariable("SERVER_ARGS", String.join(",", serverArgs));
            }

            builder.addFileEntriesLayer(serverLayer)
                    .addFileEntriesLayer(deploymentLayer)
                    .setLabels(labels);
            if (archs != null) {
                for (String arch : archs.split(",")) {
                    builder.addPlatform(arch.trim(), "linux");
                }
            }

            builder.containerize(containerizer);

            return true;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private String decrypt(final Server server) {
        SettingsDecryptionResult decrypt = settingsDecrypter.decrypt(new DefaultSettingsDecryptionRequest(server));
        return decrypt.getServer().getPassword();
    }
}
