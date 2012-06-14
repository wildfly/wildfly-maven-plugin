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

package org.jboss.as.plugin.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.plugin.cli.Cli;
import org.jboss.as.plugin.common.Operations;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class Server {
    private final ServerInfo serverInfo;
    private Process process;
    private ConsoleConsumer console;
    private volatile CommandContext commandContext;
    private final String shutdownId;

    protected Server(final ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
        shutdownId = null;
    }

    protected Server(final ServerInfo serverInfo, final String shutdownId) {
        this.serverInfo = serverInfo;
        this.shutdownId = shutdownId;
    }

    /**
     * The console that is associated with the server.
     *
     * @return the console
     */
    protected final ConsoleConsumer getConsole() {
        return console;
    }

    /**
     * Starts the server.
     *
     * @throws IOException the an error occurs creating the process
     */
    public synchronized final void start() throws IOException {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                stop();
            }
        }));
        final List<String> cmd = createLaunchCommand();
        final ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        processBuilder.redirectErrorStream(true);
        process = processBuilder.start();
        console = ConsoleConsumer.start(process.getInputStream(), shutdownId);
        long timeout = serverInfo.getStartupTimeout() * 1000;
        boolean serverAvailable = false;
        long sleep = 50;
        init();
        while (timeout > 0 && !serverAvailable) {
            serverAvailable = isStarted();
            if (!serverAvailable) {
                if (processHasDied(process))
                    break;
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    serverAvailable = false;
                    break;
                }
                timeout -= sleep;
                sleep = Math.max(sleep / 2, 100);
            }
        }
        if (!serverAvailable) {
            destroyProcess();
            throw new IllegalStateException(String.format("Managed server was not started within [%d] s", serverInfo.getStartupTimeout()));
        }
    }

    /**
     * Stops the server.
     */
    public synchronized final void stop() {
        try {
            stopServer();
            try {
                if (commandContext != null) {
                    commandContext.terminateSession();
                }
            } catch (Exception ignore) {
                // no-op
            }
        } finally {
            if (process != null) {
                process.destroy();
                try {
                    process.waitFor();
                } catch (InterruptedException ignore) {
                    // no-op
                }
            }
        }
    }

    /**
     * Invokes any optional initialization that should take place after the process has been launched. Note the server
     * may not be completely started when the method is invoked.
     *
     * @throws IOException if an IO error occurs
     */
    protected abstract void init() throws IOException;

    /**
     * Stops the server before the process is destroyed. A no-op override will just destroy the process.
     */
    protected abstract void stopServer();

    /**
     * Checks the status of the server and returns {@code true} if the server is fully started.
     *
     * @return {@code true} if the server is fully started, otherwise {@code false}
     */
    public abstract boolean isStarted();

    /**
     * Returns the client that used to execute management operations on the server.
     *
     * @return the client to execute management operations
     */
    public abstract ModelControllerClient getClient();

    /**
     * Creates the command to launch the server for the process.
     *
     * @return the commands used to launch the server
     */
    protected abstract List<String> createLaunchCommand();

    /**
     * Deploys the application to the server.
     *
     * @param file           the file to deploy
     * @param deploymentName the name of the deployment
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     * @throws IOException
     */
    public abstract void deploy(final File file, final String deploymentName) throws MojoExecutionException, MojoFailureException, IOException;

    /**
     * Execute a CLI command.
     *
     * @param cmd the command to execute
     *
     * @throws IOException              if an error occurs on the connected client
     * @throws IllegalArgumentException if the command is invalid or was unsuccessful
     */
    public synchronized void executeCliCommand(final String cmd) throws IOException {
        if (!isStarted()) {
            throw new IllegalStateException("Cannot execute commands on a server that is not running.");
        }
        CommandContext ctx = commandContext;
        if (ctx == null) {
            commandContext = ctx = Cli.createAndBind(null);
        }
        final ModelControllerClient client = getClient();
        final ModelNode op;
        final ModelNode result;
        try {
            op = ctx.buildRequest(cmd);
            result = client.execute(op);
        } catch (CommandFormatException e) {
            throw new IllegalArgumentException(String.format("Command '%s' is invalid", cmd), e);
        }
        if (!Operations.successful(result)) {
            throw new IllegalArgumentException(String.format("Command '%s' was unsuccessful. Reason: %s", cmd, Operations.getFailureDescription(result)));
        }
    }


    private int destroyProcess() {
        if (process == null)
            return 0;
        process.destroy();
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean processHasDied(final Process process) {
        try {
            process.exitValue();
            return true;
        } catch (IllegalThreadStateException e) {
            // good
            return false;
        }
    }

    /**
     * Runnable that consumes the output of the process.
     *
     * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
     */
    static class ConsoleConsumer implements Runnable {

        static ConsoleConsumer start(final InputStream stream, final String shutdownId) {
            final ConsoleConsumer result = new ConsoleConsumer(stream, shutdownId);
            final Thread t = new Thread(result);
            t.setName("AS7-Console");
            t.start();
            return result;
        }

        private final InputStream in;
        private final String shutdownId;
        private final CountDownLatch latch;

        private ConsoleConsumer(final InputStream in, final String shutdownId) {
            this.in = in;
            latch = new CountDownLatch(1);
            this.shutdownId = shutdownId;
        }

        @Override
        public void run() {

            try {
                byte[] buf = new byte[512];
                int num;
                while ((num = in.read(buf)) != -1) {
                    System.out.write(buf, 0, num);
                    if (shutdownId != null && new String(buf).contains(shutdownId))
                        latch.countDown();
                }
            } catch (IOException ignore) {
            }
        }

        void awaitShutdown(final long seconds) throws InterruptedException {
            if (shutdownId == null) latch.countDown();
            latch.await(seconds, TimeUnit.SECONDS);
        }

    }
}
