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
import java.util.Set;

import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.plugin.common.Operations;
import org.jboss.as.plugin.common.Operations.CompositeOperationBuilder;
import org.jboss.dmr.ModelNode;

/**
 * CLI commands to run.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Commands {

    /**
     * {@code true} if commands should be executed in a batch or {@code false} if they should be executed one at a
     * time.
     */
    @Parameter
    private boolean batch;

    /**
     * The CLI commands to execute.
     */
    @Parameter
    private Set<String> commands;

    /**
     * Indicates whether or not commands should be executed in a batch.
     *
     * @return {@code true} if commands should be executed in a batch, otherwise {@code false}
     */
    public boolean isBatch() {
        return batch;
    }

    /**
     * Checks of there are commands that should be executed.
     *
     * @return {@code true} if there are commands to be processed, otherwise {@code false}
     */
    public boolean hasCommands() {
        return commands != null && !commands.isEmpty();
    }

    /**
     * Returns the set of commands to process.
     * <p/>
     * Could be {@code null} if not defined. Use {@link #hasCommands()} to ensure there are commands to execute.
     *
     * @return the set of commands to process
     */
    public Set<String> getCommands() {
        return commands;
    }

    /**
     * Execute the commands.
     *
     * @param client the client used to execute the commands
     *
     * @throws IOException              if the client has an IOException
     * @throws IllegalArgumentException if an command is invalid
     */
    public final void execute(final ModelControllerClient client) throws IOException {
        if (hasCommands()) {
            final CommandContext ctx = create();
            try {
                if (isBatch()) {
                    final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
                    for (String cmd : getCommands()) {
                        try {
                            builder.addStep(ctx.buildRequest(cmd));
                        } catch (CommandFormatException e) {
                            throw new IllegalArgumentException(String.format("Command '%s' is invalid", cmd), e);
                        }
                    }
                    final ModelNode result = client.execute(builder.build());
                    if (!Operations.successful(result)) {
                        throw new IllegalArgumentException(Operations.getFailureDescription(result));
                    }
                } else {
                    for (String cmd : getCommands()) {
                        final ModelNode result;
                        try {
                            result = client.execute(ctx.buildRequest(cmd));
                        } catch (CommandFormatException e) {
                            throw new IllegalArgumentException(String.format("Command '%s' is invalid", cmd), e);
                        }
                        if (!Operations.successful(result)) {
                            throw new IllegalArgumentException(String.format("Command '%s' was unsuccessful. Reason: %s", cmd, Operations.getFailureDescription(result)));
                        }
                    }
                }
            } finally {
                ctx.terminateSession();
            }
        }
    }

    /**
     * Creates the command context and binds the client to the context.
     * <p/>
     * If the client is {@code null}, no client is bound to the context.
     *
     * @return the command line context
     *
     * @throws IllegalStateException if the context fails to initialize
     */
    public static CommandContext create() {
        final CommandContext commandContext;
        try {
            commandContext = CommandContextFactory.getInstance().newCommandContext();
        } catch (CliInitializationException e) {
            throw new IllegalStateException("Failed to initialize CLI context", e);
        }
        return commandContext;
    }
}
