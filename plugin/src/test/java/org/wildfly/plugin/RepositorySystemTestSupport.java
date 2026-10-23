/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin;

import java.nio.file.Path;

import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.LocalRepository;

/**
 * Boots the real repository system through Plexus for tests; {@link #close()} disposes the container.
 */
public final class RepositorySystemTestSupport implements AutoCloseable {

    private final PlexusContainer container;
    private final RepositorySystem system;
    private final DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();

    public RepositorySystemTestSupport(final Path localRepository) throws Exception {
        container = new DefaultPlexusContainer(new DefaultContainerConfiguration()
                .setClassPathScanning(PlexusConstants.SCANNING_INDEX));
        try {
            system = container.lookup(RepositorySystem.class);
            session.setLocalRepositoryManager(system.newLocalRepositoryManager(session,
                    new LocalRepository(localRepository.toFile())));
        } catch (Exception e) {
            container.dispose();
            throw e;
        }
    }

    public RepositorySystem getSystem() {
        return system;
    }

    public DefaultRepositorySystemSession getSession() {
        return session;
    }

    @Override
    public void close() {
        container.dispose();
    }
}
