/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.glassfish.grizzly.http.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.glassfish.grizzly.Buffer;

/**
 * The general set of Cookie utility methods.
 * 
 * @author Grizzly team
 */
public final class CookieUtils {

    /**
     * If set to true, then it will double quote the value and update cookie version
     * when there is special characters.
     *
     * Tomcat uses STRICT_SERVLET_COMPLIANCE, whereas this code uses
     * COOKIE_VERSION_ONE_STRICT_COMPLIANCE, which unlike the
     * Tomcat variant not only affects cookie generation, but also cookie parsing.
     * By default, cookies are parsed as v0 cookies, in order to maintain backward
     * compatibility with GlassFish v2.x
     */
    public static final boolean COOKIE_VERSION_ONE_STRICT_COMPLIANCE =
            Boolean.getBoolean("org.glassfish.web.rfc2109_cookie_names_enforced");

    public static final boolean RFC_6265_SUPPORT_ENABLED =
            Boolean.getBoolean("org.glassfish.web.rfc_6265_support_enabled");

    /**
     * If set to false, we don't use the IE6/7 Max-Age/Expires work around
     */
    public static final boolean ALWAYS_ADD_EXPIRES =
        Boolean.valueOf(System.getProperty("org.glassfish.grizzly.util.http.ServerCookie.ALWAYS_ADD_EXPIRES", "true"));

    /*
    List of Separator Characters (see isSeparator())
    Excluding the '/' char violates the RFC, but
    it looks like a lot of people put '/'
    in unquoted values: '/': ; //47
    '\t':9 ' ':32 '\"':34 '\'':39 '(':40 ')':41 ',':44 ':':58 ';':59 '<':60
    '=':61 '>':62 '?':63 '@':64 '[':91 '\\':92 ']':93 '{':123 '}':125
     */
    static final char SEPARATORS[] = {'\t', ' ', '\"', '\'', '(', ')', ',',
        ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '{', '}'};

    static final boolean separators[] = new boolean[128];
    static {
        for (int i = 0; i < 128; i++) {
            separators[i] = false;
        }
        for (char SEPARATOR : SEPARATORS) {
            separators[SEPARATOR] = true;
        }
    }

    static final String OLD_COOKIE_PATTERN = "EEE, dd-MMM-yyyy HH:mm:ss z";

    public static final ThreadLocal<SimpleDateFormat> OLD_COOKIE_FORMAT =
            new ThreadLocal<SimpleDateFormat>() {

                @Override
                protected SimpleDateFormat initialValue() {
                    // old cookie pattern format
                    SimpleDateFormat f = new SimpleDateFormat(OLD_COOKIE_PATTERN, Locale.US);
                    f.setTimeZone(TimeZone.getTimeZone("GMT"));
                    return f;
                }
            };
    static final String ancientDate = OLD_COOKIE_FORMAT.get().format(new Date(10000));

    static final String tspecials = ",; ";
    static final String tspecials2 = "()<>@,;:\\\"/[]?={} \t";
    static final String tspecials2NoSlash = "()<>@,;:\\\"[]?={} \t";

    
    /*
     * Tests a string and returns true if the string counts as a
     * reserved token in the Java language.
     *
     * @param value the <code>String</code> to be tested
     *
     * @return      <code>true</code> if the <code>String</code> is a reserved
     *              token; <code>false</code> if it is not
     */
    public static boolean isToken(String value) {
        return isToken(value, null);
    }

    public static boolean isToken(String value, String literals) {
        String ts = (literals == null ? tspecials : literals);
        if (value == null) {
            return true;
        }
        int len = value.length();

        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);

