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

package org.jboss.as.plugin.common;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;

import org.apache.maven.plugin.logging.Log;
import org.jboss.as.plugin.AbstractJbossMavenPluginMojoTestCase;
import org.jboss.as.plugin.deployment.Deploy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

/**
 * @author stevemoyer
 *
 */
public class AbstractServerConnectionTest extends AbstractJbossMavenPluginMojoTestCase {

    Log log;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        super.setUp();
        log = mock(Log.class);
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Tests that if there is an <id> provided in the pom.xml file but no
     * username and password, and there is no settings.xml file in the
     * plugin's context, then the plugin falls back to prompting for
     * credentials on the CLI.
     */
    @Test
    public void testIdProvidedInPomButNoSettingsFile() throws Exception{
        final  File pom = getPomAndVerify("id-provided-pom.xml");
        final Deploy mojo = lookupMojoAndVerify("deploy", pom);
        mojo.setLog(log);
        mojo.getCallbackHandler();
        verify(log).debug(Deploy.DEBUG_MESSAGE_NO_SETTINGS_FILE);
    }

    /**
     * Tests that if there is an <id> provided in the pom.xml file but no
     * username and password, and there is a settings.xml file that doesn't
     * match the id of the server, then the plugin falls back to prompting
     * for credentials on the CLI.
     */
    @Test
    public void testIdProvidedInPomButNoServerSection() throws Exception {
        final File pom = getPomAndVerify("id-provided-pom.xml");
        final File settings = getSettingsAndVerify("missing-id-settings.xml");
        final Deploy mojo = lookupMojoVerifyAndApplySettings("deploy",  pom, settings);
        mojo.setLog(log);
        mojo.getCallbackHandler();
        verify(log).debug(Deploy.DEBUG_MESSAGE_NO_SERVER_SECTION);
    }

    /**
     * Tests that if there is an <id> provided in the pom.xml file but no
     * username and password, and there is a settings.xml file that has a
     * <server> section that matches the id, but there is no username and
     * password specified, then the plugin falls back to prompting for
     * credentials on the CLI.
     */
    @Test
    public void testIdProvidedInPomButNoCredentials() throws Exception {
        final File pom = getPomAndVerify("id-provided-pom.xml");
        final File settings = getSettingsAndVerify("id-provided-settings.xml");
        final Deploy mojo = lookupMojoVerifyAndApplySettings("deploy", pom, settings);
        mojo.setLog(log);
        mojo.getCallbackHandler();
        final InOrder inOrder = inOrder(log);
        inOrder.verify(log).debug(Deploy.DEBUG_MESSAGE_SETTINGS_HAS_ID);
        inOrder.verify(log).debug(Deploy.DEBUG_MESSAGE_NO_CREDS);
    }

    /**
     * Test that if credentials are provided in the pom.xml file, they are used
     * regardless of whether an <id> element is also present.
     */
    @Test
    public void testCredentialsProvidedInPom() throws Exception {
        final File pom = getPomAndVerify("credentials-provided-pom.xml");
        final Deploy mojo = lookupMojoAndVerify("deploy", pom);
        mojo.setLog(log);
        mojo.getCallbackHandler();
    }

    /**
     * Tests that if there is an <id> provided in the pom.xml file but no
     * username and password, and there is a settings.xml file that has a
     * <server> section that matches the id, and the section includes
     * credentials, then they are used by the plugin.
     */
    @Test
    public void testCredentialsProvidedInSettings() throws Exception {
        final File pom = getPomAndVerify("id-provided-pom.xml");
        final File settings = getSettingsAndVerify("credentials-provided-settings.xml");
        final Deploy mojo = lookupMojoVerifyAndApplySettings("deploy", pom, settings);
        mojo.setLog(log);
        mojo.getCallbackHandler();
        final InOrder inOrder = inOrder(log);
        inOrder.verify(log).debug(Deploy.DEBUG_MESSAGE_SETTINGS_HAS_ID);
        inOrder.verify(log).debug(Deploy.DEBUG_MESSAGE_SETTINGS_HAS_CREDS);
    }

    /**
     * Test that if there is no <id> element and no credentials in the pom.xml
     * file, then it falls back to prompting for them on the CLI.
     */
    @Test
    public void testNoCredentialsOrIdInPom() throws Exception {
        final File pom = getPomAndVerify("missing-id-pom.xml");
        final Deploy mojo = lookupMojoAndVerify("deploy", pom);
        mojo.setLog(log);
        mojo.getCallbackHandler();
        verify(log).debug(Deploy.DEBUG_MESSAGE_NO_ID);
    }

}
