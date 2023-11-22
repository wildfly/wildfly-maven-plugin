/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

package org.wildfly.plugins.core.bootablejar;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.wildfly.plugin.core.Environment;
import org.wildfly.plugin.core.ServerHelper;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class BootLoggingConfigurationIT {

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile(".*\\$\\{.*}.*");
    private static Process currentProcess;
    private static Path stdout;
    private static ModelControllerClient client;

    @Rule
    public TestName testName = new TestName();

    private final Deque<ModelNode> tearDownOps = new ArrayDeque<>();
    private Path tmpDir;

    @BeforeClass
    public static void startWildFly() throws Exception {
        stdout = Files.createTempFile("stdout-", ".log");
        final StandaloneCommandBuilder builder = StandaloneCommandBuilder.of(Environment.WILDFLY_HOME)
                .addJavaOptions(Environment.getJvmArgs());
        currentProcess = Launcher.of(builder)
                .setRedirectErrorStream(true)
                .redirectOutput(stdout)
                .launch();
        client = ModelControllerClient.Factory.create(Environment.HOSTNAME, Environment.PORT);
        // Wait for standalone to start
        ServerHelper.waitForStandalone(currentProcess, client, Environment.TIMEOUT);
        Assert.assertTrue(String.format("Standalone server is not running:%n%s", getLog()),
                ServerHelper.isStandaloneRunning(client));
    }

    @AfterClass
    public static void shutdown() throws Exception {
        if (client != null) {
            ServerHelper.shutdownStandalone(client);
            client.close();
        }
        if (currentProcess != null) {
            if (!currentProcess.waitFor(Environment.TIMEOUT, TimeUnit.SECONDS)) {
                currentProcess.destroyForcibly();
            }
        }
    }

    @Before
    public void setup() throws Exception {
        tmpDir = Environment.createTempPath("test-config", testName.getMethodName());
        if (Files.notExists(tmpDir)) {
            Files.createDirectories(tmpDir);
        }
    }

    @After
    public void cleanUp() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        ModelNode op;
        while ((op = tearDownOps.pollFirst()) != null) {
            builder.addStep(op);
        }
        executeOperation(builder.build());
    }

    @Test
    public void testDefault() throws Exception {
        generateAndTest();
    }

    @Test
    public void testAsyncHandler() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Add a file handler
        final ModelNode fileHandler = createLoggingAddress("file-handler", "test-file");
        ModelNode op = Operations.createAddOperation(fileHandler);
        op.get("named-formatter").set("PATTERN");
        op.get("append").set(true);
        final ModelNode file = op.get("file");
        file.get("relative-to").set("jboss.server.log.dir");
        file.get("path").set("test-file.log");
        builder.addStep(op);

        // Add the async handler
        final ModelNode asyncAddress = createLoggingAddress("async-handler", "async");
        op = Operations.createAddOperation(asyncAddress);
        op.get("overflow-action").set("DISCARD");
        op.get("queue-length").set(5000);
        final ModelNode subhandlers = op.get("subhandlers").setEmptyList();
        subhandlers.add("test-file");
        builder.addStep(op);

        // Add the handler to the root-logger
        builder.addStep(createAddHandlerOp("async"));

        executeOperation(builder.build());
        tearDownOps.add(Operations.createRemoveOperation(asyncAddress));
        tearDownOps.add(Operations.createRemoveOperation(fileHandler));
        generateAndTest();
    }

    @Test
    public void testDefaultConsole() throws Exception {
        final ModelNode address = createLoggingAddress("console-handler", "new-handler");
        // Just do a raw add which will add the default formatter rather than a named-formatter
        executeOperation(Operations.createAddOperation(address));
        tearDownOps.add(Operations.createRemoveOperation(address));
        generateAndTest();
    }

    @Test
    public void testCustomHandler() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        final ModelNode formatterAddress = createLoggingAddress("custom-formatter", "json");
        ModelNode op = Operations.createAddOperation(formatterAddress);
        op.get("class").set("org.jboss.logmanager.formatters.JsonFormatter");
        op.get("module").set("org.jboss.logmanager");
        ModelNode properties = op.get("properties");
        properties.get("prettyPrint").set("true");
        properties.get("recordDelimiter").set("|");
        builder.addStep(op);

        final ModelNode handlerAddress = createLoggingAddress("custom-handler", "custom-console");
        op = Operations.createAddOperation(handlerAddress);
        op.get("class").set("org.jboss.logmanager.handlers.ConsoleHandler");
        op.get("module").set("org.jboss.logmanager");
        op.get("named-formatter").set("json");
        properties = op.get("properties");
        properties.get("target").set("SYSTEM_ERR");
        builder.addStep(op);

        builder.addStep(createAddHandlerOp("custom-console"));

        executeOperation(builder.build());
        // Create the tear down ops
        tearDownOps.addLast(Operations.createRemoveOperation(handlerAddress));
        tearDownOps.addLast(Operations.createRemoveOperation(formatterAddress));

        generateAndTest();
    }

    @Test
    public void testCustomHandlerNoProperties() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        final ModelNode formatterAddress = createLoggingAddress("custom-formatter", "json");
        ModelNode op = Operations.createAddOperation(formatterAddress);
        op.get("class").set("org.jboss.logmanager.formatters.JsonFormatter");
        op.get("module").set("org.jboss.logmanager");
        builder.addStep(op);

        final ModelNode handlerAddress = createLoggingAddress("custom-handler", "custom-console");
        op = Operations.createAddOperation(handlerAddress);
        op.get("class").set("org.jboss.logmanager.handlers.ConsoleHandler");
        op.get("module").set("org.jboss.logmanager");
        op.get("named-formatter").set("json");
        builder.addStep(op);

        builder.addStep(createAddHandlerOp("custom-console"));

        executeOperation(builder.build());
        // Create the tear down ops
        tearDownOps.addLast(Operations.createRemoveOperation(handlerAddress));
        tearDownOps.addLast(Operations.createRemoveOperation(formatterAddress));

        generateAndTest();
    }

    @Test
    public void testPeriodicRotatingFileHandler() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Create a handler to assign the formatter to
        final ModelNode handlerAddress = createLoggingAddress("periodic-rotating-file-handler", "new-file");
        final ModelNode op = Operations.createAddOperation(handlerAddress);
        op.get("named-formatter").set("PATTERN");
        op.get("suffix").set(".yyyy-MM-dd");
        final ModelNode file = op.get("file");
        file.get("relative-to").set("jboss.server.log.dir");
        file.get("path").set("test.log");
        builder.addStep(op);

        builder.addStep(createAddHandlerOp("new-file"));

        executeOperation(builder.build());
        tearDownOps.add(Operations.createRemoveOperation(handlerAddress));

        generateAndTest();
    }

    @Test
    public void testPeriodicSizeRotatingFileHandler() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Create a handler to assign the formatter to
        final ModelNode handlerAddress = createLoggingAddress("periodic-size-rotating-file-handler", "new-file");
        final ModelNode op = Operations.createAddOperation(handlerAddress);
        op.get("named-formatter").set("PATTERN");
        op.get("suffix").set(".yyyy-MM-dd");
        op.get("rotate-on-boot").set(false);
        op.get("rotate-size").set("${test.rotate.size:50M}");
        final ModelNode file = op.get("file");
        file.get("relative-to").set("jboss.server.log.dir");
        file.get("path").set("test.log");
        builder.addStep(op);

        builder.addStep(createAddHandlerOp("new-file"));

        executeOperation(builder.build());
        tearDownOps.add(Operations.createRemoveOperation(handlerAddress));

        generateAndTest();
    }

    @Test
    public void testSizeRotatingFileHandler() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Create a handler to assign the formatter to
        final ModelNode handlerAddress = createLoggingAddress("size-rotating-file-handler", "new-file");
        final ModelNode op = Operations.createAddOperation(handlerAddress);
        op.get("named-formatter").set("PATTERN");
        op.get("rotate-on-boot").set(false);
        op.get("rotate-size").set("50M");
        op.get("max-backup-index").set(100);
        final ModelNode file = op.get("file");
        file.get("relative-to").set("jboss.server.log.dir");
        file.get("path").set("test.log");
        builder.addStep(op);

        builder.addStep(createAddHandlerOp("new-file"));

        executeOperation(builder.build());
        tearDownOps.add(Operations.createRemoveOperation(handlerAddress));

        generateAndTest();
    }

    @Test
    @Ignore("This test is failing on CI. See WFCORE-5155.")
    public void testSocketHandler() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Add the socket binding
        final ModelNode socketBindingAddress = Operations.createAddress("socket-binding-group", "standard-sockets",
                "remote-destination-outbound-socket-binding", "log-server");
        ModelNode op = Operations.createAddOperation(socketBindingAddress);
        op.get("host").set(Environment.HOSTNAME);
        op.get("port").set(Environment.getLogServerPort());
        builder.addStep(op);

        // Add a socket handler
        final ModelNode address = createLoggingAddress("socket-handler", "socket");
        op = Operations.createAddOperation(address);
        op.get("named-formatter").set("PATTERN");
        op.get("outbound-socket-binding-ref").set("log-server");
        builder.addStep(op);

        // Add the handler to the root-logger
        builder.addStep(createAddHandlerOp("socket"));

        executeOperation(builder.build());
        tearDownOps.add(Operations.createRemoveOperation(address));
        tearDownOps.add(Operations.createRemoveOperation(socketBindingAddress));

        generateAndTest();
    }

    @Test
    public void testSyslogHandler() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Add a socket handler
        final ModelNode address = createLoggingAddress("syslog-handler", "syslog");
        final ModelNode op = Operations.createAddOperation(address);
        op.get("app-name").set("test-app");
        op.get("enabled").set(false);
        op.get("facility").set("local-use-0");
        op.get("hostname").set(Environment.HOSTNAME);
        op.get("level").set("WARN");
        op.get("named-formatter").set("PATTERN");
        op.get("port").set(Environment.getLogServerPort());
        builder.addStep(op);

        // Add the handler to the root-logger
        builder.addStep(createAddHandlerOp("syslog"));

        executeOperation(builder.build());
        tearDownOps.add(Operations.createRemoveOperation(address));

        generateAndTest();
    }

    @Test
    public void testFilter() throws Exception {
        final ModelNode filterAddress = createLoggingAddress("filter", "testFilter");
        ModelNode op = Operations.createAddOperation(filterAddress);
        op.get("class").set(TestFilter.class.getName());
        op.get("module").set("org.wildfly.plugins.core.bootablejar");
        final ModelNode constructorProperties = op.get("constructor-properties");
        constructorProperties.get("constructorText").set(" | constructor property text");
        final ModelNode properties = op.get("properties");
        properties.get("propertyText").set(" | property text");
        executeOperation(op);
        tearDownOps.add(Operations.createRemoveOperation(filterAddress));

        generateAndTest();
    }

    @Test
    public void testFilterNoProperties() throws Exception {
        final ModelNode filterAddress = createLoggingAddress("filter", "testFilter");
        ModelNode op = Operations.createAddOperation(filterAddress);
        op.get("class").set(TestFilter.class.getName());
        op.get("module").set("org.wildfly.plugins.core.bootablejar");
        executeOperation(op);
        tearDownOps.add(Operations.createRemoveOperation(filterAddress));

        generateAndTest();
    }

    @Test
    public void testJsonFormatter() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        final ModelNode formatterAddress = createLoggingAddress("json-formatter", "json");
        ModelNode op = Operations.createAddOperation(formatterAddress);
        op.get("pretty-print").set(false);
        op.get("exception-output-type").set("${test.type:formatted}");
        op.get("date-format").set("yyyy-MM-dd'T'HH:mm:SSSZ");

        final ModelNode keyOverrides = op.get("key-overrides").setEmptyObject();
        keyOverrides.get("message").set("msg");
        keyOverrides.get("stack-trace").set("cause");

        final ModelNode metaData = op.get("meta-data").setEmptyObject();
        metaData.get("app-name").set("test");
        metaData.get("@version").set("1");

        op.get("print-details").set(true);
        op.get("record-delimiter").set("\n");
        op.get("zone-id").set("GMT");
        builder.addStep(op);

        // Create a handler to assign the formatter to
        final ModelNode handlerAddress = createLoggingAddress("file-handler", "json-file");
        op = Operations.createAddOperation(handlerAddress);
        op.get("append").set(false);
        op.get("level").set("DEBUG");
        op.get("named-formatter").set("json");
        final ModelNode file = op.get("file");
        file.get("relative-to").set("jboss.server.log.dir");
        file.get("path").set("test-json.log");
        builder.addStep(op);

        builder.addStep(createAddHandlerOp("json-file"));

        executeOperation(builder.build());
        tearDownOps.add(Operations.createRemoveOperation(handlerAddress));
        tearDownOps.add(Operations.createRemoveOperation(formatterAddress));

        generateAndTest();
    }

    @Test
    public void testPatternFormatter() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        final ModelNode formatterAddress = createLoggingAddress("pattern-formatter", "new-pattern");
        ModelNode op = Operations.createAddOperation(formatterAddress);
        op.get("pattern").set("[test] %d{HH:mm:ss,SSS} %-5p [%c] %s%e%n");
        op.get("color-map").set("info:blue,warn:yellow,error:red,debug:cyan");
        builder.addStep(op);

        // Create a handler to assign the formatter to
        final ModelNode handlerAddress = createLoggingAddress("file-handler", "new-file");
        op = Operations.createAddOperation(handlerAddress);
        op.get("append").set(false);
        op.get("encoding").set("ISO-8859-1");
        op.get("level").set("DEBUG");
        op.get("filter-spec").set("any(accept,match(\".*\"))");
        op.get("named-formatter").set("new-pattern");
        final ModelNode file = op.get("file");
        file.get("relative-to").set("jboss.server.log.dir");
        file.get("path").set("test.log");
        builder.addStep(op);

        executeOperation(builder.build());
        tearDownOps.add(Operations.createRemoveOperation(handlerAddress));
        tearDownOps.add(Operations.createRemoveOperation(formatterAddress));

        generateAndTest();
    }

    @Test
    public void testLogger() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Add a filter for the logger
        final ModelNode filterAddress = createLoggingAddress("filter", "testFilter");
        ModelNode op = Operations.createAddOperation(filterAddress);
        op.get("class").set(TestFilter.class.getName());
        op.get("module").set("org.wildfly.plugins.core.bootablejar");
        builder.addStep(op);

        // Add a formatter for the handler
        final ModelNode formatterAddress = createLoggingAddress("pattern-formatter", "custom-formatter");
        op = Operations.createAddOperation(formatterAddress);
        op.get("pattern").set("[%X{debug.token} %K{level}%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n");
        builder.addStep(op);

        // Add a handler for the logger
        final ModelNode handlerAddress = createLoggingAddress("console-handler", "custom-console");
        op = Operations.createAddOperation(handlerAddress);
        op.get("named-formatter").set("custom-formatter");
        builder.addStep(op);

        // Create the logger
        final ModelNode loggerAddress = createLoggingAddress("logger", "org.jboss.as");
        op = Operations.createAddOperation(loggerAddress);
        op.get("level").set("${test.level:DEBUG}");
        op.get("use-parent-handlers").set(false);
        op.get("filter-spec").set("all(testFilter)");
        final ModelNode handlers = op.get("handlers").setEmptyList();
        handlers.add("custom-console");
        builder.addStep(op);

        executeOperation(builder.build());
        tearDownOps.add(Operations.createRemoveOperation(loggerAddress));
        tearDownOps.add(Operations.createRemoveOperation(handlerAddress));
        tearDownOps.add(Operations.createRemoveOperation(formatterAddress));
        tearDownOps.add(Operations.createRemoveOperation(filterAddress));

        generateAndTest();
    }

    @Test
    public void testWithProperties() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Create some expected properties
        final Properties expectedProperties = new Properties();
        expectedProperties.setProperty("test.level", "TRACE");
        expectedProperties.setProperty("test.rotate-on-boot", "true");
        expectedProperties.setProperty("test.pretty.print", "true");
        expectedProperties.setProperty("test.exception-output-type", "formatted");
        expectedProperties.setProperty("test.zone.id", "UTC");
        expectedProperties.setProperty("test.dir", System.getProperty("java.io.tmpdir"));

        // Add the system properties
        for (String key : expectedProperties.stringPropertyNames()) {
            final ModelNode address = Operations.createAddress("system-property", key);
            final ModelNode op = Operations.createAddOperation(address);
            op.get("value").set(expectedProperties.getProperty(key));
            builder.addStep(op);
        }
        // Add a path and set this after
        final ModelNode tmpPathAddress = Operations.createAddress("path", "custom.log.dir");
        ModelNode op = Operations.createAddOperation(tmpPathAddress);
        op.get("path").set("${test.dir}");
        builder.addStep(op);

        final ModelNode logPathAddress = Operations.createAddress("path", "test.log.dir");
        op = Operations.createAddOperation(logPathAddress);
        op.get("relative-to").set("custom.log.dir");
        op.get("path").set("logs");
        builder.addStep(op);

        // Add one property that won't be used so it shouldn't end up in the boot-config.properties
        final ModelNode sysPropAddress = Operations.createAddress("system-property", "unused.property");
        op = Operations.createAddOperation(sysPropAddress);
        op.get("value").set("not used");
        builder.addStep(op);
        tearDownOps.add(Operations.createRemoveOperation(sysPropAddress));

        // Create a formatter
        final ModelNode formatterAddress = createLoggingAddress("json-formatter", "json");
        op = Operations.createAddOperation(formatterAddress);
        op.get("pretty-print").set("${test.pretty.print:false}");
        op.get("exception-output-type").set("${test.exception-output-type:detailed}");
        op.get("zone-id").set("${test.zone.id:GMT}");
        builder.addStep(op);

        // Create a file handler
        final ModelNode handlerAddress = createLoggingAddress("size-rotating-file-handler", "json-file");
        op = Operations.createAddOperation(handlerAddress);
        op.get("named-formatter").set("json");
        op.get("rotate-on-boot").set("${test.rotate-on-boot:false}");
        op.get("rotate-size").set("50M");
        op.get("max-backup-index").set(100);
        final ModelNode file = op.get("file");
        file.get("relative-to").set("test.log.dir");
        file.get("path").set("test.log");
        builder.addStep(op);
        // We don't actually expect the custom.log.dir property here as it should be written to the file as
        // ${test.dir}/${test.log.dir}/test.log
        expectedProperties.setProperty("test.log.dir", "logs");

        // Create a logger
        final ModelNode loggerAddress = createLoggingAddress("logger", "org.wildfly.core");
        op = Operations.createAddOperation(loggerAddress);
        op.get("level").set("${test.level:INFO}");
        builder.addStep(op);

        builder.addStep(createAddHandlerOp("json-file"));

        executeOperation(builder.build());
        tearDownOps.add(Operations.createRemoveOperation(loggerAddress));
        tearDownOps.add(Operations.createRemoveOperation(handlerAddress));
        tearDownOps.add(Operations.createRemoveOperation(formatterAddress));
        tearDownOps.add(Operations.createRemoveOperation(logPathAddress));
        tearDownOps.add(Operations.createRemoveOperation(tmpPathAddress));

        // Remove all the properties last
        for (String name : expectedProperties.stringPropertyNames()) {
            // test.log.dir isn't an actual system property
            if ("test.log.dir".equals(name))
                continue;
            final ModelNode address = Operations.createAddress("system-property", name);
            tearDownOps.addLast(Operations.createRemoveOperation(address));
        }

        generateAndTest(expectedProperties);
    }

    @Test
    public void testNestedPaths() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Create some expected properties
        final Properties expectedProperties = new Properties();
        // Add a path and set this after
        final ModelNode tmpPathAddress = Operations.createAddress("path", "custom.log.dir");
        ModelNode op = Operations.createAddOperation(tmpPathAddress);
        op.get("path").set("custom-logs");
        op.get("relative-to").set("jboss.server.log.dir");
        builder.addStep(op);

        final ModelNode logPathAddress = Operations.createAddress("path", "test.log.dir");
        op = Operations.createAddOperation(logPathAddress);
        op.get("relative-to").set("custom.log.dir");
        op.get("path").set("logs");
        builder.addStep(op);
        expectedProperties.setProperty("custom.log.dir", "custom-logs");
        expectedProperties.setProperty("test.log.dir", "logs");

        // Create a file handler
        final ModelNode handlerAddress = createLoggingAddress("file-handler", "test-file");
        op = Operations.createAddOperation(handlerAddress);
        op.get("named-formatter").set("PATTERN");
        final ModelNode file = op.get("file");
        file.get("relative-to").set("test.log.dir");
        file.get("path").set("test.log");
        builder.addStep(op);

        builder.addStep(createAddHandlerOp("test-file"));

        executeOperation(builder.build());
        tearDownOps.add(Operations.createRemoveOperation(handlerAddress));
        tearDownOps.add(Operations.createRemoveOperation(logPathAddress));
        tearDownOps.add(Operations.createRemoveOperation(tmpPathAddress));

        generateAndTest(expectedProperties);
    }

    @Test
    public void testMultiKeyExpression() throws Exception {
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Create some expected properties
        final Properties expectedProperties = new Properties();
        expectedProperties.setProperty("test.prod.level", "INFO");
        expectedProperties.setProperty("test.min.level", "WARN");

        // Add the system properties
        for (String key : expectedProperties.stringPropertyNames()) {
            final ModelNode address = Operations.createAddress("system-property", key);
            final ModelNode op = Operations.createAddOperation(address);
            op.get("value").set(expectedProperties.getProperty(key));
            builder.addStep(op);
            tearDownOps.add(Operations.createRemoveOperation(address));
        }

        // Create a logger to set the level on
        final ModelNode address = createLoggingAddress("logger", BootLoggingConfigurationIT.class.getName());
        final ModelNode op = Operations.createAddOperation(address);
        op.get("level").set("${test.dev.level,test.prod.level,test.min.level:DEBUG}");
        builder.addStep(op);

        executeOperation(builder.build());
        tearDownOps.add(Operations.createRemoveOperation(address));

        generateAndTest(expectedProperties);
    }

    private void generateAndTest() throws Exception {
        generateAndTest(null);
    }

    private void generateAndTest(final Properties expectedBootConfig) throws Exception {
        final BootLoggingConfiguration config = new BootLoggingConfiguration();
        // @TODO, we can't use AbstractLogEnabled, it is not in the maven plugin classloader.
        // config.enableLogging(TestLogger.getLogger(BootLoggingConfigurationTestCase.class));
        config.generate(tmpDir, client);
        compare(load(findLoggingConfig(), true, true),
                load(tmpDir.resolve("logging.properties"), false, true), true);
        final Path bootConfig = tmpDir.resolve("boot-config.properties");
        if (expectedBootConfig == null) {
            // The file should not exist
            Assert.assertTrue("Expected " + bootConfig + " not to exist", Files.notExists(bootConfig));
        } else {
            compare(expectedBootConfig, load(bootConfig, false, false), false);
        }
    }

    private ModelNode createAddHandlerOp(final String handlerName) {
        final ModelNode address = createLoggingAddress("root-logger", "ROOT");
        // Create the remove op first
        ModelNode op = Operations.createOperation("remove-handler", address);
        op.get("name").set(handlerName);
        tearDownOps.addFirst(op);

        // Create the add op
        op = Operations.createOperation("add-handler", address);
        op.get("name").set(handlerName);
        return op;
    }

    private Path findLoggingConfig() throws IOException {
        final Path serverLogConfig = Environment.WILDFLY_HOME.resolve("standalone").resolve("configuration")
                .resolve("logging.properties");
        Assert.assertTrue("Could find config file " + serverLogConfig, Files.exists(serverLogConfig));
        return Files.copy(serverLogConfig, tmpDir.resolve("server-logging.properties"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static ModelNode createLoggingAddress(final String... parts) {
        final Collection<String> addresses = new ArrayList<>();
        addresses.add("subsystem");
        addresses.add("logging");
        Collections.addAll(addresses, parts);
        return Operations.createAddress(addresses);
    }

    private static ModelNode executeOperation(final ModelNode op) throws IOException {
        return executeOperation(Operation.Factory.create(op));
    }

    private static ModelNode executeOperation(final Operation op) throws IOException {
        final ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(String.format("Operation %s failed: %s", op.getOperation(),
                    Operations.getFailureDescription(result).asString()));
        }
        // Reload if required
        if (result.hasDefined(ClientConstants.RESPONSE_HEADERS)) {
            final ModelNode responseHeaders = result.get(ClientConstants.RESPONSE_HEADERS);
            if (responseHeaders.hasDefined("process-state")) {
                if (ClientConstants.CONTROLLER_PROCESS_STATE_RELOAD_REQUIRED
                        .equals(responseHeaders.get("process-state").asString())) {
                    executeOperation(Operations.createOperation("reload"));
                    try {
                        ServerHelper.waitForStandalone(currentProcess, client, Environment.TIMEOUT);
                    } catch (InterruptedException | TimeoutException e) {
                        e.printStackTrace();
                        Assert.fail("Reloading the server failed: " + e.getLocalizedMessage());
                    }
                }
            }
        }
        return Operations.readResult(result);
    }

    private static String getLog() throws IOException {
        final StringBuilder result = new StringBuilder();
        Files.readAllLines(stdout, StandardCharsets.UTF_8).forEach(line -> result.append(line).append(System.lineSeparator()));
        return result.toString();
    }

    private static void compare(final Properties expected, final Properties found, final boolean resolveExpressions)
            throws IOException {
        compareKeys(expected, found);
        compareValues(expected, found, resolveExpressions);
    }

    private static void compareKeys(final Properties expected, final Properties found) {
        final Set<String> expectedKeys = new TreeSet<>(expected.stringPropertyNames());
        final Set<String> foundKeys = new TreeSet<>(found.stringPropertyNames());
        // Find the missing expected keys
        final Set<String> missing = new TreeSet<>(expectedKeys);
        missing.removeAll(foundKeys);
        Assert.assertTrue("Missing the following keys in the generated file: " + missing.toString(),
                missing.isEmpty());

        // Find additional keys
        missing.addAll(foundKeys);
        missing.removeAll(expectedKeys);
        Assert.assertTrue("Found the following extra keys in the generated file: " + missing.toString(),
                missing.isEmpty());
    }

    private static void compareValues(final Properties expected, final Properties found, final boolean resolveExpressions)
            throws IOException {
        final Set<String> keys = new TreeSet<>(expected.stringPropertyNames());
        for (String key : keys) {
            final String expectedValue = expected.getProperty(key);
            final String foundValue = found.getProperty(key);
            if (key.endsWith("fileName")) {
                final Path foundFileName = resolvePath(foundValue);
                Assert.assertEquals(Paths.get(expectedValue).normalize(), foundFileName);
            } else {
                if (expectedValue.contains(",")) {
                    // Assume the values are a list
                    final List<String> expectedValues = stringToList(expectedValue);
                    final List<String> foundValues = stringToList(foundValue);
                    Assert.assertEquals(String.format("Found %s expected %s", foundValues, expectedValues), expectedValues,
                            foundValues);
                } else {
                    if (resolveExpressions && EXPRESSION_PATTERN.matcher(foundValue).matches()) {
                        String resolvedValue = resolveExpression(foundValue);
                        // Handle some special cases
                        if ("formatted".equals(resolvedValue)) {
                            resolvedValue = resolvedValue.toUpperCase();
                        }
                        Assert.assertEquals(expectedValue, resolvedValue);
                    } else {
                        Assert.assertEquals(expectedValue, foundValue);
                    }
                }
            }
        }
    }

    private static List<String> stringToList(final String value) {
        final List<String> result = new ArrayList<>();
        Collections.addAll(result, value.split(","));
        Collections.sort(result);
        return result;
    }

    private static Properties load(final Path path, final boolean expected, final boolean filter) throws IOException {
        final Properties result = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            result.load(reader);
        }
        if (filter) {
            if (expected) {
                result.remove("handlers");
                result.remove("formatters");
                result.remove("filters");
            } else {
                // For some reason the default console-handler and periodic-rotating-file-handler don't persist the enabled
                // attribute.
                for (String key : result.stringPropertyNames()) {
                    if (key.equals("handler.CONSOLE.enabled") || key.equals("handler.FILE.enabled")) {
                        result.remove(key);
                        final String propertiesKey = resolvePrefix(key) + ".properties";
                        final String value = result.getProperty(propertiesKey);
                        if (value != null) {
                            if ("enabled".equals(value)) {
                                result.remove(propertiesKey);
                            } else {
                                result.setProperty(propertiesKey, value.replace("enabled,", "").replace(",enabled", ""));
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private static String resolvePrefix(final String key) {
        final int i = key.lastIndexOf('.');
        if (i > 0) {
            return key.substring(0, i);
        }
        return key;
    }

    private static Path resolvePath(final String path) throws IOException {
        Path resolved = Paths.get(path);
        if (EXPRESSION_PATTERN.matcher(path).matches()) {
            // For testing purposes we're just going to use the last entry which should be a path entry
            final LinkedList<Expression> expressions = new LinkedList<>(Expression.parse(path));
            Assert.assertFalse("The path could not be resolved: " + path, expressions.isEmpty());
            final Expression expression = expressions.getLast();
            // We're assuming we only have one key entry which for testing purposes should be okay
            final ModelNode op = Operations.createOperation("path-info",
                    Operations.createAddress("path", expression.getKeys().get(0)));
            final ModelNode result = client.execute(op);
            if (!Operations.isSuccessfulOutcome(result)) {
                Assert.fail(Operations.getFailureDescription(result).asString());
            }
            final ModelNode pathInfo = Operations.readResult(result);
            final String resolvedPath = pathInfo.get("path", "resolved-path").asString();
            resolved = Paths.get(resolvedPath, resolved.getFileName().toString());
        }
        return resolved.normalize();
    }

    private static String resolveExpression(final String value) throws IOException {
        // Resolve the expression
        ModelNode op = Operations.createOperation("resolve-expression");
        op.get("expression").set(value);
        return executeOperation(op).asString();
    }
}
