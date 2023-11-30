/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.core;

import org.jboss.galleon.config.ConfigId;

/**
 * Simple wrapper for configuration ids.
 *
 * @author Emmanuel Hugonnet (c) 2018 Red Hat, inc.
 */
public class ConfigurationId {

    private String name;
    private String model;

    public ConfigurationId() {
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public ConfigId getId() {
        return new ConfigId(model, name);
    }

    public boolean isModelOnly() {
        return name == null || name.isEmpty();
    }

    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("{");
        if (model != null) {
            buf.append("model=").append(model);
        }
        if (name != null) {
            if (buf.length() > 1) {
                buf.append(' ');
            }
            buf.append("name=").append(name);
        }
        return buf.append('}').toString();
    }
}
