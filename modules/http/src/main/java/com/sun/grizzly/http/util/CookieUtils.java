/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2008 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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

package com.sun.grizzly.http.util;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Grizzly;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Logger;

/**
 * The set of Cookie utility methods for serializing and parsing.
 * 
 * @author Grizzly team
 */
public class CookieUtils {
    private static final Logger LOGGER = Grizzly.logger(CookieUtils.class);

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
    static final boolean COOKIE_VERSION_ONE_STRICT_COMPLIANCE =
            Boolean.getBoolean("org.glassfish.web.rfc2109_cookie_names_enforced");

    /**
     * If set to false, we don't use the IE6/7 Max-Age/Expires work around
     */
    public static final boolean ALWAYS_ADD_EXPIRES =
            Boolean.valueOf(System.getProperty(
            "com.sun.grizzly.util.http.ServerCookie.ALWAYS_ADD_EXPIRES", "true")).booleanValue();

    /*
    List of Separator Characters (see isSeparator())
    Excluding the '/' char violates the RFC, but
    it looks like a lot of people put '/'
    in unquoted values: '/': ; //47
    '\t':9 ' ':32 '\"':34 '\'':39 '(':40 ')':41 ',':44 ':':58 ';':59 '<':60
    '=':61 '>':62 '?':63 '@':64 '[':91 '\\':92 ']':93 '{':123 '}':125
     */
    public static final char SEPARATORS[] = {'\t', ' ', '\"', '\'', '(', ')', ',',
        ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '{', '}'};

    protected static final boolean separators[] = new boolean[128];
    static {
        for (int i = 0; i < 128; i++) {
            separators[i] = false;
        }
        for (int i = 0; i < SEPARATORS.length; i++) {
            separators[SEPARATORS[i]] = true;
        }
    }

    private static final String OLD_COOKIE_PATTERN =
            "EEE, dd-MMM-yyyy HH:mm:ss z";

    private static final ThreadLocal<SimpleDateFormat> OLD_COOKIE_FORMAT =
            new ThreadLocal<SimpleDateFormat>() {

                @Override
                protected SimpleDateFormat initialValue() {
                    // old cookie pattern format
                    SimpleDateFormat f = new SimpleDateFormat(OLD_COOKIE_PATTERN, Locale.US);
                    f.setTimeZone(TimeZone.getTimeZone("GMT"));
                    return f;
                }
            };
    private static final String ancientDate = OLD_COOKIE_FORMAT.get().format(new Date(10000));

    private static final String tspecials = ",; ";
    private static final String tspecials2 = "()<>@,;:\\\"/[]?={} \t";
    private static final String tspecials2NoSlash = "()<>@,;:\\\"[]?={} \t";

    /**
     * Parses a cookie header after the initial "Cookie:"
     * [WS][$]token[WS]=[WS](token|QV)[;|,]
     * RFC 2965
     * JVK
     */
    public static void parseClientCookies(Collection<Cookie> cookies,
            Buffer buffer, int off, int len) {
        parseClientCookies(cookies, buffer, off, len, COOKIE_VERSION_ONE_STRICT_COMPLIANCE);
    }

