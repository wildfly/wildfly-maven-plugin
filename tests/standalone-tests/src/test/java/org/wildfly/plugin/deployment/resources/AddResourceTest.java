/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment.resources;

import static org.junit.Assert.*;

import org.junit.Test;
import org.wildfly.plugin.deployment.resource.AddResourceMojo;
import org.wildfly.plugin.tests.AbstractWildFlyServerMojoTest;

/**
 * AddResource test case
 *
 * @author <a href="mailto:dave.himself@gmail.com">Dave Heath</a>
 */
// @Ignore("Composite operations don't seem to be working with datasources")
public class AddResourceTest extends AbstractWildFlyServerMojoTest {

    @Test
    public void testCanAddCompositeResource() throws Exception {

        final AddResourceMojo addResourceMojo = lookupMojoAndVerify("add-resource", "add-resource-with-composite-pom.xml");
        try {
            addResourceMojo.execute();
        } catch (Exception ex) {
            fail(ex.getMessage());
        }

    }

    @Test
    public void testCanAddResource() throws Exception {

        final AddResourceMojo addResourceMojo = lookupMojoAndVerify("add-resource", "add-resource-pom.xml");
        try {
            addResourceMojo.execute();
        } catch (Exception ex) {
            fail(ex.getMessage());
        }

    }

    @Test
    public void testCanAddXaDataSource() throws Exception {

        final AddResourceMojo addResourceMojo = lookupMojoAndVerify("add-resource", "add-resource-xa-datasource.xml");
        try {
            addResourceMojo.execute();
        } catch (Exception ex) {
            fail(ex.getMessage());
        }

    }

}
