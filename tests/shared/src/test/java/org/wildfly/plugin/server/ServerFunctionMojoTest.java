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

import java.net.UnknownHostException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.wildfly.plugin.tests.AbstractWildFlyMojoTest;
import org.wildfly.plugin.tests.Environment;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ServerFunctionMojoTest extends AbstractWildFlyMojoTest {

    @Test
    public void testStartStandalone() throws Exception {
        final StartMojo mojo = getStartMojo();
        mojo.execute();
        try (final ModelControllerClient client = createClient()) {
            // Verify the server is running
            Assert.assertTrue("The start goal did not start the server.", ServerHelper.isStandaloneRunning(client));
        }
    }

    @Test
    public void testShutdownStandalone() throws Exception {
        // Start up the server and ensure it's running
        final StartMojo startMojo = getStartMojo();
        startMojo.execute();
        try (final ModelControllerClient client = createClient()) {
            // Verify the server is running
            Assert.assertTrue("The start goal did not start the server.", ServerHelper.isStandaloneRunning(client));
        }

        // Look up the stop mojo and attempt to stop
        final ShutdownMojo stopMojo = lookupMojoAndVerify("shutdown", "shutdown-pom.xml");
        stopMojo.execute();
        try (final ModelControllerClient client = createClient()) {
            // Verify the server is running
            Assert.assertFalse("The start goal did not start the server.", ServerHelper.isStandaloneRunning(client));
        }
    }

    private StartMojo getStartMojo() throws Exception {
        // Start up the server and ensure it's running
        final StartMojo startMojo = lookupMojoAndVerify("start", "start-pom.xml");
        setValue(startMojo, "jbossHome", Environment.WILDFLY_HOME.toString());
        setValue(startMojo, "serverArgs", new String[] {"-Djboss.management.http.port=" + Integer.toString(Environment.PORT)});
        return startMojo;
    }

    private static ModelControllerClient createClient() throws UnknownHostException {
        return ModelControllerClient.Factory.create(Environment.HOSTNAME, Environment.PORT);
    }
}
