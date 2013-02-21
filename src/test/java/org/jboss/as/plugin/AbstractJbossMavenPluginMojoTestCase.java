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

package org.jboss.as.plugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.apache.maven.settings.io.SettingsParseException;
import org.apache.maven.settings.io.SettingsReader;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.plugin.common.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;

/**
 *
 * @author swm16 (swm16@psu.edu)
 */
public abstract class AbstractJbossMavenPluginMojoTestCase extends AbstractMojoTestCase {
    static final String BASE_CONFIG_DIR = System.getProperty("jboss.test.config.dir");

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Gets a settings.xml file from the input File and prepares it to be
     * attached to a pom.xml
     *
     * @param userSettingsFile file object pointing to the candidate settings file
     * @return the settings object
     * @throws IOException - if the settings file can't be read
     */
    private Settings getSettingsFile(File userSettingsFile) throws IOException {
        Map<String, ?> options = Collections.singletonMap( SettingsReader.IS_STRICT, Boolean.TRUE );
        SettingsReader reader = new DefaultSettingsReader();

        Settings settings = null;
        try {
            settings = reader.read(userSettingsFile, options);
        } catch(SettingsParseException e) {

        }

        return settings;
    }

    protected File getPom(final String name) {
        return new File(BASE_CONFIG_DIR, name);
    }

    /**
     * Locates the POM based on it's name and verifies that it exists.
     *
     * @param name the name of the pom file
     *
     * @return the pom file
     */
    protected File getPomAndVerify(final String name) {
        File file = getPom(name);
        assertNotNull(file);
        assertTrue(file.exists());
        return file;
    }

    protected File getSettings(final String name) {
        return new File(BASE_CONFIG_DIR, name);
    }

    /**
     * Locates the settings file based on it's name and verifies that it exists.
     *
     * @param name the name of the settings file
     *
     * @return the settings file
     */
    protected File getSettingsAndVerify(final String name) {
        File file = getSettings(name);
        assertNotNull(file);
        assertTrue(file.exists());
        return file;
    }

    /**
     * Looks up the specified mojo by name, passing it the POM file that
     * references it, then verifying that the lookup was successful.
     *
     * @param mojoName the name of the mojo being tested
     * @param pomFile the pom.xml file to be used during testing
     * @return the Mojo object under test
     * @throws Exception if the mojo can not be found
     */
    @SuppressWarnings("unchecked")
    public <T extends Mojo> T lookupMojoAndVerify(String mojoName, File pomFile) throws Exception {
        T mojo = (T) lookupMojo(mojoName, pomFile);
        assertNotNull(mojo);
        return mojo;
    }

    /**
     * Looks up the specified mojo by name, passing it the POM file that
     * references it and a settings file that configures it, then verifying
     * that the lookup was successful.
     *
     * @param mojoName the name of the mojo being tested
     * @param pomFile the pom.xml file to be used during testing
     * @param settingsFile the settings.xml file to be used during testing
     * @return the Mojo object under test
     * @throws Exception if the mojo can not be found
     */
    @SuppressWarnings("unchecked")
    public <T extends Mojo> T lookupMojoVerifyAndApplySettings(String mojoName, File pomFile, File settingsFile) throws Exception {
        T mojo = (T) lookupMojo(mojoName, pomFile);
        assertNotNull(mojo);
        setVariableValueToObject(mojo, "settings", getSettingsFile(settingsFile));
        return mojo;
    }

    protected ModelNode executeOperation(final ModelControllerClient client, final ModelNode op) throws IOException {
        final ModelNode result = client.execute(op);
        assertTrue(Operations.getFailureDescription(result), Operations.isSuccessfulOutcome(result));
        return result;
    }

}
