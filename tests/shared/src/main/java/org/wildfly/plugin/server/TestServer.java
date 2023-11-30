/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.server;

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.core.DeploymentManager;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface TestServer {

    void start();

    void stop();

    ModelControllerClient getClient();

    DeploymentManager getDeploymentManager();
}
