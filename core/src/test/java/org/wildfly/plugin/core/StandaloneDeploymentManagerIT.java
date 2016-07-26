/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2016 Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.core;

import java.io.IOException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.core.launcher.StandaloneCommandBuilder;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StandaloneDeploymentManagerIT extends AbstractDeploymentManagerTest {

    @SuppressWarnings("StaticVariableMayNotBeInitialized")
    private static ServerProcess process;
    @SuppressWarnings("StaticVariableMayNotBeInitialized")
    private static ModelControllerClient client;

    @BeforeClass
    public static void startServer() throws Exception {
        boolean ok = false;
        try {
            client = Environment.createClient();
            if (ServerHelper.isDomainRunning(client) || ServerHelper.isStandaloneRunning(client)) {
                Assert.fail("A WildFly server is already running: " + ServerHelper.getContainerDescription(client));
            }
            final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(Environment.WILDFLY_HOME);
            process = ServerProcess.start(commandBuilder, null, System.out);
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
