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

package org.wildfly.plugin.common;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface PropertyNames {

    String ADD_RESOURCE_FORCE = "add-resource.force";

    String BATCH = "wildfly.batch";

    String CHECK_PACKAGING = "wildfly.checkPackaging";

    String COMMANDS = "wildfly.commands";

    String DEPLOY_FORCE = "deploy.force";

    String DEPLOYMENT_CONTENT_URL = "wildfly.deployment.contentUrl";

    String DEPLOYMENT_FILENAME = "wildfly.deployment.filename";

    String DEPLOYMENT_NAME = "wildfly.deployment.name";

    String DEPLOYMENT_RUNTIME_NAME = "wildfly.deployment.runtime.name";

    String DEPLOYMENT_TARGET_DIR = "wildfly.deployment.targetDir";

    String DOMAIN_CONFIG = "wildfly.domainConfig";

    String FAIL_ON_ERROR = "wildfly.failOnError";

    String HOST_CONFIG = "wildfly.hostConfig";

    String HOSTNAME = "wildfly.hostname";

    String ID = "wildfly.id";

    String INCLUDE_PACKAGINGS = "wildfly.includePackagings";

    String IGNORE_MISSING_DEPLOYMENT = "undeploy.ignoreMissingDeployment";

    String JAVA_HOME = "java.home";

    String WILDFLY_ARTIFACT = "wildfly.artifact";

    String WILDFLY_ARTIFACT_ID = "wildfly.artifactId";

    String WILDFLY_AUTH_CLIENT_CONFIG = "wildfly.authConfig";

    String WILDFLY_CLASSIFIER = "wildfly.classifier";

    String WILDFLY_GROUP_ID = "wildfly.groupId";

    String JBOSS_HOME = "jboss-as.home"; // TODO (jrp) look at this one

    String WILDFLY_PACKAGING = "wildfly.packaging";

    String WILDFLY_VERSION = "wildfly.version";

    String JAVA_OPTS = "wildfly.javaOpts";

    String MODULES_PATH = "wildfly.modulesPath";

    String PASSWORD = "wildfly.password";

    String PORT = "wildfly.port";

    String PROTOCOL = "wildfly.protocol";

    String PROFILES = "wildfly.profiles";

    String RELOAD = "wildfly.reload";

    String SCRIPTS = "wildfly.scripts";

    String SERVER_CONFIG = "wildfly.serverConfig";

    String SERVER_GROUPS = "wildfly.serverGroups";

    String SKIP = "wildfly.skip";

    String STARTUP_TIMEOUT = "wildfly.startupTimeout";

    String STDOUT = "wildfly.stdout";

    String TIMEOUT = "wildfly.timeout";

    String USERNAME = "wildfly.username";

    String PROPERTIES_FILE = "wildfly.propertiesFile";

    String SERVER_ARGS = "wildfly.serverArgs";

    String OFFLINE = "wildfly.offline";

}
