/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.common;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.wildfly.common.Assert;
import org.wildfly.plugin.core.ServerProcess;

/**
 * Represents a stream used to consume standard out.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StandardOutputStream extends OutputStream implements Closeable {

    /**
     * The target for the the output stream.
     */
    public enum Target {
        /**
         * The output stream data has been collected and can be queried.
         */
        COLLECTING,
        /**
         * The output stream data has been discarded.
         */
        DISCARDING,
        /**
         * The output stream will be redirected to a file.
         */
        FILE,
        /**
         * The output stream will be redirected to {@link System#err}.
         */
        SYSTEM_ERR,
        /**
         * The output stream will be redirected to {@link System#out}.
         */
        SYSTEM_OUT
    }

    private final OutputStream delegate;
    private final Target target;
    private final Path stdoutPath;

    private StandardOutputStream(final OutputStream delegate, final Target target, final Path stdoutPath) {
        this.delegate = delegate;
        this.target = target;
        this.stdoutPath = stdoutPath;
    }

    /**
     * Parses the string and attempts to determine where the data for the stream should be written. The following are
     * the options for the value:
     * <ul>
     * <li>{@code none} indicates the data for this stream will be consumed and {@link #toString()} will return the
     * data of the {@code discardNone} parameter is {@code false}, otherwise the data will be discarded</li>
     * <li>{@code System.out} or {@code System.err} to write to the respective stream</li>
     * <li>Any other value is assumed to be the path to a file and the data will written to the file</li>
     * </ul>
     *
     * @param stdout      the value to be parsed
     * @param discardNone {@code true} if the {@code stdout} value is {@code none} and the data should be discarded,
     *                    otherwise the data will be consumed if the {@code stdout} value is {@code none} and will be
     *                    available via {@link #toString()}
     *
     * @return a new output stream
     *
     * @throws IOException if there is an error creating the stream
     */
    public static StandardOutputStream parse(final String stdout, final boolean discardNone) throws IOException {
        Assert.checkNotNullParam("stdout", stdout);
        Path stdoutPath = null;
        final Target target;
        final OutputStream out;
        final String value = stdout.trim().toLowerCase(Locale.ENGLISH);
        if ("system.out".equals(value)) {
            out = System.out;
            target = Target.SYSTEM_OUT;
        } else if ("system.err".equals(value)) {
            out = System.err;
            target = Target.SYSTEM_ERR;
        } else if ("none".equals(value)) {
            if (discardNone) {
                target = Target.DISCARDING;
                out = ServerProcess.DISCARDING;
            } else {
                target = Target.COLLECTING;
                out = new ByteArrayOutputStream();
            }
        } else {
            target = Target.FILE;
            // Attempt to create a file
            stdoutPath = Paths.get(value);
            if (Files.notExists(stdoutPath)) {
                final Path parent = stdoutPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.createFile(stdoutPath);
            }
            out = new BufferedOutputStream(Files.newOutputStream(stdoutPath));
        }
        return new StandardOutputStream(out, target, stdoutPath);
    }

    /**
     * The path to the file where the data was written.
     *
     * @return the path to where the data was written, otherwise {@link null} if the data was not written to a file.
     */
    public Path getStdoutPath() {
        return stdoutPath;
    }

    /**
     * The target the data was written to.
     *
     * @return the target
     */
    public Target getTarget() {
        return target;
    }

    @Override
    public void write(final int b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        delegate.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        if (target != Target.SYSTEM_ERR && target != Target.SYSTEM_OUT) {
            delegate.close();
        }
    }

    @Override
    public String toString() {
        if (target == Target.COLLECTING) {
            return delegate.toString();
        }
        return super.toString();
    }
}
