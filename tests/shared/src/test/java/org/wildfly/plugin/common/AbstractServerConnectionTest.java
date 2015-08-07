/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.common;

import static org.junit.Assert.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.apache.maven.settings.io.SettingsParseException;
import org.apache.maven.settings.io.SettingsReader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.wildfly.plugin.deployment.DeployMojo;
import org.wildfly.plugin.tests.AbstractWildFlyMojoTest;

/**
 * @author stevemoyer
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AbstractServerConnectionTest extends AbstractWildFlyMojoTest {

    Log log;

    /**
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        log = mock(Log.class);
    }

    /**
     * Tests that if there is an <id> provided in the pom.xml file but no
     * username and password, and there is no settings.xml file in the
     * plugin's context, then the plugin falls back to prompting for
     * credentials on the CLI.
     */
    @Test
    public void testIdProvidedInPomButDefaultSettingsFile() throws Exception {
        final DeployMojo mojo = lookupMojoVerifyAndApplySettings("deploy", "id-provided-pom.xml", "default-settings.xml");
        mojo.setLog(log);
        mojo.getClientConfiguration();
        verify(log).debug(DeployMojo.DEBUG_MESSAGE_NO_SERVER_SECTION);
    }

    /**
     * Tests that if there is an <id> provided in the pom.xml file but no
     * username and password, and there is a settings.xml file that doesn't
     * match the id of the server, then the plugin falls back to prompting
     * for credentials on the CLI.
     */
    @Test
    public void testIdProvidedInPomButNoServerSection() throws Exception {
        final DeployMojo mojo = lookupMojoVerifyAndApplySettings("deploy", "id-provided-pom.xml", "missing-id-settings.xml");
        mojo.setLog(log);
        mojo.getClientConfiguration();
        verify(log).debug(DeployMojo.DEBUG_MESSAGE_NO_SERVER_SECTION);
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
        final DeployMojo mojo = lookupMojoVerifyAndApplySettings("deploy", "id-provided-pom.xml", "id-provided-settings.xml");
        mojo.setLog(log);
        mojo.getClientConfiguration();
        final InOrder inOrder = inOrder(log);
        inOrder.verify(log).debug(DeployMojo.DEBUG_MESSAGE_SETTINGS_HAS_ID);
        inOrder.verify(log).debug(DeployMojo.DEBUG_MESSAGE_NO_CREDS);
    }

    /**
     * Test that if credentials are provided in the pom.xml file, they are used
     * regardless of whether an <id> element is also present.
     */
    @Test
    public void testCredentialsProvidedInPom() throws Exception {
        final DeployMojo mojo = lookupMojoAndVerify("deploy", "credentials-provided-pom.xml");
        mojo.setLog(log);
        mojo.getClientConfiguration();
    }

    /**
     * Tests that if there is an <id> provided in the pom.xml file but no
     * username and password, and there is a settings.xml file that has a
     * <server> section that matches the id, and the section includes
     * credentials, then they are used by the plugin.
     */
    @Test
    public void testCredentialsProvidedInSettings() throws Exception {
        final DeployMojo mojo = lookupMojoVerifyAndApplySettings("deploy", "id-provided-pom.xml", "credentials-provided-settings.xml");
        mojo.setLog(log);
        mojo.getClientConfiguration();
        final InOrder inOrder = inOrder(log);
        inOrder.verify(log).debug(DeployMojo.DEBUG_MESSAGE_SETTINGS_HAS_ID);
        inOrder.verify(log).debug(DeployMojo.DEBUG_MESSAGE_SETTINGS_HAS_CREDS);
    }

    /**
     * Test that if there is no <id> element and no credentials in the pom.xml
     * file, then it falls back to prompting for them on the CLI.
     */
    @Test
    public void testNoCredentialsOrIdInPom() throws Exception {
        final DeployMojo mojo = lookupMojoAndVerify("deploy", "missing-id-pom.xml");
        mojo.setLog(log);
        mojo.getClientConfiguration();
        verify(log).debug(DeployMojo.DEBUG_MESSAGE_NO_ID);
    }

    /**
     * Looks up the specified mojo by name, passing it the POM file that
     * references it and a settings file that configures it, then verifying
     * that the lookup was successful.
     *
     * @param mojoName         the name of the mojo being tested
     * @param pomFileName      the name of the pom.xml file to be used during testing
     * @param settingsFileName the settings.xml file to be used during testing
     *
     * @return the Mojo object under test
     *
     * @throws Exception if the mojo can not be found
     */
    @SuppressWarnings("unchecked")
    private <T extends Mojo> T lookupMojoVerifyAndApplySettings(final String mojoName, final String pomFileName, final String settingsFileName) throws Exception {
        T mojo = lookupMojoAndVerify(mojoName, pomFileName);
        rule.setVariableValueToObject(mojo, "settings", getSettingsFile(settingsFileName));
        return mojo;
    }

    /**
     * Gets a settings.xml file from the input File and prepares it to be
     * attached to a pom.xml
     *
     * @param fileName file object pointing to the candidate settings file
     *
     * @return the settings object
     *
     * @throws java.io.IOException - if the settings file can't be read
     */
    private Settings getSettingsFile(final String fileName) throws IOException {
        Map<String, ?> options = Collections.singletonMap(SettingsReader.IS_STRICT, Boolean.TRUE);
        SettingsReader reader = new DefaultSettingsReader();
        final File settingsFile = new File(BASE_CONFIG_DIR, fileName);
        assertTrue("Could not find settings file: " + settingsFile.getAbsolutePath(), settingsFile.exists());

        Settings settings = null;
        try {
            settings = reader.read(settingsFile, options);
        } catch (SettingsParseException e) {

        }

        return settings;
    }

}