    /**
     * Parses a cookie header after the initial "Cookie:"
     * [WS][$]token[WS]=[WS](token|QV)[;|,]
     * RFC 2965
     * JVK
     */
    public static void parseClientCookies(Collection<Cookie> cookies,
            Buffer buffer, int off, int len, boolean versionOneStrictCompliance) {
        
        if (len <= 0 || buffer == null) {
            throw new IllegalArgumentException();
        }
        
        int end = off + len;
        int pos = off;
        int nameStart = 0;
        int nameEnd = 0;
        int valueStart = 0;
        int valueEnd = 0;
        int version = 0;

        LazyCookie cookie = null;
        LazyCookieState lazyCookie = null;
        
        boolean isSpecial;
        boolean isQuoted;

        while (pos < end) {
            isSpecial = false;
            isQuoted = false;

            // Skip whitespace and non-token characters (separators)
            while (pos < end
                    && (isSeparator(buffer.get(pos)) || isWhiteSpace(buffer.get(pos)))) {
                pos++;
            }

            if (pos >= end) {
                return;
            }

            // Detect Special cookies
            if (buffer.get(pos) == '$') {
                isSpecial = true;
                pos++;
            }

            // Get the cookie name. This must be a token
            valueEnd = valueStart = nameStart = pos;
            pos = nameEnd = getTokenEndPosition(buffer, pos, end);

            // Skip whitespace
            while (pos < end && isWhiteSpace(buffer.get(pos))) {
                pos++;
            }

            // Check for an '=' -- This could also be a name-only
            // cookie at the end of the cookie header, so if we
            // are past the end of the header, but we have a name
            // skip to the name-only part.
            if (pos < end && buffer.get(pos) == '=') {

                // Skip whitespace
                do {
                    pos++;
                } while (pos < end && isWhiteSpace(buffer.get(pos)));

                if (pos >= end) {
                    return;
                }

                // Determine what type of value this is, quoted value,
                // token, name-only with an '=', or other (bad)
                switch (buffer.get(pos)) {
                    case '"':
                        ; // Quoted Value
                        isQuoted = true;
                        valueStart = pos + 1; // strip "
                        // getQuotedValue returns the position before
                        // at the last qoute. This must be dealt with
                        // when the bytes are copied into the cookie
                        valueEnd = getQuotedValueEndPosition(buffer,
                                valueStart, end);
                        // We need pos to advance
                        pos = valueEnd;
                        // Handles cases where the quoted value is
                        // unterminated and at the end of the header,
                        // e.g. [myname="value]
                        if (pos >= end) {
                            return;
                        }
                        break;
                    case ';':
                    case ',':
                        // Name-only cookie with an '=' after the name token
                        // This may not be RFC compliant
                        valueStart = valueEnd = -1;
                        // The position is OK (On a delimiter)
                        break;
                    default:
                        if (!isSeparator(buffer.get(pos), versionOneStrictCompliance)) {
                        // Token
                            // Token
                            valueStart = pos;
                            // getToken returns the position at the delimeter
                            // or other non-token character
                            valueEnd = getTokenEndPosition(buffer, valueStart, end,
                                versionOneStrictCompliance);
                            // We need pos to advance
                            pos = valueEnd;
                        } else {
                            // INVALID COOKIE, advance to next delimiter
                            // The starting character of the cookie value was
                            // not valid.
                            LOGGER.fine("Invalid cookie. Value not a token or quoted value");
                            while (pos < end && buffer.get(pos) != ';'
                                    && buffer.get(pos) != ',') {
                                pos++;
                            }

                            pos++;
                            // Make sure no special avpairs can be attributed to
                            // the previous cookie by setting the current cookie
                            // to null
                            cookie = null;
                            lazyCookie = null;
                            continue;
                        }
                }
            } else {
                // Name only cookie
                valueStart = valueEnd = -1;
                pos = nameEnd;

            }

            // We should have an avpair or name-only cookie at this
            // point. Perform some basic checks to make sure we are
            // in a good state.

            // Skip whitespace
            while (pos < end && isWhiteSpace(buffer.get(pos))) {
                pos++;
            }

            // Make sure that after the cookie we have a separator. This
            // is only important if this is not the last cookie pair
            while (pos < end && buffer.get(pos) != ';' && buffer.get(pos) != ',') {
                pos++;
            }

            pos++;

            // All checks passed. Add the cookie, start with the
            // special avpairs first
            if (isSpecial) {
                isSpecial = false;
                // $Version must be the first avpair in the cookie header
                // (sc must be null)
                if (equals("Version", buffer, nameStart, nameEnd)
                        && cookie == null) {
                    // Set version
                    if (buffer.get(valueStart) == '1'
                            && valueEnd == (valueStart + 1)) {
                        version = 1;
                    } else {
                        // unknown version (Versioning is not very strict)
                    }
                    continue;
                }

                // We need an active cookie for Path/Port/etc.
                if (cookie == null) {
                    continue;
                }

                // Domain is more common, so it goes first
                if (equals("Domain", buffer, nameStart, nameEnd)) {
                    lazyCookie.getDomain().setBuffer(buffer,
                            valueStart,
                            valueEnd - valueStart);
                    continue;
                }

                if (equals("Path", buffer, nameStart, nameEnd)) {
                    lazyCookie.getPath().setBuffer(buffer,
                            valueStart,
                            valueEnd - valueStart);
                    continue;
                }


                if (equals("Port", buffer, nameStart, nameEnd)) {
                    // sc.getPort is not currently implemented.
                    // sc.getPort().setBytes( bytes,
                    //                        valueStart,
                    //                        valueEnd-valueStart );
                    continue;
                }

                // Unknown cookie, complain
                LOGGER.fine("Unknown Special Cookie");

            } else { // Normal Cookie
                cookie = new LazyCookie();
                lazyCookie = cookie.lazy();

                lazyCookie.setVersion(version);
                lazyCookie.getName().setBuffer(buffer, nameStart,
                        nameEnd - nameStart);

                if (valueStart != -1) { // Normal AVPair
                    lazyCookie.getValue().setBuffer(buffer, valueStart,
                            valueEnd - valueStart);
                    if (isQuoted) {
                        // We know this is a byte value so this is safe
                        unescapeDoubleQuotes(lazyCookie.getValue());
                    }
                } else {
                    // Name Only
                    lazyCookie.getValue().setString("");
                }

                cookies.add(cookie);
                
                continue;
            }
        }
    }

