/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment.resources;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.wildfly.plugin.deployment.resource.AddResourceMojo;
import org.wildfly.plugin.tests.TestEnvironment;
import org.wildfly.testing.junit.extension.annotation.WildFlyDomainTest;

/**
 * AddResource test case
 *
 * @author <a href="mailto:dave.himself@gmail.com">Dave Heath</a>
 */
@MojoTest
@WildFlyDomainTest
@Basedir(TestEnvironment.TEST_PROJECT_PATH)
public class AddResourceTest {

    @Test
    @InjectMojo(goal = "add-resource", pom = "add-resource-with-composite-pom.xml")
    public void testCanAddCompositeResource(final AddResourceMojo addResourceMojo) {
        Assertions.assertDoesNotThrow(addResourceMojo::execute);

    }

    @Test
    @InjectMojo(goal = "add-resource", pom = "add-resource-pom.xml")
    public void testCanAddResource(final AddResourceMojo addResourceMojo) {
        Assertions.assertDoesNotThrow(addResourceMojo::execute);

    }

    @Test
    @InjectMojo(goal = "add-resource", pom = "add-resource-xa-datasource.xml")
    public void testCanAddXaDataSource(final AddResourceMojo addResourceMojo) {
        Assertions.assertDoesNotThrow(addResourceMojo::execute);

    }

}
