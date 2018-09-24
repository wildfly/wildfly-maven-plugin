/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.plugins.annotations.Parameter;
import org.wildfly.plugin.common.Environment;

/**
 * Used to describe the users that should be added the server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AddUser {

    /**
     * A collection of users that should be added to the server.
     */
    @Parameter
    private List<User> users;

    /**
     * Allows a user to be set by a string value. Only one user is allowed to be created via this method. The format is
     * {@code user:password:groups:realm:true|false}.
     * <p>
     * Both username and password are required. Groups can be a comma delimited set of groups.
     * </p>
     *
     * @param value the string user value
     */
    public void set(final String value) {
        final User user = new User();
        user.set(value);
        users = Collections.singletonList(user);
    }

    boolean hasUsers() {
        return users != null && !users.isEmpty();
    }

    void addUsers(final Path wildflyHome, final Path javaHome) throws IOException {
        if (users != null) {
            for (User user : users) {
                addUser(wildflyHome, user, javaHome);
            }
        }
    }

    private void addUser(final Path wildflyHome, final User user, final Path javaHome) throws IOException {
        final List<String> cmd = new ArrayList<>();
        cmd.add(Environment.getJavaCommand(javaHome));
        if (Environment.isModularJvm(javaHome)) {
            Collections.addAll(cmd, Environment.getModularJvmArguments());
        }
        cmd.add("-jar");
        cmd.add(wildflyHome.resolve("jboss-modules.jar").toString());
        cmd.add("-mp");
        cmd.add(wildflyHome.resolve("modules").toString());
        cmd.add("org.jboss.as.domain-add-user");
        cmd.add("-u");
        cmd.add(user.getUsername());
        cmd.add("-p");
        cmd.add(user.getPassword());
        cmd.add("-s");

        setOptionalValue(cmd, "-a", user.isApplicationUser());
        setOptionalValue(cmd, "-g", user.getGroups());
        setOptionalValue(cmd, "-r", user.getRealm());

        final ProcessBuilder processBuilder = new ProcessBuilder(cmd)
                .inheritIO()
                .directory(wildflyHome.toFile());
        processBuilder.environment().put("JBOSS_HOME", wildflyHome.toString());

        final Process process = processBuilder.start();
        final Thread shutdown = ProcessDestroyTimer.start(process, 30L);
        int returnValue = -1;
        try {
            returnValue = process.waitFor();
        } catch (InterruptedException ignore) {

        }
        if (returnValue != 0) {
            throw new IllegalStateException("Could not add user");
        }
        shutdown.interrupt();
    }

    @Override
    public String toString() {
        if (users == null) {
            return "null";
        }
        final StringBuilder result = new StringBuilder(128);
        final Iterator<User> iterator = users.iterator();
        while (iterator.hasNext()) {
            result.append(iterator.next());
            if (iterator.hasNext()) {
                result.append(System.lineSeparator());
                result.append('\t');
            }
        }
        return result.toString();
    }

    private static void setOptionalValue(final Collection<String> cmd, final String key, final boolean value) {
        if (value) {
            cmd.add(key);
        }
    }

    private static void setOptionalValue(final Collection<String> cmd, final String key, final String value) {
        if (value != null && !value.isEmpty()) {
            cmd.add(key);
            cmd.add(value);
        }
    }

    private static void setOptionalValue(final Collection<String> cmd, final String key, final Iterable<String> value) {
        if (value != null) {
            final Iterator<String> iterator = value.iterator();
            if (iterator.hasNext()) {
                cmd.add(key);
                final StringBuilder sb = new StringBuilder();
                while (iterator.hasNext()) {
                    sb.append(iterator.next());
                    if (iterator.hasNext()) {
                        sb.append(',');
                    }
                }
                cmd.add(sb.toString());
            }
        }
    }
}
