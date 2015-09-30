/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.deployment;

import java.io.File;

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.deployment.Deployment.Type;

/**
 * Creates a deployment execution.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class DeploymentBuilder<T extends DeploymentBuilder<? super T>> {

    protected final ModelControllerClient client;
    private File content;
    private String name;
    private String runtimeName;
    private Type type;
    private String matchPattern;
    private MatchPatternStrategy matchPatternStrategy;

    protected DeploymentBuilder(final ModelControllerClient client) {
        if (client == null) {
            throw new IllegalArgumentException("The client must be set to communicate with the server.");
        }
        this.client = client;
    }

    /**
     * Builds the deployment.
     *
     * @return the deployment
     */
    public Deployment build() {
        validate();
        return doBuild();
    }

    /**
     * Gets the content for the deployment which may be {@code null} when this is an undeploy action.
     *
     * @return the content for the deployment or {@code null}
     */
    protected File getContent() {
        return content;
    }

    /**
     * Sets the content to be deployed.
     *
     * @param content the content do be deployed
     *
     * @return this builder
     */
    public T setContent(final File content) {
        this.content = content;
        return getThis();
    }

    protected String getName() {
        return name;
    }

    /**
     * Sets the name for this deployment.
     *
     * @param name the name for this deployment
     *
     * @return this builder
     */
    public T setName(final String name) {
        this.name = name;
        return getThis();
    }

    protected String getRuntimeName() {
        return runtimeName;
    }

    /**
     * Sets the runtime name to use for the deployment.
     *
     * @param runtimeName the deployments runtime name
     *
     * @return this builder
     */
    public T setRuntimeName(final String runtimeName) {
        this.runtimeName = runtimeName;
        return getThis();
    }

    protected Type getType() {
        return type;
    }

    /**
     * Sets the type for the deployment.
     *
     * @param type the deployment type
     *
     * @return this builder
     */
    public T setType(final Type type) {
        this.type = type;
        return getThis();
    }

    protected String getMatchPattern() {
        return matchPattern;
    }

    /**
     * Sets the pattern used for validating multiple deployment matching.
     *
     * @param matchPattern the pattern to use
     *
     * @return this builder
     */
    public T setMatchPattern(final String matchPattern) {
        this.matchPattern = matchPattern;
        return getThis();
    }

    protected MatchPatternStrategy getMatchPatternStrategy() {
        return matchPatternStrategy;
    }

    /**
     * Sets the strategy to use for validating multiple deployment matching.
     *
     * @param matchPatternStrategy the strategy to use
     *
     * @return this builder
     */
    public T setMatchPatternStrategy(final MatchPatternStrategy matchPatternStrategy) {
        this.matchPatternStrategy = matchPatternStrategy;
        return getThis();
    }

    protected abstract T getThis();

    protected abstract Deployment doBuild();

    protected void validate() {
        if (type != Type.UNDEPLOY && type != Type.UNDEPLOY_IGNORE_MISSING && content == null) {
            throw new IllegalStateException("The content to be deployed must be set for for deployments and re-deployments.");
        }
    }
}
