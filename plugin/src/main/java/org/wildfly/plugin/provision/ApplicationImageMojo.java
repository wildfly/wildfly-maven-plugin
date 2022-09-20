/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
import java.util.Arrays;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Build (and push) an application image containing the provisioned server and the deployment.
 * <p>
 * The {@code image} goal extends the {@code package} goal, building and pushing the image occurs after the server
 * is provisioned and the deployment deployed in it.
 * <p>
 * The {@code image} goal relies on a Docker binary to execute all image commands (build, login, push).
 *
 * @since 4.0
 */
@Mojo(name = "image", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class ApplicationImageMojo extends PackageServerMojo {

    public static final int DOCKER_CMD_CHECK_TIMEOUT = 3000;

    /**
     * The configuration of the application image.
     * <p>
     * The {@code image} goal accepts the following configuration:
     * <p>
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
     *   &lt;!-- (optional) the JDK version used by the application. Default is "11", Allowed values are "11" and "17 --&gt;
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
     */
    @Parameter(alias = "image")
    private ApplicationImageInfo image;

    @Override
    protected String getGoal() {
        return "image";
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();

        if (image == null) {
            image = new ApplicationImageInfo();
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

            if (!isDockerBinaryAvailable(image.dockerBinary)) {
                throw new MojoExecutionException(String.format("Unable to build application image with %s. Please check your %s installation",
                        image.dockerBinary, image.dockerBinary));
            }

            String image = this.image.getApplicationImageName(project.getArtifactId());

            boolean buildSuccess = buildApplicationImage(image, runtimeImage);
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
            getLog().info("Registry was not set. Using docker.io");
        }
        if (image.user != null && image.password != null) {
            String[] dockerArgs = new String[] {
                    "login", registry,
                    "-u", image.user,
                    "-p", image.password
            };
            boolean loginSuccessful = ExecUtil.exec(getLog(), image.dockerBinary, dockerArgs);
            if (!loginSuccessful) {
                throw new MojoExecutionException(String.format("Could not log to the container registry with the command %s %s %s",
                        image.dockerBinary,
                        String.join(" ", Arrays.copyOf(dockerArgs, dockerArgs.length -1)),
                        "*******"));
            }
        }
    }

    private boolean buildApplicationImage(String image, String runtimeImage) throws IOException {
        getLog().info(format("Building application image %s using %s.", image, this.image.dockerBinary));
        String[] dockerArgs = new String[] {"build", "-t", image, "."};

        getLog().info(format("Executing the following command to build application image: '%s %s'", this.image.dockerBinary, join(" ", dockerArgs)));
        return ExecUtil.exec(getLog(), Paths.get(project.getBuild().getDirectory()).toFile(), this.image.dockerBinary, dockerArgs);

    }

    private boolean pushApplicationImage(String image) {
        getLog().info(format("Pushing application image %s using %s.", image, this.image.dockerBinary));

        String[] dockerArgs = new String[] {"push", image};

        getLog().info(format("Executing the following command to push application image: '%s %s'", this.image.dockerBinary, join(" ", dockerArgs)));
        return ExecUtil.exec(getLog(), Paths.get("target").toFile(), this.image.dockerBinary, dockerArgs);
    }

    private void generateDockerfile(String runtimeImage, Path targetDir, String wildflyDirectory) throws IOException {
        Files.writeString(targetDir.resolve("Dockerfile"),
                "FROM " + runtimeImage + "\n" +
                        "COPY --chown=jboss:root " + wildflyDirectory + " $JBOSS_HOME\n" +
                        "RUN chmod -R ug+rwX $JBOSS_HOME",
                StandardCharsets.UTF_8);
    }

    private boolean isDockerBinaryAvailable(String dockerBinary) {
        try {
            if (!ExecUtil.execSilentWithTimeout(Duration.ofMillis(DOCKER_CMD_CHECK_TIMEOUT), dockerBinary, "-v")) {

                getLog().warn(format("'%s -v' returned an error code. Make sure your %s binary is correct", dockerBinary, dockerBinary));
                return false;
            }
        } catch (Exception e) {
            getLog().warn(format("No %s binary found or general error: %s", dockerBinary, e));
            return false;
        }

        return true;
    }

}
