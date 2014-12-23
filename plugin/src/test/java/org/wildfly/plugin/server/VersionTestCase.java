/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.server;

import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.plugin.server.RuntimeVersions.VersionComparator;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class VersionTestCase {

    @Test
    public void testSortOrder() {

        compareLatest("10.0.2.Final", "10.0.2.Final", "9.0.1.Final", "8.0.0.Final", "1.0.0.Final", "10.0.0.Final");
        compareLatest("20.0.4.Alpha4", "2.0.3.Final", "20.0.4.Alpha3", "20.0.4.Alpha4", "10.0.5.Final");
        compareLatest("7.5.Final", "7.1.1.Final", "7.1.3.Final", "7.5.Final", "7.4.Final");
    }

    private void compareLatest(final String expected, final String... versions) {
        final SortedSet<String> set = new TreeSet<>(new VersionComparator());
        set.addAll(Arrays.asList(versions));
        Assert.assertEquals(expected, set.last());
    }
}