    /**
     * Parses a cookie header after the initial "Cookie:"
     * [WS][$]token[WS]=[WS](token|QV)[;|,]
     * RFC 2965
     * JVK
     */
    public static void parseClientCookies(Collection<Cookie> cookies,
            String cookiesStr, boolean versionOneStrictCompliance) {
        
        if (cookiesStr == null) {
            throw new IllegalArgumentException();
        }
        
        int end = cookiesStr.length();
        int pos = 0;
        int nameStart = 0;
        int nameEnd = 0;
        int valueStart = 0;
        int valueEnd = 0;
        int version = 0;

        Cookie cookie = null;
        
        boolean isSpecial;
        boolean isQuoted;

        while (pos < end) {
            isSpecial = false;
            isQuoted = false;

            // Skip whitespace and non-token characters (separators)
            while (pos < end
                    && (isSeparator(cookiesStr.charAt(pos)) || isWhiteSpace(cookiesStr.charAt(pos)))) {
                pos++;
            }

            if (pos >= end) {
                return;
            }

            // Detect Special cookies
            if (cookiesStr.charAt(pos) == '$') {
                isSpecial = true;
                pos++;
            }

            // Get the cookie name. This must be a token
            valueEnd = valueStart = nameStart = pos;
            pos = nameEnd = getTokenEndPosition(cookiesStr, pos, end);

            // Skip whitespace
            while (pos < end && isWhiteSpace(cookiesStr.charAt(pos))) {
                pos++;
            }

            // Check for an '=' -- This could also be a name-only
            // cookie at the end of the cookie header, so if we
            // are past the end of the header, but we have a name
            // skip to the name-only part.
            if (pos < end && cookiesStr.charAt(pos) == '=') {

                // Skip whitespace
                do {
                    pos++;
                } while (pos < end && isWhiteSpace(cookiesStr.charAt(pos)));

                if (pos >= end) {
                    return;
                }

                // Determine what type of value this is, quoted value,
                // token, name-only with an '=', or other (bad)
                switch (cookiesStr.charAt(pos)) {
                    case '"':
                        ; // Quoted Value
                        isQuoted = true;
                        valueStart = pos + 1; // strip "
                        // getQuotedValue returns the position before
                        // at the last qoute. This must be dealt with
                        // when the bytes are copied into the cookie
                        valueEnd = getQuotedValueEndPosition(cookiesStr,
                                valueStart, end);
                        // We need pos to advance
                        pos = valueEnd;
                        // Handles cases where the quoted value is
                        // unterminated and at the end of the header,
                        // e.g. [myname="value]
                        if (pos >= end) {
                            return;
                        }
                        break;
                    case ';':
                    case ',':
                        // Name-only cookie with an '=' after the name token
                        // This may not be RFC compliant
                        valueStart = valueEnd = -1;
                        // The position is OK (On a delimiter)
                        break;
                    default:
                        if (!isSeparator(cookiesStr.charAt(pos), versionOneStrictCompliance)) {
                        // Token
                            // Token
                            valueStart = pos;
                            // getToken returns the position at the delimeter
                            // or other non-token character
                            valueEnd = getTokenEndPosition(cookiesStr, valueStart, end,
                                versionOneStrictCompliance);
                            // We need pos to advance
                            pos = valueEnd;
                        } else {
                            // INVALID COOKIE, advance to next delimiter
                            // The starting character of the cookie value was
                            // not valid.
                            LOGGER.fine("Invalid cookie. Value not a token or quoted value");
                            while (pos < end && cookiesStr.charAt(pos) != ';'
                                    && cookiesStr.charAt(pos) != ',') {
                                pos++;
                            }

                            pos++;
                            // Make sure no special avpairs can be attributed to
                            // the previous cookie by setting the current cookie
                            // to null
                            cookie = null;
                            continue;
                        }
                }
            } else {
                // Name only cookie
                valueStart = valueEnd = -1;
                pos = nameEnd;

            }

            // We should have an avpair or name-only cookie at this
            // point. Perform some basic checks to make sure we are
            // in a good state.

            // Skip whitespace
            while (pos < end && isWhiteSpace(cookiesStr.charAt(pos))) {
                pos++;
            }

            // Make sure that after the cookie we have a separator. This
            // is only important if this is not the last cookie pair
            while (pos < end && cookiesStr.charAt(pos) != ';' && cookiesStr.charAt(pos) != ',') {
                pos++;
            }

            pos++;

            // All checks passed. Add the cookie, start with the
            // special avpairs first
            if (isSpecial) {
                isSpecial = false;
                // $Version must be the first avpair in the cookie header
                // (sc must be null)
                if (equals("Version", cookiesStr, nameStart, nameEnd)
                        && cookie == null) {
                    // Set version
                    if (cookiesStr.charAt(valueStart) == '1'
                            && valueEnd == (valueStart + 1)) {
                        version = 1;
                    } else {
                        // unknown version (Versioning is not very strict)
                    }
                    continue;
                }

                // We need an active cookie for Path/Port/etc.
                if (cookie == null) {
                    continue;
                }

                // Domain is more common, so it goes first
                if (equals("Domain", cookiesStr, nameStart, nameEnd)) {
                    cookie.setDomain(cookiesStr.substring(valueStart, valueEnd));
                    continue;
                }

                if (equals("Path", cookiesStr, nameStart, nameEnd)) {
                    cookie.setPath(cookiesStr.substring(valueStart, valueEnd));
                    continue;
                }


                if (equals("Port", cookiesStr, nameStart, nameEnd)) {
                    // sc.getPort is not currently implemented.
                    // sc.getPort().setBytes( bytes,
                    //                        valueStart,
                    //                        valueEnd-valueStart );
                    continue;
                }

                // Unknown cookie, complain
                LOGGER.fine("Unknown Special Cookie");

            } else { // Normal Cookie

                String name = cookiesStr.substring(nameStart, nameEnd);
                String value;

                if (valueStart != -1) { // Normal AVPair
                    if (isQuoted) {
                        // We know this is a byte value so this is safe
                        value = unescapeDoubleQuotes(cookiesStr, valueStart, valueEnd);
                    } else {
                        value = cookiesStr.substring(valueStart, valueEnd);
                    }
                } else {
                    // Name Only
                    value = "";
                }

                cookie = new Cookie(name, value);
                cookie.setVersion(version);

                cookies.add(cookie);
                
                continue;
            }
        }
    }
    
    
    // TODO RFC2965 fields also need to be passed
    public static void serializeServerCookie(StringBuffer buf,
            Cookie cookie) {
        serializeServerCookie(buf, cookie,
                COOKIE_VERSION_ONE_STRICT_COMPLIANCE, ALWAYS_ADD_EXPIRES);
    }

