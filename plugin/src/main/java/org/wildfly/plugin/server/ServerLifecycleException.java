package org.wildfly.plugin.server;

public class ServerLifecycleException extends Exception {
    private static final long serialVersionUID = -8178535511373340006L;

    public ServerLifecycleException(String message) {
        super(message);
    }

    public ServerLifecycleException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServerLifecycleException(Throwable cause) {
        super(cause);
    }

}
