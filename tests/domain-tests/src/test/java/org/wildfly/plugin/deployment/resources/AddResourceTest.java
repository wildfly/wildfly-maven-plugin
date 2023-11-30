/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment.resources;

import static org.junit.Assert.*;

import java.util.Collections;

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

        final AddResourceMojo addResourceMojo = find("add-resource-with-composite-pom.xml");
        try {
            addResourceMojo.execute();
        } catch (Exception ex) {
            fail(ex.getMessage());
        }

    }

    @Test
    public void testCanAddResource() throws Exception {

        AddResourceMojo addResourceMojo = find("add-resource-pom.xml");
        try {
            addResourceMojo.execute();
        } catch (Exception ex) {
            fail(ex.getMessage());
        }

    }

    @Test
    public void testCanAddXaDataSource() throws Exception {

        final AddResourceMojo addResourceMojo = find("add-resource-xa-datasource.xml");
        try {
            addResourceMojo.execute();
        } catch (Exception ex) {
            fail(ex.getMessage());
        }

    }

    private AddResourceMojo find(final String pom) throws Exception {
        final AddResourceMojo addResourceMojo = lookupMojoAndVerify("add-resource", pom);
        // Profiles are required to be set and when there is a property defined on an attribute parameter the test
        // harness does not set the fields
        setValue(addResourceMojo, "profiles", Collections.singletonList("full"));
        return addResourceMojo;
    }

}
