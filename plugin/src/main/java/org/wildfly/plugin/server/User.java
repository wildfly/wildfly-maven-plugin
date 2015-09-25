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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Represents a user for the server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class User {

    /**
     * The username for the user
     */
    @Parameter(required = true)
    private String username;

    /**
     * The password for the user
     */
    @Parameter(required = true)
    private String password;

    /**
     * A collection of groups the user belongs to.
     */
    @Parameter
    private List<String> groups;

    /**
     * The realm the user belongs to.
     */
    @Parameter
    private String realm;

    /**
     * Indicates whether or not this is an application user or not. A value of {@code true} indicates an applications.
     */
    @Parameter(alias = "application-user", defaultValue = "false")
    private boolean applicationUser;

    /**
     * Returns the username for the user.
     *
     * @return the user name
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the password for the user.
     *
     * @return the users password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns a collection of groups the user belongs to.
     *
     * @return the groups the user belongs
     */
    public List<String> getGroups() {
        return groups;
    }

    /**
     * Returns the realm the user belongs to.
     *
     * @return the users realm
     */
    public String getRealm() {
        return realm;
    }

    /**
     * Indicates whether or not this is an application user.
     *
     * @return {@code true} if this is an application user, otherwise {@code false}
     */
    public boolean isApplicationUser() {
        return applicationUser;
    }

    /**
     * Allows a user to be set by a string value. The format is {@code user:password:groups:realm:true|false}.
     * <p>
     * Both username and password are required. Groups can be a comma delimited set of groups.
     * </p>
     *
     * @param value the string user value
     */
    public void set(final String value) {
        // username:password:groups:realm:true|false
        if (value != null) {
            final List<String> parts = splitAndTrim(value, ':');
            // User name and password should be required so we need a minimum of 2
            switch (parts.size()) {
                case 5: {
                    applicationUser = Boolean.parseBoolean(parts.get(4));
                }
                case 4: {
                    realm = parts.get(3);
                }
                case 3: {
                    final String g = parts.get(2);
                    if (!g.isEmpty()) {
                        groups = splitAndTrim(g, ',');
                    }
                }
                case 2: {
                    username = parts.get(0);
                    password = parts.get(1);
                    break;
                }
                default:
                    throw new IllegalArgumentException("Username and password are required parameters");
            }
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, password);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof User)) {
            return false;
        }
        final User other = (User) obj;
        return Objects.equals(username, other.username) && Objects.equals(password, other.password);
    }

    @Override
    public String toString() {
        return "User{username=" + username + ", groups=" + groups + ", realm=" + realm + ", applicationUser=" + applicationUser + "}";
    }

    private static List<String> splitAndTrim(final String value, final char splitChar) {
        final List<String> result = new ArrayList<>();
        final StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == splitChar) {
                result.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            result.add(sb.toString().trim());
        }
        return result;
    }
}
