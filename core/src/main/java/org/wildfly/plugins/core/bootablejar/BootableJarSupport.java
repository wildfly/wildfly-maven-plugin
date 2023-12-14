/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugins.core.bootablejar;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.galleon.MessageWriter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.GalleonFeaturePackRuntime;
import org.jboss.galleon.api.GalleonProvisioningRuntime;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.jboss.logging.Logger;
import org.wildfly.plugins.core.cli.CLIForkedBootConfigGenerator;
import org.wildfly.plugins.core.cli.ForkedCLIUtil;

/**
 *
 * @author jdenise
 */
public class BootableJarSupport {

    public static final String BOOTABLE_SUFFIX = "bootable";

    public static final String JBOSS_MODULES_GROUP_ID = "org.jboss.modules";
    public static final String JBOSS_MODULES_ARTIFACT_ID = "jboss-modules";

    private static final String MODULE_ID_JAR_RUNTIME = "org.wildfly.bootable-jar";

    private static final String BOOT_ARTIFACT_ID = "wildfly-jar-boot";
    public static final String WILDFLY_ARTIFACT_VERSIONS_RESOURCE_PATH = "wildfly/artifact-versions.properties";

    /**
     * Package a wildfly server as a bootable JAR.
     */
    public static void packageBootableJar(Path targetJarFile, Path target,
            GalleonProvisioningConfig config, Path serverHome, MavenRepoManager resolver,
            MessageWriter writer, Logger log) throws Exception {
        Path contentRootDir = target.resolve("bootable-jar-build-artifacts");
        if (Files.exists(contentRootDir)) {
            IoUtils.recursiveDelete(contentRootDir);
        }
        Files.createDirectories(contentRootDir);
        try {
            ScannedArtifacts bootable;
            Path emptyHome = contentRootDir.resolve("tmp-home");
            Files.createDirectories(emptyHome);
            try (Provisioning pm = new GalleonBuilder().addArtifactResolver(resolver).newProvisioningBuilder(config)
                    .setInstallationHome(emptyHome)
                    .setMessageWriter(writer)
                    .build()) {
                bootable = scanArtifacts(pm, config, log);
                pm.storeProvisioningConfig(config, contentRootDir.resolve("provisioning.xml"));
            }
            String[] paths = new String[bootable.getCliArtifacts().size()];
            int i = 0;
            for (MavenArtifact a : bootable.getCliArtifacts()) {
                resolver.resolve(a);
                paths[i] = a.getPath().toAbsolutePath().toString();
                i += 1;
            }
            Path output = File.createTempFile("cli-script-output", null).toPath();
            Files.deleteIfExists(output);
            output.toFile().deleteOnExit();
            IoUtils.recursiveDelete(emptyHome);
            ForkedCLIUtil.fork(log, paths, CLIForkedBootConfigGenerator.class, serverHome, output);
            zipServer(serverHome, contentRootDir);
            buildJar(contentRootDir, targetJarFile, bootable, resolver);
        } finally {
            IoUtils.recursiveDelete(contentRootDir);
        }
    }

    public static void unzipCloudExtension(Path contentDir, String version, MavenRepoManager resolver)
            throws MavenUniverseException, IOException {
        MavenArtifact ma = new MavenArtifact();
        ma.setGroupId("org.wildfly.plugins");
        ma.setArtifactId("wildfly-jar-cloud-extension");
        ma.setExtension("jar");
        ma.setVersion(version);
        resolver.resolve(ma);
        ZipUtils.unzip(ma.getPath(), contentDir);
    }

    public static void zipServer(Path home, Path contentDir) throws IOException {
        cleanupServer(home);
        Path target = contentDir.resolve("wildfly.zip");
        ZipUtils.zip(home, target);
    }

    private static void cleanupServer(Path jbossHome) throws IOException {
        Path history = jbossHome.resolve("standalone").resolve("configuration").resolve("standalone_xml_history");
        IoUtils.recursiveDelete(history);
        Files.deleteIfExists(jbossHome.resolve("README.txt"));
    }

