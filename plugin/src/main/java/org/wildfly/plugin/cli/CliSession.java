/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A CLI execution.
 *
 * @author jdenise
 */
public class CliSession {

    private List<File> scripts = Collections.emptyList();
    private List<File> propertiesFiles = Collections.emptyList();
    private String[] javaOpts = {};
    boolean resolveExpressions = false;
    private List<String> commands = new ArrayList<>();

    /**
     * Set the list of CLI commands to execute.
     *
     * @param commands List of script CLI commands
     */
    public void setCommands(List<String> commands) {
        this.commands = commands;
    }

    /**
     * Get the list of CLI commands to execute.
     *
     * @return The list of CLI commands.
     */
    public List<String> getCommands() {
        return commands;
    }

    /**
     * Set the list of JVM options to pass to CLI process.
     *
     * @param javaOpts List of JVM Options.
     */
    public void setJavaOpts(String[] javaOpts) {
        this.javaOpts = javaOpts;
    }

    /**
     * Get the list of JVM options to pass to CLI process.
     *
     * @return The list of Java Options
     */
    public String[] getJavaOpts() {
        return javaOpts;
    }

    /**
     * Set the list of CLI script files to execute.
     *
     * @param scripts List of script file paths.
     */
    public void setScripts(List<File> scripts) {
        this.scripts = scripts;
    }

    /**
     * Get the list of CLI script files to execute.
     *
     * @return The list of file paths.
     */
    public List<File> getScripts() {
        return scripts;
    }

    /**
     * Set the properties files used when executing the CLI.
     *
     * @param propertiesFiles List of Path to properties file.
     */
    public void setPropertiesFiles(List<File> propertiesFiles) {
        this.propertiesFiles = propertiesFiles;
    }

    /**
     * Get the properties files used when executing the CLI.
     *
     * @return The properties file path.
     */
    public List<File> getPropertiesFiles() {
        return propertiesFiles;
    }

    /**
     * By default, the CLI doesn't resolve expressions located in scripts locally. In order to have the expressions
     * resolved locally, set this value to true.
     *
     * @param resolveExpressions True to resolve locally, false to resolve at server execution time.
     */
    public void setResolveExpressions(boolean resolveExpressions) {
        this.resolveExpressions = resolveExpressions;
    }

    /**
     * Get the expression resolution value.
     *
     * @return The expression resolution value.
     */
    public boolean getResolveExpression() {
        return resolveExpressions;
    }

}
