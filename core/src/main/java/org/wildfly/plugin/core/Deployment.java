/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.plugin.core;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.wildfly.common.Assert;

/**
 * Represents a deployment to be deployed or redeployed to a server.
 * <p>
 * Instances of this are not thread-safe.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({ "unused", "WeakerAccess" })
public class Deployment implements DeploymentDescription, Comparable<Deployment> {

    private final DeploymentContent content;
    private final Set<String> serverGroups;
    private String name;
    private String runtimeName;
    private boolean enabled = true;

    private Deployment(final DeploymentContent content, final String name) {
        this.content = content;
        this.name = Assertions.requiresNotNullOrNotEmptyParameter("name", (name == null ? this.content.resolvedName() : name));
        serverGroups = new LinkedHashSet<>();
    }

    /**
     * Creates a new deployment for the file. If the file is a directory the content will be deployed exploded using
     * the file system location.
     *
     * @param content the file containing the content
     *
     * @return the deployment
     */
    public static Deployment of(final File content) {
        final DeploymentContent deploymentContent = DeploymentContent.of(Assert.checkNotNullParam("content", content).toPath());
        return new Deployment(deploymentContent, null);
    }

    /**
     * Creates a new deployment for the path. If the path is a directory the content will be deployed exploded using
     * the file system location.
     *
     * @param content the path containing the content
     *
     * @return the deployment
     */
    public static Deployment of(final Path content) {
        final DeploymentContent deploymentContent = DeploymentContent.of(Assert.checkNotNullParam("content", content));
        return new Deployment(deploymentContent, null);
    }

    /**
     * Creates a new deployment for the input stream. The name is required for the deployment and cannot be {@code null}.
     * If {@link #setName(String)} with a {@code null} argument is invoked when using this static factory an
     * {@link IllegalArgumentException} will be thrown.
     * <p>
     * The {@linkplain InputStream content} will be copied, stored in-memory and then closed. Large content should be
     * written to a file and the {@link #of(Path)} or {@link #of(File)} static factory methods should be used.
     * </p>
     *
     * @param content the input stream representing the content
     * @param name    the name for the deployment
     *
     * @return the deployment
     */
    public static Deployment of(final InputStream content, final String name) {
        final DeploymentContent deploymentContent = DeploymentContent.of(Assert.checkNotNullParam("content", content));
        return new Deployment(deploymentContent, Assertions.requiresNotNullOrNotEmptyParameter("name", name));
    }

    /**
     * Creates a new deployment for the URL. The target server will require access to the URL.
     *
     * @param url the URL representing the content
     *
     * @return the deployment
     */
    public static Deployment of(final URL url) {
        final DeploymentContent deploymentContent = DeploymentContent.of(Assert.checkNotNullParam("url", url));
        return new Deployment(deploymentContent, null);
    }

    /**
     * Creates a new deployment for the path. If the path is a directory the content will be deployed exploded using
     * the file system location. Otherwise, the content is deployed with the local path as an archive.
     *
     * @param content the path containing the content
     *
     * @return the deployment
     */
    public static Deployment local(final Path content) {
        final DeploymentContent deploymentContent = DeploymentContent.local(Assert.checkNotNullParam("content", content));
        return new Deployment(deploymentContent, null);
    }

    /**
     * Adds a server group for the deployment.
     *
     * @param serverGroup the server group to add
     *
     * @return this deployment
     *
     * @see #addServerGroups(Collection)
     * @see #addServerGroups(String...)
     */
    public Deployment addServerGroup(final String serverGroup) {
        serverGroups.add(serverGroup);
        return this;
    }

    /**
     * Adds the server groups for the deployment.
     *
     * @param serverGroups the server groups to add
     *
     * @return this deployment
     *
     * @see #addServerGroup(String)
     * @see #addServerGroups(Collection)
     */
    public Deployment addServerGroups(final String... serverGroups) {
        return addServerGroups(Arrays.asList(serverGroups));
    }

    /**
     * Adds the server groups for the deployment.
     *
     * @param serverGroups the server groups to add
     *
     * @return this deployment
     *
     * @see #addServerGroup(String)
     * @see #addServerGroups(String...)
     */
    public Deployment addServerGroups(final Collection<String> serverGroups) {
        this.serverGroups.addAll(serverGroups);
        return this;
    }

    @Override
    public Set<String> getServerGroups() {
        return Collections.unmodifiableSet(serverGroups);
    }

    /**
     * Sets the server groups for the deployment.
     *
     * @param serverGroups the server groups to set
     *
     * @return this deployment
     */
    public Deployment setServerGroups(final String... serverGroups) {
        return setServerGroups(Arrays.asList(serverGroups));
    }

    /**
     * Sets the server groups for the deployment.
     *
     * @param serverGroups the server groups to set
     *
     * @return this deployment
     */
    public Deployment setServerGroups(final Collection<String> serverGroups) {
        this.serverGroups.clear();
        this.serverGroups.addAll(serverGroups);
        return this;
    }

    /**
     * Indicates whether or not the deployment should be enabled by default.
     * <p>
     * If the value is set to {@code false} the content will only be added. An explicit {@code deploy} operation will
     * be required to deploy the content to the runtime.
     * </p>
     *
     * @return {@code true} if the deployment should be enabled, {@code false} if the deployment should not be enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether or not the deployment should be enabled. If set to {@code false} the deployment will not be enabled,
     * but the content will be uploaded and added. This is set to {@code true} by default.
     * <p>
     * If the value is set to {@code false} the content will only be added. An explicit {@code deploy} operation will
     * be required to deploy the content to the runtime.
     * </p>
     *
     * @param enabled {@code false} to keep the content disabled
     *
     * @return this deployment
     */
    public Deployment setEnabled(final boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Sets the name for the deployment. This can be {@code null} for a deployment created based on a file system path.
     * If the deployment was created using in input stream and the value is {@code null} an {@link IllegalArgumentException}
     * will be thrown.
     *
     * @param name the name for the deployment
     *
     * @return this deployment
     */
    public Deployment setName(final String name) {
        if (name == null) {
            this.name = content.resolvedName();
            if (this.name == null) {
                throw new IllegalArgumentException("The name parameter is required and could not be resolved from the content: "
                        + content);
            }
        } else {
            this.name = name;
        }
        return this;
    }

    /**
     * Returns the runtime name set for the deployment which may be {@code null}.
     *
     * @return the runtime name set or {@code null} if one was not set
     */
    public String getRuntimeName() {
        return runtimeName;
    }

    /**
     * Sets the runtime name for the deployment.
     *
     * @param runtimeName the runtime name, can be {@code null}
     *
     * @return this deployment
     */
    public Deployment setRuntimeName(final String runtimeName) {
        this.runtimeName = runtimeName;
        return this;
    }

    @Override
    public int compareTo(@SuppressWarnings("NullableProblems") final Deployment o) {
        return name.compareTo(o.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Deployment)) {
            return false;
        }
        final Deployment other = (Deployment) obj;
        return Objects.equals(name, other.name);
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder(Deployment.class.getSimpleName());
        result.append('(');
        result.append("name=").append(name);
        if (runtimeName != null) {
            result.append(", runtimeName=").append(runtimeName);
        }
        if (!serverGroups.isEmpty()) {
            result.append(", serverGroups=").append(serverGroups);
        }
        result.append(", content=").append(content);
        return result.append(')').toString();
    }

    DeploymentContent getContent() {
        return content;
    }
}