    public static ScannedArtifacts scanArtifacts(Provisioning pm, GalleonProvisioningConfig config, Logger log)
            throws Exception {
        Set<MavenArtifact> cliArtifacts = new HashSet<>();
        MavenArtifact jbossModules = null;
        MavenArtifact bootArtifact = null;
        try (GalleonProvisioningRuntime rt = pm.getProvisioningRuntime(config)) {
            for (GalleonFeaturePackRuntime fprt : rt.getGalleonFeaturePacks()) {
                if (fprt.getGalleonPackage(MODULE_ID_JAR_RUNTIME) != null) {
                    // We need to discover GAV of the associated boot.
                    Path artifactProps = fprt.getResource(WILDFLY_ARTIFACT_VERSIONS_RESOURCE_PATH);
                    final Map<String, String> propsMap = new HashMap<>();
                    try {
                        readProperties(artifactProps, propsMap);
                    } catch (Exception ex) {
                        throw new Exception("Error reading artifact versions", ex);
                    }
                    for (Map.Entry<String, String> entry : propsMap.entrySet()) {
                        String value = entry.getValue();
                        MavenArtifact a = getArtifact(value);
                        if (BOOT_ARTIFACT_ID.equals(a.getArtifactId())) {
                            // We got it.
                            log.debug("Found " + a + " in " + fprt.getFPID());
                            bootArtifact = a;
                            break;
                        }
                    }
                }
                // Lookup artifacts to retrieve the required dependencies for isolated CLI execution
                Path artifactProps = fprt.getResource(WILDFLY_ARTIFACT_VERSIONS_RESOURCE_PATH);
                final Map<String, String> propsMap = new HashMap<>();
                try {
                    readProperties(artifactProps, propsMap);
                } catch (Exception ex) {
                    throw new Exception("Error reading artifact versions", ex);
                }
                for (Map.Entry<String, String> entry : propsMap.entrySet()) {
                    String value = entry.getValue();
                    MavenArtifact a = getArtifact(value);
                    if ("wildfly-cli".equals(a.getArtifactId())
                            && "org.wildfly.core".equals(a.getGroupId())) {
                        // We got it.
                        a.setClassifier("client");
                        log.debug("Found " + a + " in " + fprt.getFPID());
                        cliArtifacts.add(a);
                        continue;
                    }
                    if (JBOSS_MODULES_ARTIFACT_ID.equals(a.getArtifactId())
                            && JBOSS_MODULES_GROUP_ID.equals(a.getGroupId())) {
                        jbossModules = a;
                    }
                }
            }
        }
        if (bootArtifact == null) {
            throw new ProvisioningException("Server doesn't support bootable jar packaging");
        }
        if (jbossModules == null) {
            throw new ProvisioningException("JBoss Modules not found in dependency, can't create a Bootable JAR");
        }
        return new ScannedArtifacts(bootArtifact, jbossModules, cliArtifacts);
    }

    public static void buildJar(Path contentDir, Path jarFile, ScannedArtifacts bootable, MavenRepoManager resolver)
            throws Exception {
        resolver.resolve(bootable.getBoot());
        Path rtJarFile = bootable.getBoot().getPath();
        resolver.resolve(bootable.getJbossModules());
        Path jbossModulesFile = bootable.getJbossModules().getPath();
        ZipUtils.unzip(jbossModulesFile, contentDir);
        ZipUtils.unzip(rtJarFile, contentDir);
        ZipUtils.zip(contentDir, jarFile);
    }

    private static void readProperties(Path propsFile, Map<String, String> propsMap) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(propsFile)) {
            String line = reader.readLine();
            while (line != null) {
                line = line.trim();
                if (!line.isEmpty() && line.charAt(0) != '#') {
                    final int i = line.indexOf('=');
                    if (i < 0) {
                        throw new Exception("Failed to parse property " + line + " from " + propsFile);
                    }
                    propsMap.put(line.substring(0, i), line.substring(i + 1));
                }
                line = reader.readLine();
            }
        }
    }

    static MavenArtifact getArtifact(String str) {
        final String[] parts = str.split(":");
        final String groupId = parts[0];
        final String artifactId = parts[1];
        String version = parts[2];
        String classifier = parts[3];
        String extension = parts[4];

        MavenArtifact ma = new MavenArtifact();
        ma.setGroupId(groupId);
        ma.setArtifactId(artifactId);
        ma.setVersion(version);
        ma.setClassifier(classifier);
        ma.setExtension(extension);
        return ma;
    }
}
