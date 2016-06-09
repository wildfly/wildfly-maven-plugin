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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.wildfly.core.launcher.CommandBuilder;
import org.wildfly.core.launcher.Launcher;

/**
 * A simple wrapped process that allows access to the thread used to consume the standard output.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ServerProcess extends Process {

    /**
     * An output stream that discards all written output.
     */
    @SuppressWarnings("NullableProblems")
    public static final OutputStream DISCARDING = new OutputStream() {
        @Override
        public void write(final byte[] b) throws IOException {
            // do nothing
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            // do nothing
        }

        @Override
        public void write(final int b) throws IOException {
            // do nothing
        }
    };

    private final Process delegate;
    private final Thread consoleConsumer;
    private final boolean inherited;

    private ServerProcess(final Process delegate, final Thread consoleConsumer) {
        this.delegate = delegate;
        this.consoleConsumer = consoleConsumer;
        this.inherited = consoleConsumer == null;
    }

    /**
     * Starts a server process based. The standard input, standard output and standard error streams will be inherited
     * from the parent process.
     *
     * @param commandBuilder the command used to start the server
     *
     * @return the server process started
     *
     * @throws IOException if an error occurs starting the process
     */
    public static ServerProcess start(final CommandBuilder commandBuilder) throws IOException {
        return start(commandBuilder, null, null);
    }

    /**
     * Starts a server process based. The standard input, standard output and standard error streams will be inherited
     * from the parent process.
     *
     * @param commandBuilder the command used to start the server
     * @param env            any environment variables to add to the server processes environment variables, can be {@code null}
     *
     * @return the server process started
     *
     * @throws IOException if an error occurs starting the process
     */
    public static ServerProcess start(final CommandBuilder commandBuilder, final Map<String, String> env) throws IOException {
        return start(commandBuilder, env, null);
    }

    /**
     * Starts a server process based.
     * <p>
     * If the {@code stdout} parameter is not {@code null} both standard out and standard error will be redirected to
     * to the output stream provided. If the parameter is {@code null} all standard streams will be inherited from the
     * parent process.
     * </p>
     *
     * @param commandBuilder the command used to start the server
     * @param env            any environment variables to add to the server processes environment variables, can be {@code null}
     * @param stdout         the output stream used to consume standard out and standard error, can be {@code null}
     *
     * @return the server process started
     *
     * @throws IOException if an error occurs starting the process
     */
    public static ServerProcess start(final CommandBuilder commandBuilder, final Map<String, String> env, final OutputStream stdout) throws IOException {
        final Launcher launcher = Launcher.of(Assertions.requiresNotNullParameter(commandBuilder, "commandBuilder"));
        if (env != null) {
            launcher.addEnvironmentVariables(env);
        }
        // Determine if we should consume stdout
        if (stdout == null) {
            launcher.inherit();
        } else {
            launcher.setRedirectErrorStream(true);
        }
        Thread consoleConsumer = null;
        final Process process = launcher.launch();
        if (stdout != null) {
            consoleConsumer = consumeOutput(process, stdout);
        }
        return new ServerProcess(process, consoleConsumer);
    }

    @Override
    public OutputStream getOutputStream() {
        return delegate.getOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return delegate.getInputStream();
    }

    @Override
    public InputStream getErrorStream() {
        return delegate.getErrorStream();
    }

    @Override
    public int waitFor() throws InterruptedException {
        return delegate.waitFor();
    }

    @Override
    public int exitValue() {
        return delegate.exitValue();
    }

    @Override
    public void destroy() {
        delegate.destroy();
    }

    /**
     * Returns the thread used to consume the standard output streams. If {@link #isInheritedStreams()} returns
     * {@code false} this will return {@code null}.
     *
     * @return the consumer thread or {@code null} if the processes inherits the streams from the parent process
     */
    public Thread getConsoleConsumer() {
        return consoleConsumer;
    }

    /**
     * Returns {@code true} if the streams are inherited.
     *
     * @return {@code true} if the streams are inherited from the parent process, otherwise {@code false}
     */
    public boolean isInheritedStreams() {
        return inherited;
    }


    private static Thread consumeOutput(final Process process, final OutputStream stdout) {
        final Thread thread = new Thread(new ConsoleConsumer(process.getInputStream(), stdout), "WildFly-stdout");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static class ConsoleConsumer implements Runnable {
        private final InputStream in;
        private final OutputStream out;

        ConsoleConsumer(final InputStream in, final OutputStream out) {
            this.in = in;
            this.out = out;
        }


        @Override
        public void run() {
            byte[] buffer = new byte[64];
            try {
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            } catch (IOException ignore) {
            }
        }
    }
}