    // TODO RFC2965 fields also need to be passed
    public static void serializeServerCookie(StringBuffer buf,
            Cookie cookie, boolean versionOneStrictCompliance,
            boolean alwaysAddExpires) {

        // Servlet implementation checks name
        buf.append(cookie.getName());
        buf.append("=");
        // Servlet implementation does not check anything else

        int version = maybeQuote2(cookie.getVersion(), buf, cookie.getValue(), true);

        // Add version 1 specific information
        if (version == 1) {
            // Version=1 ... required
            buf.append("; Version=1");

            // Comment=comment
            final String comment = cookie.getComment();
            if (comment != null) {
                buf.append("; Comment=");
                maybeQuote2(version, buf, comment, versionOneStrictCompliance);
            }
        }

        // Add domain information, if present
        final String domain = cookie.getDomain();
        if (domain != null) {
            buf.append("; Domain=");
            maybeQuote2(version, buf, domain, versionOneStrictCompliance);
        }

        // Max-Age=secs ... or use old "Expires" format
        // TODO RFC2965 Discard
        final int maxAge = cookie.getMaxAge();
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
                    OLD_COOKIE_FORMAT.get().format(
                            new Date(System.currentTimeMillis()
                            + maxAge * 1000L),
                            buf, new FieldPosition(0));
                }

            }
        }

        // Path=path
        String path = cookie.getPath();
        if (path != null) {
            buf.append("; Path=");

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
        if (cookie.isSecure()) {
            buf.append("; Secure");
        }

        // httpOnly
        if (cookie.isHttpOnly()) {
            buf.append("; HttpOnly");
        }
    }

    /**
     * Quotes values using rules that vary depending on Cookie version.
     * @param version
     * @param buf
     * @param value
     */
    public static int maybeQuote2(int version, StringBuffer buf, String value,
            boolean versionOneStrictCompliance) {
        return maybeQuote2(version, buf, value, false, versionOneStrictCompliance);
    }

    public static int maybeQuote2(int version, StringBuffer buf, String value,
            boolean allowVersionSwitch, boolean versionOneStrictCompliance) {
        return maybeQuote2(version, buf, value, null, allowVersionSwitch, versionOneStrictCompliance);
    }

    public static int maybeQuote2(int version, StringBuffer buf, String value,
            String literals, boolean allowVersionSwitch, boolean versionOneStrictCompliance) {
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
        } else {
            buf.append(value);
        }
        return version;
    }

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

    /**
     * Unescapes any double quotes in the given cookie value.
     *
     * @param bc The cookie value to modify
     */
    public static void unescapeDoubleQuotes(BufferChunk bc) {

        if (bc == null || bc.size() == 0) {
            return;
        }

        int src = bc.getStart();
        int end = bc.getEnd();
        int dest = src;
        Buffer buffer = bc.getBuffer();

        while (src < end) {
            if (buffer.get(src) == '\\' && src < end && buffer.get(src + 1) == '"') {
                src++;
            }
            buffer.put(dest, buffer.get(src));
            dest++;
            src++;
        }

        bc.setEnd(dest);
    }

    /**
     * Unescapes any double quotes in the given cookie value.
     *
     * @param bc The cookie value to modify
     * @return new length
     */
    public static int unescapeDoubleQuotes(Buffer buffer, int start, int length) {

        if (buffer == null || length <= 0) {
            return length;
        }

        int src = start;
        int end = src + length;
        int dest = src;

        while (src < end) {
            if (buffer.get(src) == '\\' && src < end && buffer.get(src + 1) == '"') {
                src++;
            }
            buffer.put(dest, buffer.get(src));
            dest++;
            src++;
        }

        return dest - start;
    }

    /**
     * Unescapes any double quotes in the given cookie value.
     *
     * @param s The cookie value to modify
     * @return new String
     */
    public static String unescapeDoubleQuotes(String s, int start, int length) {

        if (s == null || s.length() == 0) {
            return s;
        }

        final StringBuilder sb = new StringBuilder(s.length());
        
        int src = start;
        int end = src + length;

        while (src < end) {
            if (s.charAt(src) == '\\' && src < end && s.charAt(src + 1) == '"') {
                src++;
            }

            sb.append(s.charAt(src));
            src++;
        }

        return sb.toString();
    }
    
    public static boolean alreadyQuoted(String value) {
        if (value == null || value.length() == 0) {
            return false;
        }
        return (value.charAt(0) == '\"' && value.charAt(value.length() - 1) == '\"');
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
            if (c > 0 && c < 126) {
                return separators[c];
            } else {
                return false;
            }
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
