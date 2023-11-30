/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.deployment;

import java.io.File;

import org.apache.maven.plugins.annotations.Parameter;
import org.wildfly.plugin.common.PropertyNames;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractAppDeployment extends AbstractDeployment {

    /**
     * The target directory the application to be deployed is located.
     */
    @Parameter(defaultValue = "${project.build.directory}/", property = PropertyNames.DEPLOYMENT_TARGET_DIR)
    private File targetDir;

    /**
     * The file name of the application to be deployed.
     * <p>
     * The {@code filename} property does have a default of <code>${project.build.finalName}.${project.packaging}</code>.
     * The default value is not injected as it normally would be due to packaging types like {@code ejb} that result in
     * a file with a {@code .jar} extension rather than an {@code .ejb} extension.
     * </p>
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_FILENAME)
    private String filename;

    /**
     * By default certain package types are ignored when processing, e.g. {@code maven-project} and {@code pom}. Set
     * this value to {@code false} if this check should be bypassed.
     */
    @Parameter(alias = "check-packaging", property = PropertyNames.CHECK_PACKAGING, defaultValue = "true")
    private boolean checkPackaging;

    @Override
    protected boolean skipExecution() {
        boolean skip = super.skipExecution();
        if (!skip) {
            final PackageType packageType = PackageType.resolve(project);
            if (checkPackaging && packageType.isIgnored()) {
                getLog().debug(String.format("Ignoring packaging type %s.", packageType.getPackaging()));
                skip = true;
            }
        }
        return skip;
    }

    @Override
    protected File file() {
        final PackageType packageType = PackageType.resolve(project);
        final String filename;
        if (this.filename == null) {
            filename = String.format("%s.%s", project.getBuild().getFinalName(), packageType.getFileExtension());
        } else {
            filename = this.filename;
        }
        return new File(targetDir, filename);
    }
}
