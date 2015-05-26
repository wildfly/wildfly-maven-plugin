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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class VersionTestCase {

    @Test
    public void testGetLatest() {
        compareLatest("10.0.2.Final", "10.0.2.Final", "9.0.1.Final", "8.0.0.Final", "1.0.0.Final", "10.0.0.Final");
        compareLatest("20.0.4.Alpha4", "2.0.3.Final", "20.0.4.Alpha3", "20.0.4.Alpha4", "10.0.5.Final");
        compareLatest("7.5.Final", "7.1.1.Final", "7.1.3.Final", "7.5.Final", "7.4.Final", "7.5.Final-SNAPSHOT");
    }

    @Test
    public void testSortOrder() {
        // Define a list in the expected order
        final List<String> orderedVersions = Arrays.asList(
                "1.0.0.a1-SNAPSHOT",
                "1.0.0.Alpha1",
                "1.0.0.Beta1",
                "1.0.0.b2",
                "1.0.0.Final",
                "1.0.1.Alpha3",
                "1.0.1.Alpha20",
                "1.7.0_6",
                "1.7.0_07-b06",
                "1.7.0_07-b07",
                "1.7.0_07",
                "1.7.0_09-a06",
                "10.1.0.Beta1",
                "10.1.0.GA-SNAPSHOT",
                "10.1.0",
                "10.1.1.Final",
                "11.0.0.Alpha5",
                "11.0.0.GA"
        );

        final List<String> versions = new ArrayList<>(orderedVersions);
        Collections.shuffle(versions);

        // All entries should in the same order
        Assert.assertTrue(orderedVersions.containsAll(versions));
        Collections.sort(versions, new VersionComparator());
        Assert.assertTrue(orderedVersions.equals(versions));
    }

    private void compareLatest(final String expected, final String... versions) {
        final SortedSet<String> set = new TreeSet<>(new VersionComparator());
        set.addAll(Arrays.asList(versions));
        Assert.assertEquals(expected, set.last());
    }
}
