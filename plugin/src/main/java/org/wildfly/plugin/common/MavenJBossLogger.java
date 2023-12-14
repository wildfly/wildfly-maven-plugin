/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.common;

import java.text.MessageFormat;

import org.apache.maven.plugin.logging.Log;
import org.jboss.logging.Logger;

/**
 * A logger which delegates to a {@link Log}.
 * <p>
 * For {@link #isEnabled(Level)} {@link org.jboss.logging.Logger.Level#TRACE} is ignored and
 * {@link org.jboss.logging.Logger.Level#FATAL}
 * is treated as an {@link Log#error(CharSequence, Throwable) error}.
 * </p>
 * <p>
 * For the log methods, {@link org.jboss.logging.Logger.Level#TRACE} is treated as a {@link Log#debug(CharSequence, Throwable)
 * debug}
 * and {@link org.jboss.logging.Logger.Level#FATAL} is treated as {@link Log#error(CharSequence, Throwable) error}.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class MavenJBossLogger extends Logger {
    private final Log mavenLogger;

    public MavenJBossLogger(final Log mavenLogger) {
        super(mavenLogger.toString());
        this.mavenLogger = mavenLogger;
    }

    @Override
    protected void doLog(final Level level, final String loggerClassName, final Object message, final Object[] parameters,
            final Throwable thrown) {
        final String msg = parameters == null ? String.valueOf(message)
                : MessageFormat.format(String.valueOf(message), parameters);
        doMavenLog(level, msg, thrown);
    }

    @Override
    protected void doLogf(final Level level, final String loggerClassName, final String format, final Object[] parameters,
            final Throwable thrown) {
        final String msg = String.format(format, parameters);
        doMavenLog(level, msg, thrown);
    }

    @Override
    public boolean isEnabled(final Level level) {
        switch (level) {
            case DEBUG:
                return mavenLogger.isDebugEnabled();
            case INFO:
                return mavenLogger.isInfoEnabled();
            case WARN:
                return mavenLogger.isWarnEnabled();
            case FATAL:
            case ERROR:
                return mavenLogger.isErrorEnabled();
        }
        return false;
    }

    private void doMavenLog(final Level level, final String msg, final Throwable thrown) {
        switch (level) {
            case TRACE:
            case DEBUG:
                if (thrown == null) {
                    mavenLogger.debug(msg);
                } else {
                    mavenLogger.debug(msg, thrown);
                }
                break;
            case WARN:
                if (thrown == null) {
                    mavenLogger.warn(msg);
                } else {
                    mavenLogger.warn(msg, thrown);
                }
                break;
            case FATAL:
            case ERROR:
                if (thrown == null) {
                    mavenLogger.error(msg);
                } else {
                    mavenLogger.error(msg, thrown);
                }
                break;
            default:
                if (thrown == null) {
                    mavenLogger.info(msg);
                } else {
                    mavenLogger.info(msg, thrown);
                }
        }
    }
}
