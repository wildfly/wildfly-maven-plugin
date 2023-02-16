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

/**
 * This class holds all configuration to build and push application image
 * from the {@code image} goal.
 */
public class ApplicationImageInfo {

    /**
     * Whether the application image should be built (default is {@code true}).
     */
    protected boolean build = true;

    /**
     * Whether the application image should be pushed (default is {@code false}).
     */
    protected boolean push = false;

    /**
     * Determine which WildFly runtime image to use so that the application runs with the specified JDK.
     * If the value is not set, the `latest` tag of the WildFly runtime image is used.
     * Accepted values are "11", "17".
     */
    private String jdkVersion;

    /**
     * The group part of the name of the application image.
     */
    private String group;

    /**
     * The name part of the application image. If not set, the value of the artifactId (in lower case) is used.
     */
    private String name;

    /**
     * The tag part of the application image (default is @{code latest}.
     */
    private String tag = "latest";

    /**
     * The container registry.
     *
     * If set, the registry is added to the application name.
     * If the image is pushed and the registry is not set, it defaults to "docker.io" to login to the registry.
     */
    protected String registry;

    /**
     * The user name to login to the container registry.
     */
    protected String user;

    /**
     * The user password to login to the container registry.
     */
    protected String password;

    /**
     * The binary used to build and push images. If not explicitly set, there will be an attempt to determine the binary
     * to use. The first attempt will be to check the {@code docker} command. If that command is not available,
     * {@code podman} is attempted. If neither is available {@code null} will be set as the default and an error will
     * occur if attempting to build or push an image.
     */
    private String dockerBinary;

    String getApplicationImageName(String artifactId) {
        String registry = this.registry != null ? this.registry + "/"  : "";
        String group = this.group != null ? this.group + "/" : "";
        String imageName = this.name != null ? this.name : artifactId.toLowerCase();
        String tag = this.tag;

        return registry + group + imageName + ":" + tag;
    }

    String getWildFlyRuntimeImage() {
        String runtimeImageName = "quay.io/wildfly/wildfly-runtime:";
        String runtimeImageTag = "";
        if (jdkVersion == null) {
            runtimeImageTag = "latest";
        } else {
            switch (jdkVersion) {
                case "17":
                case "11":
                    runtimeImageTag = "latest-jdk" + jdkVersion;
            }
        }
        return runtimeImageName + runtimeImageTag;
    }

    String getDockerBinary() {
        if (dockerBinary == null) {
            dockerBinary = ExecUtil.resolveImageBinary();
        }
        return dockerBinary;
    }

    /**
     * Sets the {@code binary} if the injected property is not {@code null}.
     *
     * @param dockerBinary the docker binary to use
     */
    void setDockerBinary(final String dockerBinary) {
        if (this.dockerBinary == null) {
            this.dockerBinary = dockerBinary;
        }
    }
}
