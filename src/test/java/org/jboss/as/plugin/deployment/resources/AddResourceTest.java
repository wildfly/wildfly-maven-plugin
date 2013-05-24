/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.plugin.deployment.resources;

import java.io.File;

import org.apache.maven.project.MavenProject;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.plugin.AbstractItTestCase;
import org.jboss.as.plugin.deployment.resource.AddResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * AddResource test case
 *
 * @author <a href="mailto:dave.himself@gmail.com">Dave Heath</a>
 */
public class AddResourceTest extends AbstractItTestCase {

    @ContainerResource
    private ManagementClient managementClient;

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }


    @Test
    public void testCanAddCompositeResource() throws Exception {
        final MavenProject mavenProject = new MavenProject();
        mavenProject.setPackaging("war");

        final File pom = getPom("add-resource-with-composite-pom.xml");

        final AddResource addResourceMojo = lookupMojoAndVerify("add-resource", pom);
        try {
            addResourceMojo.execute();
        } catch (Exception ex) {
            fail(ex.getMessage());
        }

    }

    @Test
    public void testCanAddResource() throws Exception {
        final MavenProject mavenProject = new MavenProject();
        mavenProject.setPackaging("war");

        final File pom = getPom("add-resource-pom.xml");

        final AddResource addResourceMojo = lookupMojoAndVerify("add-resource", pom);
        try {
            addResourceMojo.execute();
        } catch (Exception ex) {
            fail(ex.getMessage());
        }

    }

    @Test
    public void testThrowsExceptionWhenAddingResourceInExecution() throws Exception {
        final MavenProject mavenProject = new MavenProject();
        mavenProject.setPackaging("war");

        final File pom = getPom("add-resource-in-execution-pom.xml");

        final AddResource addResourceMojo = lookupMojoAndVerify("add-resource", pom);
        try {
            addResourceMojo.execute();
        } catch (Exception expectedToBeThrown) {
            assertNotNull(expectedToBeThrown);
        }

    }

}
