/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.Buffer;
import java.nio.BufferOverflowException;
import java.util.Date;
import static org.glassfish.grizzly.http.util.CookieUtils.*;

/**
 * The set of Cookie utility methods for cookie serialization.
 *
 * @author Grizzly team
 */
public class CookieSerializerUtils {


    // TODO RFC2965 fields also need to be passed
    public static void serializeServerCookie(StringBuilder buf,
            Cookie cookie) {
        serializeServerCookie(buf,
                              COOKIE_VERSION_ONE_STRICT_COMPLIANCE,
                              RFC_6265_SUPPORT_ENABLED,
                              ALWAYS_ADD_EXPIRES,
                              cookie);
    }

    // TODO RFC2965 fields also need to be passed
    public static void serializeServerCookie(final StringBuilder buf,
            final boolean versionOneStrictCompliance,
            final boolean rfc6265Support,
            final boolean alwaysAddExpires,
            final Cookie cookie) {
        
        serializeServerCookie(buf, versionOneStrictCompliance, rfc6265Support,
                alwaysAddExpires, cookie.getName(), cookie.getValue(),
                cookie.getVersion(), cookie.getPath(), cookie.getDomain(),
                cookie.getComment(), cookie.getMaxAge(), cookie.isSecure(),
                cookie.isHttpOnly());
    }
    
    // TODO RFC2965 fields also need to be passed
    public static void serializeServerCookie(final StringBuilder buf,
            final boolean versionOneStrictCompliance,
            final boolean rfc6265Support,
            final boolean alwaysAddExpires,
            final String name,
            final String value,
            int version,
            String path,
            final String domain,
            final String comment,
            final int maxAge,
            final boolean isSecure,
            final boolean isHttpOnly) {
        // Servlet implementation checks name
        buf.append(name);
        buf.append('=');
        // Servlet implementation does not check anything else

        version = maybeQuote2(version, buf, value, true, rfc6265Support);

        // Add version 1 specific information
        if (version == 1) {
            // Version=1 ... required
            buf.append("; Version=1");

            // Comment=comment
            if (comment != null) {
                buf.append("; Comment=");
                maybeQuote2(version, buf, comment, versionOneStrictCompliance, rfc6265Support);
            }
        }

        // Add domain information, if present
        if (domain != null) {
            buf.append("; Domain=");
            maybeQuote2(version, buf, domain, versionOneStrictCompliance, rfc6265Support);
        }

        // Max-Age=secs ... or use old "Expires" format
        // TODO RFC2965 Discard
        if (maxAge >= 0) {
            if (version > 0) {
                buf.append("; Max-Age=");
                buf.append(maxAge);
            }
            // IE6, IE7 and possibly other browsers don't understand Max-Age.
            // They do understand Expires, even with V1 cookies!
            if (version == 0 || alwaysAddExpires) {
                // Wdy, DD-Mon-YY HH:MM:SS GMT ( Expires Netscape format )
                buf.append("; Expires=");
                // To expire immediately we need to set the time in past
                if (maxAge == 0) {
                    buf.append(ancientDate);
                } else {
                    buf.append(OLD_COOKIE_FORMAT.get().format(
                            new Date(System.currentTimeMillis()
                            + maxAge * 1000L)));
                }

            }
        }

        // Path=path
        if (path != null) {
            buf.append("; Path=");

            UEncoder encoder = new UEncoder();
            encoder.addSafeCharacter('/');
            encoder.addSafeCharacter('"');
            path = encoder.encodeURL(path, true);

            if (version == 0) {
                maybeQuote2(version, buf, path, versionOneStrictCompliance, rfc6265Support);
            } else {
                maybeQuote2(version, buf, path, tspecials2NoSlash, false, versionOneStrictCompliance, rfc6265Support);
            }
        }

        // Secure
        if (isSecure) {
            buf.append("; Secure");
        }

        // httpOnly
        if (isHttpOnly) {
            buf.append("; HttpOnly");
        }
    }

    // TODO RFC2965 fields also need to be passed
    public static void serializeServerCookie(Buffer buf,
            Cookie cookie) {
        serializeServerCookie(buf,
                COOKIE_VERSION_ONE_STRICT_COMPLIANCE, ALWAYS_ADD_EXPIRES,
                cookie);
    }

