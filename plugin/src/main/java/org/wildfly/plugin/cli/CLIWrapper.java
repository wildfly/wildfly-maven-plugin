/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.cli;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Interacts with CLI API using reflection.
 *
 * @author jdenise
 */
class CLIWrapper implements AutoCloseable {

    private final Object ctx;
    private final Method handle;
    private final Method handleSafe;
    private final Method terminateSession;
    private final Method bindClient;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final String origConfig;

    public CLIWrapper(Path jbossHome, boolean resolveExpression, ClassLoader loader) throws Exception {
        if (jbossHome != null) {
            Path config = jbossHome.resolve("bin").resolve("jboss-cli.xml");
            origConfig = System.getProperty("jboss.cli.config");
            if (Files.exists(config)) {
                System.setProperty("jboss.cli.config", config.toString());
            }
        } else {
            origConfig = null;
        }
        final Object builder = loader.loadClass("org.jboss.as.cli.impl.CommandContextConfiguration$Builder").newInstance();
        final Method setEchoCommand = builder.getClass().getMethod("setEchoCommand", boolean.class);
        setEchoCommand.invoke(builder, true);
        final Method setResolve = builder.getClass().getMethod("setResolveParameterValues", boolean.class);
        setResolve.invoke(builder, resolveExpression);
        final Method setOutput = builder.getClass().getMethod("setConsoleOutput", OutputStream.class);
        setOutput.invoke(builder, out);
        Object ctxConfig = builder.getClass().getMethod("build").invoke(builder);
        Object factory = loader.loadClass("org.jboss.as.cli.CommandContextFactory").getMethod("getInstance").invoke(null);
        final Class<?> configClass = loader.loadClass("org.jboss.as.cli.impl.CommandContextConfiguration");
        ctx = factory.getClass().getMethod("newCommandContext", configClass).invoke(factory, ctxConfig);
        handle = ctx.getClass().getMethod("handle", String.class);
        handleSafe = ctx.getClass().getMethod("handleSafe", String.class);
        terminateSession = ctx.getClass().getMethod("terminateSession");
        bindClient = ctx.getClass().getMethod("bindClient", ModelControllerClient.class);
    }

    public void bindClient(ModelControllerClient client) throws Exception {
        bindClient.invoke(ctx, client);
    }

    public void handle(String command) throws Exception {
        handle.invoke(ctx, command);
    }

    public void handleSafe(String command) throws Exception {
        handleSafe.invoke(ctx, command);
    }

    public String getOutput() {
        return out.toString();
    }

    @Override
    public void close() throws Exception {
        try {
            terminateSession.invoke(ctx);
        } finally {
            if (origConfig != null) {
                System.setProperty("jboss.cli.config", origConfig);
            }
        }
    }

}
