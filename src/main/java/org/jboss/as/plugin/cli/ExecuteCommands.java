/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.plugin.cli;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.plugin.common.AbstractServerConnection;

/**
 * Execute commands to the running JBoss Application Server.
 * <p/>
 * Commands should be formatted in the same manor CLI commands are formatted.
 * <p/>
 * Executing commands in a batch will rollback all changes if one command fails.
 * <pre>
 *      &lt;execute-commands&gt;
 *          &lt;batch&gt;true&lt;/batch&gt;
 *          &lt;commands&gt;
 *              &lt;command&gt;/subsystem=logging/console=CONSOLE:write-attribute(name=level,value=DEBUG)&lt;/command&gt;
 *          &lt;/commands&gt;
 *      &lt;/execute-commands&gt;
 * </pre>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "execute-commands", threadSafe = true)
public class ExecuteCommands extends AbstractServerConnection {

    /**
     * The commands to execute.
     */
    @Parameter(alias = "execute-commands")
    private Commands executeCommands;

    @Override
    public String goal() {
        return "execute-commands";
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().debug("Executing commands");
        synchronized (CLIENT_LOCK) {
            final ModelControllerClient client = getClient();
            try {
                executeCommands.execute(client);
            } catch (IOException e) {
                throw new MojoFailureException("Could not execute commands.", e);
            } finally {
                close();
            }
        }
    }
}
