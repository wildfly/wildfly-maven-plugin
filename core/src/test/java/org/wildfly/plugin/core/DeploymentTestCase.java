/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.plugin.core;

import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.plugin.core.common.Simple;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DeploymentTestCase {
    private static final Path TEST_DEPLOYMENT_DIR = Paths.get(System.getProperty("test.deployment.dir", ".")).toAbsolutePath()
            .normalize();
    private static final String TEST_DEPLOYMENT_FILE_NAME = TEST_DEPLOYMENT_DIR.getFileName().toString();

    @Test
    public void testPathDeploymentName() {
        final Deployment deployment = Deployment.of(TEST_DEPLOYMENT_DIR);
        Assert.assertEquals(TEST_DEPLOYMENT_FILE_NAME, deployment.getName());

        // Set the file name and expect the new name
        final String name = "changed.war";
        deployment.setName(name);
        Assert.assertEquals(name, deployment.getName());

        // Reset the name to null and expect the file name
        deployment.setName(null);
        Assert.assertEquals(TEST_DEPLOYMENT_FILE_NAME, deployment.getName());
    }

    @Test
    public void testInputStreamDeploymentName() {
        final String name = "test.war";
        final WebArchive war = ShrinkWrap.create(WebArchive.class, name)
                .addClass(Simple.class);
        final TestInputStream in = new TestInputStream(war);
        final Deployment deployment = Deployment.of(in, name);
        Assert.assertEquals(name, deployment.getName());
        Assert.assertTrue("Expected to the input stream to be closed", in.closed.get());

        // Set the file name and expect the new name
        final String changedName = "changed.war";
        deployment.setName(changedName);
        Assert.assertEquals(changedName, deployment.getName());

        // Reset the name to null and expect a failure
        try {
            deployment.setName(null);
            Assert.fail("Expected setting the name to null for an input stream to fail.");
        } catch (IllegalArgumentException ignore) {
        }
    }

    private static class TestInputStream extends FilterInputStream {
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private TestInputStream(final Archive<?> archive) {
            super(archive.as(ZipExporter.class).exportAsInputStream());
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                closed.set(true);
            }
        }
    }
}