    // TODO RFC2965 fields also need to be passed
    public static void serializeServerCookie(Buffer buf,
            boolean versionOneStrictCompliance,
            boolean alwaysAddExpires,
            Cookie cookie) {
        serializeServerCookie(buf, versionOneStrictCompliance, alwaysAddExpires,
                cookie.getName(), cookie.getValue(), cookie.getVersion(),
                cookie.getPath(), cookie.getDomain(), cookie.getComment(),
                cookie.getMaxAge(), cookie.isSecure(), cookie.isHttpOnly());
    }

    // TODO RFC2965 fields also need to be passed
    public static void serializeServerCookie(final Buffer buf,
            final boolean versionOneStrictCompliance,
            final boolean alwaysAddExpires,
            final String name,
            final String value,
            int version,
            String path,
            final String domain,
            final String comment,
            final int maxAge,
            final boolean isSecure,
            final boolean isHttpOnly) {

        // Servlet implementation checks name
        put(buf, name);
        put(buf, '=');
        // Servlet implementation does not check anything else

        version = maybeQuote2(version, buf, value, true);

        // Add version 1 specific information
        if (version == 1) {
            // Version=1 ... required
            put(buf, "; Version=1");

            // Comment=comment
            if (comment != null) {
                put(buf, "; Comment=");
                maybeQuote2(version, buf, comment, versionOneStrictCompliance);
            }
        }

        // Add domain information, if present
        if (domain != null) {
            put(buf, "; Domain=");
            maybeQuote2(version, buf, domain, versionOneStrictCompliance);
        }

        // Max-Age=secs ... or use old "Expires" format
        // TODO RFC2965 Discard
        if (maxAge >= 0) {
            if (version > 0) {
                put(buf, "; Max-Age=");
                putInt(buf, maxAge);
            }
            // IE6, IE7 and possibly other browsers don't understand Max-Age.
            // They do understand Expires, even with V1 cookies!
            if (version == 0 || alwaysAddExpires) {
                // Wdy, DD-Mon-YY HH:MM:SS GMT ( Expires Netscape format )
                put(buf, "; Expires=");
                // To expire immediately we need to set the time in past
                if (maxAge == 0) {
                    put(buf, ancientDate);
                } else {
                    put(buf,
                    OLD_COOKIE_FORMAT.get().format(
                            new Date(System.currentTimeMillis()
                            + maxAge * 1000L)));
                }

            }
        }

        // Path=path
        if (path != null) {
            put(buf, "; Path=");

            UEncoder encoder = new UEncoder();
            encoder.addSafeCharacter('/');
            encoder.addSafeCharacter('"');
            path = encoder.encodeURL(path, true);

            if (version == 0) {
                maybeQuote2(version, buf, path, versionOneStrictCompliance);
            } else {
                maybeQuote2(version, buf, path, tspecials2NoSlash, false, versionOneStrictCompliance);
            }
        }

        // Secure
        if (isSecure) {
            put(buf, "; Secure");
        }

        // httpOnly
        if (isHttpOnly) {
            put(buf, "; HttpOnly");
        }
    }

    // TODO RFC2965 fields also need to be passed
    public static void serializeClientCookies(StringBuilder buf,
            Cookie... cookies) {
        serializeClientCookies(buf,
                               COOKIE_VERSION_ONE_STRICT_COMPLIANCE,
                               RFC_6265_SUPPORT_ENABLED,
                               cookies);
    }

