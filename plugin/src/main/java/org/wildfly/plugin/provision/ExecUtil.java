// copied and adapted from Quarkus ExecUtil.java class at https://github.com/quarkusio/quarkus/blob/main/core/deployment/src/main/java/io/quarkus/deployment/util/ExecUtil.java

package org.wildfly.plugin.provision;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.logging.Log;
import org.wildfly.plugin.core.ConsoleConsumer;

public class ExecUtil {

    private static final int PROCESS_CHECK_INTERVAL = 500;

    private static class HandleOutput implements Runnable {

        private final InputStream is;
        private Log log;

        HandleOutput(InputStream is, org.apache.maven.plugin.logging.Log log) {
            this.is = is;
            this.log = log;
        }

        @Override
        public void run() {
            try (InputStreamReader isr = new InputStreamReader(is);
                    BufferedReader reader = new BufferedReader(isr)) {

                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    if (log != null) {
                        log.info(line);
                    }
                }
            } catch (IOException e) {
                log.error("Failed to handle output", e);
            }
        }
    }

    /**
     * Execute the specified command from within the current directory.
     *
     * @param command The command
     * @param args    The command arguments
     * @return true if commands where executed successfully
     */
    public static boolean exec(Log log, String command, String... args) {
        return exec(log, new File("."), command, args);
    }

    /**
     * Execute the specified command from within the current directory.
     *
     * @param out     the output stream used to consume the console output
     * @param command The command
     * @param args    The command arguments
     * @return true if commands where executed successfully
     */
    public static boolean exec(final OutputStream out, String command, String... args) {
        return exec(out, new File("."), command, args);
    }

    /**
     * Execute silently the specified command until the given timeout from within the current directory.
     *
     * @param timeout The timeout
     * @param command The command
     * @param args    The command arguments
     * @return true if commands where executed successfully
     */
    public static boolean execSilentWithTimeout(Duration timeout, String command, String... args) {
        return execWithTimeout(null, new File("."), timeout, command, args);
    }

    /**
     * Execute the specified command from within the specified directory.
     * The method allows specifying an output filter that processes the command output.
     *
     * @param directory The directory
     * @param command   The command
     * @param args      The command arguments
     * @return true if commands where executed successfully
     */
    public static boolean exec(Log log, File directory, String command,
            String... args) {
        try {
            Process process = startProcess(directory, command, args);
            new HandleOutput(process.getInputStream(), log).run();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Execute the specified command from within the specified directory.
     * The method allows specifying an output filter that processes the command output.
     *
     * @param out       the output stream used to consume the console output
     * @param directory The directory
     * @param command   The command
     * @param args      The command arguments
     * @return true if commands where executed successfully
     */
    public static boolean exec(final OutputStream out, final File directory, final String command,
            final String... args) {
        try {
            Process process = startProcess(directory, command, args);
            ConsoleConsumer.start(process.getInputStream(), out);
            process.waitFor();
            return process.exitValue() == 0;
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Execute the specified command until the given timeout from within the specified directory.
     * The method allows specifying an output filter that processes the command output.
     *
     * @param directory The directory
     * @param timeout   The timeout
     * @param command   The command
     * @param args      The command arguments
     * @return true if commands where executed successfully
     */
    public static boolean execWithTimeout(Log log, File directory,
            Duration timeout, String command, String... args) {
        try {
            Process process = startProcess(directory, command, args);
            Thread t = new Thread(new HandleOutput(process.getInputStream(), log));
            t.setName("Process stdout");
            t.setDaemon(true);
            t.start();
            process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            destroyProcess(process);
            return process.exitValue() == 0;
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Start a process executing given command with arguments within the specified directory.
     *
     * @param directory The directory
     * @param command   The command
     * @param args      The command arguments
     * @return the process
     */
    public static Process startProcess(File directory, String command, String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = command;
            if (args.length > 0) {
                System.arraycopy(args, 0, cmd, 1, args.length);
            }
            return new ProcessBuilder()
                    .directory(directory)
                    .command(cmd)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new RuntimeException("Input/Output error while executing command.", e);
        }
    }

    /**
     * Kill the process, if still alive, kill it forcibly
     *
     * @param process the process to kill
     */
    public static void destroyProcess(Process process) {
        process.destroy();
        int i = 0;
        while (process.isAlive() && i++ < 10) {
            try {
                process.waitFor(PROCESS_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    /**
     * Resolves which image binary to use. First {@code docker} is tried. If docker does not exist, then {@code podman}
     * is tried. If podman does not exist {@code null} is returned.
     *
     * @return the resolved binary, or {@code null} if docker or podman was not found
     */
    public static String resolveImageBinary() {
        try {
            if (execSilentWithTimeout(Duration.ofSeconds(3), "docker", "-v")) {
                return "docker";
            }
        } catch (Exception ignore) {
        }
        try {
            if (execSilentWithTimeout(Duration.ofSeconds(3), "podman", "-v")) {
                return "podman";
            }
        } catch (Exception ignore) {
        }
        return null;
    }

}