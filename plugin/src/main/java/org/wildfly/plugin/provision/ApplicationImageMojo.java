/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import static java.lang.String.format;
import static java.lang.String.join;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
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

/**
 * Build (and push) an application image containing the provisioned server and the deployment.
 * <p>
 * The {@code image} goal extends the {@code package} goal, building and pushing the image occurs after the server
 * is provisioned and the deployment deployed in it.
 * <p>
 * The {@code image} goal relies on a Docker binary to execute all image commands (build, login, push).
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

    public static final int DOCKER_CMD_CHECK_TIMEOUT = 3000;

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
     *   &lt;!-- (optional) The binary used to perform image commands (build, login, push) (default is "docker") --&gt;
     *   &lt;docker-binary&gt;docker&lt;/docker-binary&gt;
     *
     *   &lt;!-- (optional) the JDK version used by the application. Allowed values are "11" and "17". If unspecified, the "latest" tag is used to determine the JDK version used by WildFly runtime image --&gt;
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
    @Parameter(alias = "docker-binary", property = PropertyNames.WILDFLY_IMAGE_BINARY)
    private String dockerBinary;

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
            // The Dockerfile is always generated when the image goal is run.
            // This allows the user to then use the generated Dockerfile in other contexts than Maven.
            String runtimeImage = this.image.getWildFlyRuntimeImage();
            getLog().info(format("Generating Dockerfile %s from base image %s",
                    Paths.get(project.getBuild().getDirectory()).resolve("Dockerfile"),
                    runtimeImage));
            generateDockerfile(runtimeImage, Paths.get(project.getBuild().getDirectory()), provisioningDir);

            if (!image.build) {
                return;
            }
            // Check if the binary was set via a property
            if (image.dockerBinary == null) {
                image.setDockerBinary(project.getProperties().getProperty(PropertyNames.WILDFLY_IMAGE_BINARY,
                        System.getProperty(PropertyNames.WILDFLY_IMAGE_BINARY)));
            }

            final String imageBinary = image.getDockerBinary();
            if (imageBinary == null) {
                throw new MojoExecutionException("Could not locate a binary to build the image with. Please check your " +
                        "installation and either set the path to the binary in your PATH environment variable or define the " +
                        "define the fully qualified path in your configuration, <docker-binary>/path/to/docker</docker-binary>. "
                        +
                        "The path can also be defined with the -Dwildfly.image.binary=/path/to/docker system property.");
            }
            if (!isImageBinaryAvailable(imageBinary)) {
                throw new MojoExecutionException(
                        String.format("Unable to build application image with %1$s. Please check your %1$s installation",
                                imageBinary));
            }

            String image = this.image.getApplicationImageName(project.getArtifactId());

            boolean buildSuccess = buildApplicationImage(image);
            if (!buildSuccess) {
                throw new MojoExecutionException(String.format("Unable to build application image %s", image));
            }
            getLog().info(String.format("Successfully built application image %s", image));

            if (this.image.push) {
                logToRegistry();

                boolean pushSuccess = pushApplicationImage(image);
                if (!pushSuccess) {
                    throw new MojoExecutionException(String.format("Unable to push application image %s", image));
                }
                getLog().info(String.format("Successfully pushed application image %s", image));
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getLocalizedMessage(), e);
        }
    }

    private void logToRegistry() throws MojoExecutionException {
        String registry = image.registry;
        if (registry == null) {
            getLog().info(String.format("Registry was not set. Using default for %s.", image.getDockerBinary()));
        }
        if (image.user != null && image.password != null) {
            String[] dockerArgs = new String[] {
                    "login", registry,
                    "-u", image.user,
                    "-p", image.password
            };
            boolean loginSuccessful = ExecUtil.exec(getLog(), image.getDockerBinary(), dockerArgs);
            if (!loginSuccessful) {
                throw new MojoExecutionException(
                        String.format("Could not log to the container registry with the command: %s login %s -u %s -p *****",
                                image.getDockerBinary(), registry, image.user));
            }
        }
    }

    private boolean buildApplicationImage(String image) throws IOException {
        getLog().info(format("Building application image %s using %s.", image, this.image.getDockerBinary()));
        String[] dockerArgs = new String[] { "build", "-t", image, "." };

        getLog().info(format("Executing the following command to build application image: '%s %s'",
                this.image.getDockerBinary(), join(" ", dockerArgs)));
        return ExecUtil.exec(getLog(), Paths.get(project.getBuild().getDirectory()).toFile(), this.image.getDockerBinary(),
                dockerArgs);

    }

    private boolean pushApplicationImage(String image) {
        getLog().info(format("Pushing application image %s using %s.", image, this.image.getDockerBinary()));

        String[] dockerArgs = new String[] { "push", image };

        getLog().info(format("Executing the following command to push application image: '%s %s'", this.image.getDockerBinary(),
                join(" ", dockerArgs)));
        return ExecUtil.exec(getLog(), Paths.get(project.getBuild().getDirectory()).toFile(), this.image.getDockerBinary(),
                dockerArgs);
    }

    private void generateDockerfile(String runtimeImage, Path targetDir, String wildflyDirectory)
            throws IOException, MojoExecutionException {

        Path jbossHome = Path.of(wildflyDirectory);
        // Docker requires the source file be relative to the context directory. From the documentation:
        // The <src> path must be inside the context of the build; you cannot COPY ../something /something, because
        // the first step of a docker build is to send the context directory (and subdirectories) to the docker daemon.
        if (jbossHome.isAbsolute()) {
            jbossHome = targetDir.relativize(jbossHome);
        }

        String targetName = getDeploymentTargetName();

        // Create the Dockerfile content
        final StringBuilder dockerfileContent = new StringBuilder();
        dockerfileContent.append("FROM ").append(runtimeImage).append('\n');
        if (labels != null) {
            labels.forEach(
                    (key, value) -> dockerfileContent.append("LABEL ").append(key).append("=\"")
                            .append(value.replace("\"", "\\\"")).append("\"\n"));
        }
        dockerfileContent.append("COPY --chown=jboss:root ").append(jbossHome).append(" $JBOSS_HOME\n")
                .append("RUN chmod -R ug+rwX $JBOSS_HOME\n")
                .append("COPY --chown=jboss:root ").append(getDeploymentContent().getFileName())
                .append(" $JBOSS_HOME/standalone/deployments/").append(targetName);

        final List<String> serverArgs = new ArrayList<>();
        if (!layers.isEmpty() && !layersConfigurationFileName.equals(Constants.STANDALONE_XML)) {
            serverArgs.add("-c=" + layersConfigurationFileName);
        } else if (!serverConfig.equals(Constants.STANDALONE_XML)) {
            serverArgs.add("-c=" + serverConfig);
        }

        if (!serverArgs.isEmpty()) {
            dockerfileContent.append('\n').append("ENV SERVER_ARGS=\"").append(String.join(",", serverArgs)).append('"');
        }

        Files.writeString(targetDir.resolve("Dockerfile"), dockerfileContent, StandardCharsets.UTF_8);
    }

    private boolean isImageBinaryAvailable(String imageBinary) {
        try {
            if (!ExecUtil.execSilentWithTimeout(Duration.ofMillis(DOCKER_CMD_CHECK_TIMEOUT), imageBinary, "-v")) {

                getLog().warn(format("'%1$s -v' returned an error code. Make sure your %1$s binary is correct", imageBinary));
                return false;
            }
        } catch (Exception e) {
            getLog().warn(format("No %s binary found or general error: %s", imageBinary, e));
            return false;
        }

        return true;
    }

    private String decrypt(final Server server) {
        SettingsDecryptionResult decrypt = settingsDecrypter.decrypt(new DefaultSettingsDecryptionRequest(server));
        return decrypt.getServer().getPassword();
    }
}
