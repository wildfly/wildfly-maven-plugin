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

package org.wildfly.plugin.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.testing.MojoRule;
import org.junit.Rule;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractWildFlyMojoTest {

    protected final String DEPLOYMENT_NAME = System.getProperty("wildfly.test.deployment.name");
    protected final String BASE_CONFIG_DIR = System.getProperty("wildfly.test.config.dir");

    @Rule
    public MojoRule rule = new MojoRule() {
        @Override
        protected void before() throws Throwable {
        }

        @Override
        protected void after() {
        }
    };

    /**
     * Returns the deployment test.war from the test resources.
     *
     * @return the deployment
     */
    protected File getDeployment() {
        return new File(BASE_CONFIG_DIR, DEPLOYMENT_NAME);
    }

    /**
     * Configures a MOJO based on the goal and the pom file then verifies the goal MOJO was found.
     *
     * @param goal     the name of the goal being tested
     * @param fileName the name of the POM file to be used during testing
     *
     * @return the MOJO object under test
     *
     * @throws java.lang.AssertionError if the MOJO was not found
     */
    @SuppressWarnings("unchecked")
    public <T extends Mojo> T lookupMojoAndVerify(final String goal, final String fileName) throws Exception {
        final Path baseDir = Paths.get(BASE_CONFIG_DIR);
        assertTrue("Not a directory: " + BASE_CONFIG_DIR, Files.exists(baseDir));
        final Path pom = Paths.get(BASE_CONFIG_DIR, fileName);
        assertTrue(Files.exists(pom));
        Files.copy(pom, baseDir.resolve("pom.xml"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        T mojo = (T) rule.lookupConfiguredMojo(baseDir.toFile(), goal);
        assertNotNull(mojo);
        return mojo;
    }

}
