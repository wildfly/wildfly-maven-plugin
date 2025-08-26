/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Utilities for the environment.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Environment {

    // Used when starting an embedded server
    public static final String MINIMAL_STABILITY_LEVEL = "experimental";

    private static final String[] MODULAR_JVM_ARGUMENTS = {
            "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-exports=jdk.unsupported/sun.reflect=ALL-UNNAMED",
            "--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED",
            "--add-modules=java.se",
    };
    private static final Logger LOGGER = Logger.getLogger(Environment.class);
    private static final boolean WINDOWS;

    static {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        WINDOWS = os.contains("win");
    }

    /**
     * Checks to see if the {@code javaHome} is a modular JVM.
     *
     * @param javaHome the Java Home if {@code null} an attempt to discover the Java Home will be done
     *
     * @return {@code true} if this is a modular environment
     */
    public static boolean isModularJvm(final Path javaHome) {
        boolean result;
        final List<String> cmd = new ArrayList<>();
        cmd.add(getJavaCommand(javaHome));
        cmd.add("--add-modules=java.se");
        cmd.add("-version");
        final ProcessBuilder builder = new ProcessBuilder(cmd);
        Process process = null;
        Path stdout = null;
        try {
            // Create a temporary file for stdout
            stdout = Files.createTempFile("stdout", ".txt");
            process = builder.redirectErrorStream(true)
                    .redirectOutput(stdout.toFile()).start();

            if (process.waitFor(1, TimeUnit.SECONDS)) {
                result = process.exitValue() == 0;
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(getStdoutMessage("The process timed out waiting for the response.", stdout));
                }
                result = false;
            }
        } catch (IOException | InterruptedException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(getStdoutMessage("The process ended in error.", stdout), e);
            }
            result = false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (stdout != null) {
                try {
                    Files.deleteIfExists(stdout);
                } catch (IOException ignore) {
                }
            }
        }
        return result;
    }

    /**
     * Returns the default JVM arguments for a modular environment.
     *
     * @return the modular arguments
     */
    public static String[] getModularJvmArguments() {
        return Arrays.copyOf(MODULAR_JVM_ARGUMENTS, MODULAR_JVM_ARGUMENTS.length);
    }

    /**
     * Returns the Java command to use.
     *
     * @param javaHome the Java Home, if {@code null} an attempt to determine the command will be done
     *
     * @return the Java executable command
     */
    public static String getJavaCommand(final Path javaHome) {
        final Path resolvedJavaHome = javaHome == null ? findJavaHome() : javaHome;
        final String exe;
        if (resolvedJavaHome == null) {
            exe = "java";
        } else {
            exe = resolvedJavaHome.resolve("bin").resolve("java").toString();
        }
        if (exe.contains(" ")) {
            return "\"" + exe + "\"";
        }
        if (WINDOWS) {
            return exe + ".exe";
        }
        return exe;
    }

    /**
     * Determines if this is a Windows environment.
     *
     * @return {@code true} if this is a Windows environment, otherwise {@code false}
     */
    public static boolean isWindows() {
        return WINDOWS;
    }

    private static Path findJavaHome() {
        String path = WildFlySecurityManager.getPropertyPrivileged("java.home", null);
        if (path != null) {
            path = WildFlySecurityManager.getEnvPropertyPrivileged("JAVA_HOME", null);
        }
        if (path == null) {
            return null;
        }
        Path resolved = Paths.get(path);
        if (Files.exists(resolved)) {
            return resolved;
        }
        return null;
    }

    private static String getStdoutMessage(final String message, final Path file) {
        final StringBuilder result = new StringBuilder(message);
        try {
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                result.append(System.lineSeparator())
                        .append(line);
            }
        } catch (IOException e) {
            result.append(System.lineSeparator())
                    .append("Failed to read the stdout: ")
                    .append(e.getMessage());
        }
        return result.toString();
    }
}
