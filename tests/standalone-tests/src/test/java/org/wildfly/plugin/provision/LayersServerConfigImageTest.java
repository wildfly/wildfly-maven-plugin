/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.wildfly.plugin.categories.ChannelsRequired;
import org.wildfly.plugin.tests.TestEnvironment;

@Category(ChannelsRequired.class)
public class LayersServerConfigImageTest extends AbstractImageTest {

    @Test
    public void layersConfigurationFileName() throws Exception {
        Assume.assumeFalse("This test is flaky on Windows, ignore it on Windows.", TestEnvironment.isWindows());
        assertConfigFileName("image-layers-server-config", "SERVER_ARGS=-c=standalone-core.xml");
    }
}
