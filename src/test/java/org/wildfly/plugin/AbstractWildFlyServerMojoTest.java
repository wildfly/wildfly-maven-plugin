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

package org.wildfly.plugin;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.junit.runner.RunWith;
import org.wildfly.plugin.common.ServerOperations;
import org.wildfly.plugin.runner.WildFlyTestRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyTestRunner.class)
public abstract class AbstractWildFlyServerMojoTest extends AbstractWildFlyMojoTest {

    @Inject
    protected ModelControllerClient client;

    protected ModelNode executeOperation(final ModelNode op) throws IOException {
        final ModelNode result = client.execute(op);
        assertTrue(ServerOperations.getFailureDescriptionAsString(result), ServerOperations.isSuccessfulOutcome(result));
        return result;
    }

    protected static ModelNode createAddress(final String... resourceParts) {
        final ModelNode address = new ModelNode().setEmptyList();
        @SuppressWarnings("unchecked")
        final List<String> parts = new ArrayList<String>(Arrays.asList(resourceParts));
        if (!parts.isEmpty()) {
            if (parts.size() % 2 != 0) {
                parts.add("*");
            }
            String key = null;
            for (String value : parts) {
                if (key == null) {
                    key = value;
                } else {
                    address.add(key, value);
                    key = null;
                }
            }
        }
        return address;
    }

}
