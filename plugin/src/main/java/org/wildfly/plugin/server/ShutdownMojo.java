/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.server;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.core.ServerHelper;

/**
 * Shuts down a running WildFly Application Server.
 * <p/>
 * Can also be used to issue a reload instead of a full shutdown. If a reload is executed the process will wait for the
 * serer to be available before returning.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "shutdown")
public class ShutdownMojo extends AbstractServerConnection {

    /**
     * Set to {@code true} if a {@code reload} operation should be invoked instead of a {@code shutdown}. For domain
     * servers this executes a {@code reload-servers} operation. If set to {@code true} the process will wait up to the
     * {@code timeout} limit for the server to be available before returning.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.RELOAD)
    private boolean reload;

    /**
     * Set to {@code true} if you want to skip server shutdown, otherwise {@code false}.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.SKIP)
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug("Skipping server shutdown");
            return;
        }
        try (ModelControllerClient client = createClient()) {
            if (ServerHelper.getContainerDescription(client).isDomain()) {
                if (reload) {
                    client.execute(ServerOperations.createOperation("reload-servers"));
                    ServerHelper.waitForDomain(client, timeout);
                } else {
                    ServerHelper.shutdownDomain(DomainClient.Factory.create(client));
                }
            } else {
                if (reload) {
                    client.execute(ServerOperations.createOperation(ServerOperations.RELOAD));
                    ServerHelper.waitForStandalone(client, timeout);
                } else {
                    ServerHelper.shutdownStandalone(client);
                }
            }
            // Bad hack to get maven to complete it's message output
            try {
                TimeUnit.MILLISECONDS.sleep(500L);
            } catch (InterruptedException ignore) {
                ignore.printStackTrace();
                // no-op
            }
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Please make sure a server is running before executing goal " +
                    "%s. Reason: %s", goal(), e.getMessage()), e);
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Could not execute goal %s. Reason: %s", goal(), e.getMessage()), e);
        }
    }

    @Override
    public String goal() {
        return "shutdown";
    }
}
