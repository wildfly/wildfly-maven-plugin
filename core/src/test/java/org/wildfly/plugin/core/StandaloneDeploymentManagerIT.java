/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.core;

import java.io.IOException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.core.launcher.StandaloneCommandBuilder;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("StaticVariableMayNotBeInitialized")
public class StandaloneDeploymentManagerIT extends AbstractDeploymentManagerTest {

    private static Process process;
    private static ModelControllerClient client;
    private static Thread consoleConsomer;

    @BeforeClass
    public static void startServer() throws Exception {
        boolean ok = false;
        try {
            client = Environment.createClient();
            if (ServerHelper.isDomainRunning(client) || ServerHelper.isStandaloneRunning(client)) {
                Assert.fail("A WildFly server is already running: " + ServerHelper.getContainerDescription(client));
            }
            final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(Environment.WILDFLY_HOME);
            process = Launcher.of(commandBuilder).launch();
            consoleConsomer = ConsoleConsumer.start(process, System.out);
            ServerHelper.waitForStandalone(client, Environment.TIMEOUT);
            ok = true;
        } finally {
            if (!ok) {
                final Process p = process;
                final ModelControllerClient c = client;
                process = null;
                client = null;
                try {
                    ProcessHelper.destroyProcess(p);
                } finally {
                    safeClose(c);
                }
            }
        }
    }

    @AfterClass
    @SuppressWarnings("StaticVariableUsedBeforeInitialization")
    public static void shutdown() throws Exception {
        try {
            if (client != null) {
                ServerHelper.shutdownStandalone(client);
                safeClose(client);
            }
        } finally {
            if (process != null) {
                process.destroy();
                process.waitFor();
            }
        }
    }

    @Test
    public void testDeploymentQueries() throws Exception {
        Assert.assertTrue("No deployments should exist.", deploymentManager.getDeployments().isEmpty());
        Assert.assertTrue("No deployments should exist.", deploymentManager.getDeploymentNames().isEmpty());
        try {
            deploymentManager.getDeployments("main-server-group");
            Assert.fail("This is not a domain server and DeploymentManager.getDeployments(serverGroup) should have failed.");
        } catch (IllegalStateException ignore) {
        }
    }

    @Override
    protected ModelControllerClient getClient() {
        return client;
    }

    @Override
    protected ModelNode createDeploymentResourceAddress(final String deploymentName) throws IOException {
        return DeploymentOperations.createAddress(ClientConstants.DEPLOYMENT, deploymentName);
    }
}
