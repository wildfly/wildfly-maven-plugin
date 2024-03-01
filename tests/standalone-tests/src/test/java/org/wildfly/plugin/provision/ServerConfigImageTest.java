/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import org.junit.Assume;
import org.junit.Test;
import org.wildfly.plugin.tests.TestEnvironment;

public class ServerConfigImageTest extends AbstractImageTest {

    @Test
    public void serverConfig() throws Exception {
        Assume.assumeFalse("This test is flaky on Windows, ignore it on Windows.", TestEnvironment.isWindows());
        assertConfigFileName("image-server-config", "SERVER_ARGS=-c=standalone-microprofile.xml");
    }
}
