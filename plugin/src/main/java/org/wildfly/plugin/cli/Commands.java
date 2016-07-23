/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugins.annotations.Parameter;

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
    private List<String> commands = new ArrayList<>();

    /**
     * The CLI script files to execute.
     */
    @Parameter
    private List<File> scripts = new ArrayList<>();

    /**
     * The properties files to use when executing CLI scripts or commands.
     */
    @Parameter
    private List<File> propertiesFiles = new ArrayList<>();

    /**
     * Indicates whether or not subsequent commands should be executed if an error occurs executing a command. A value of
     * {@code false} will continue processing commands even if a previous command execution results in a failure.
     */
    @Parameter(alias = "fail-on-error", defaultValue = "true")
    private boolean failOnError = true;

    /**
     * Indicates whether or not commands should be executed in a batch.
     *
     * @return {@code true} if commands should be executed in a batch, otherwise
     * {@code false}
     */
    public boolean isBatch() {
        return batch;
    }

    /**
     * Get the defined commands or an empty list.
     *
     * @return the defined commands or an empty list
     */
    protected List<String> getCommands() {
        if (hasCommands()) {
            return new ArrayList<>(commands);
        }
        return Collections.emptyList();
    }

    /**
     * Get the defined script files or an empty list.
     *
     * @return the defined script files or an empty list
     */
    protected List<File> getScripts() {
        if (hasScripts()) {
            return new ArrayList<>(scripts);
        }
        return Collections.emptyList();
    }

    /**
     * Get the defined properties files or an empty list.
     *
     * @return the defined properties files or an empty list
     */
    protected List<File> getPropertiesFiles() {
        if (hasPropertiesFiles()) {
            return new ArrayList<>(propertiesFiles);
        }
        return Collections.emptyList();
    }

    /**
     * Checks where or not subsequent commands should be run or not if a failure occurs.
     *
     * @return {@code true} if subsequent commands should not be executed if there was a failed command, {@code false}
     * if subsequent command should continue to run.
     */
    protected boolean isFailOnError() {
        return failOnError;
    }

    /**
     * Checks if there are commands that should be executed.
     *
     * @return {@code true} if there are commands to be processed, otherwise {@code false}
     */
    public boolean hasCommands() {
        return commands != null && !commands.isEmpty();
    }

    /**
     * Checks if there are CLI script files that should be executed.
     *
     * @return {@code true} if there are CLI script files to be processed, otherwise {@code false}
     */
    public boolean hasScripts() {
        return scripts != null && !scripts.isEmpty();
    }

    /**
     * Checks of there are CLI properties files that should be used when executing scripts or commands.
     *
     * @return {@code true} if there are CLI properties flies to be processed, otherwise {@code false}
     */
    public boolean hasPropertiesFiles() {
        return propertiesFiles != null && !propertiesFiles.isEmpty();
    }
}
