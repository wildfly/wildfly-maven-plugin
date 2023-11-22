/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugins.core.cli;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.wildfly.plugins.core.bootablejar.BootLoggingConfiguration;

/**
 * Generate boot logging config forked process entry point.
 *
 * @author jdenise
 */
public class CLIForkedBootConfigGenerator {

    public static void main(String[] args) throws Exception {
        Path jbossHome = Paths.get(args[0]);
        Path cliOutput = Paths.get(args[1]);
        Path systemProperties = Paths.get(args[2]);
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream(systemProperties.toFile())) {
            properties.load(in);
            for (String key : properties.stringPropertyNames()) {
                System.setProperty(key, properties.getProperty(key));
            }
        }
        try (CLIWrapper executor = new CLIWrapper(jbossHome, false, CLIForkedBootConfigGenerator.class.getClassLoader(),
                new BootLoggingConfiguration())) {
            try {
                executor.generateBootLoggingConfig();
            } finally {
                Files.write(cliOutput, executor.getOutput().getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
