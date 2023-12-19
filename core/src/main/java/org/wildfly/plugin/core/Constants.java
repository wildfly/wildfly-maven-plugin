/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.core;

/**
 * @author jdenise
 */
public interface Constants {

    String CLI_RESOLVE_PARAMETERS_VALUES = "--resolve-parameter-values";
    String CLI_ECHO_COMMAND_ARG = "--echo-command";
    String PLUGIN_PROVISIONING_FILE = ".wildfly-maven-plugin-provisioning.xml";
    String STANDALONE = "standalone";
    String STANDALONE_XML = "standalone.xml";
    String FORK_EMBEDDED_PROCESS_OPTION = "jboss-fork-embedded";
}
