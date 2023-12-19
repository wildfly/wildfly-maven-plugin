/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.wildfly.plugin.tools.ConsoleConsumer;

/**
 * Information on how the {@code stdout} should be consumed.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StandardOutput {

    private static final String SYSTEM_OUT = "system.out";
    private static final String SYSTEM_ERR = "system.err";
    private static final String NONE = "none";

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
        SYSTEM_OUT,
        /**
         * The output stream for a process will be inherited from this parent process.
         */
        INHERIT
    }

    private final OutputStream consumerStream;
    private final Redirect destination;
    private final Target target;
    private final Path stdoutPath;

    private StandardOutput(final Target target, final OutputStream consumerStream, final Redirect destination,
            final Path stdoutPath) {
        this.target = target;
        this.consumerStream = consumerStream;
        this.destination = destination;
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
     *                        otherwise the data will be consumed if the {@code stdout} value is {@code none} and will be
     *                        available via {@link #toString()}
     *
     * @return a new output stream
     *
     * @throws IOException if there is an error creating the stream
     */
    public static StandardOutput parse(final String stdout, final boolean discardNone) throws IOException {
        return parse(stdout, discardNone, false);
    }

    /**
     * Parses the string and attempts to determine where the data for the stream should be written.The following are
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
     *                        otherwise the data will be consumed if the {@code stdout} value is {@code none} and will be
     *                        available via {@link #toString()}
     * @param append      If stdout is a file, append output to existing file if true, otherwise a new file is created.
     *
     * @return a new output stream
     *
     * @throws IOException if there is an error creating the stream
     */
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public static StandardOutput parse(final String stdout, final boolean discardNone, final boolean append)
            throws IOException {
        if (stdout == null) {
            return new StandardOutput(Target.INHERIT, null, Redirect.INHERIT, null);
        }
        final Target target;
        Path stdoutPath = null;
        final OutputStream out;
        final String value = stdout.trim();
        if (SYSTEM_OUT.equalsIgnoreCase(value)) {
            target = Target.SYSTEM_OUT;
            out = System.out;
        } else if (SYSTEM_ERR.equalsIgnoreCase(value)) {
            target = Target.SYSTEM_ERR;
            out = System.err;
        } else if (NONE.equalsIgnoreCase(value)) {
            if (discardNone) {
                target = Target.DISCARDING;
                out = DISCARDING;
            } else {
                target = Target.COLLECTING;
                out = new ByteArrayOutputStream();
            }
        } else {
            // Attempt to create a file
            stdoutPath = Paths.get(stdout.trim());
            if (Files.notExists(stdoutPath)) {
                final Path parent = stdoutPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.createFile(stdoutPath);
            }
            target = Target.FILE;
            out = null;
        }
        Redirect destination = null;
        if (stdoutPath != null) {
            destination = append ? Redirect.appendTo(stdoutPath.toFile()) : Redirect.to(stdoutPath.toFile());
        }
        return new StandardOutput(target, out, destination, stdoutPath);
    }

    public static boolean isFile(String output) {
        return output != null && !SYSTEM_OUT.equals(output) && !SYSTEM_ERR.equals(output) && !NONE.equals(output);
    }

    /**
     * An option redirect for the {@code stdout}.
     *
     * @return the optional redirect
     */
    public Optional<Redirect> getRedirect() {
        return Optional.ofNullable(destination);
    }

    /**
     * If the processes {@code stdout} should be consumed a thread which consumes it will be started.
     *
     * @param process the process to possibly start the thread for
     *
     * @return the optional thread
     */
    public Optional<Thread> startConsumer(final Process process) {
        Thread thread = null;
        if (consumerStream != null) {
            thread = ConsoleConsumer.start(process, consumerStream);
        }
        return Optional.ofNullable(thread);
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
    public String toString() {
        if (target == Target.COLLECTING) {
            return consumerStream.toString();
        }
        return super.toString();
    }

    /**
     * An output stream that discards all written output.
     */
    @SuppressWarnings("NullableProblems")
    private static final OutputStream DISCARDING = new OutputStream() {
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
}
