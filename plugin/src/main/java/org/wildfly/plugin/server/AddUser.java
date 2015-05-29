package org.wildfly.plugin.server;

import org.apache.maven.plugins.annotations.Parameter;

public class AddUser {
    @Parameter
    public String user;
    @Parameter
    public String password;
}
