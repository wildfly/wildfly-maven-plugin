/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.server;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ProcessDestroyTimer extends Thread {
    private final Process process;
    private final long timeout;

    ProcessDestroyTimer(final Process process, final long timeout) {
        this.process = process;
        this.timeout = timeout;
    }

    static ProcessDestroyTimer start(final Process process, final long timeout) {
        final ProcessDestroyTimer result = new ProcessDestroyTimer(process, timeout);
        result.start();
        return result;
    }

    @Override
    public void run() {
        try {
            TimeUnit.SECONDS.sleep(timeout);
            process.destroy();
        } catch (InterruptedException ignore) {
        }
    }
}
