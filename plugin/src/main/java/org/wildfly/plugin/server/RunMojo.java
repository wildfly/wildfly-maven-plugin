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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.core.launcher.CommandBuilder;
import org.wildfly.plugin.cli.CommandConfiguration;
import org.wildfly.plugin.cli.CommandExecutor;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.core.Deployment;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.deployment.PackageType;

/**
 * Starts a standalone instance of WildFly and deploys the application to the server.
 * <p/>
 * This goal will block until cancelled or a shutdown is invoked from a management client.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.PACKAGE)
public class RunMojo extends AbstractServerStartMojo {

    @Inject
    private CommandExecutor commandExecutor;

    /**
     * The CLI commands to execute before the deployment is deployed.
     */
    @Parameter(property = PropertyNames.COMMANDS)
    private List<String> commands = new ArrayList<>();

    /**
     * The CLI script files to execute before the deployment is deployed.
     */
    @Parameter(property = PropertyNames.SCRIPTS)
    private List<File> scripts = new ArrayList<>();

    /**
     * The path to the server configuration to use.
     */
    @Parameter(alias = "server-config", property = PropertyNames.SERVER_CONFIG)
    private String serverConfig;

    /**
     * Specifies the name used for the deployment.
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_NAME)
    private String name;

    /**
     * The runtime name for the deployment.
     * <p>
     * In some cases users may wish to have two deployments with the same {@code runtime-name} (e.g. two versions of
     * {@code example.war}) both available in the management configuration, in which case the deployments would need to
     * have distinct {@code name} values but would have the same {@code runtime-name}.
     * </p>
     */
    @Parameter(alias = "runtime-name", property = PropertyNames.DEPLOYMENT_RUNTIME_NAME)
    private String runtimeName;

    /**
     * The file name of the application to be deployed.
     * <p>
     * The {@code filename} property does have a default of <code>${project.build.finalName}.${project.packaging}</code>.
     * The default value is not injected as it normally would be due to packaging types like {@code ejb} that result in
     * a file with a {@code .jar} extension rather than an {@code .ejb} extension.
     * </p>
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_FILENAME)
    private String filename;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }
        try {
            final Log log = getLog();
            final Path deploymentContent = getDeploymentContent();
            // The deployment must exist before we do anything
            if (Files.notExists(deploymentContent)) {
                throw new MojoExecutionException(
                        String.format("The deployment '%s' could not be found.", deploymentContent.toAbsolutePath()));
            }
            // Start the server
            log.info("Server is starting up. Press CTRL + C to stop the server.");
            final ServerContext context = startServer(ServerType.STANDALONE);
            Process process = context.process();
            try (ModelControllerClient client = createClient()) {
                // Execute commands before the deployment is done
                final CommandConfiguration cmdConfig = CommandConfiguration.of(this::createClient, this::getClientConfiguration)
                        .addCommands(commands)
                        .addScripts(scripts)
                        .setJBossHome(context.jbossHome())
                        .setAutoReload(true)
                        .setFork(true)
                        .setStdout("none")
                        .setTimeout(timeout)
                        .build();
                commandExecutor.execute(cmdConfig, mavenRepoManager);
                // Create the deployment and deploy
                final Deployment deployment = Deployment.of(deploymentContent)
                        .setName(name)
                        .setRuntimeName(runtimeName);
                final DeploymentManager deploymentManager = DeploymentManager.Factory.create(client);
                deploymentManager.forceDeploy(deployment);
            } catch (MojoExecutionException | MojoFailureException e) {
                if (process != null) {
                    process.destroyForcibly().waitFor(10L, TimeUnit.SECONDS);
                }
                throw e;
            }
            try {
                // Wait for the process to die
                boolean keepRunning = true;
                while (keepRunning) {
                    final int exitCode = process.waitFor();
                    // 10 is the magic code used in the scripts to survive a :shutdown(restart=true) operation
                    if (exitCode == 10) {
                        // Ensure the current process is destroyed and restart a new one
                        process.destroy();
                        process = startServer(ServerType.STANDALONE).process();
                    } else {
                        keepRunning = false;
                    }
                }
            } catch (Exception e) {
                throw new MojoExecutionException("The server failed to start", e);
            } finally {
                if (process != null)
                    process.destroy();
            }
        } catch (Exception e) {
            throw new MojoExecutionException("The server failed to start", e);
        }
    }

    @Override
    protected CommandBuilder createCommandBuilder(final Path jbossHome) throws MojoExecutionException {
        return createStandaloneCommandBuilder(jbossHome, serverConfig);
    }

    @Override
    public String goal() {
        return "run";
    }

    private Path getDeploymentContent() {
        final PackageType packageType = PackageType.resolve(project);
        final String filename;
        if (this.filename == null) {
            filename = String.format("%s.%s", project.getBuild().getFinalName(), packageType.getFileExtension());
        } else {
            filename = this.filename;
        }
        return targetDir.toPath().resolve(filename);
    }
}
