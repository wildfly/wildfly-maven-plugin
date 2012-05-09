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

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DeploymentArchive {

    private final File targetDir;
    private final String fileName;
    private final String name;

    private DeploymentArchive(final File targetDir, final String fileName) {
        this.targetDir = targetDir;
        this.fileName = fileName;
        name = fileName;
    }

    private DeploymentArchive(final String targetDir, final String fileName) {
        this.targetDir = new File(targetDir);
        this.fileName = fileName;
        name = fileName;
    }

    public static DeploymentArchive resolve(final MavenProject project) {
        final MavenProject p = Deployments.resolveProject(project);
        String targetDir = p.getBuild().getDirectory();
        String fileName = p.getBuild().getFinalName() + "." + p.getPackaging();
        // Check the project for already set values
        for (Plugin plugin : p.getBuild().getPlugins()) {
            if ("jboss-as-maven-plugin".equals(plugin.getArtifactId()))
                if (plugin.getConfiguration() instanceof Xpp3Dom) {
                    final Xpp3Dom dom = (Xpp3Dom) plugin.getConfiguration();
                    final Xpp3Dom fileNameNode = dom.getChild("filename");
                    if (fileNameNode != null) {
                        fileName = fileNameNode.getValue();
                    }
                    final Xpp3Dom targetDirNode = dom.getChild("targetDir");
                    if (targetDirNode != null) {
                        targetDir = targetDirNode.getValue();
                    }
                }
        }
        return new DeploymentArchive(targetDir, fileName);
    }

    public File getTargetDir() {
        return targetDir;
    }

    public String getFileName() {
        return fileName;
    }

    public String getName() {
        return name;
    }

    public File getArchive() {
        return new File(targetDir, fileName);
    }
}