    // TODO RFC2965 fields also need to be passed
    public static void serializeClientCookies(StringBuilder buf,
            boolean versionOneStrictCompliance,
            boolean rfc6265Support,
            Cookie... cookies) {

        if (cookies.length == 0) {
            return;
        }

        final int version = cookies[0].getVersion();

        if (!rfc6265Support && version == 1) {
            buf.append("$Version=\"1\"; ");
        }

        for (int i = 0; i < cookies.length; i++) {
            final Cookie cookie = cookies[i];

            buf.append(cookie.getName());
            buf.append('=');
            // Servlet implementation does not check anything else

            maybeQuote2(version, buf, cookie.getValue(), true, rfc6265Support);

            // If version == 1 - add domain and path
            if (!rfc6265Support && version == 1) {
                // $Domain="domain"
                final String domain = cookie.getDomain();
                if (domain != null) {
                    buf.append("; $Domain=");
                    maybeQuote2(version, buf, domain, versionOneStrictCompliance, rfc6265Support);
                }

                // $Path="path"
                String path = cookie.getPath();
                if (path != null) {
                    buf.append("; $Path=");

                    UEncoder encoder = new UEncoder();
                    encoder.addSafeCharacter('/');
                    encoder.addSafeCharacter('"');
                    path = encoder.encodeURL(path, true);

                    maybeQuote2(version, buf, path, tspecials2NoSlash, false, versionOneStrictCompliance, rfc6265Support);
                }
            }

            if (i < cookies.length - 1) {
                buf.append("; ");
            }
        }
    }

    // TODO RFC2965 fields also need to be passed
    public static void serializeClientCookies(Buffer buf,
            Cookie... cookies) {
        serializeClientCookies(buf, COOKIE_VERSION_ONE_STRICT_COMPLIANCE, cookies);
    }

    // TODO RFC2965 fields also need to be passed
    public static void serializeClientCookies(Buffer buf,
            boolean versionOneStrictCompliance,
            Cookie... cookies) {

        if (cookies.length == 0) {
            return;
        }

        final int version = cookies[0].getVersion();

        if (version == 1) {
            put(buf, "$Version=\"1\"; ");
        }

        for (int i = 0; i < cookies.length; i++) {
            final Cookie cookie = cookies[i];

            put(buf, cookie.getName());
            put(buf, "=");
            // Servlet implementation does not check anything else

            maybeQuote2(version, buf, cookie.getValue(), true);

            // If version == 1 - add domain and path
            if (version == 1) {
                // $Domain="domain"
                final String domain = cookie.getDomain();
                if (domain != null) {
                    put(buf, "; $Domain=");
                    maybeQuote2(version, buf, domain, versionOneStrictCompliance);
                }

                // $Path="path"
                String path = cookie.getPath();
                if (path != null) {
                    put(buf, "; $Path=");

                    UEncoder encoder = new UEncoder();
                    encoder.addSafeCharacter('/');
                    encoder.addSafeCharacter('"');
                    path = encoder.encodeURL(path, true);

                    maybeQuote2(version, buf, path, tspecials2NoSlash, false, versionOneStrictCompliance);
                }
            }

            if (i < cookies.length - 1) {
                put(buf, "; ");
            }
        }
    }
    
    /**
     * Quotes values using rules that vary depending on Cookie version.
     * @param version
     * @param buf
     * @param value
     */
    public static int maybeQuote2(int version, StringBuilder buf, String value,
            boolean versionOneStrictCompliance,
            boolean rfc6265Enabled) {
        return maybeQuote2(version, buf, value, false, versionOneStrictCompliance,
                           rfc6265Enabled);
    }

    public static int maybeQuote2(int version, StringBuilder buf, String value,
            boolean allowVersionSwitch, boolean versionOneStrictCompliance,
            boolean rfc6265Enabled) {
        return maybeQuote2(version, buf, value, null, allowVersionSwitch,
                           versionOneStrictCompliance, rfc6265Enabled);
    }

    public static int maybeQuote2(int version, StringBuilder buf, String value,
            String literals, boolean allowVersionSwitch, boolean versionOneStrictCompliance,
            boolean rfc6265Enabled) {
        if (value == null || value.length() == 0) {
            buf.append("\"\"");
        } else if (containsCTL(value, version)) {
            throw new IllegalArgumentException("Control character in cookie value, consider BASE64 encoding your value");
        } else if (alreadyQuoted(value)) {
            buf.append('"');
            buf.append(escapeDoubleQuotes(value, 1, value.length() - 1));
            buf.append('"');
        } else if (allowVersionSwitch && versionOneStrictCompliance && version == 0 && !isToken2(value, literals)) {
            buf.append('"');
            buf.append(escapeDoubleQuotes(value, 0, value.length()));
            buf.append('"');
            version = 1;
        } else if (version == 0 && !isToken(value, literals)) {
            buf.append('"');
            buf.append(escapeDoubleQuotes(value, 0, value.length()));
            buf.append('"');
        } else if (version == 1 && !isToken2(value, literals)) {
            buf.append('"');
            buf.append(escapeDoubleQuotes(value, 0, value.length()));
            buf.append('"');
        } else if (version < 0 && rfc6265Enabled) {
            buf.append('"');
            buf.append(escapeDoubleQuotes(value, 0, value.length()));
            buf.append('"');
        } else {
            buf.append(value);
        }
        return version;
    }


