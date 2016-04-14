package org.wildfly.plugin.server;

public class StartupTimeoutException extends ServerLifecycleException {

    private static final long serialVersionUID = 2523948958417669077L;

    public StartupTimeoutException(String message) {
        super(message);
    }

    public StartupTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    public StartupTimeoutException(Throwable cause) {
        super(cause);
    }

}
