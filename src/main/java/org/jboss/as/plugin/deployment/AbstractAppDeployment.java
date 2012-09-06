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

import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractAppDeployment extends AbstractDeployment {


    /**
     * The target directory the application to be deployed is located.
     */
    @Parameter(defaultValue = "${project.build.directory}/")
    private File targetDir;

    /**
     * The file name of the application to be deployed.
     */
    @Parameter(defaultValue = "${project.build.finalName}.${project.packaging}")
    private String filename;

    /**
     * By default certain package types are ignored when processing. Set this value to {@code false} if this check
     * should be bypassed.
     */
    @Parameter(alias = "check-packing", property = "jboss-as.checkPackaging", defaultValue = "true")
    private boolean checkPackaging;

    @Override
    protected File file() {
        return new File(targetDir, filename);
    }

    @Override
    protected final boolean checkPackaging() {
        return checkPackaging;
    }
}