    /**
     * Quotes values using rules that vary depending on Cookie version.
     * @param version
     * @param buf
     * @param value
     */
    public static int maybeQuote2(int version, Buffer buf, String value,
            boolean versionOneStrictCompliance) {
        return maybeQuote2(version, buf, value, false, versionOneStrictCompliance);
    }

    public static int maybeQuote2(int version, Buffer buf, String value,
            boolean allowVersionSwitch, boolean versionOneStrictCompliance) {
        return maybeQuote2(version, buf, value, null, allowVersionSwitch, versionOneStrictCompliance);
    }

    public static int maybeQuote2(int version, Buffer buf, String value,
            String literals, boolean allowVersionSwitch, boolean versionOneStrictCompliance) {
        if (value == null || value.length() == 0) {
            put(buf, "\"\"");
        } else if (containsCTL(value, version)) {
            throw new IllegalArgumentException("Control character in cookie value, consider BASE64 encoding your value");
        } else if (alreadyQuoted(value)) {
            put(buf, '"');
            put(buf, escapeDoubleQuotes(value, 1, value.length() - 1));
            put(buf, '"');
        } else if (allowVersionSwitch && versionOneStrictCompliance && version == 0 && !isToken2(value, literals)) {
            put(buf, '"');
            put(buf, escapeDoubleQuotes(value, 0, value.length()));
            put(buf, '"');
            version = 1;
        } else if (version == 0 && !isToken(value, literals)) {
            put(buf, '"');
            put(buf, escapeDoubleQuotes(value, 0, value.length()));
            put(buf, '"');
        } else if (version == 1 && !isToken2(value, literals)) {
            put(buf, '"');
            put(buf, escapeDoubleQuotes(value, 0, value.length()));
            put(buf, '"');
        } else {
            put(buf, value);
        }
        return version;
    }

    /**
     * Escapes any double quotes in the given string.
     *
     * @param s the input string
     * @param beginIndex start index inclusive
     * @param endIndex exclusive
     * @return The (possibly) escaped string
     */
    private static String escapeDoubleQuotes(String s, int beginIndex, int endIndex) {

        if (s == null || s.length() == 0 || s.indexOf('"') == -1) {
            return s;
        }

        StringBuilder b = new StringBuilder();
        for (int i = beginIndex; i < endIndex; i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                b.append(c);
                //ignore the character after an escape, just append it
                if (++i >= endIndex) {
                    throw new IllegalArgumentException("Invalid escape character in cookie value.");
                }
                b.append(s.charAt(i));
            } else if (c == '"') {
                b.append('\\').append('"');
            } else {
                b.append(c);
            }
        }

        return b.toString();
    }
    
    public static boolean alreadyQuoted(String value) {
        return !(value == null || value.length() == 0)
                  && (value.charAt(0) == '\"'
                  && value.charAt(value.length() - 1) == '\"');
    }
    
    static void put(Buffer dstBuffer, int c) {
        dstBuffer.put((byte) c);
    }

    static void putInt(Buffer dstBuffer, int intValue) {
        put(dstBuffer, Integer.toString(intValue));
    }

    static void put(Buffer dstBuffer, String s) {
        final int size = s.length();
        
        if (dstBuffer.remaining() < size) {
            throw new BufferOverflowException();
        }

        for (int i = 0; i < size; i++) {
            dstBuffer.put((byte) s.charAt(i));
        }
    }

    static void put(StringBuilder dstBuffer, int c) {
        dstBuffer.append((char) c);
    }

    static void putInt(StringBuilder dstBuffer, int intValue) {
        dstBuffer.append(intValue);
    }

    static void put(StringBuilder dstBuffer, String s) {
        dstBuffer.append(s);
    }

}
