/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugins.core.bootablejar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jboss.dmr.ValueExpression;

/**
 * A simple expression parser which only parses the possible keys and default values.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class Expression {

    private static final int INITIAL = 0;
    private static final int GOT_DOLLAR = 1;
    private static final int GOT_OPEN_BRACE = 2;
    private static final int RESOLVED = 3;
    private static final int DEFAULT = 4;

    private final List<String> keys;
    private final String defaultValue;

    private Expression(final Collection<String> keys, final String defaultValue) {
        this.keys = new ArrayList<>(keys);
        this.defaultValue = defaultValue;
    }

    /**
     * Creates a collection of expressions based on the value.
     *
     * @param value the expression value
     *
     * @return the expression keys and default value for each expression found within the value
     */
    static Collection<Expression> parse(final ValueExpression value) {
        return parseExpression(value.getExpressionString());
    }

    /**
     * Creates a collection of expressions based on the value.
     *
     * @param value the expression value
     *
     * @return the expression keys and default value for each expression found within the value
     */
    static Collection<Expression> parse(final String expression) {
        return parseExpression(expression);
    }

    /**
     * All the keys associated with this expression.
     *
     * @return the keys
     */
    List<String> getKeys() {
        return Collections.unmodifiableList(keys);
    }

    /**
     * Checks if there is a default value.
     *
     * @return {@code true} if the default value is not {@code null}, otherwise {@code false}
     */
    boolean hasDefault() {
        return defaultValue != null;
    }

    /**
     * Returns the default value which may be {@code null}.
     *
     * @return the default value
     */
    String getDefaultValue() {
        return defaultValue;
    }

    void appendTo(final StringBuilder builder) {
        builder.append("${");
        final Iterator<String> iter = keys.iterator();
        while (iter.hasNext()) {
            builder.append(iter.next());
            if (iter.hasNext()) {
                builder.append(',');
            }
        }
        if (hasDefault()) {
            builder.append(':').append(defaultValue);
        }
        builder.append('}');
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        appendTo(builder);
        return builder.toString();
    }

    private static Collection<Expression> parseExpression(final String expression) {
        final Collection<Expression> result = new ArrayList<>();
        final Collection<String> keys = new ArrayList<>();
        final StringBuilder key = new StringBuilder();
        String defaultValue = null;
        final char[] chars = expression.toCharArray();
        final int len = chars.length;
        int state = 0;
        int start = -1;
        int nameStart = -1;
        for (int i = 0; i < len; i++) {
            char ch = chars[i];
            switch (state) {
                case INITIAL: {
                    if (ch == '$') {
                        state = GOT_DOLLAR;
                    }
                    continue;
                }
                case GOT_DOLLAR: {
                    if (ch == '{') {
                        start = i + 1;
                        nameStart = start;
                        state = GOT_OPEN_BRACE;
                    } else {
                        // invalid; emit and resume
                        state = INITIAL;
                    }
                    continue;
                }
                case GOT_OPEN_BRACE: {
                    switch (ch) {
                        case ':':
                        case '}':
                        case ',': {
                            final String name = expression.substring(nameStart, i).trim();
                            if ("/".equals(name)) {
                                state = ch == '}' ? INITIAL : RESOLVED;
                                continue;
                            } else if (":".equals(name)) {
                                state = ch == '}' ? INITIAL : RESOLVED;
                                continue;
                            }
                            key.append(name);
                            if (ch == '}') {
                                state = INITIAL;
                                if (key.length() > 0) {
                                    keys.add(key.toString());
                                    key.setLength(0);
                                }
                                result.add(new Expression(keys, defaultValue));
                                defaultValue = null;
                                keys.clear();
                                continue;
                            } else if (ch == ',') {
                                if (key.length() > 0) {
                                    keys.add(key.toString());
                                    key.setLength(0);
                                }
                                nameStart = i + 1;
                                continue;
                            } else {
                                start = i + 1;
                                state = DEFAULT;
                                if (key.length() > 0) {
                                    keys.add(key.toString());
                                    key.setLength(0);
                                }
                                continue;
                            }
                        }
                        default: {
                            continue;
                        }
                    }
                }
                case RESOLVED: {
                    if (ch == '}') {
                        state = INITIAL;
                        if (keys.size() > 0) {
                            result.add(new Expression(keys, defaultValue));
                            defaultValue = null;
                            keys.clear();
                        }
                    }
                    continue;
                }
                case DEFAULT: {
                    if (ch == '}') {
                        state = INITIAL;
                        defaultValue = expression.substring(start, i);
                        if (key.length() > 0) {
                            keys.add(key.toString());
                            key.setLength(0);
                        }
                        if (keys.size() > 0) {
                            result.add(new Expression(keys, defaultValue));
                            defaultValue = null;
                            keys.clear();
                        }
                    }
                    continue;
                }
                default:
                    throw new IllegalStateException();
            }
        }
        if (key.length() > 0) {
            keys.add(key.toString());
            result.add(new Expression(keys, defaultValue));
        }
        return result;
    }
}
