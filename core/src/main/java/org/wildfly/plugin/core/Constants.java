/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.core;

/**
 * @author jdenise
 */
public interface Constants {

    public static final String CLI_RESOLVE_PARAMETERS_VALUES = "--resolve-parameter-values";
    public static final String CLI_ECHO_COMMAND_ARG = "--echo-command";
    public static final String PLUGIN_PROVISIONING_FILE = ".wildfly-maven-plugin-provisioning.xml";
    public static final String STANDALONE = "standalone";
    public static final String STANDALONE_XML = "standalone.xml";
    public static final String FORK_EMBEDDED_PROCESS_OPTION = "jboss-fork-embedded";
}
