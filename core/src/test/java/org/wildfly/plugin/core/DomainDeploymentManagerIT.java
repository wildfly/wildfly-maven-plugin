/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.core;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.core.launcher.DomainCommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("StaticVariableMayNotBeInitialized")
public class DomainDeploymentManagerIT extends AbstractDeploymentManagerTest {
    private static final String DEFAULT_SERVER_GROUP = "main-server-group";
    // Workaround for WFCORE-4121
    private static final String[] MODULAR_JDK_ARGUMENTS = {
            "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-exports=jdk.unsupported/sun.reflect=ALL-UNNAMED",
            "--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED",
            "--add-modules=java.se",
    };
    private static final boolean IS_MODULAR_JDK;

    static {
        final String javaVersion = System.getProperty("java.specification.version");
        int vmVersion;
        try {
            final Matcher matcher = Pattern.compile("^(?:1\\.)?(\\d+)$").matcher(javaVersion); // match 1.<number> or <number>
            if (matcher.find()) {
                vmVersion = Integer.valueOf(matcher.group(1));
            } else {
                throw new RuntimeException("Unknown version of jvm " + javaVersion);
            }
        } catch (Exception e) {
            vmVersion = 8;
        }
        IS_MODULAR_JDK = vmVersion > 8;
    }

    private static Process process;
    private static DomainClient client;
    private static Thread consoleConsomer;

    @BeforeClass
    public static void startServer() throws Exception {
        boolean ok = false;
        try {
            client = DomainClient.Factory.create(Environment.createClient());
            if (ServerHelper.isDomainRunning(client) || ServerHelper.isStandaloneRunning(client)) {
                Assert.fail("A WildFly server is already running: " + ServerHelper.getContainerDescription(client));
            }
            final DomainCommandBuilder commandBuilder = DomainCommandBuilder.of(Environment.WILDFLY_HOME);
            if (IS_MODULAR_JDK) {
                commandBuilder.addHostControllerJavaOptions(MODULAR_JDK_ARGUMENTS);
            }
            process = Launcher.of(commandBuilder).launch();
            consoleConsomer = ConsoleConsumer.start(process, System.out);
            ServerHelper.waitForDomain(client, Environment.TIMEOUT);
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
                ServerHelper.shutdownDomain(client);
                safeClose(client);
            }
        } finally {
            if (process != null) {
                process.destroy();
                process.waitFor();
            }
            if (consoleConsomer != null) {
                consoleConsomer.interrupt();
            }
        }
    }

    @Test
    public void testFailedDeploy() throws Exception {
        // Expect a failure with no server groups defined
        final Deployment failedDeployment = createDefaultDeployment("test-failed-deployment.war")
                .setServerGroups(Collections.<String> emptySet());
        assertFailed(deploymentManager.deploy(failedDeployment));
        assertDeploymentDoesNotExist(failedDeployment);
    }

    @Test
    public void testFailedDeployMulti() throws Exception {
        // Expect a failure with no server groups defined
        final Set<Deployment> failedDeployments = new HashSet<>();
        failedDeployments.add(createDefaultDeployment("test-failed-deployment-1.war"));
        failedDeployments
                .add(createDefaultDeployment("test-failed-deployment-2.war").setServerGroups(Collections.<String> emptySet()));
        assertFailed(deploymentManager.deploy(failedDeployments));
        for (Deployment failedDeployment : failedDeployments) {
            assertDeploymentDoesNotExist(failedDeployment);
        }
    }

    @Test
    public void testFailedForceDeploy() throws Exception {
        // Expect a failure with no server groups defined
        final Deployment failedDeployment = createDefaultDeployment("test-failed-deployment.war")
                .setServerGroups(Collections.<String> emptySet());
        assertFailed(deploymentManager.forceDeploy(failedDeployment));
        assertDeploymentDoesNotExist(failedDeployment);

    }

    @Test
    public void testFailedRedeploy() throws Exception {
        // Expect a failure with no server groups defined
        assertFailed(deploymentManager
                .redeploy(createDefaultDeployment("test-redeploy.war").setServerGroups(Collections.<String> emptySet())));
    }

    @Test
    public void testFailedUndeploy() throws Exception {
        // Undeploy with an additional server-group where the deployment does not exist
        undeployForSuccess(
                UndeployDescription.of("test-undeploy-multi-server-groups.war")
                        .setFailOnMissing(false)
                        .setRemoveContent(false)
                        .addServerGroup("other-server-group"),
                Collections.singleton(DEFAULT_SERVER_GROUP), false);

        // Undeploy with an additional server-group where the deployment does not exist
        final Deployment deployment = createDefaultDeployment("test-undeploy-multi-server-groups-failed.war");
        deployForSuccess(deployment);
        final DeploymentResult result = deploymentManager.undeploy(
                UndeployDescription.of(deployment)
                        .setFailOnMissing(true)
                        .addServerGroup("other-server-group"));
        assertFailed(result);
        assertDeploymentExists(deployment, true);
    }

    @Test
    public void testDeploymentQueries() throws Exception {
        Assert.assertTrue("No deployments should exist.", deploymentManager.getDeployments().isEmpty());
        Assert.assertTrue("No deployments should exist.", deploymentManager.getDeploymentNames().isEmpty());
        Assert.assertTrue(String.format("No deployments should exist on %s", DEFAULT_SERVER_GROUP),
                deploymentManager.getDeployments(DEFAULT_SERVER_GROUP).isEmpty());
    }

    @Override
    protected ModelControllerClient getClient() {
        return client;
    }

    @Override
    protected ModelNode createDeploymentResourceAddress(final String deploymentName) throws IOException {
        return ServerHelper.determineHostAddress(getClient())
                .add(ClientConstants.SERVER, "server-one")
                .add(ClientConstants.DEPLOYMENT, deploymentName);
    }

    @Override
    Deployment configureDeployment(final Deployment deployment) {
        return deployment.addServerGroups(DEFAULT_SERVER_GROUP);
    }
}
