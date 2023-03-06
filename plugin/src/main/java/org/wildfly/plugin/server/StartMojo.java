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

package org.wildfly.plugin.server;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.wildfly.core.launcher.CommandBuilder;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.StandardOutput;

/**
 * Starts a standalone instance of WildFly Application Server.
 * <p/>
 * The purpose of this goal is to start a WildFly Application Server for testing during the maven lifecycle.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "start", requiresDependencyResolution = ResolutionScope.RUNTIME)
public class StartMojo extends AbstractServerStartMojo {

    /**
     * The path to the server configuration to use. This is only used for standalone servers.
     */
    @Parameter(alias = "server-config", property = PropertyNames.SERVER_CONFIG)
    private String serverConfig;

    /**
     * The name of the domain configuration to use. This is only used for domain servers.
     */
    @Parameter(alias = "domain-config", property = PropertyNames.DOMAIN_CONFIG)
    private String domainConfig;

    /**
     * The name of the host configuration to use. This is only used for domain servers.
     */
    @Parameter(alias = "host-config", property = PropertyNames.HOST_CONFIG)
    private String hostConfig;

    /**
     * The type of server to start.
     * <p>
     * {@code STANDALONE} for a standalone server and {@code DOMAIN} for a domain server.
     * </p>
     */
    @Parameter(alias = "server-type", property = "wildfly.server.type", defaultValue = "STANDALONE")
    protected ServerType serverType;

    /**
     * Indicates how {@code stdout} and {@code stderr} should be handled for the spawned server process. Note that
     * {@code stderr} will be redirected to {@code stdout} if the value is defined unless the value is {@code none}.
     * <div>
     * By default {@code stdout} and {@code stderr} are inherited from the current process. You can change the setting
     * to one of the follow:
     * <ul>
     * <li>{@code none} indicates the {@code stdout} and {@code stderr} stream should not be consumed. This should
     * generally only be used if the {@code shutdown} goal is used in the same maven process.</li>
     * <li>{@code System.out} or {@code System.err} to redirect to the current processes <em>(use this option if you
     * see odd behavior from maven with the default value)</em></li>
     * <li>Any other value is assumed to be the path to a file and the {@code stdout} and {@code stderr} will be
     * written there</li>
     * </ul>
     * </div>
     * <div>
     * Note that if this goal is not later followed by a {@code shutdown} goal in the same maven process you should use
     * a file to redirect the {@code stdout} and {@code stderr} to. Both output streams will be redirected to the same
     * file.
     * </div>
     */
    @Parameter(property = PropertyNames.STDOUT)
    private String stdout;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Log log = getLog();
        if (skip) {
            log.debug("Skipping server start");
            return;
        }

        // Determine how stdout should be consumed
        try {
            startServer(serverType);
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("The server failed to start", e);
        }
    }

    @Override
    protected CommandBuilder createCommandBuilder(final Path jbossHome) throws MojoExecutionException {
        if (serverType == ServerType.DOMAIN) {
            return createDomainCommandBuilder(jbossHome, domainConfig, hostConfig);
        }
        return createStandaloneCommandBuilder(jbossHome, serverConfig);
    }

    @Override
    protected StandardOutput standardOutput() throws IOException {
        return StandardOutput.parse(stdout, true);
    }

    @Override
    public String goal() {
        return "start";
    }
}
