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

import java.text.ParseException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.Cookies;
import org.glassfish.grizzly.http.LazyCookieState;
import org.glassfish.grizzly.utils.Charsets;

import static org.glassfish.grizzly.http.util.CookieUtils.*;

/**
 * The set of Cookie utility methods for cookie parsing.
 *
 * There is duplication of logic within which we know to be frowned upon, however
 * it is done with performance in mind.
 *
 * @author Grizzly team
 */

public class CookieParserUtils {
    private static final Logger LOGGER = Grizzly.logger(CookieParserUtils.class);

    /**
     * Parses a cookie header after the initial "Cookie:"
     * [WS][$]token[WS]=[WS](token|QV)[;|,]
     * RFC 2965
     * JVK
     */
    public static void parseClientCookies(Cookies cookies,
            Buffer buffer, int off, int len) {
        parseClientCookies(cookies, buffer, off, len, COOKIE_VERSION_ONE_STRICT_COMPLIANCE, RFC_6265_SUPPORT_ENABLED);
    }

    /**
     * Parses a cookie header after the initial "Cookie:"
     * [WS][$]token[WS]=[WS](token|QV)[;|,]
     * RFC 2965
     * JVK
     */
    public static void parseClientCookies(Cookies cookies,
                                          Buffer buffer,
                                          int off,
                                          int len,
                                          boolean versionOneStrictCompliance,
                                          boolean rfc6265Enabled) {
        if (cookies == null) {
            throw new IllegalArgumentException("cookies cannot be null.");
        }
        if (buffer == null) {
            throw new IllegalArgumentException("buffer cannot be null.");
        }
        if (len <= 0) {
            return;
        }

        if (buffer.hasArray()) {
            parseClientCookies(cookies,
                               /*buffer,*/
                               buffer.array(),
                               off + buffer.arrayOffset(),
                               len,
                               versionOneStrictCompliance,
                               rfc6265Enabled);
            return;
        }

        int end = off + len;
        int pos = off;
        int nameStart;
        int nameEnd;
        int valueStart;
        int valueEnd;
        int version = 0;

        Cookie cookie = null;
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
            nameStart = pos;
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
                        // Quoted Value
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
                if (CookieUtils.equals("Version", buffer, nameStart, nameEnd)
                        && cookie == null) {
                    if (rfc6265Enabled) {
                        continue;
                    }
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
                if (CookieUtils.equals("Domain", buffer, nameStart, nameEnd)) {
                    lazyCookie.getDomain().setBuffer(buffer,
                            valueStart, valueEnd);
                    continue;
                }

                if (CookieUtils.equals("Path", buffer, nameStart, nameEnd)) {
                    lazyCookie.getPath().setBuffer(buffer,
                            valueStart, valueEnd);
                    continue;
                }


//                if (CookieUtils.equals("Port", buffer, nameStart, nameEnd)) {
//                    // sc.getPort is not currently implemented.
//                    // sc.getPort().setBytes( bytes,
//                    //                        valueStart,
//                    //                        valueEnd-valueStart );
//                    continue;
//                }

                // Unknown cookie, complain
                LOGGER.fine("Unknown Special Cookie");

            } else { // Normal Cookie
                cookie = cookies.getNextUnusedCookie();
                lazyCookie = cookie.getLazyCookieState();
                if (!rfc6265Enabled && !cookie.isVersionSet()) {
                    cookie.setVersion(version);
                }
                lazyCookie.getName().setBuffer(buffer, nameStart, nameEnd);

                if (valueStart != -1) { // Normal AVPair
                    lazyCookie.getValue().setBuffer(buffer, valueStart, valueEnd);
                    if (isQuoted) {
                        // We know this is a byte value so this is safe
                        unescapeDoubleQuotes(lazyCookie.getValue());
                    }
                } else {
                    // Name Only
                    lazyCookie.getValue().setString("");
                }
            }
        }
    }

    /**
     * Parses a cookie header after the initial "Cookie:"
     * [WS][$]token[WS]=[WS](token|QV)[;|,]
     * RFC 2965
     * JVK
     */
    public static void parseClientCookies(Cookies cookies,
            byte[] bytes, int off, int len) {
        parseClientCookies(cookies, bytes, off, len, COOKIE_VERSION_ONE_STRICT_COMPLIANCE, false);
    }

    /**
     * Parses a cookie header after the initial "Cookie:"
     * [WS][$]token[WS]=[WS](token|QV)[;|,]
     * RFC 2965
     * JVK
     */
    public static void parseClientCookies(Cookies cookies,
                                           byte[] bytes,
                                           int off,
                                           int len,
                                           boolean versionOneStrictCompliance,
                                           boolean rfc6265Enabled) {

        if (cookies == null) {
            throw new IllegalArgumentException("cookies cannot be null.");
        }
        if (bytes == null) {
            throw new IllegalArgumentException("bytes cannot be null.");
        }
        if (len <= 0) {
            return;
        }
        // keep note of the array offset - we need it for translation
        // into the byte[] but it's also needed when translating positions
        // *back* into the buffer.
//        int arrayOffset = buffer.arrayOffset();
        int end = off /*+ arrayOffset*/ + len;
        int pos = off /*+ arrayOffset*/;
        int nameStart;
        int nameEnd;
        int valueStart;
        int valueEnd;
        int version = 0;

        Cookie cookie = null;
        LazyCookieState lazyCookie = null;

        boolean isSpecial;
        boolean isQuoted;

        while (pos < end) {
            isSpecial = false;
            isQuoted = false;

            // Skip whitespace and non-token characters (separators)
            while (pos < end
                    && (isSeparator(bytes[pos]) || isWhiteSpace(bytes[pos]))) {
                pos++;
            }

            if (pos >= end) {
                return;
            }

            // Detect Special cookies
            if (bytes[pos] == '$') {
                isSpecial = true;
                pos++;
            }

            // Get the cookie name. This must be a token
            nameStart = pos;
            pos = nameEnd = getTokenEndPosition(bytes, pos, end);

            // Skip whitespace
            while (pos < end && isWhiteSpace(bytes[pos])) {
                pos++;
            }

            // Check for an '=' -- This could also be a name-only
            // cookie at the end of the cookie header, so if we
            // are past the end of the header, but we have a name
            // skip to the name-only part.
            if (pos < end && bytes[pos] == '=') {

                // Skip whitespace
                do {
                    pos++;
                } while (pos < end && isWhiteSpace(bytes[pos]));

                if (pos >= end) {
                    return;
                }

                // Determine what type of value this is, quoted value,
                // token, name-only with an '=', or other (bad)
                switch (bytes[pos]) {
                    case '"':
                        // Quoted Value
                        isQuoted = true;
                        valueStart = pos + 1; // strip "
                        // getQuotedValue returns the position before
                        // at the last qoute. This must be dealt with
                        // when the bytes are copied into the cookie
                        valueEnd = getQuotedValueEndPosition(bytes,
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
                        if (!isSeparator(bytes[pos], versionOneStrictCompliance)) {
                            // Token
                            // Token
                            valueStart = pos;
                            // getToken returns the position at the delimeter
                            // or other non-token character
                            valueEnd = getTokenEndPosition(bytes, valueStart, end,
                                    versionOneStrictCompliance);
                            // We need pos to advance
                            pos = valueEnd;
                        } else {
                            // INVALID COOKIE, advance to next delimiter
                            // The starting character of the cookie value was
                            // not valid.
                            LOGGER.fine("Invalid cookie. Value not a token or quoted value");
                            while (pos < end && bytes[pos] != ';'
                                    && bytes[pos] != ',') {
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
            while (pos < end && isWhiteSpace(bytes[pos])) {
                pos++;
            }

            // Make sure that after the cookie we have a separator. This
            // is only important if this is not the last cookie pair
            while (pos < end && bytes[pos] != ';' && bytes[pos] != ',') {
                pos++;
            }

            pos++;

            // All checks passed. Add the cookie, start with the
            // special avpairs first
            if (isSpecial) {
                isSpecial = false;
                // $Version must be the first avpair in the cookie header
                // (sc must be null)
                if (CookieUtils.equals("Version", bytes, nameStart, nameEnd)
                        && cookie == null) {
                    if (rfc6265Enabled) {
                        continue;
                    }
                    // Set version
                    if (bytes[valueStart] == '1'
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
                if (CookieUtils.equals("Domain", bytes, nameStart, nameEnd)) {
                    lazyCookie.getDomain().setBytes(bytes,
                            valueStart, valueEnd);
                    continue;
                }

                if (CookieUtils.equals("Path", bytes, nameStart, nameEnd)) {
                    lazyCookie.getPath().setBytes(bytes,
                            valueStart, valueEnd);
                    continue;
                }

                // Unknown cookie, complain
                LOGGER.fine("Unknown Special Cookie");

            } else { // Normal Cookie
                cookie = cookies.getNextUnusedCookie();
                lazyCookie = cookie.getLazyCookieState();
                if (!rfc6265Enabled && !cookie.isVersionSet()) {
                    cookie.setVersion(version);
                }
                lazyCookie.getName().setBytes(bytes, nameStart, nameEnd);

                if (valueStart != -1) { // Normal AVPair
                    lazyCookie.getValue().setBytes(bytes, valueStart, valueEnd);
                    if (isQuoted) {
                        // We know this is a byte value so this is safe
                        unescapeDoubleQuotes(lazyCookie.getValue());
                    }
                } else {
                    // Name Only
                    lazyCookie.getValue().setString("");
                }
            }
        }
    }

    /**
     * Parses a cookie header after the initial "Cookie:"
     * [WS][$]token[WS]=[WS](token|QV)[;|,]
     * RFC 2965
     * JVK
     */
    public static void parseClientCookies(Cookies cookies,
                                          String cookiesStr,
                                          boolean versionOneStrictCompliance,
                                          boolean rfc6265Enabled) {
        if (cookies == null) {
            throw new IllegalArgumentException("cookies cannot be null.");
        }
        if (cookiesStr == null) {
            throw new IllegalArgumentException("cookieStr cannot be null.");
        }
        if (cookiesStr.length() == 0) {
            return;
        }

        int end = cookiesStr.length();
        int pos = 0;
        int nameStart;
        int nameEnd;
        int valueStart;
        int valueEnd;
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
            nameStart = pos;
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
                        // Quoted Value
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
                if (CookieUtils.equals("Version", cookiesStr, nameStart, nameEnd)
                        && cookie == null) {
                    if (rfc6265Enabled) {
                        continue;
                    }
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
                if (CookieUtils.equals("Domain", cookiesStr, nameStart, nameEnd)) {
                    cookie.setDomain(cookiesStr.substring(valueStart, valueEnd));
                    continue;
                }

                if (CookieUtils.equals("Path", cookiesStr, nameStart, nameEnd)) {
                    cookie.setPath(cookiesStr.substring(valueStart, valueEnd));
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
                        value = unescapeDoubleQuotes(cookiesStr, valueStart, valueEnd - valueStart);
                    } else {
                        value = cookiesStr.substring(valueStart, valueEnd);
                    }
                } else {
                    // Name Only
                    value = "";
                }

                cookie = cookies.getNextUnusedCookie();
                cookie.setName(name);
                cookie.setValue(value);
                if (!rfc6265Enabled && !cookie.isVersionSet()) {
                    cookie.setVersion(version);
                }
            }
        }
    }

    public static void parseServerCookies(Cookies cookies,
                                           byte[] bytes,
                                           int off,
                                           int len,
                                           boolean versionOneStrictCompliance,
                                           boolean rfc6265Enabled) {

        if (cookies == null) {
            throw new IllegalArgumentException("cookies cannot be null.");
        }
        if (bytes == null) {
            throw new IllegalArgumentException("bytes cannot be null.");
        }
        if (len <= 0) {
            return;
        }

        // keep note of the array offset - we need it for translation
        // into the byte[] but it's also needed when translating positions
        // *back* into the buffer.
//        int arrayOffset = buffer.arrayOffset();
        int end = off + /*arrayOffset +*/ len;
        int pos = off /*+ arrayOffset*/;
        int nameStart;
        int nameEnd;
        int valueStart;
        int valueEnd;

        Cookie cookie = null;
        LazyCookieState lazyCookie = null;

        boolean isQuoted;

        while (pos < end) {
            isQuoted = false;

            // Skip whitespace and non-token characters (separators)
            while (pos < end
                    && (isSeparator(bytes[pos]) || isWhiteSpace(bytes[pos]))) {
                pos++;
            }

            if (pos >= end) {
                return;
            }

            // Get the cookie name. This must be a token
            nameStart = pos;
            pos = nameEnd = getTokenEndPosition(bytes, pos, end);

            // Skip whitespace
            while (pos < end && isWhiteSpace(bytes[pos])) {
                pos++;
            }

            // Check for an '=' -- This could also be a name-only
            // cookie at the end of the cookie header, so if we
            // are past the end of the header, but we have a name
            // skip to the name-only part.
            if (pos < end && bytes[pos] == '=') {

                // Skip whitespace
                do {
                    pos++;
                } while (pos < end && isWhiteSpace(bytes[pos]));

                if (pos >= end) {
                    return;
                }

                // Determine what type of value this is, quoted value,
                // token, name-only with an '=', or other (bad)
                switch (bytes[pos]) {
                    case '"':
                        // Quoted Value
                        isQuoted = true;
                        valueStart = pos + 1; // strip "
                        // getQuotedValue returns the position before
                        // at the last qoute. This must be dealt with
                        // when the bytes are copied into the cookie
                        valueEnd = getQuotedValueEndPosition(bytes,
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
                        if (!isSeparator(bytes[pos], versionOneStrictCompliance)) {
                            // Token
                            // Token
                            valueStart = pos;
                            // getToken returns the position at the delimeter
                            // or other non-token character
                            valueEnd = getTokenEndPosition(bytes, valueStart, end,
                                    versionOneStrictCompliance);
                            // We need pos to advance
                            pos = valueEnd;
                        } else {
                            // INVALID COOKIE, advance to next delimiter
                            // The starting character of the cookie value was
                            // not valid.
                            LOGGER.fine("Invalid cookie. Value not a token or quoted value");
                            while (pos < end && bytes[pos] != ';'
                                    && bytes[pos] != ',') {
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
            while (pos < end && isWhiteSpace(bytes[pos])) {
                pos++;
            }

            // Make sure that after the cookie we have a separator. This
            // is only important if this is not the last cookie pair
            while (pos < end && bytes[pos] != ';' && bytes[pos] != ',') {
                pos++;
            }

            pos++;

            // All checks passed. Add the cookie, start with the
            // special avpairs first
            if (cookie != null) {
                // Domain is more common, so it goes first
                if (lazyCookie.getDomain().isNull() &&
                        equalsIgnoreCase("Domain", bytes, nameStart, nameEnd)) {
                    lazyCookie.getDomain().setBytes(bytes,
                            valueStart, valueEnd);
                    continue;
                }

                // Path
                if (lazyCookie.getPath().isNull() &&
                        equalsIgnoreCase("Path", bytes, nameStart, nameEnd)) {
                    lazyCookie.getPath().setBytes(bytes,
                            valueStart, valueEnd);
                    continue;
                }

                // Version
                if (CookieUtils.equals("Version", bytes, nameStart, nameEnd)) {
                    if (rfc6265Enabled) {
                        continue;
                    }
                    // Set version
                    if (bytes[valueStart] == '1'
                            && valueEnd == (valueStart + 1)) {
                        cookie.setVersion(1);
                    } else {
                        // unknown version (Versioning is not very strict)
                    }
                    continue;
                }

                // Comment
                if (lazyCookie.getComment().isNull() &&
                        CookieUtils.equals("Comment", bytes, nameStart, nameEnd)) {
                    lazyCookie.getComment().setBytes(bytes,
                            valueStart, valueEnd);
                    continue;
                }

                // Max-Age
                if (cookie.getMaxAge() == -1 &&
                        CookieUtils.equals("Max-Age", bytes, nameStart, nameEnd)) {
                    cookie.setMaxAge(Ascii.parseInt(bytes,
                            valueStart,
                            valueEnd - valueStart));
                    continue;
                }

                // Expires
                if ((cookie.getVersion() == 0 || !cookie.isVersionSet()) && cookie.getMaxAge() == -1 &&
                        equalsIgnoreCase("Expires", bytes, nameStart, nameEnd)) {
                    try {
                        valueEnd = getTokenEndPosition(bytes, valueEnd + 1, end, false);
                        pos = valueEnd + 1;

                        final String expiresDate = new String(bytes,
                                valueStart, valueEnd - valueStart,
                                Charsets.ASCII_CHARSET);

                        final Date date = OLD_COOKIE_FORMAT.get().parse(expiresDate);
                        cookie.setMaxAge(getMaxAgeDelta(date.getTime(), System.currentTimeMillis()) / 1000);
                    } catch (ParseException ignore) {
                    }

                    continue;
                }

                // Secure
                if (!cookie.isSecure() &&
                        equalsIgnoreCase("Secure", bytes, nameStart, nameEnd)) {
                    lazyCookie.setSecure(true);
                    continue;
                }

                // HttpOnly
                if (!cookie.isHttpOnly() &&
                        CookieUtils.equals("HttpOnly", bytes, nameStart, nameEnd)) {
                    cookie.setHttpOnly(true);
                    continue;
                }

                if (CookieUtils.equals("Discard", bytes, nameStart, nameEnd)) {
                    continue;
                }
            }

            // Normal Cookie
            cookie = cookies.getNextUnusedCookie();
            if (!rfc6265Enabled && !cookie.isVersionSet()) {
                cookie.setVersion(0);
            }
            lazyCookie = cookie.getLazyCookieState();

            lazyCookie.getName().setBytes(bytes, nameStart, nameEnd);

            if (valueStart != -1) { // Normal AVPair
                lazyCookie.getValue().setBytes(bytes, valueStart, valueEnd);
                if (isQuoted) {
                    // We know this is a byte value so this is safe
                    unescapeDoubleQuotes(lazyCookie.getValue());
                }
            } else {
                // Name Only
                lazyCookie.getValue().setString("");
            }
        }
    }

    public static void parseServerCookies(Cookies cookies,
                                          Buffer buffer,
                                          int off,
                                          int len,
                                          boolean versionOneStrictCompliance,
                                          boolean rfc6265Enabled) {

        if (cookies == null) {
            throw new IllegalArgumentException("cookies cannot be null.");
        }
        if (buffer == null) {
            throw new IllegalArgumentException("buffer cannot be null.");
        }
        if (len <= 0) {
            return;
        }

        if (buffer.hasArray()) {
            parseServerCookies(cookies,
                               /*buffer,*/
                               buffer.array(),
                               off + buffer.arrayOffset(),
                               len,
                               versionOneStrictCompliance,
                               rfc6265Enabled);
            return;
        }

        int end = off + len;
        int pos = off;
        int nameStart;
        int nameEnd;
        int valueStart;
        int valueEnd;

        Cookie cookie = null;
        LazyCookieState lazyCookie = null;

        boolean isQuoted;

        while (pos < end) {
            isQuoted = false;

            // Skip whitespace and non-token characters (separators)
            while (pos < end
                    && (isSeparator(buffer.get(pos)) || isWhiteSpace(buffer.get(pos)))) {
                pos++;
            }

            if (pos >= end) {
                return;
            }

            // Get the cookie name. This must be a token
            nameStart = pos;
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
                        // Quoted Value
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
            if (cookie != null) {
                // Domain is more common, so it goes first
                if (lazyCookie.getDomain().isNull() &&
                        equalsIgnoreCase("Domain", buffer, nameStart, nameEnd)) {
                    lazyCookie.getDomain().setBuffer(buffer,
                            valueStart, valueEnd);
                    continue;
                }

                // Path
                if (lazyCookie.getPath().isNull() &&
                        equalsIgnoreCase("Path", buffer, nameStart, nameEnd)) {
                    lazyCookie.getPath().setBuffer(buffer,
                            valueStart, valueEnd);
                    continue;
                }

                // Version
                if (CookieUtils.equals("Version", buffer, nameStart, nameEnd)) {
                    if (rfc6265Enabled) {
                        continue;
                    }
                    // Set version
                    if (buffer.get(valueStart) == '1'
                            && valueEnd == (valueStart + 1)) {
                        cookie.setVersion(1);
                    } else {
                        // unknown version (Versioning is not very strict)
                    }
                    continue;
                }

                // Comment
                if (lazyCookie.getComment().isNull() &&
                        CookieUtils.equals("Comment", buffer, nameStart, nameEnd)) {
                    lazyCookie.getComment().setBuffer(buffer,
                            valueStart, valueEnd);
                    continue;
                }

                // Max-Age
                if (cookie.getMaxAge() == -1 &&
                        CookieUtils.equals("Max-Age", buffer, nameStart, nameEnd)) {
                    cookie.setMaxAge(Ascii.parseInt(buffer,
                            valueStart,
                            valueEnd - valueStart));
                    continue;
                }

                // Expires
                if ((cookie.getVersion() == 0 || !cookie.isVersionSet()) && cookie.getMaxAge() == -1 &&
                        equalsIgnoreCase("Expires", buffer, nameStart, nameEnd)) {
                    try {
                        valueEnd = getTokenEndPosition(buffer, valueEnd + 1, end, false);
                        pos = valueEnd + 1;

                        final String expiresDate =
                                buffer.toStringContent(Charsets.ASCII_CHARSET,
                                                       valueStart,
                                                       valueEnd);
                        final Date date = OLD_COOKIE_FORMAT.get().parse(expiresDate);
                        cookie.setMaxAge(getMaxAgeDelta(date.getTime(), System.currentTimeMillis()) / 1000);
                    } catch (ParseException ignore) {
                    }

                    continue;
                }

                // Secure
                if (!cookie.isSecure() &&
                        equalsIgnoreCase("Secure", buffer, nameStart, nameEnd)) {
                    lazyCookie.setSecure(true);
                    continue;
                }

                // HttpOnly
                if (!cookie.isHttpOnly() &&
                        CookieUtils.equals("HttpOnly", buffer, nameStart, nameEnd)) {
                    cookie.setHttpOnly(true);
                    continue;
                }

                if (CookieUtils.equals("Discard", buffer, nameStart, nameEnd)) {
                    continue;
                }
            }

            // Normal Cookie
            cookie = cookies.getNextUnusedCookie();
            if (!rfc6265Enabled && !cookie.isVersionSet()) {
                cookie.setVersion(0);
            }
            lazyCookie = cookie.getLazyCookieState();

            lazyCookie.getName().setBuffer(buffer, nameStart, nameEnd);

            if (valueStart != -1) { // Normal AVPair
                lazyCookie.getValue().setBuffer(buffer, valueStart, valueEnd);
                if (isQuoted) {
                    // We know this is a byte value so this is safe
                    unescapeDoubleQuotes(lazyCookie.getValue());
                }
            } else {
                // Name Only
                lazyCookie.getValue().setString("");
            }
        }
    }

    public static void parseServerCookies(Cookies cookies,
                                          String cookiesStr,
                                          boolean versionOneStrictCompliance,
                                          boolean rfc6265Enabled) {

        if (cookies == null) {
            throw new IllegalArgumentException("cookies cannot be null.");
        }

        if (cookiesStr == null) {
            throw new IllegalArgumentException();
        }
        if (cookiesStr.length() == 0) {
            return;
        }

        int end = cookiesStr.length();
        int pos = 0;
        int nameStart;
        int nameEnd;
        int valueStart;
        int valueEnd;

        Cookie cookie = null;

        boolean isQuoted;

        while (pos < end) {
            isQuoted = false;

            // Skip whitespace and non-token characters (separators)
            while (pos < end
                    && (isSeparator(cookiesStr.charAt(pos)) || isWhiteSpace(cookiesStr.charAt(pos)))) {
                pos++;
            }

            if (pos >= end) {
                return;
            }

            // Get the cookie name. This must be a token
            nameStart = pos;
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
                        // Quoted Value
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
            if (cookie != null) {
                // Domain is more common, so it goes first
                if (cookie.getDomain() == null &&
                        equalsIgnoreCase("Domain", cookiesStr, nameStart, nameEnd)) {
                    cookie.setDomain(cookiesStr.substring(valueStart, valueEnd));
                    continue;
                }

                // Path
                if (cookie.getPath() == null &&
                        equalsIgnoreCase("Path", cookiesStr, nameStart, nameEnd)) {
                    cookie.setPath(cookiesStr.substring(valueStart, valueEnd));
                    continue;
                }

                // Version
                if (CookieUtils.equals("Version", cookiesStr, nameStart, nameEnd)) {
                    if (rfc6265Enabled) {
                        continue;
                    }
                    // Set version
                    if (cookiesStr.charAt(valueStart) == '1'
                            && valueEnd == (valueStart + 1)) {
                        cookie.setVersion(1);
                    } else {
                        if (!rfc6265Enabled) {
                            cookie.setVersion(0);
                        }
                    }
                    continue;
                }

                // Comment
                if (cookie.getComment() == null &&
                        CookieUtils.equals("Comment", cookiesStr, nameStart, nameEnd)) {
                    cookie.setComment(cookiesStr.substring(valueStart, valueEnd));
                    continue;
                }

                // Max-Age
                if (cookie.getMaxAge() == -1 &&
                        CookieUtils.equals("Max-Age", cookiesStr, nameStart, nameEnd)) {
                    cookie.setMaxAge(Integer.parseInt(
                            cookiesStr.substring(valueStart, valueEnd)));
                    continue;
                }

                // Expires
                if ((cookie.getVersion() == 0 || cookie.isVersionSet()) && cookie.getMaxAge() == -1 &&
                        equalsIgnoreCase("Expires", cookiesStr, nameStart, nameEnd)) {
                    try {
                        valueEnd = getTokenEndPosition(cookiesStr, valueEnd + 1, end, false);
                        pos = valueEnd + 1;
                        final String expiresDate =
                                cookiesStr.substring(valueStart, valueEnd);
                        final Date date = OLD_COOKIE_FORMAT.get().parse(expiresDate);
                        cookie.setMaxAge(getMaxAgeDelta(date.getTime(), System.currentTimeMillis()) / 1000);
                    } catch (ParseException ignore) {
                    }

                    continue;
                }

                // Secure
                if (!cookie.isSecure() &&
                        equalsIgnoreCase("Secure", cookiesStr, nameStart, nameEnd)) {
                    cookie.setSecure(true);
                    continue;
                }

                // HttpOnly
                if (!cookie.isHttpOnly() &&
                        CookieUtils.equals("HttpOnly", cookiesStr, nameStart, nameEnd)) {
                    cookie.setHttpOnly(true);
                    continue;
                }

                if (CookieUtils.equals("Discard", cookiesStr,  nameStart,  nameEnd)) {
                    continue;
                }
            }

            // Normal Cookie
            String name = cookiesStr.substring(nameStart, nameEnd);
            String value;

            if (valueStart != -1) { // Normal AVPair
                if (isQuoted) {
                    // We know this is a byte value so this is safe
                    value = unescapeDoubleQuotes(cookiesStr, valueStart,
                            valueEnd - valueStart);
                } else {
                    value = cookiesStr.substring(valueStart, valueEnd);
                }
            } else {
                // Name Only
                value = "";
            }

            cookie = cookies.getNextUnusedCookie();
            if (!rfc6265Enabled && !cookie.isVersionSet()) {
                cookie.setVersion(0);
            }
            cookie.setName(name);
            cookie.setValue(value);
        }
    }

    /**
     * Unescapes any double quotes in the given cookie value.
     *
     * @param dc The cookie value to modify
     */
    public static void unescapeDoubleQuotes(DataChunk dc) {
        switch (dc.getType()) {
            case Bytes:
                unescapeDoubleQuotes(dc.getByteChunk());
                return;
            case Buffer:
                unescapeDoubleQuotes(dc.getBufferChunk());
                return;
            case String:
                final String s = dc.toString();
                dc.setString(unescapeDoubleQuotes(s, 0, s.length()));
                return;
            case Chars:

            default: throw new NullPointerException();

        }
    }

    /**
     * Unescapes any double quotes in the given cookie value.
     *
     * @param bc The cookie value to modify
     */
    public static void unescapeDoubleQuotes(final ByteChunk bc) {

        if (bc == null || bc.getLength() == 0) {
            return;
        }

        int src = bc.getStart();
        int end = bc.getEnd();
        int dest = src;
        final byte[] buffer = bc.getBuffer();

        while (src < end) {
            if (buffer[src] == '\\' && src < end && buffer[src + 1] == '"') {
                src++;
            }
            buffer[dest] = buffer[src];
            dest++;
            src++;
        }

        bc.setEnd(dest);
    }

    /**
     * Unescapes any double quotes in the given cookie value.
     *
     * @param bc The cookie value to modify
     */
    public static void unescapeDoubleQuotes(BufferChunk bc) {

        if (bc == null || bc.getLength() == 0) {
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
     * @param cc The cookie value to modify
     */
    @SuppressWarnings("UnusedDeclaration")
    public static void unescapeDoubleQuotes(CharChunk cc) {

        if (cc == null || cc.getLength() == 0) {
            return;
        }

        int src = cc.getStart();
        int end = cc.getLimit();
        int dest = src;
        char[] buffer = cc.getBuffer();

        while (src < end) {
            if (buffer[src] == '\\' && src < end && buffer[src + 1] == '"') {
                src++;
            }
            buffer[dest] = buffer[src];
            dest++;
            src++;
        }

        cc.setLimit(dest);
    }

    /**
     * Un-escapes any double quotes in the given cookie value.
     *
     * @param buffer the cookie buffer.
     * @param start start position.
     * @param length number of bytes to un-escape.
     * @return new length
     */
    @SuppressWarnings("UnusedDeclaration")
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


    private static int getMaxAgeDelta(long date1, long date2) {
        long result = date1 - date2;
        if (result > Integer.MAX_VALUE) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Integer overflow when calculating max age delta.  Date: " + date1 + ", current date: " + date2 + ".  Using Integer.MAX_VALUE for further calculation.");
            }
            return Integer.MAX_VALUE;
        } else {
            return (int) result;
        }
    }

}
