/*
 * Copyright 2016-2023 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.plugin.core;

import org.apache.maven.plugin.logging.Log;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
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

    public static void initTrackers(ProvisioningManager pm, Log log) {
        pm.getLayoutFactory().setProgressCallback(ProvisioningLayoutFactory.TRACK_PACKAGES,
                new PluginProgressTracker<String>(log, "Installing packages", false));
        pm.getLayoutFactory().setProgressCallback(ProvisioningLayoutFactory.TRACK_CONFIGS,
                new PluginProgressTracker<String>(log, "Generating configurations", true));
        pm.getLayoutFactory().setProgressCallback(ProvisioningLayoutFactory.TRACK_LAYOUT_BUILD,
                new PluginProgressTracker<String>(log, "Resolving feature-packs", false));
        pm.getLayoutFactory().setProgressCallback("JBMODULES",
                new PluginProgressTracker<String>(log, "Resolving artifacts", false));
        pm.getLayoutFactory().setProgressCallback("JBEXTRACONFIGS",
                new PluginProgressTracker<String>(log, "Generating extra configurations", true));
    }
}
