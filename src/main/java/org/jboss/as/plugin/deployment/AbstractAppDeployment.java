/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.plugin.deployment;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.as.plugin.common.PropertyNames;

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

    private PackageType packageType;

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        final PackageType packageType = getPackageType();
        if (checkPackaging && packageType.isIgnored()) {
            getLog().debug(String.format("Ignoring packaging type %s.", packageType.getPackaging()));
        } else {
            super.doExecute();
        }
    }

    @Override
    protected File file() {
        final PackageType packageType = getPackageType();
        final String filename;
        if (this.filename == null) {
            filename = String.format("%s.%s", project.getBuild().getFinalName(), packageType.getFileExtension());
        } else {
            filename = this.filename;
        }
        return new File(targetDir, filename);
    }

    protected final synchronized PackageType getPackageType() {
        if (packageType == null) {
            packageType = PackageType.resolve(project);
        }
        return packageType;
    }
}
