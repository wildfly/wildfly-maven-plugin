/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.common;

import org.apache.maven.plugin.logging.Log;
import org.wildfly.glow.GlowMessageWriter;

/**
 *
 * @author jdenise
 */
public class GlowMavenMessageWriter implements GlowMessageWriter {

    private final Log log;

    public GlowMavenMessageWriter(Log log) {
        this.log = log;
    }

    @Override
    public void info(Object s) {
        log.info(s.toString());
    }

    @Override
    public void warn(Object s) {
        log.warn(s.toString());
    }

    @Override
    public void error(Object s) {
        log.error(s.toString());
    }

    @Override
    public void trace(Object s) {
        log.debug(s.toString());
    }
}
