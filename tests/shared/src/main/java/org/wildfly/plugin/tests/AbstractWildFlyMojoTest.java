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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.junit.Assert;
import org.junit.Rule;
import org.wildfly.plugin.core.Deployment;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractWildFlyMojoTest {

    protected final String DEPLOYMENT_NAME = "test.war";
    protected static final String BASE_CONFIG_DIR = System.getProperty("wildfly.test.config.dir");

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
    protected Deployment getDeployment() {
        return Deployment.of(Paths.get(BASE_CONFIG_DIR, "target", DEPLOYMENT_NAME));
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
    public <T extends Mojo> T lookupMojoAndVerify(final String goal, final String fileName) throws Exception {
        final Path pom = getPomFile(fileName);
        MavenProject project = readMavenProject(pom);
        @SuppressWarnings("unchecked")
        T mojo = (T) rule.lookupConfiguredMojo(project, goal);
        assertNotNull(mojo);
        setDefaultEnvironment(mojo);
        return mojo;
    }

    public static Path getPomFile(String fileName) {
        final Path baseDir = getBaseDir();
        final Path pom = baseDir.resolve(fileName);
        assertTrue(Files.exists(pom));
        return pom;
    }

    public static Path getBaseDir() {
        final Path baseDir = Paths.get(BASE_CONFIG_DIR);
        assertTrue("Not a directory: " + BASE_CONFIG_DIR, Files.exists(baseDir));
       return baseDir;
    }

    public MavenProject readMavenProject(Path pom)
            throws Exception {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setBaseDirectory(pom.getParent().toFile());
        ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
        configuration.setRepositorySession(new DefaultRepositorySystemSession());
        MavenProject project = rule.lookup(ProjectBuilder.class).build(pom.toFile(), configuration).getProject();
        Assert.assertNotNull(project);
        return project;
    }

    protected static void setDefaultEnvironment(final Mojo instance) throws NoSuchFieldException, IllegalAccessException {
        setValue(instance, "port", TestEnvironment.PORT);
        setValue(instance, "hostname", TestEnvironment.HOSTNAME);
    }

    protected static void setValue(final Object instance, final String name, final Object value) throws NoSuchFieldException, IllegalAccessException {
        setValue(instance.getClass(), instance, name, value);
    }

    private static void setValue(final Class<?> clazz, final Object instance, final String name, final Object value) throws NoSuchFieldException, IllegalAccessException {
        if (clazz == null || Object.class.getName().equals(clazz.getName())) {
            throw new NoSuchFieldException("Field " + name + " not found on " + instance.getClass().getName());
        }
        try {
            final Field field = clazz.getDeclaredField(name);
            setValue(field, instance, value);
        } catch (NoSuchFieldException e) {
            setValue(clazz.getSuperclass(), instance, name, value);
        }
    }

    private static void setValue(final Field field, final Object instance, final Object value) throws IllegalAccessException {
        field.setAccessible(true);
        field.set(instance, value);
    }

}
