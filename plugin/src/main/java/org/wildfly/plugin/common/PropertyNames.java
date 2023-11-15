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

    String CHANNELS = "wildfly.channels";

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

    String IGNORE_MISSING_DEPLOYMENT = "undeploy.ignoreMissingDeployment";

    String JAVA_HOME = "java.home";

    String JBOSS_HOME = "jboss-as.home"; // TODO (jrp) look at this one

    String JAVA_OPTS = "wildfly.javaOpts";

    String MODULES_PATH = "wildfly.modulesPath";

    String OFFLINE = "wildfly.offline";

    String PASSWORD = "wildfly.password";

    String PORT = "wildfly.port";

    String PROPERTIES_FILE = "wildfly.propertiesFile";

    String PROTOCOL = "wildfly.protocol";

    String PROFILES = "wildfly.profiles";

    String RELOAD = "wildfly.reload";

    String RESOLVE_EXPRESSIONS = "wildfly.resolveExpressions";

    String SCRIPTS = "wildfly.scripts";

    String SERVER_ARGS = "wildfly.serverArgs";

    String SERVER_CONFIG = "wildfly.serverConfig";

    String SERVER_GROUPS = "wildfly.serverGroups";

    String SKIP = "wildfly.skip";

    String SKIP_PROVISION = "wildfly.provision.skip";

    String SKIP_PACKAGE = "wildfly.package.skip";

    String SKIP_PACKAGE_DEPLOYMENT = "wildfly.package.deployment.skip";

    String STARTUP_TIMEOUT = "wildfly.startupTimeout";

    String STDOUT = "wildfly.stdout";

    String TIMEOUT = "wildfly.timeout";

    String USERNAME = "wildfly.username";

    String WILDFLY_AUTH_CLIENT_CONFIG = "wildfly.authConfig";

    String WILDFLY_FEATURE_PACK_LOCATION = "wildfly.feature-pack.location";

    String WILDFLY_IMAGE_BINARY = "wildfly.image.binary";

    String WILDFLY_LAYERS_CONFIGURATION_FILE_NAME = "wildfly.provisioning.layers.configuration.file.name";

    String WILDFLY_ORIGINAL_ARTIFACT_VERSION_RESOLUTION = "wildfly.provisioning.original-artifact-version-resolution";

    String WILDFLY_PACKAGING_EXTRA_CONTENT_DIRS = "wildfly.packaging.extra.dirs";

    String WILDFLY_PROVISIONING_DIR = "wildfly.provisioning.dir";

    String WILDFLY_PROVISIONING_FEATURE_PACKS = "wildfly.provisioning.feature-packs";

    String WILDFLY_PROVISIONING_FILE = "wildfly.provisioning.file";

    String WILDFLY_PROVISIONING_LAYERS = "wildfly.provisioning.layers";

    String WILDFLY_PROVISIONING_LAYERS_EXCLUDED = "wildfly.provisioning.layers.excluded";

    String WILDFLY_PROVISIONING_LOG_TIME = "wildfly.provisioning.log.time";

    String WILDFLY_PROVISIONING_OFFLINE = "wildfly.provisioning.offline";

    String WILDFLY_PROVISIONING_OVERWRITE_PROVISIONED_SERVER = "wildfly.provisioning.overwrite-provisioned-server";

    String WILDFLY_PROVISIONING_RECORD_STATE = "wildfly.provisioning.record.state";

    String WILDFLY_VERSION = "wildfly.version";
}
