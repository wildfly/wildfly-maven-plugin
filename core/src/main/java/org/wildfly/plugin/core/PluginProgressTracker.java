/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.core;

import org.apache.maven.plugin.logging.Log;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.progresstracking.ProgressCallback;
import org.jboss.galleon.progresstracking.ProgressTracker;

/**
 *
 * @author jdenise@redhat.com
 */
public class PluginProgressTracker<T> implements ProgressCallback<T> {

    private static final String DELAYED_EXECUTION_MSG = "Delayed generation, waiting...";
    private final Log log;
    private final String msgStart;
    private long lastTime;
    private final boolean delayed;

    private PluginProgressTracker(Log log, String msgStart, boolean delayed) {
        this.log = log;
        this.msgStart = msgStart;
        this.delayed = delayed;
    }

    @Override
    public void starting(ProgressTracker<T> tracker) {
        log.info(msgStart);
        lastTime = System.currentTimeMillis();
    }

    @Override
    public void processing(ProgressTracker<T> tracker) {
        // The case of config generated in forked process.
        if (delayed && tracker.getItem() == null) {
            log.info(DELAYED_EXECUTION_MSG);
            return;
        }
        // Print a message every 5 seconds
        if (System.currentTimeMillis() - lastTime > 5000) {
            if (tracker.getTotalVolume() > 0) {
                log.info(String.format("%s of %s (%s%%)",
                        tracker.getProcessedVolume(), tracker.getTotalVolume(),
                        ((double) Math.round(tracker.getProgress() * 10)) / 10));
            } else {
                log.info("In progress...");
            }
            lastTime = System.currentTimeMillis();
        }
    }

    @Override
    public void processed(ProgressTracker<T> tracker) {
    }

    @Override
    public void pulse(ProgressTracker<T> tracker) {
    }

    @Override
    public void complete(ProgressTracker<T> tracker) {
    }

    public static void initTrackers(Provisioning pm, Log log) {
        pm.setProgressCallback(org.jboss.galleon.Constants.TRACK_PACKAGES,
                new PluginProgressTracker<String>(log, "Installing packages", false));
        pm.setProgressCallback(org.jboss.galleon.Constants.TRACK_CONFIGS,
                new PluginProgressTracker<String>(log, "Generating configurations", true));
        pm.setProgressCallback(org.jboss.galleon.Constants.TRACK_LAYOUT_BUILD,
                new PluginProgressTracker<String>(log, "Resolving feature-packs", false));
        pm.setProgressCallback("JBMODULES",
                new PluginProgressTracker<String>(log, "Resolving artifacts", false));
        pm.setProgressCallback("JBEXTRACONFIGS",
                new PluginProgressTracker<String>(log, "Generating extra configurations", true));
    }
}
