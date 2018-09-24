/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

import java.io.BufferedReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.plugin.core.ServerHelper;
import org.wildfly.plugin.tests.AbstractWildFlyMojoTest;
import org.wildfly.plugin.tests.TestEnvironment;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ServerFunctionMojoTest extends AbstractWildFlyMojoTest {

    @After
    public void shutdown() throws Exception {
        // Ensure the server is shutdown
        try (ModelControllerClient client = createClient()) {
            boolean isDomain;
            try {
                isDomain = ServerHelper.isDomainServer(client);
            } catch (Exception ignore) {
                isDomain = false;
            }
            // Ensure we shutdown the server
            if (isDomain) {
                if (ServerHelper.isDomainRunning(client)) {
                    ServerHelper.shutdownDomain(client);
                }
            } else {
                if (ServerHelper.isStandaloneRunning(client)) {
                    ServerHelper.shutdownStandalone(client);
                }
            }
        }
    }

    @Test
    public void testStartStandalone() throws Exception {
        final StartMojo mojo = getStartMojo();
        mojo.execute();
        try (ModelControllerClient client = createClient()) {
            // Verify the server is running
            Assert.assertTrue("The start goal did not start the server.", ServerHelper.isStandaloneRunning(client));
            Assert.assertFalse("This should be a standalone server, but found a domain server.", ServerHelper.isDomainServer(client));
        }
    }

    @Test
    public void testShutdownStandalone() throws Exception {
        // Start up the server and ensure it's running
        final StartMojo startMojo = getStartMojo();
        startMojo.execute();
        try (ModelControllerClient client = createClient()) {
            // Verify the server is running
            Assert.assertTrue("The start goal did not start the server.", ServerHelper.isStandaloneRunning(client));
        }

        // Look up the stop mojo and attempt to stop
        final ShutdownMojo stopMojo = lookupMojoAndVerify("shutdown", "shutdown-pom.xml");
        stopMojo.execute();
        try (ModelControllerClient client = createClient()) {
            // Verify the server is running
            Assert.assertFalse("The start goal did not start the server.", ServerHelper.isStandaloneRunning(client));
        }
    }

    @Test
    public void testStartAndAddUserStandalone() throws Exception {
        final StartMojo mojo = getStartMojo();
        // The MOJO lookup replaces a configured add-users configuration with a default value so we need to manually
        // create and insert the field for testing
        setValue(mojo, "addUser", createAddUsers("admin:admin.1234:admin", "user:user.1234:user,mgmt::true"));
        mojo.execute();
        try (ModelControllerClient client = createClient()) {
            // Verify the server is running
            Assert.assertTrue("The start goal did not start the server.", ServerHelper.isStandaloneRunning(client));
        }

        final Path standaloneConfigDir = TestEnvironment.WILDFLY_HOME.resolve("standalone").resolve("configuration");

        // Check the management users
        final Path mgmtUsers = standaloneConfigDir.resolve("mgmt-users.properties");
        Assert.assertTrue("File " + mgmtUsers + " does not exist", Files.exists(mgmtUsers));
        Assert.assertTrue("User admin was not added to the mgmt-user.properties file", fileContains(mgmtUsers, "admin="));

        // Check the management users
        final Path mgmtGroups = standaloneConfigDir.resolve("mgmt-groups.properties");
        Assert.assertTrue("File " + mgmtGroups + " does not exist", Files.exists(mgmtGroups));
        Assert.assertTrue("User admin was not added to the mgmt-groups.properties file", fileContains(mgmtGroups, "admin=admin"));

        // Check the application users
        final Path appUsers = standaloneConfigDir.resolve("application-users.properties");
        Assert.assertTrue("File " + appUsers + " does not exist", Files.exists(appUsers));
        Assert.assertTrue("User user was not added to the application-user.properties file", fileContains(appUsers, "user="));

        // Check the management users
        final Path appGroups = standaloneConfigDir.resolve("application-roles.properties");
        Assert.assertTrue("File " + appGroups + " does not exist", Files.exists(appGroups));
        Assert.assertTrue("User user was not added to the application-roles.properties file", fileContains(appGroups, "user=user,mgmt"));
    }

    @Test
    public void testStartDomain() throws Exception {
        final StartMojo mojo = getStartMojo("start-domain-pom.xml");
        mojo.execute();
        try (DomainClient client = DomainClient.Factory.create(createClient())) {
            // Verify the server is running
            Assert.assertTrue("The start goal did not start the server.", ServerHelper.isDomainRunning(client));
            Assert.assertTrue("This should be a domain server server, but found a standalone server.", ServerHelper.isDomainServer(client));
        }
    }

    @Test
    public void testShutdownDomain() throws Exception {
        // Start up the server and ensure it's running
        final StartMojo startMojo = getStartMojo("start-domain-pom.xml");
        startMojo.execute();
        try (DomainClient client = DomainClient.Factory.create(createClient())) {
            // Verify the server is running
            Assert.assertTrue("The start goal did not start the server.", ServerHelper.isDomainRunning(client));
        }

        // Look up the stop mojo and attempt to stop
        final ShutdownMojo stopMojo = lookupMojoAndVerify("shutdown", "shutdown-pom.xml");
        stopMojo.execute();
        try (DomainClient client = DomainClient.Factory.create(createClient())) {
            // Verify the server is running
            Assert.assertFalse("The start goal did not start the server.", ServerHelper.isDomainRunning(client));
        }
    }

    private StartMojo getStartMojo() throws Exception {
        return getStartMojo("start-pom.xml");
    }

    private StartMojo getStartMojo(final String pomFile) throws Exception {
        // Start up the server and ensure it's running
        final StartMojo startMojo = lookupMojoAndVerify("start", pomFile);
        setValue(startMojo, "jbossHome", TestEnvironment.WILDFLY_HOME.toString());
        setValue(startMojo, "serverArgs", new String[]{"-Djboss.management.http.port=" + Integer.toString(TestEnvironment.PORT)});
        return startMojo;
    }

    private static ModelControllerClient createClient() throws UnknownHostException {
        return ModelControllerClient.Factory.create(TestEnvironment.HOSTNAME, TestEnvironment.PORT);
    }

    private static boolean fileContains(final Path path, final String text) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static AddUser createAddUsers(final String... userStrings) throws NoSuchFieldException, IllegalAccessException {
        final AddUser result = new AddUser();
        final List<User> users = new ArrayList<>(userStrings.length);
        for (String userString : userStrings) {
            users.add(createUser(userString));
        }
        setValue(result, "users", users);
        return result;
    }

    private static User createUser(final String userString) {
        final User user = new User();
        user.set(userString);
        return user;
    }
}
