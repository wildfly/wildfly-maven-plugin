/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.server;

import java.nio.file.Path;

import org.wildfly.core.launcher.CommandBuilder;

/**
 * The context of a server that has been started.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface ServerContext {

    /**
     * The running process.
     *
     * @return the process
     */
    Process process();

    /**
     * The command builder used to start the server.
     *
     * @return the command builder
     */
    CommandBuilder commandBuilder();

    /**
     * The directory used to start the server.
     *
     * @return the server directory
     */
    Path jbossHome();
}
