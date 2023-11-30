/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.provision;

import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Provision a server.
 *
 * @author jfdenise
 * @since 3.0
 */
@Mojo(name = "provision", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class ProvisionServerMojo extends AbstractProvisionServerMojo {

    @Override
    protected String getGoal() {
        return "provision";
    }

    @Override
    protected void serverProvisioned(Path jbossHome) throws MojoExecutionException, MojoFailureException {
    }

}
