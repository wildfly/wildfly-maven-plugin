/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugins.core.bootablejar;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * Generates a new {@code logging.properties} file based on the logging subsystem model.
 *
 * <p>
 * This should be considered a hack which generates a {@code logging.properties} file. The generated file will not
 * necessarily be identical to that of which WildFly generates. Expressions will be written to the generated file. For
 * this reason a new file will be generated which the entry point needs to load as system properties before the log
 * manager is configured.
 * </p>
 *
 * <p>
 * Also handlers, formatters and filters considered explicit will not be configured at boot. As they are not used by
 * another resource this should not be an issue. Once the logging subsystems runtime phase is executed these resources
 * will be initialized.
 * </p>
 *
 * <p>
 * The generated file cannot support log4j appenders created as custom-handlers. Boot errors will
 * occur if this happens.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
// @TODO, we can't use AbstractLogEnabled, it is not in the maven plugin classloader.
public class BootLoggingConfiguration { // extends AbstractLogEnabled {

    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+)([kKmMgGbBtT])?");
    private static final String NEW_LINE = System.lineSeparator();

    private static final Collection<String> IGNORED_PROPERTIES = Arrays.asList(
            "java.ext.dirs",
            "java.home",
            "jboss.home.dir",
            "java.io.tmpdir",
            "jboss.controller.temp.dir",
            "jboss.server.base.dir",
            "jboss.server.config.dir",
            "jboss.server.data.dir",
            "jboss.server.default.config",
            "jboss.server.deploy.dir",
            "jboss.server.log.dir",
            "jboss.server.persist.config",
            "jboss.server.management.uuid",
            "jboss.server.temp.dir",
            "modules.path",
            "org.jboss.server.bootstrap.maxThreads",
            "user.dir",
            "user.home");
    private static final String KEY_OVERRIDES = "keyOverrides";
    private final Map<String, String> properties;
    private final Map<String, String> usedProperties;
    private final Map<String, String> additionalPatternFormatters;
    private ModelControllerClient client;

    public BootLoggingConfiguration() {
        properties = new HashMap<>();
        usedProperties = new TreeMap<>();
        additionalPatternFormatters = new LinkedHashMap<>();
    }

    public void generate(final Path configDir, final ModelControllerClient client) throws Exception {
        properties.clear();
        usedProperties.clear();
        additionalPatternFormatters.clear();
        // First we need to determine if there is a logging subsystem, if not we don't need to handle rewriting the
        // configuration.
        ModelNode op = Operations.createOperation("read-children-names");
        op.get(ClientConstants.CHILD_TYPE).set("subsystem");
        ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            throw new Exception("Could not determine if the logging subsystem was present: "
                    + Operations.getFailureDescription(result).asString());
        } else {
            if (Operations.readResult(result)
                    .asList()
                    .stream()
                    .noneMatch((name) -> name.asString().equals("logging"))) {
                return;
            }
        }
        // Create the operations to read the resources required
        final Operations.CompositeOperationBuilder builder = Operations.CompositeOperationBuilder.create()
                .addStep(Operations.createReadResourceOperation(Operations.createAddress("subsystem", "logging"), true));
        op = Operations.createOperation("read-children-resources");
        op.get(ClientConstants.CHILD_TYPE).set("system-property");
        builder.addStep(op);
        op = Operations.createOperation("read-children-resources");
        op.get(ClientConstants.CHILD_TYPE).set("path");
        builder.addStep(op);

        result = client.execute(builder.build());
        if (!Operations.isSuccessfulOutcome(result)) {
            throw new Exception("Failed to determine the logging configuration: "
                    + Operations.getFailureDescription(result).asString());
        }
        result = Operations.readResult(result);
        // step-1 is the subsystem, step-2 is the system properties and step-3 is the paths
        final ModelNode subsystem = Operations.readResult(result.get("step-1"));
        final ModelNode systemProperties = Operations.readResult(result.get("step-2"));
        final ModelNode paths = Operations.readResult(result.get("step-3"));

        // This shouldn't happen, but let's be safe
        if (subsystem.isDefined()) {
            // Sets the client to use
            this.client = client;
            parseProperties(systemProperties);
            try (BufferedWriter writer = Files.newBufferedWriter(configDir.resolve("logging.properties"),
                    StandardCharsets.UTF_8)) {
                writer.write("# Note this file has been generated and will be overwritten if a");
                writer.write(NEW_LINE);
                writer.write("# logging subsystem has been defined in the XML configuration.");
                writer.write(NEW_LINE);
                writer.write(NEW_LINE);

                writeLoggers(writer, subsystem);
                writeHandlers(writer, subsystem, paths);
                // Note the formatters MUST be written after the handlers. Handlers have a legacy "formatter" attribute and
                // additional pattern-formatters may need be written.
                writeFormatters(writer, subsystem);
                writeFilters(writer, subsystem);
            } catch (IOException e) {
                throw new Exception("Failed to write the logging configuration file to " + configDir.toAbsolutePath(), e);
            }

            // Collect the properties we need at boot
            final Properties requiredProperties = new Properties();
            final Iterator<Map.Entry<String, String>> iter = usedProperties.entrySet().iterator();
            while (iter.hasNext()) {
                final Map.Entry<String, String> entry = iter.next();
                final String key = entry.getKey();
                if (properties.containsKey(key)) {
                    requiredProperties.put(key, properties.get(key));
                } else {
                    // @TODO, we can't use AbstractLogEnabled, it is not in the maven plugin classloader.
                    // getLogger().warn(String.format("The value for the expression \"%s\" could not be resolved " +
                    // "and may not be set at boot if no default value is available.", entry.getValue()));
                    System.err.println(String.format("The value for the expression \"%s\" could not be resolved "
                            + "and may not be set at boot if no default value is available.", entry.getValue()));
                }
                iter.remove();
            }

            if (!requiredProperties.isEmpty()) {
                // Note the hard-coded "boot-config.properties", the bootable JAR entry point will look for this file
                // and process it if it exists.
                try (BufferedWriter writer = Files.newBufferedWriter(configDir.resolve("boot-config.properties"))) {
                    requiredProperties.store(writer, "Bootable JAR boot properties required by the log manager.");
                } catch (IOException e) {
                    throw new Exception("Failed to write the system properties required by the logging configuration file to "
                            + configDir.toAbsolutePath(), e);
                }
            }
        }
    }

    private void writeFilters(final Writer writer, final ModelNode subsystem) throws IOException {
        if (subsystem.hasDefined("filter")) {
            for (Property property : subsystem.get("filter").asPropertyList()) {
                final String name = property.getName();
                final ModelNode model = property.getValue();
                final String prefix = "filter." + name;
                writeProperty(writer, prefix, null, resolveAsString(model.get("class")));
                writeProperty(writer, prefix, "module", resolveAsString(model.get("module")));

                final ModelNode allProperties = new ModelNode();

                if (model.hasDefined("constructor-properties")) {
                    final ModelNode properties = model.get("constructor-properties");
                    final Collection<String> constructorNames = properties.asPropertyList()
                            .stream()
                            .map(Property::getName)
                            .collect(Collectors.toList());
                    writeProperty(writer, prefix, "constructorProperties", toCsvString(constructorNames));
                    for (String n : constructorNames) {
                        allProperties.get(n).set(properties.get(n));
                    }
                }
                if (model.hasDefined("properties")) {
                    final ModelNode properties = model.get("properties");
                    final Collection<String> propertyNames = properties.asPropertyList()
                            .stream()
                            .map(Property::getName)
                            .collect(Collectors.toList());
                    for (String n : propertyNames) {
                        allProperties.get(n).set(properties.get(n));
                    }
                }
                if (allProperties.isDefined()) {
                    writeProperty(writer, prefix, "properties", toCsvString(allProperties.asPropertyList()
                            .stream()
                            .map(Property::getName)
                            .collect(Collectors.toList())));
                    writeProperties(writer, prefix, allProperties);
                }
            }
            writer.write(NEW_LINE);
        }
    }

    private void writeFormatters(final Writer writer, final ModelNode subsystem) throws IOException {
        // Formatters
        if (subsystem.hasDefined("custom-formatter")) {
            writeCustomFormatter(writer, subsystem.get("custom-formatter").asPropertyList());
        }
        if (subsystem.hasDefined("json-formatter")) {
            writeStructuredFormatter("org.jboss.logmanager.formatters.JsonFormatter", writer,
                    subsystem.get("json-formatter").asPropertyList());
        }
        if (subsystem.hasDefined("pattern-formatter")) {
            writePatternFormatter(writer, subsystem.get("pattern-formatter").asPropertyList());
        }
        if (subsystem.hasDefined("xml-formatter")) {
            writeStructuredFormatter("org.jboss.logmanager.formatters.XmlFormatter", writer,
                    subsystem.get("xml-formatter").asPropertyList());
        }
    }

    private void writeCustomFormatter(final Writer writer, final List<Property> formatters) throws IOException {
        for (Property property : formatters) {
            final String name = property.getName();
            final ModelNode model = property.getValue().clone();
            final String prefix = "formatter." + name;
            writeProperty(writer, prefix, null, resolveAsString(model.remove("class")));
            writeProperty(writer, prefix, "module", resolveAsString(model.remove("module")));
            if (model.hasDefined("properties")) {
                final ModelNode properties = model.get("properties");
                // Next we need to write the properties
                final Collection<String> definedPropertyNames = properties.asPropertyList()
                        .stream()
                        .filter((p) -> p.getValue().isDefined())
                        .map(Property::getName)
                        .collect(Collectors.toList());
                writeProperty(writer, prefix, "properties", toCsvString(definedPropertyNames));
                // Write the property values
                for (String attributeName : definedPropertyNames) {
                    writeProperty(writer, prefix, attributeName, properties.get(attributeName));
                }
            }
            writer.write(NEW_LINE);
        }
    }

    private void writePatternFormatter(final Writer writer, final List<Property> formatters) throws IOException {
        for (Property property : formatters) {
            final String name = property.getName();
            final ModelNode model = property.getValue().clone();
            final String prefix = "formatter." + name;
            writeProperty(writer, prefix, null, "org.jboss.logmanager.formatters.PatternFormatter");

            // Next we need to write the properties
            final Collection<String> definedPropertyNames = model.asPropertyList()
                    .stream()
                    .filter((p) -> p.getValue().isDefined())
                    .map(Property::getName)
                    .collect(Collectors.toList());
            writeProperty(writer, prefix, "properties", toCsvString(definedPropertyNames
                    .stream()
                    .map(BootLoggingConfiguration::resolvePropertyName)
                    .collect(Collectors.toList())));
            // Write the property values
            for (String attributeName : definedPropertyNames) {
                writeProperty(writer, prefix, resolvePropertyName(attributeName), model.get(attributeName));
            }
            writer.write(NEW_LINE);
        }

        // Write any additional pattern-formatters that were defined on a handlers "formatter" attribute
        final Iterator<Map.Entry<String, String>> iter = additionalPatternFormatters.entrySet().iterator();
        while (iter.hasNext()) {
            final Map.Entry<String, String> entry = iter.next();
            final String prefix = "formatter." + entry.getKey();
            writeProperty(writer, prefix, null, "org.jboss.logmanager.formatters.PatternFormatter");
            writeProperty(writer, prefix, "constructorProperties", "pattern");
            writeProperty(writer, prefix, "properties", "pattern");
            writeProperty(writer, prefix, "pattern", entry.getValue());
            writer.write(NEW_LINE);
            iter.remove();
        }
    }

    private void writeStructuredFormatter(final String type, final Writer writer,
            final List<Property> formatters) throws IOException {
        for (Property property : formatters) {
            final String name = property.getName();
            final ModelNode model = property.getValue().clone();
            final String prefix = "formatter." + name;
            writeProperty(writer, prefix, null, type);
            boolean needKeyOverrides = !model.hasDefined("key-overrides");
            // The key-overrides are used as constructor parameters
            // This property is alwasy added.
            writeProperty(writer, prefix, "constructorProperties", KEY_OVERRIDES);
            // Next we need to write the properties
            final Collection<String> definedPropertyNames = model.asPropertyList()
                    .stream()
                    .filter((p) -> p.getValue().isDefined())
                    .map(Property::getName)
                    .collect(Collectors.toList());
            if (needKeyOverrides) {
                definedPropertyNames.add(KEY_OVERRIDES);
            }
            writeProperty(writer, prefix, "properties", toCsvString(definedPropertyNames
                    .stream()
                    .map(BootLoggingConfiguration::resolvePropertyName)
                    .collect(Collectors.toList())));
            // Write the property values
            for (String attributeName : definedPropertyNames) {
                final ModelNode value = model.get(attributeName);
                // Handle special cases
                if ("exception-output-type".equals(attributeName)) {
                    writeProperty(writer, prefix, resolvePropertyName(attributeName), toEnumString(model.get(attributeName)));
                } else {
                    if (needKeyOverrides && KEY_OVERRIDES.equals(attributeName)) {
                        // The value is empty if explicitely added.
                        writeProperty(writer, prefix, resolvePropertyName(attributeName), "");
                    } else {
                        writeProperty(writer, prefix, resolvePropertyName(attributeName), value);
                    }
                }
            }
            writer.write(NEW_LINE);
        }
    }

    private void writeHandlers(final Writer writer, final ModelNode subsystem, final ModelNode pathModel) throws IOException {
        if (subsystem.hasDefined("async-handler")) {
            writeAsyncHandlers(writer, subsystem.get("async-handler").asPropertyList());
        }

        if (subsystem.hasDefined("console-handler")) {
            writeConsoleHandlers(writer, subsystem.get("console-handler").asPropertyList());
        }
        if (subsystem.hasDefined("custom-handler")) {
            writeCustomHandlers(writer, subsystem.get("custom-handler").asPropertyList());
        }
        if (subsystem.hasDefined("file-handler")) {
            writeFileHandlers(pathModel, "org.jboss.logmanager.handlers.FileHandler", writer,
                    subsystem.get("file-handler").asPropertyList());
        }
        if (subsystem.hasDefined("periodic-rotating-file-handler")) {
            writeFileHandlers(pathModel, "org.jboss.logmanager.handlers.PeriodicRotatingFileHandler", writer,
                    subsystem.get("periodic-rotating-file-handler").asPropertyList());
        }
        if (subsystem.hasDefined("periodic-size-rotating-file-handler")) {
            writeFileHandlers(pathModel, "org.jboss.logmanager.handlers.PeriodicSizeRotatingFileHandler", writer,
                    subsystem.get("periodic-size-rotating-file-handler").asPropertyList());
        }
        if (subsystem.hasDefined("size-rotating-file-handler")) {
            writeFileHandlers(pathModel, "org.jboss.logmanager.handlers.SizeRotatingFileHandler", writer,
                    subsystem.get("size-rotating-file-handler").asPropertyList());
        }
        if (subsystem.hasDefined("socket-handler")) {
            writeSocketHandler(writer, subsystem.get("socket-handler").asPropertyList());
        }
        if (subsystem.hasDefined("syslog-handler")) {
            writeSyslogHandler(writer, subsystem.get("syslog-handler").asPropertyList());
        }
    }

    private void writeAsyncHandlers(final Writer writer, final List<Property> handlers) throws IOException {
        for (Property property : handlers) {
            final String name = property.getName();
            final String prefix = "handler." + name;
            final ModelNode model = property.getValue().clone();
            writeCommonHandler("org.jboss.logmanager.handlers.AsyncHandler", writer, name, prefix, model);
            final ModelNode subhandlers = model.remove("subhandlers");
            if (isDefined(subhandlers)) {
                writeProperty(writer, prefix, "handlers", subhandlers);
            }
            // Next we need to write the properties
            final Collection<String> definedPropertyNames = model.asPropertyList()
                    .stream()
                    .filter((p) -> p.getValue().isDefined())
                    .map(Property::getName)
                    .collect(Collectors.toList());
            definedPropertyNames.add("closeChildren");
            writeProperty(writer, prefix, "properties", toCsvString(definedPropertyNames
                    .stream()
                    .map(BootLoggingConfiguration::resolvePropertyName)
                    .collect(Collectors.toList())));
            // Write the constructor properties
            writeProperty(writer, prefix, "constructorProperties", "queueLength");
            // Write the property values
            for (String attributeName : definedPropertyNames) {
                if ("closeChildren".equals(attributeName)) {
                    writeProperty(writer, prefix, attributeName, "false");
                } else {
                    writeProperty(writer, prefix, resolvePropertyName(attributeName), model.get(attributeName));
                }
            }
            writer.write(NEW_LINE);
        }
    }

    private void writeConsoleHandlers(final Writer writer, final List<Property> handlers) throws IOException {
        for (Property property : handlers) {
            final String name = property.getName();
            final String prefix = "handler." + name;
            final ModelNode model = property.getValue().clone();
            writeCommonHandler("org.jboss.logmanager.handlers.ConsoleHandler", writer, name, prefix, model);
            // Next we need to write the properties
            final Collection<String> definedPropertyNames = model.asPropertyList()
                    .stream()
                    .filter((p) -> p.getValue().isDefined())
                    .map(Property::getName)
                    .collect(Collectors.toList());
            writeProperty(writer, prefix, "properties", toCsvString(definedPropertyNames
                    .stream()
                    .map(BootLoggingConfiguration::resolvePropertyName)
                    .collect(Collectors.toList())));
            // Write the property values
            for (String attributeName : definedPropertyNames) {
                if ("target".equals(attributeName)) {
                    writeProperty(writer, prefix, resolvePropertyName(attributeName), toEnumString(model.get(attributeName)));
                } else {
                    writeProperty(writer, prefix, resolvePropertyName(attributeName), model.get(attributeName));
                }
            }
            writer.write(NEW_LINE);
        }
    }

    private void writeCustomHandlers(final Writer writer, final List<Property> handlers) throws IOException {
        for (Property property : handlers) {
            final String name = property.getName();
            final String prefix = "handler." + name;
            final ModelNode model = property.getValue().clone();
            writeCommonHandler(null, writer, name, prefix, model);
            // Next we need to write the properties
            if (model.hasDefined("properties")) {
                final Collection<String> definedPropertyNames = model.get("properties").asPropertyList()
                        .stream()
                        .filter((p) -> p.getValue().isDefined())
                        .map(Property::getName)
                        .collect(Collectors.toList());
                if (model.hasDefined("enabled")) {
                    definedPropertyNames.add("enabled");
                }
                writeProperty(writer, prefix, "properties", toCsvString(definedPropertyNames));
                final ModelNode properties = model.get("properties");
                for (String attributeName : definedPropertyNames) {
                    if ("enabled".equals(attributeName)) {
                        if (model.hasDefined(attributeName)) {
                            writeProperty(writer, prefix, attributeName, model.get(attributeName));
                        }
                    } else {
                        writeProperty(writer, prefix, attributeName, properties.get(attributeName));
                    }
                }
            } else {
                if (model.hasDefined("enabled")) {
                    writeProperty(writer, prefix, "properties", "enabled");
                    writeProperty(writer, prefix, "enabled", model.get("enabled"));
                }
            }
            writer.write(NEW_LINE);
        }
    }

    private void writeFileHandlers(final ModelNode pathModel, final String type, final Writer writer,
            final List<Property> handlers) throws IOException {
        for (Property property : handlers) {
            final String name = property.getName();
            final String prefix = "handler." + name;
            final ModelNode model = property.getValue().clone();

            final ModelNode file = model.remove("file");
            // If the file is not defined, which shouldn't happen, we'll just skip this one
            if (!isDefined(file)) {
                continue;
            }

            writeCommonHandler(type, writer, name, prefix, model);

            // Next we need to write the properties
            final Collection<String> definedPropertyNames = model.asPropertyList()
                    .stream()
                    .filter((p) -> p.getValue().isDefined())
                    .map(Property::getName)
                    .collect(Collectors.toList());
            final Collection<String> propertyNames = definedPropertyNames
                    .stream()
                    .map(BootLoggingConfiguration::resolvePropertyName)
                    .collect(Collectors.toList());
            propertyNames.add("fileName");
            writeProperty(writer, prefix, "properties", toCsvString(propertyNames));

            // Write the constructor properties
            writeProperty(writer, prefix, "constructorProperties", "fileName,append");

            // Write the remainder of the properties
            for (String attributeName : definedPropertyNames) {
                // The rotate-size requires special conversion
                if ("rotate-size".equals(attributeName)) {
                    final String resolvedValue = String.valueOf(parseSize(model.get(attributeName)));
                    writeProperty(writer, prefix, resolvePropertyName(attributeName), resolvedValue);
                } else {
                    writeProperty(writer, prefix, resolvePropertyName(attributeName), model.get(attributeName));
                }
            }

            // Write the fileName
            final StringBuilder result = new StringBuilder();
            if (file.hasDefined("relative-to")) {
                final String relativeTo = file.get("relative-to").asString();
                resolveRelativeTo(pathModel, relativeTo, result);
            }
            if (file.hasDefined("path")) {
                result.append(resolveAsString(file.get("path")));
            }
            writeProperty(writer, prefix, "fileName", result.toString());
            writer.write(NEW_LINE);
        }
    }

    private void writeSocketHandler(final Writer writer, final List<Property> handlers) throws IOException {
        // Socket handlers are actually configured late initialized defined as a DelayedHandler
        for (Property property : handlers) {
            final String name = property.getName();
            final String prefix = "handler." + name;
            final ModelNode model = property.getValue().clone();
            writeCommonHandler("org.jboss.logmanager.handlers.DelayedHandler", writer, name, prefix, model);
            if (model.hasDefined("enabled")) {
                writeProperty(writer, prefix, "properties", "enabled");
                writeProperty(writer, prefix, "enabled", model.get("enabled"));
            }
            writer.write(NEW_LINE);
        }
    }

    private void writeSyslogHandler(final Writer writer, final List<Property> handlers) throws IOException {
        // Socket handlers are actually configured late initialized defined as a DelayedHandler
        for (Property property : handlers) {
            final String name = property.getName();
            final String prefix = "handler." + name;
            final ModelNode model = property.getValue().clone();
            writeCommonHandler("org.jboss.logmanager.handlers.SyslogHandler", writer, name, prefix, model);

            // Next we need to write the properties
            final Collection<String> definedPropertyNames = model.asPropertyList()
                    .stream()
                    .filter((p) -> p.getValue().isDefined())
                    .map(Property::getName)
                    .collect(Collectors.toList());
            writeProperty(writer, prefix, "properties", toCsvString(definedPropertyNames
                    .stream()
                    .map(BootLoggingConfiguration::resolvePropertyName)
                    .collect(Collectors.toList())));
            for (String attributeName : definedPropertyNames) {
                if ("facility".equals(attributeName)) {
                    writeProperty(writer, prefix, resolvePropertyName(attributeName), toEnumString(model.get(attributeName)));
                } else {
                    writeProperty(writer, prefix, resolvePropertyName(attributeName), model.get(attributeName));
                }
            }
            writer.write(NEW_LINE);
        }
    }

    private void writeCommonHandler(final String type, final Writer writer, final String name,
            final String prefix, final ModelNode model) throws IOException {
        if (type == null) {
            writeProperty(writer, prefix, null, resolveAsString(model.remove("class")));
            writeProperty(writer, prefix, "module", resolveAsString(model.remove("module")));
        } else {
            writeProperty(writer, prefix, null, type);
        }

        // Remove the legacy "name" attribute
        model.remove("name");

        // Write the level
        final ModelNode level = model.remove("level");
        if (isDefined(level)) {
            writeProperty(writer, prefix, "level", level);
        }
        final ModelNode encoding = model.remove("encoding");
        if (isDefined(encoding)) {
            writeProperty(writer, prefix, "encoding", encoding);
        }

        final ModelNode namedFormatter = model.remove("named-formatter");
        final ModelNode formatter = model.remove("formatter");
        if (isDefined(namedFormatter)) {
            writeProperty(writer, prefix, "formatter", namedFormatter.asString());
        } else if (isDefined(formatter)) {
            // We need to add a formatter with the known name used in WildFly
            final String defaultFormatterName = name + "-wfcore-pattern-formatter";
            additionalPatternFormatters.put(defaultFormatterName, resolveAsString(formatter));
            writeProperty(writer, prefix, "formatter", defaultFormatterName);
        }
        // Write the filter spec and remove the filter attribute which we will not use
        model.remove("filter");
        final ModelNode filter = model.remove("filter-spec");
        if (isDefined(filter)) {
            writeProperty(writer, prefix, "filter", filter);
        }
    }

    private void writeLoggers(final Writer writer, final ModelNode model) throws IOException {
        if (model.hasDefined("logger")) {
            final List<Property> loggerModel = model.get("logger").asPropertyList();
            writer.write("# Additional loggers to configure (the root logger is always configured)");
            writer.write(NEW_LINE);
            // First we need to list the loggers to define
            writeProperty(writer, "loggers", null, toCsvString(loggerModel
                    .stream()
                    .map(Property::getName)
                    .collect(Collectors.toList())));
            writer.write(NEW_LINE);
            // Next get the root logger
            if (model.hasDefined("root-logger", "ROOT")) {
                writeLogger(writer, null, model.get("root-logger", "ROOT"));
            }

            for (Property property : loggerModel) {
                writeLogger(writer, property.getName(), property.getValue());
            }
        }
    }

    private void writeLogger(final Writer writer, final String name, final ModelNode model) throws IOException {
        final String prefix = name == null ? "logger" : "logger." + name;
        if (model.hasDefined("filter-spec")) {
            writeProperty(writer, prefix, "filter", model.get("filter-spec"));
        }
        if (model.hasDefined("handlers")) {
            writeProperty(writer, prefix, "handlers", toCsvString(model.get("handlers").asList()
                    .stream()
                    .map(ModelNode::asString)
                    .collect(Collectors.toList())));
        }
        if (model.hasDefined("level")) {
            writeProperty(writer, prefix, "level", model.get("level"));
        }
        if (model.hasDefined("use-parent-filters")) {
            writeProperty(writer, prefix, "useParentFilters", model.get("use-parent-filters"));
        }
        if (model.hasDefined("use-parent-handlers")) {
            writeProperty(writer, prefix, "useParentHandlers", model.get("use-parent-handlers"));
        }
        writer.write(NEW_LINE);
    }

    private void writeProperties(final Writer writer, final String prefix, final ModelNode model) throws IOException {
        for (Property property : model.asPropertyList()) {
            final String name = property.getName();
            final ModelNode value = property.getValue();
            if (value.isDefined()) {
                writeProperty(writer, prefix, name, value);
            }
        }
    }

    private void writeProperty(final Writer out, final String prefix, final String name, final ModelNode value)
            throws IOException {
        writeProperty(out, prefix, name, resolveAsString(value));
    }

    private String toEnumString(final ModelNode value) {
        final StringBuilder result = new StringBuilder();
        if (value.getType() == ModelType.EXPRESSION) {
            final Collection<Expression> expressions = Expression.parse(value.asExpression());
            for (Expression expression : expressions) {
                addUsedProperties(expression, value.asString());
                result.append("${");
                final Iterator<String> iter = expression.getKeys().iterator();
                while (iter.hasNext()) {
                    result.append(iter.next());
                    if (iter.hasNext()) {
                        result.append(',');
                    }
                }
                if (expression.hasDefault()) {
                    result.append(':');
                    final String dft = expression.getDefaultValue();
                    for (char c : dft.toCharArray()) {
                        if (c == '-' || c == '.') {
                            result.append('_');
                        } else {
                            result.append(Character.toUpperCase(c));
                        }
                    }
                }
                result.append('}');
            }
        } else {
            for (char c : value.asString().toCharArray()) {
                if (c == '-' || c == '.') {
                    result.append('_');
                } else {
                    result.append(Character.toUpperCase(c));
                }
            }
        }
        return result.toString();
    }

    private String resolveAsString(final ModelNode value) {
        if (value.getType() == ModelType.LIST) {
            return toCsvString(value.asList()
                    .stream()
                    .map(ModelNode::asString)
                    .collect(Collectors.toList()));
        } else if (value.getType() == ModelType.OBJECT) {
            return modelToMap(value);
        } else {
            if (value.getType() == ModelType.EXPRESSION) {
                final Collection<Expression> expressions = Expression.parse(value.asExpression());
                addUsedProperties(expressions, value.asString());
            }
            return value.asString();
        }
    }

    private long parseSize(final ModelNode value) throws IOException {
        String stringValue;
        // This requires some special handling as we need the resolved value.
        if (value.getType() == ModelType.EXPRESSION) {
            // We need update the usedProperties
            final Collection<Expression> expressions = Expression.parse(value.asExpression());
            addUsedProperties(expressions, value.asString());
            // Now we need to resolve the expression
            final ModelNode op = Operations.createOperation("resolve-expression");
            op.get("expression").set(value.asString());
            final ModelNode result = client.execute(op);
            if (!Operations.isSuccessfulOutcome(result)) {
                throw new RuntimeException(String.format("Failed to resolve the expression %s: %s", value.asString(),
                        Operations.getFailureDescription(result).asString()));
            }
            stringValue = Operations.readResult(result).asString();
        } else {
            stringValue = value.asString();
        }
        final Matcher matcher = SIZE_PATTERN.matcher(stringValue);
        // This shouldn't happen, but we shouldn't fail either
        if (!matcher.matches()) {
            // by default, rotate at 10MB
            return 0xa0000L;
        }
        long qty = Long.parseLong(matcher.group(1), 10);
        final String chr = matcher.group(2);
        if (chr != null) {
            switch (chr.charAt(0)) {
                case 'b':
                case 'B':
                    break;
                case 'k':
                case 'K':
                    qty <<= 10L;
                    break;
                case 'm':
                case 'M':
                    qty <<= 20L;
                    break;
                case 'g':
                case 'G':
                    qty <<= 30L;
                    break;
                case 't':
                case 'T':
                    qty <<= 40L;
                    break;
                default:
                    // by default, rotate at 10MB
                    return 0xa0000L;
            }
        }
        return qty;
    }

    private void parseProperties(final ModelNode model) {
        if (model.isDefined()) {
            for (Property property : model.asPropertyList()) {
                final String key = property.getName();
                if (IGNORED_PROPERTIES.contains(key)) {
                    continue;
                }
                final ModelNode value = property.getValue().get("value");
                if (value.isDefined()) {
                    properties.put(key, value.asString());
                }
            }
        }
    }

    private void resolveRelativeTo(final ModelNode pathModel, final String relativeTo, final StringBuilder builder) {
        if (pathModel.hasDefined(relativeTo)) {
            final ModelNode path = pathModel.get(relativeTo);
            if (path.hasDefined("relative-to")) {
                resolveRelativeTo(pathModel, path.get("relative-to").asString(), builder);
            }
            if (path.hasDefined("path")) {
                final ModelNode pathEntry = path.get("path");
                if (pathEntry.getType() == ModelType.EXPRESSION) {
                    final Collection<Expression> expressions = Expression.parse(pathEntry.asExpression());
                    for (Expression expression : expressions) {
                        for (String key : expression.getKeys()) {
                            if (!properties.containsKey(key)) {
                                // @TODO, we can't use AbstractLogEnabled, it is not in the maven plugin classloader.
                                // getLogger().warn(String.format("The path %s is an undefined property. If not set at boot time
                                // unexpected results may occur.", pathEntry.asString()));
                                System.err.println(String.format(
                                        "The path %s is an undefined property. If not set at boot time unexpected results may occur.",
                                        pathEntry.asString()));
                            } else {
                                // We use the property name and value directly rather than referencing the path
                                usedProperties.put(key, properties.get(key));
                                expression.appendTo(builder);
                            }
                        }
                    }
                } else {
                    if (!IGNORED_PROPERTIES.contains(relativeTo)) {
                        properties.put(relativeTo, pathEntry.asString());
                        usedProperties.put(relativeTo, pathEntry.asString());
                    }
                    builder.append("${")
                            .append(relativeTo)
                            .append("}");
                }
            }
            // Use a Linux style path separator as we can't use a Windows one on Linux, but we
            // can use a Linux one on Windows.
            builder.append('/');
        }
    }

    private void addUsedProperties(final Collection<Expression> expressions, final String value) {
        for (Expression expression : expressions) {
            addUsedProperties(expression, value);
        }
    }

    private void addUsedProperties(final Expression expression, final String value) {
        for (String key : expression.getKeys()) {
            usedProperties.put(key, value);
        }
    }

    private static void writeProperty(final Writer out, final String prefix, final String name, final String value)
            throws IOException {
        if (name == null) {
            writeKey(out, prefix);
        } else {
            writeKey(out, String.format("%s.%s", prefix, name));
        }
        writeValue(out, value);
        out.write(NEW_LINE);
    }

    private static void writeValue(final Appendable out, final String value) throws IOException {
        writeSanitized(out, value, false);
    }

    private static void writeKey(final Appendable out, final String key) throws IOException {
        writeSanitized(out, key, true);
        out.append('=');
    }

    private static void writeSanitized(final Appendable out, final String string, final boolean escapeSpaces)
            throws IOException {
        for (int x = 0; x < string.length(); x++) {
            final char c = string.charAt(x);
            switch (c) {
                case ' ':
                    if (x == 0 || escapeSpaces)
                        out.append('\\');
                    out.append(c);
                    break;
                case '\t':
                    out.append('\\').append('t');
                    break;
                case '\n':
                    out.append('\\').append('n');
                    break;
                case '\r':
                    out.append('\\').append('r');
                    break;
                case '\f':
                    out.append('\\').append('f');
                    break;
                case '\\':
                case '=':
                case ':':
                case '#':
                case '!':
                    out.append('\\').append(c);
                    break;
                default:
                    out.append(c);
            }
        }
    }

    private static String modelToMap(final ModelNode value) {
        if (value.getType() != ModelType.OBJECT) {
            return null;
        }
        final List<Property> properties = value.asPropertyList();
        final StringBuilder result = new StringBuilder();
        final Iterator<Property> iterator = properties.iterator();
        while (iterator.hasNext()) {
            final Property property = iterator.next();
            escapeKey(result, property.getName());
            result.append('=');
            final ModelNode v = property.getValue();
            if (v.isDefined()) {
                escapeValue(result, v.asString());
            }
            if (iterator.hasNext()) {
                result.append(',');
            }
        }
        return result.toString();
    }

    private static boolean isDefined(final ModelNode value) {
        return value != null && value.isDefined();
    }

    private static String toCsvString(final Collection<String> names) {
        final StringBuilder result = new StringBuilder(1024);
        Iterator<String> iterator = names.iterator();
        while (iterator.hasNext()) {
            final String name = iterator.next();
            // No need to write empty names
            if (!name.isEmpty()) {
                result.append(name);
                if (iterator.hasNext()) {
                    result.append(",");
                }
            }
        }
        return result.toString();
    }

    private static String resolvePropertyName(final String modelName) {
        if ("autoflush".equals(modelName)) {
            return "autoFlush";
        }
        if ("color-map".equals(modelName)) {
            return "colors";
        }
        if ("syslog-format".equals(modelName)) {
            return "syslogType";
        }
        if ("server-address".equals(modelName)) {
            return "serverHostname";
        }
        if (modelName.contains("-")) {
            final StringBuilder builder = new StringBuilder();
            boolean cap = false;
            for (char c : modelName.toCharArray()) {
                if (c == '-') {
                    cap = true;
                    continue;
                }
                if (cap) {
                    builder.append(Character.toUpperCase(c));
                    cap = false;
                } else {
                    builder.append(c);
                }
            }
            return builder.toString();
        }
        return modelName;
    }

    /**
     * Escapes a maps key value for serialization to a string. If the key contains a {@code \} or an {@code =} it will
     * be escaped by a preceding {@code \}. Example: {@code  key\=} or {@code \\key}.
     *
     * @param sb  the string builder to append the escaped key to
     * @param key the key
     */
    private static void escapeKey(final StringBuilder sb, final String key) {
        final char[] chars = key.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            // Ensure that \ and = are escaped
            if (c == '\\') {
                final int n = i + 1;
                if (n >= chars.length) {
                    sb.append('\\').append('\\');
                } else {
                    final char next = chars[n];
                    if (next == '\\' || next == '=') {
                        // Nothing to do, already properly escaped
                        sb.append(c);
                        sb.append(next);
                        i = n;
                    } else {
                        // Now we need to escape the \
                        sb.append('\\').append('\\');
                    }
                }
            } else if (c == '=') {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
    }

    /**
     * Escapes a maps value for serialization to a string. If a value contains a {@code \} or a {@code ,} it will be
     * escaped by a preceding {@code \}. Example: {@code part1\,part2} or {@code value\\other}.
     *
     * @param sb    the string builder to append the escaped value to
     * @param value the value
     */
    private static void escapeValue(final StringBuilder sb, final String value) {
        if (value != null) {
            final char[] chars = value.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                final char c = chars[i];
                // Ensure that \ and , are escaped
                if (c == '\\') {
                    final int n = i + 1;
                    if (n >= chars.length) {
                        sb.append('\\').append('\\');
                    } else {
                        final char next = chars[n];
                        if (next == '\\' || next == ',') {
                            // Nothing to do, already properly escaped
                            sb.append(c);
                            sb.append(next);
                            i = n;
                        } else {
                            // Now we need to escape the \
                            sb.append('\\').append('\\');
                        }
                    }
                } else if (c == ',') {
                    sb.append('\\').append(c);
                } else {
                    sb.append(c);
                }
            }
        }
    }
}
