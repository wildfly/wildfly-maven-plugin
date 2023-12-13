/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugins.core.bootablejar;

import java.util.Set;

import org.jboss.galleon.universe.maven.MavenArtifact;

/**
 *
 * @author jdenise
 */
public class ScannedArtifacts {

    private final MavenArtifact jbossModules;
    private final MavenArtifact boot;
    private final Set<MavenArtifact> cliArtifacts;

    public ScannedArtifacts(MavenArtifact bootArtifact, MavenArtifact jbossModules, Set<MavenArtifact> cliArtifacts) {
        this.boot = bootArtifact;
        this.jbossModules = jbossModules;
        this.cliArtifacts = cliArtifacts;
    }

    /**
     * @return the boot
     */
    public MavenArtifact getBoot() {
        return boot;
    }

    /**
     * @return the jbossModules
     */
    public MavenArtifact getJbossModules() {
        return jbossModules;
    }

    /**
     * @return the cliArtifacts
     */
    public Set<MavenArtifact> getCliArtifacts() {
        return cliArtifacts;
    }

}
