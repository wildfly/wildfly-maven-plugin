/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.plugin.deployment.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Domain {

    /**
     * The profiles where resources should be deployed.
     */
    @Parameter
    private List<String> profiles;

    /**
     * The server groups the content should be deployed to.
     */
    @Parameter(alias = "server-groups")
    private List<String> serverGroups;

    /**
     * The profiles where resources should be added. If no profiles were defined an empty list is returned.
     *
     * @return the profiles or an empty list.
     */
    public List<String> getProfiles() {
        return profiles == null ? Collections.<String>emptyList() : new ArrayList<String>(profiles);
    }

    /**
     * The server groups to deploy to. If no server groups were defined an empty list is returned.
     *
     * @return the server groups or an empty list.
     */
    public List<String> getServerGroups() {
        return serverGroups == null ? Collections.<String>emptyList() : new ArrayList<String>(serverGroups);
    }
}
