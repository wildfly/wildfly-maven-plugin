/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.cli;

import java.io.IOException;
import java.nio.file.Path;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.core.launcher.CliCommandBuilder;
import org.wildfly.plugin.common.StandardOutput;
import org.wildfly.plugin.core.Utils;

/**
 * A command executor for executing offline CLI commands.
 *
 * @author jdenise@redhat.com
 */
@Singleton
@Named
public class OfflineCommandExecutor extends AbstractCommandExecutor<BaseCommandConfiguration> {

    /**
     * Executes forked offline CLI commands based on the configuration.
     *
     * @param config           the configuration used to execute the CLI commands
     * @param artifactResolver Resolver to retrieve CLI artifact for in-process execution.
     *
     * @throws MojoFailureException   if the JBoss Home directory is required and invalid
     * @throws MojoExecutionException if an error occurs executing the CLI commands
     */
    @Override
    public void execute(final BaseCommandConfiguration config, MavenRepoManager artifactResolver)
            throws MojoFailureException, MojoExecutionException {
        if (!Utils.isValidHomeDirectory(config.getJBossHome())) {
            throw new MojoFailureException("Invalid JBoss Home directory is not valid: " + config.getJBossHome());
        }
        executeInNewProcess(config);
    }

    @Override
    protected int executeInNewProcess(final BaseCommandConfiguration config, final Path scriptFile, final StandardOutput stdout)
            throws MojoExecutionException, IOException {
        CliCommandBuilder builder = createCommandBuilder(config, scriptFile);
        return launchProcess(builder, config, stdout);
    }
}
