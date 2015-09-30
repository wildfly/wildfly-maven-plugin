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

package org.wildfly.plugin.deployment.domain;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.wildfly.plugin.deployment.Deployment;
import org.wildfly.plugin.deployment.DeploymentBuilder;

/**
 * Creates a builder for the creation of a deployment for a domain server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DomainDeploymentBuilder extends DeploymentBuilder<DomainDeploymentBuilder> {
    private final Domain domain;

    /**
     * Creates a new deployment builder.
     *
     * @param client the client used to communicate with the server
     * @param domain the domain configuration
     */
    public DomainDeploymentBuilder(final ModelControllerClient client, final Domain domain) {
        super(client);
        this.domain = domain;
    }

    @Override
    protected DomainDeploymentBuilder getThis() {
        return this;
    }

    @Override
    protected void validate() {
        super.validate();
        if (domain == null) {
            throw new IllegalStateException("A domain myst be defined for domain deployments");
        }
    }

    @Override
    protected Deployment doBuild() {
        final DomainClient domainClient = (client instanceof DomainClient) ? (DomainClient) client : DomainClient.Factory.create(client);
        return new DomainDeployment(domainClient, domain, getContent(), getName(), getRuntimeName(), getType(), getMatchPattern(), getMatchPatternStrategy());
    }
}
