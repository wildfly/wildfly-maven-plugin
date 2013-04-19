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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.plugin.common.ServerOperations;
import org.jboss.as.plugin.common.Streams;
import org.jboss.dmr.ModelNode;

/**
 * CLI commands to run.
 * <p/>
 * <pre>
 *      &lt;commands&gt;
 *          &lt;batch&gt;false&lt;/batch&gt;
 *          &lt;command&gt;/subsystem=logging/console-handler:CONSOLE:write-attribute(name=level,value=TRACE)&lt;/command&gt;
 *      &lt;/commands&gt;
 * </pre>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author <a href="mailto:heinz.wilming@akquinet.de">Heinz Wilming</a>
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
    private List<String> commands = new ArrayList<String>();

    /**
     * The CLI script files to execute.
     */
    @Parameter
    private List<File> scripts = new ArrayList<File>();

    /**
     * Indicates whether or not commands should be executed in a batch.
     *
     * @return {@code true} if commands should be executed in a batch, otherwise
     *         {@code false}
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
    public List<String> getCommands() {
        return commands;
    }

    /**
     * Returns the CLI script files to process.
     *
     * @return the CLI script files to process.
     */
    public List<File> getScripts() {
        return scripts;
    }

    /**
     * Checks of there are a CLI script file that should be executed.
     *
     * @return {@code true} if there are a CLI script to be processed, otherwise
     *         {@code false}
     */
    public boolean hasScripts() {
        return scripts != null && !scripts.isEmpty();
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
        final boolean hasCommands = hasCommands();
        final boolean hasScripts = hasScripts();

        if (hasCommands || hasScripts) {
            final CommandContext ctx = create(client);
            try {

                if (isBatch()) {
                    executeBatch(client, ctx);
                } else {
                    executeCommands(client, ctx);
                }
                executeScripts(client, ctx);

            } finally {
                ctx.terminateSession();
                ctx.bindClient(null);
            }
        }

    }

    private void executeScripts(final ModelControllerClient client, final CommandContext ctx) throws IOException {

        for (File script : getScripts()) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(script), "UTF-8"));
                String line = reader.readLine();
                while (ctx.getExitCode() == 0 && !ctx.isTerminated() && line != null) {

                    ctx.handleSafe(line.trim());
                    line = reader.readLine();

                }
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to process file '" + script.getAbsolutePath() + "'", e);
            } finally {
                Streams.safeClose(reader);
            }
        }
    }

    private void executeCommands(final ModelControllerClient client, final CommandContext ctx) throws IOException {
        for (String cmd : getCommands()) {
            final ModelNode result;
            try {
                result = client.execute(ctx.buildRequest(cmd));
            } catch (CommandFormatException e) {
                throw new IllegalArgumentException(String.format("Command '%s' is invalid", cmd), e);
            }
            if (!ServerOperations.isSuccessfulOutcome(result)) {
                throw new IllegalArgumentException(String.format("Command '%s' was unsuccessful. Reason: %s", cmd, ServerOperations.getFailureDescriptionAsString(result)));
            }
        }
    }

    private void executeBatch(final ModelControllerClient client, final CommandContext ctx) throws IOException {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        for (String cmd : getCommands()) {
            try {
                builder.addStep(ctx.buildRequest(cmd));
            } catch (CommandFormatException e) {
                throw new IllegalArgumentException(String.format("Command '%s' is invalid", cmd), e);
            }
        }
        final ModelNode result = client.execute(builder.build());
        if (!ServerOperations.isSuccessfulOutcome(result)) {
            throw new IllegalArgumentException(ServerOperations.getFailureDescriptionAsString(result));
        }
    }

    /**
     * Creates the command context and binds the client to the context.
     * <p/>
     * If the client is {@code null}, no client is bound to the context.
     *
     * @param client current connected client
     *
     * @return the command line context
     *
     * @throws IllegalStateException if the context fails to initialize
     */
    public static CommandContext create(final ModelControllerClient client) {
        final CommandContext commandContext;
        try {
            commandContext = CommandContextFactory.getInstance().newCommandContext();
            commandContext.bindClient(client);
        } catch (CliInitializationException e) {
            throw new IllegalStateException("Failed to initialize CLI context", e);
        }
        return commandContext;
    }
}
