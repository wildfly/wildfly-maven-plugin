/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
