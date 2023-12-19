/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.tests.runner;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ServiceLoader;

import javax.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.wildfly.plugin.server.TestServer;
import org.wildfly.plugin.tools.DeploymentManager;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class WildFlyTestRunner extends BlockJUnit4ClassRunner {
    private static final TestServer server;

    static {
        final ServiceLoader<TestServer> loader = ServiceLoader.load(TestServer.class);
        if (loader.iterator().hasNext()) {
            server = loader.iterator().next();
        } else {
            throw new RuntimeException("No server implementation found");
        }
    }

    /**
     * Creates a BlockJUnit4ClassRunner to run {@code clazz}
     *
     * @throws org.junit.runners.model.InitializationError if the test class is malformed.
     */
    public WildFlyTestRunner(Class<?> clazz) throws InitializationError {
        super(clazz);

    }

    @Override
    protected Object createTest() throws Exception {
        Object res = super.createTest();
        doInject(getTestClass().getJavaClass(), res);
        return res;
    }

    @Override
    public void run(final RunNotifier notifier) {
        notifier.addListener(new RunListener() {
            @Override
            public void testRunFinished(Result result) throws Exception {
                super.testRunFinished(result);
                server.stop();
            }
        });
        server.start();
        super.run(notifier);
    }

    private void doInject(final Class<?> clazz, final Object instance) {
        Class<?> c = clazz;
        try {
            while (c != null && c != Object.class) {
                for (Field field : c.getDeclaredFields()) {
                    if (instance != null && !Modifier.isStatic(field.getModifiers())) {
                        if (field.isAnnotationPresent(Inject.class)) {
                            field.setAccessible(true);
                            if (field.getType() == ModelControllerClient.class) {
                                field.set(instance, server.getClient());
                            } else if (TestServer.class.isAssignableFrom(field.getType())) {
                                field.set(instance, server);
                            } else if (DeploymentManager.class.isAssignableFrom(field.getType())) {
                                field.set(instance, server.getDeploymentManager());
                            }
                        }
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject", e);
        }
    }
}
