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

package org.jboss.as.plugin.server;

import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.plugin.common.AbstractServerConnection;
import org.jboss.as.plugin.common.ServerOperations;
import org.jboss.as.plugin.common.PropertyNames;

/**
 * Shuts down a running JBoss Application Server.
 * <p/>
 * Can also be used to issue a reload instead of a full shutdown.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "shutdown")
public class Shutdown extends AbstractServerConnection {

    /**
     * Set to {@code true} if a {@code reload} operation should be invoked instead of a {@code shutdown}.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.RELOAD)
    private boolean reload;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            synchronized (CLIENT_LOCK) {
                final ModelControllerClient client = getClient();
                if (reload) {
                    client.execute(ServerOperations.createOperation(ServerOperations.RELOAD));
                } else {
                    client.execute(ServerOperations.createOperation(ServerOperations.SHUTDOWN));
                }
            }
            // Bad hack to get maven to complete it's message output
            try {
                TimeUnit.MILLISECONDS.sleep(500L);
            } catch (InterruptedException ignore) {
                ignore.printStackTrace();
                // no-op
            }
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Could not execute goal %s. Reason: %s", goal(), e.getMessage()), e);
        } finally {
            close();
        }
    }

    @Override
    public String goal() {
        return "shutdown";
    }
}
