/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.plugin.common;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface PropertyNames {

    String ADD_RESOURCE_FORCE = "add-resource.force";

    String BUNDLES_PATH = "jboss-as.bundlesPath";

    String CHECK_PACKAGING = "jboss-as.checkPackaging";

    String DEPLOY_FORCE = "deploy.force";

    String DEPLOYMENT_FILENAME = "jboss-as.deployment.filename";

    String DEPLOYMENT_TARGET_DIR = "jboss-as.deployment.targetDir";

    String ENABLE_RESOURCE = "add-resource.enableResource";

    String HOSTNAME = "jboss-as.hostname";

    String ID = "jboss-as.id";

    String IGNORE_MISSING_DEPLOYMENT = "undeploy.ignoreMissingDeployment";

    String JAVA_HOME = "java.home";

    String JBOSS_HOME = "jboss-as.home";

    String JBOSS_VERSION = "jboss-as.version";

    String JVM_ARGS = "jboss-as.jvmArgs";

    String MODULES_PATH = "jboss-as.modulesPath";

    String PASSWORD = "jboss-as.password";

    String PORT = "jboss-as.port";

    String RELOAD = "jboss-as.reload";

    String SERVER_CONFIG = "jboss-as.serverConfig";

    String STARTUP_TIMEOUT = "jboss-as.startupTimeout";

    String USERNAME = "jboss-as.username";

}
