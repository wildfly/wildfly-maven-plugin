/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
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
     * @param config the configuration used to execute the CLI commands
     * @param artifactResolver Resolver to retrieve CLI artifact for in-process execution.
     *
     * @throws MojoFailureException   if the JBoss Home directory is required and invalid
     * @throws MojoExecutionException if an error occurs executing the CLI commands
     */
    @Override
    public void execute(final BaseCommandConfiguration config, MavenRepoManager artifactResolver) throws MojoFailureException, MojoExecutionException {
        if (!Utils.isValidHomeDirectory(config.getJBossHome())) {
            throw new MojoFailureException("Invalid JBoss Home directory is not valid: " + config.getJBossHome());
        }
        executeInNewProcess(config);
    }

    @Override
    protected int executeInNewProcess(final BaseCommandConfiguration config, final Path scriptFile, final StandardOutput stdout) throws MojoExecutionException, IOException {
        CliCommandBuilder builder = createCommandBuilder(config, scriptFile);
        return launchProcess(builder, config, stdout);
    }
}
