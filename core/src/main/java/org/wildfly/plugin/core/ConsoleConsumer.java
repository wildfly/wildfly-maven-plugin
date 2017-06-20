/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

/**
 * A utility which will consume output from an {@link InputStream} and write it to an {@link OutputStream}. This is
 * commonly used when a processes {@link Process#getInputStream() stdout} or {@link Process#getErrorStream() stderr}
 * needs to be consumed and redirected somewhere.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ConsoleConsumer implements Runnable {
    private final InputStream in;
    private final OutputStream out;

    /**
     * Creates a new console consumer which will pipe the {@link InputStream} to the {@link OutputStream}.
     *
     * @param in  the input stream that should be pipped
     * @param out the output stream where the data should be written
     */
    @SuppressWarnings("WeakerAccess")
    public ConsoleConsumer(final InputStream in, final OutputStream out) {
        this.in = in;
        this.out = out;
    }

    /**
     * Creates and starts a daemon thread which consumes a {@linkplain Process processes}
     * {@link Process#getInputStream() stdout} stream and pipes the date to the output stream.
     * <p>
     * Note that when using this method the {@link ProcessBuilder#redirectErrorStream(boolean)} should likely be
     * {@code true}. Otherwise another {@linkplain #start(InputStream, OutputStream) thread} should be created to
     * consume {@link Process#getErrorStream() stderr}.
     * </p>
     *
     * @param process the process
     * @param out     the output stream where the data should be written
     *
     * @return the thread that was started
     */
    public static Thread start(final Process process, final OutputStream out) {
        return start(process.getInputStream(), out);
    }

    /**
     * Creates and starts a daemon thread which pipes int {@link InputStream} to the {@link OutputStream}.
     *
     * @param in  the input stream that should be pipped
     * @param out the output stream where the data should be written
     *
     * @return the thread that was started
     */
    public static Thread start(final InputStream in, final OutputStream out) {
        final Thread thread = new Thread(new ConsoleConsumer(in, out), "WildFly-Console-Consumer");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }


    @Override
    public void run() {
        final byte[] buffer = new byte[64];
        try {
            int len;
            while ((len = in.read(buffer)) != -1 && !Thread.interrupted()) {
                out.write(buffer, 0, len);
            }
        } catch (IOException ignore) {
        }
    }
}
