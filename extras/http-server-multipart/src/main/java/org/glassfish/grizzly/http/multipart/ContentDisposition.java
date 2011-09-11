/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.grizzly.http.multipart;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Content-Disposition representation.
 * 
 * @author Alexey Stashok
 */
public class ContentDisposition {
    protected final String disposition;

    private boolean isParamsParsed;

    protected String dispositionType;
    protected final Map<String, ParamValue> dispositionParams =
            new HashMap<String, ParamValue>();

    protected ContentDisposition(final String disposition) {
        this.disposition = disposition;
    }

    public String getDisposition() {
        return disposition;
    }

    public String getDispositionType() {
        if (!isParamsParsed) {
            isParamsParsed = true;
            parseParams();
        }

        return dispositionType;
    }

    public Set<String> getDispositionParams() {
        if (!isParamsParsed) {
            isParamsParsed = true;
            parseParams();
        }

        return dispositionParams.keySet();
    }

    public String getDispositionParam(String paramName) {
        if (!isParamsParsed) {
            isParamsParsed = true;
            parseParams();
        }

        final ParamValue v = dispositionParams.get(paramName);
        return ((v != null) ? v.get() : null);
    }

    public String getDispositionParamUnquoted(String paramName) {
        if (!isParamsParsed) {
            isParamsParsed = true;
            parseParams();
        }
        final ParamValue v = dispositionParams.get(paramName);
        return ((v != null) ? v.getUnquoted() : null);
    }

    @Override
    public String toString() {
        return disposition;
    }

    @Override
    public int hashCode() {
        return disposition.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final ContentDisposition other = (ContentDisposition) obj;
        if ((this.disposition == null) ? (other.disposition != null) : !this.disposition.equals(other.disposition)) {
            return false;
        }
        return true;
    }

    private void parseParams() {
        // find disposition type
        int semicolon1 = disposition.indexOf(';');
        if (semicolon1 == -1) {
            dispositionType = disposition;
            return;
        }

        // parse parameters
        for (int i = semicolon1 + 1; i < disposition.length(); i++) {
            // skip spaces before the param name
            final int nameStart = skipSpaces(i);
            if (nameStart == -1) {
                return;
            }

            i = nameStart;

            // find the end of the name
            final int eq = skipTo('=', i);

            if (eq == -1) {
                // unexpected eol
                throw new IllegalStateException("Can't locate '=' symbol");
            }

            final String name = disposition.substring(nameStart,
                    findLastNonSpace(eq - 1, nameStart) + 1);

            final int valueStart = skipSpaces(eq + 1);

            if (valueStart == -1) {
                // unexpected eol
                throw new IllegalStateException("Can't find parameter value");
            }

            final char q = disposition.charAt(valueStart);

            boolean isQuot = (q == '"' || q == '\'');

            final String value;
            int semicolonN;

            if (isQuot) {
                final int nextQuot = skipTo(q, valueStart + 1);

                if (nextQuot == -1) {
                    // unexpected eol
                    throw new IllegalStateException("Closing quot wasn't found");
                }

                value = disposition.substring(valueStart, nextQuot + 1);
                semicolonN = skipToSemicolon(nextQuot + 1);
            } else {
            
                // skip to ';'
                semicolonN = skipToSemicolon(valueStart + 1);

                value = disposition.substring(valueStart,
                        findLastNonSpace(semicolonN - 1, valueStart) + 1);

            }

            dispositionParams.put(name, new ParamValue(value));

            i = semicolonN + 1;
        }
    }

    private int skipSpaces(int offset) {
        while (offset < disposition.length()) {
            if (disposition.charAt(offset) >= 33) {
                return offset;
            }

            offset++;
        }

        return -1;
    }

    private int findLastNonSpace(int offset, final int lowLim) {
        while (offset >= lowLim) {
            final char c = disposition.charAt(offset);
            if (c > 32) {
                return offset;
            }

            offset--;
        }

        return -1;
    }

    private int skipTo(final char charToSkip, int offset) {
        while (offset < disposition.length()) {
            final char c = disposition.charAt(offset);
            if (c == charToSkip) {
                return offset;
            }

            offset++;
        }

        return -1;
    }

    private int skipToSemicolon(int offset) {
        while (offset < disposition.length()) {
            final char c = disposition.charAt(offset);
            if (c == ';') {
                return offset;
            }

            offset++;
        }

        return offset;
    }

    protected final static class ParamValue {
        private final String initial;
        private volatile String unquoted;

        public ParamValue(String initial) {
            this.initial = initial;
        }

        public String get() {
            return initial;
        }

        public String getUnquoted() {
            if (unquoted == null)  {
                if (isQuoted(initial)) {
                    unquoted = initial.substring(1, initial.length() -1);
                } else {
                    unquoted = initial;
                }
            }

            return unquoted;
        }

        private static boolean isQuoted(final String initial) {
            final int length = initial.length();
            if (length < 2) {
                return false;
            }

            final char c = initial.charAt(0);

            return (c == '\'' || c == '"') && c == initial.charAt(length - 1);
        }
    }
}
