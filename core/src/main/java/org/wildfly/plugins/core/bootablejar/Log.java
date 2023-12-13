/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugins.core.bootablejar;

/**
 *
 * @author jdenise
 */
public interface Log {

    void warn(String msg);

    void debug(String msg);

    void error(String msg);

    void info(String msg);

}
