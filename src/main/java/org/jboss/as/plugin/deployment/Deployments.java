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
import java.util.List;

import org.apache.maven.project.MavenProject;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Deployments {

    private Deployments() {
    }

    /**
     * Attempts to determine the module/project that should be deployed. If the project from the {@code parent}
     * parameter has no sub-projects the parameter is returned as the project that should be deployed.
     * <p>
     * The order of preference is as follows:
     * <ol>
     * <li>ejb</li>
     * <li>war</li>
     * </ol>
     * Any other packaging type will result in the {@code parent} parameter being returned.
     * </p>
     *
     * @param parent the parent project
     *
     * @return the project determined to be the best project for the deployment
     */
    public static MavenProject resolveProject(final MavenProject parent) {
        MavenProject result = parent;
        @SuppressWarnings("unchecked")
        final List<MavenProject> projects = (List<MavenProject>) parent.getCollectedProjects();
        for (MavenProject project : projects) {
            final String packaging = project.getPackaging();
            // Attempt to find the best suited project
            if ("ear".equals(packaging)) {
                result = project;
                break;
            } else if ("war".equals(packaging)) {
                result = project;
            }
        }
        return result;
    }

    /**
     * Resolves the target directory.
     * <p/>
     * If the {@code targetDir} parameter is {@code null}, the {@link org.apache.maven.model.Build#getDirectory()
     * project} is used to determine the target directory.
     *
     * @param project   the project used to determine the target if the {@code targetDir} is {@code null}
     * @param targetDir the target directory or {@code null} if it should be resolved from the {@link MavenProject
     *                  project}
     *
     * @return the target directory.
     */
    public static File resolveTargetDir(final MavenProject project, final File targetDir) {
        final File result;
        if (targetDir == null) {
            result = new File(project.getBuild().getDirectory());
        } else {
            result = targetDir;
        }
        return result;
    }

    /**
     * Resolves the final file name.
     * <p/>
     * If the {@code fileName} parameter is {@code null} the {@link org.apache.maven.model.Build#getFinalName()
     * project} is used to determine the file name.
     *
     * @param project  the project used to determine the file name if the {@code fileName} is {@code null}
     * @param fileName the file name to use or {@code null} if it should be resloved from the {@link MavenProject
     *                 project}
     *
     * @return the file name to use
     */
    public static String resolveFileName(final MavenProject project, final String fileName) {
        final String result;
        if (fileName == null) {
            result = project.getBuild().getFinalName() + "." + project.getPackaging();
        } else {
            result = fileName;
        }
        return result;
    }
}
