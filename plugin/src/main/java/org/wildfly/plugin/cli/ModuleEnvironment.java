/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.cli;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;

import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Utilities need to build up and tear down a modular or mock-modular environment.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ModuleEnvironment {

    private static final Map<Class<?>, String> DEFAULTS = new HashMap<>();
    private static final Map<Class<?>, String> JBM_DEFAULTS = new HashMap<>();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    static {
        add(DocumentBuilderFactory.class);
        add(SAXParserFactory.class);
        add(TransformerFactory.class);
        add(XPathFactory.class);
        add(XMLEventFactory.class);
        add(XMLInputFactory.class);
        add(XMLOutputFactory.class);
        add(DatatypeFactory.class);
        add(SchemaFactory.class);
        add(XMLReaderFactory.class);
    }

    /**
     * Initializes JAXP for a modular environment.
     */
    public static void initJaxp() {
        if (INITIALIZED.compareAndSet(false, true)) {
            __redirected.__JAXPRedirected.initAll();
        }
        replace(JBM_DEFAULTS);
    }

    /**
     * Restores the platform to a non-modular environment.
     */
    public static void restorePlatform() {
        replace(DEFAULTS);
    }

    private static void add(final Class<?> c) {
        DEFAULTS.put(c, System.getProperty(c.getName()));
        JBM_DEFAULTS.put(c, String.format("__redirected.__%s", c.getSimpleName()));
    }

    private static void replace(final Map<Class<?>, String> map) {
        for (Map.Entry<Class<?>, String> entry : map.entrySet()) {
            if (entry.getValue() == null) {
                System.clearProperty(entry.getKey().getName());
            } else {
                System.setProperty(entry.getKey().getName(), entry.getValue());
            }
        }
    }
}
