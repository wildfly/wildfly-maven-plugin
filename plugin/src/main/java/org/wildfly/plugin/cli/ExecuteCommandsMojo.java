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

package org.wildfly.plugin.cli;

import java.io.IOException;
import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.plugin.common.ManagementClient;
import org.wildfly.plugin.common.PropertyNames;

/**
 * Execute commands to the running WildFly Application Server.
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
public class ExecuteCommandsMojo extends AbstractServerConnection {

    /**
     * {@code true} if commands execution should be skipped.
     */
    @Parameter(defaultValue = "false")
    private boolean skip;

    /**
     * The WildFly Application Server's home directory. If defined commands will be executed in a new process launched
     * in a modular environment. This can be useful when commands from extensions need to be executed.
     */
    @Parameter(alias = "jboss-home", property = PropertyNames.JBOSS_HOME)
    private String jbossHome;

    /**
     * The commands to execute.
     */
    @Parameter(alias = "execute-commands")
    private Commands executeCommands;

    @Inject
    private CommandExecutor commandExecutor;

    @Override
    public String goal() {
        return "execute-commands";
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug("Skipping commands execution");
            return;
        }
        getLog().debug("Executing commands");
        try (final ManagementClient client = createClient()) {
            commandExecutor.execute(client, jbossHome, executeCommands.validate(getLog()));
        } catch (IOException e) {
            throw new MojoExecutionException("Could not execute commands.", e);
        }
    }
}
