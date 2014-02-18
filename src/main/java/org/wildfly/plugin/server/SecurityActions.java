/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.TimeUnit;

import org.codehaus.plexus.classworlds.realm.ClassRealm;

/**
 * Security actions to perform possibly privileged operations. No methods in this class are to be made public under any
 * circumstances!
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class SecurityActions {

    /**
     * Bad hack and needs to be removed. Taken from https://github.com/jbossas/jboss-as-maven-plugin/pull/65
     */
    private static void respawnCurrentClassLoader() {
        final ClassLoader tccl = getCurrentClassLoader();
        if (tccl instanceof ClassRealm) {
            final ClassRealm classRealm = (ClassRealm) tccl;
            final ClassLoader newParent = createNewClassLoader(classRealm);
            classRealm.setParentClassLoader(newParent);
        }
        // There's nothing we can do, just let it fail if need be.
    }

    static void registerShutdown(final Server server) {
        final Thread hook = new Thread(new Runnable() {
            @Override
            public void run() {
                respawnCurrentClassLoader();
                server.stop();
                // Bad hack to get maven to complete it's message output
                try {
                    TimeUnit.MILLISECONDS.sleep(500L);
                } catch (InterruptedException ignore) {
                    // no-op
                }
            }
        });
        hook.setDaemon(true);
        addShutdownHook(hook);
    }
    static void addShutdownHook(final Thread hook) {
        if (System.getSecurityManager() == null) {
            Runtime.getRuntime().addShutdownHook(hook);
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    Runtime.getRuntime().addShutdownHook(hook);
                    return null;
                }
            });
        }
    }

    static String getEnvironmentVariable(final String key) {
        if (System.getSecurityManager() == null) {
            return System.getenv(key);
        }
        return AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return System.getenv(key);
            }
        });
    }

    static ClassLoader getCurrentClassLoader() {
        if (System.getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        }
        return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return Thread.currentThread().getContextClassLoader();
            }
        });
    }

    // TODO (jrp) remove when the hack above is removed
    private static ClassLoader createNewClassLoader(final ClassRealm classRealm) {
        final ClassLoader result;
        if (System.getSecurityManager() == null) {
            result = new URLClassLoader(classRealm.getURLs(), classRealm.getParentClassLoader()) {
                @Override
                protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
                    final Class<?> c = classRealm.loadClassFromSelf(name);
                    return c != null ? c : super.loadClass(name, resolve);
                }
            };
        } else {
            result = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return new URLClassLoader(classRealm.getURLs(), classRealm.getParentClassLoader()) {
                        @Override
                        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
                            final Class<?> c = classRealm.loadClassFromSelf(name);
                            return c != null ? c : super.loadClass(name, resolve);
                        }
                    };
                }
            });
        }
        return result;
    }
}
