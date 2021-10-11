/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.plugin.cli;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;

/**
 * A CLI executor, resolving CLI artifact from Maven.
 * We can't have embedded/jboss modules in plugin classpath, it causes issue because we are
 * sharing the same jboss module classes between execution run inside the same
 * JVM.
 *
 * @author jdenise
 */
public class LocalCLIExecutor {

    private static final String CLI_GROUP_ID = "org.wildfly.core";
    private static final String CLI_ARTIFACT_ID = "wildfly-cli";
    private static final String CLI_CLASSIFIER = "client";
    private static final String CLI_TYPE = "jar";
    private static final String WILDLY_CORE_VERSION_PROPERTY = "version.org.wildfly.core";

    private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();
    private final ClassLoader originalCl;
    private final URLClassLoader cliCl;
    private final CLIWrapper cliWrapper;

    public LocalCLIExecutor(Path jbossHome, MavenRepoManager artifactResolver) throws Exception {
        URL[] cp = new URL[1];
        cp[0] = resolveCLI(artifactResolver);
        originalCl = Thread.currentThread().getContextClassLoader();
        cliCl = new URLClassLoader(cp, originalCl);
        Thread.currentThread().setContextClassLoader(cliCl);
        cliWrapper = new CLIWrapper(jbossHome, cliCl);
    }

    private static URL resolveCLI(MavenRepoManager artifactResolver) throws Exception {
        final URL[] cp = new URL[1];
        MavenArtifact mavenArtifact = new MavenArtifact();
        mavenArtifact.setGroupId(CLI_GROUP_ID);
        mavenArtifact.setArtifactId(CLI_ARTIFACT_ID);
        String version = retrieveCoreVersion(artifactResolver);
        mavenArtifact.setVersion(version);
        mavenArtifact.setClassifier(CLI_CLASSIFIER);
        mavenArtifact.setExtension(CLI_TYPE);
        artifactResolver.resolve(mavenArtifact);
        return mavenArtifact.getPath().toUri().toURL();
    }

    public void bindClient(ModelControllerClient client) throws Exception {
        cliWrapper.bindClient(client);
    }

    public void executeBatch(Collection<String> commands) throws Exception {
        handle("batch");
        for (String c : commands) {
            handle(c);
        }
        handle("run-batch");
    }

    public void executeCommands(final Iterable<String> commands, final boolean failOnError) throws Exception {
        for (String cmd : commands) {
            if (failOnError) {
                handle(cmd);
            } else {
                handleSafe(cmd);
            }
        }
    }

    public void handle(String command) throws Exception {
        cliWrapper.handle(command);
    }

    public void handleSafe(String command) throws Exception {
        cliWrapper.handleSafe(command);
    }

    public String getOutput() {
        return cliWrapper.getOutput();
    }

    public void close() throws Exception {
        try {
            cliWrapper.close();
        } finally {
            Thread.currentThread().setContextClassLoader(originalCl);
            try {
                cliCl.close();
            } catch (IOException e) {
            }
        }
    }

    public void execute(List<String> commands) throws Exception {
        for (String cmd : commands) {
            handle(cmd);
        }
    }

    private static String retrieveCoreVersion(MavenRepoManager artifactResolver) throws Exception {
        InputStream is = LocalCLIExecutor.class.getResourceAsStream("/META-INF/maven/plugin.xml");
        if (is == null) {
            throw new MojoExecutionException("Can't retrieve plugin descriptor");
        }
        PluginDescriptorBuilder builder = new PluginDescriptorBuilder();
        PluginDescriptor pluginDescriptor = builder.build(new InputStreamReader(is, StandardCharsets.UTF_8));
        MavenArtifact mavenArtifact = new MavenArtifact();
        mavenArtifact.setGroupId(pluginDescriptor.getGroupId());
        mavenArtifact.setArtifactId(pluginDescriptor.getArtifactId());
        mavenArtifact.setVersion(pluginDescriptor.getVersion());
        mavenArtifact.setExtension("pom");
        artifactResolver.resolve(mavenArtifact);

        Model model = readModel(mavenArtifact.getPath());

        Parent artifactParent = model.getParent();
        MavenArtifact parentArtifact = new MavenArtifact();
        parentArtifact.setGroupId(artifactParent.getGroupId());
        parentArtifact.setArtifactId(artifactParent.getArtifactId());
        parentArtifact.setVersion(artifactParent.getVersion());
        parentArtifact.setExtension("pom");
        artifactResolver.resolve(parentArtifact);

        Model parentModel = readModel(parentArtifact.getPath());
        return parentModel.getProperties().getProperty(WILDLY_CORE_VERSION_PROPERTY);
    }

    private static Model readModel(final Path pomXml) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(pomXml, getEncoding(pomXml))) {
            final MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
            final Model model = xpp3Reader.read(reader);
            model.setPomFile(pomXml.toFile());
            return model;
        } catch (org.codehaus.plexus.util.xml.pull.XmlPullParserException ex) {
            throw new IOException("Failed to parse artifact POM model", ex);
        }
    }

    private static Charset getEncoding(Path pomXml) throws IOException {
        Charset charset = StandardCharsets.UTF_8;
        try (FileReader fileReader = new FileReader(pomXml.toFile())) {
            XMLStreamReader xmlReader = XML_INPUT_FACTORY.createXMLStreamReader(fileReader);
            try {
                String encoding = xmlReader.getCharacterEncodingScheme();
                if (encoding != null) {
                    charset = Charset.forName(encoding);
                }
            } finally {
                xmlReader.close();
            }
        } catch (XMLStreamException ex) {
            throw new IOException("Failed to retrieve encoding for " + pomXml, ex);
        }
        return charset;
    }
}