            if (ts.indexOf(c) != -1) {
                return false;
            }
        }
        return true;
    }

    public static boolean containsCTL(String value, int version) {
        if (value == null) {
            return false;
        }
        int len = value.length();
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c >= 0x7f) {
                if (c == 0x09) {
                    continue; //allow horizontal tabs
                }
                return true;
            }
        }
        return false;
    }

    public static boolean isToken2(String value) {
        return isToken2(value, null);
    }

    public static boolean isToken2(String value, String literals) {
        String ts = (literals == null ? tspecials2 : literals);
        if (value == null) {
            return true;
        }
        int len = value.length();

        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (ts.indexOf(c) != -1) {
                return false;
            }
        }
        return true;
    }

    // XXX will be refactored soon!
    public static boolean equals(String s, byte[] b, int start, int end) {
        int blen = end - start;
        if (b == null || blen != s.length()) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            if (b[boff++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    
    // XXX will be refactored soon!
    public static boolean equals(String s, Buffer b, int start, int end) {
        int blen = end - start;
        if (b == null || blen != s.length()) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            if (b.get(boff++) != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    // XXX will be refactored soon!
    public static boolean equals(String s1, String s2, int start, int end) {
        int blen = end - start;
        if (s2 == null || blen != s1.length()) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            if (s2.charAt(boff++) != s1.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    // XXX will be refactored soon!
    public static boolean equalsIgnoreCase(String s, Buffer b, int start, int end) {
        int blen = end - start;
        if (b == null || blen != s.length()) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            final int b1 = Ascii.toLower(b.get(boff++));
            final int b2 = Ascii.toLower(s.charAt(i));
            if (b1 != b2) {
                return false;
            }
        }
        return true;
    }

    // XXX will be refactored soon!
    public static boolean equalsIgnoreCase(String s, byte[] b, int start, int end) {
        int blen = end - start;
        if (b == null || blen != s.length()) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            final int b1 = Ascii.toLower(b[boff++]);
            final int b2 = Ascii.toLower(s.charAt(i));
            if (b1 != b2) {
                return false;
            }
        }
        return true;
    }

    // XXX will be refactored soon!
    public static boolean equalsIgnoreCase(String s1, String s2, int start, int end) {
        int blen = end - start;
        if (s2 == null || blen != s1.length()) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            final int b1 = Ascii.toLower(s1.charAt(i));
            final int b2 = Ascii.toLower(s2.charAt(boff++));
            if (b1 != b2) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Returns true if the byte is a separator character as
     * defined in RFC2619. Since this is called often, this
     * function should be organized with the most probable
     * outcomes first.
     */
    public static boolean isSeparator(final int c) {
        return isSeparator(c, true);
    }

    public static boolean isSeparator(final int c, final boolean parseAsVersion1) {
        if (parseAsVersion1) {
            return c > 0 && c < 126 && separators[c];
        } else {
            return (c == ';' || c == ',');
        }
    }

    /**
     * Returns true if the byte is a whitespace character as
     * defined in RFC2619.
     */
    public static boolean isWhiteSpace(final int c) {
        return (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f');
    }

    /**
     * Given the starting position of a token, this gets the end of the
     * token, with no separator characters in between.
     * JVK
     */
    public static int getTokenEndPosition(Buffer buffer, int off, int end) {
        return getTokenEndPosition(buffer, off, end, true);
    }

    public static int getTokenEndPosition(Buffer buffer, int off, int end,
            boolean parseAsVersion1) {
        int pos = off;
        while (pos < end && !isSeparator(buffer.get(pos), parseAsVersion1)) {
            pos++;
        }

        if (pos > end) {
            return end;
        }
        return pos;
    }

    /**
     * Given the starting position of a token, this gets the end of the
     * token, with no separator characters in between.
     * JVK
     */
    public static int getTokenEndPosition(byte[] bytes, int off, int end) {
        return getTokenEndPosition(bytes, off, end, true);
    }

    public static int getTokenEndPosition(byte[] bytes, int off, int end,
                                          boolean parseAsVersion1) {
        int pos = off;
        while (pos < end && !isSeparator(bytes[pos], parseAsVersion1)) {
            pos++;
        }

        if (pos > end) {
            return end;
        }
        return pos;
    }

    /**
     * Given the starting position of a token, this gets the end of the
     * token, with no separator characters in between.
     * JVK
     */
    public static int getTokenEndPosition(String s, int off, int end) {
        return getTokenEndPosition(s, off, end, true);
    }

    public static int getTokenEndPosition(String s, int off, int end,
            boolean parseAsVersion1) {
        int pos = off;
        while (pos < end && !isSeparator(s.charAt(pos), parseAsVersion1)) {
            pos++;
        }

        if (pos > end) {
            return end;
        }
        return pos;
    }
    
    /**
     * Given a starting position after an initial quote character, this gets
     * the position of the end quote. This escapes anything after a '\' char
     * JVK RFC 2616
     */
    public static int getQuotedValueEndPosition(Buffer buffer, int off, int end) {
        int pos = off;
        while (pos < end) {
            if (buffer.get(pos) == '"') {
                return pos;
            } else if (buffer.get(pos) == '\\' && pos < (end - 1)) {
                pos += 2;
            } else {
                pos++;
            }
        }
        // Error, we have reached the end of the header w/o a end quote
        return end;
    }

    /**
     * Given a starting position after an initial quote character, this gets
     * the position of the end quote. This escapes anything after a '\' char
     * JVK RFC 2616
     */
    public static int getQuotedValueEndPosition(byte[] bytes, int off, int end) {
        int pos = off;
        while (pos < end) {
            if (bytes[pos] == '"') {
                return pos;
            } else if (bytes[pos] == '\\' && pos < (end - 1)) {
                pos += 2;
            } else {
                pos++;
            }
        }
        // Error, we have reached the end of the header w/o a end quote
        return end;
    }

    /**
     * Given a starting position after an initial quote character, this gets
     * the position of the end quote. This escapes anything after a '\' char
     * JVK RFC 2616
     */
    public static int getQuotedValueEndPosition(String s, int off, int end) {
        int pos = off;
        while (pos < end) {
            if (s.charAt(pos) == '"') {
                return pos;
            } else if (s.charAt(pos) == '\\' && pos < (end - 1)) {
                pos += 2;
            } else {
                pos++;
            }
        }
        // Error, we have reached the end of the header w/o a end quote
        return end;
    }
}
