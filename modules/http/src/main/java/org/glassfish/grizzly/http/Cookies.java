/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.CookieParserUtils;
import org.glassfish.grizzly.http.util.MimeHeaders;
import java.util.ArrayList;
import java.util.Collection;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.http.util.BufferChunk;

/**
 * A collection of cookies - reusable and tuned for server side performance.
 * Based on RFC2965 ( and 2109 )
 *
 * This class is not synchronized.
 *
 * @author Costin Manolache
 * @author kevin seguin
 */
public final class Cookies {

    private static final Logger logger = Grizzly.logger(Cookies.class);
    // expected average number of cookies per request
    private static final int INITIAL_SIZE = 4;

    private final Collection<Cookie> cookies =
            new ArrayList<Cookie>(INITIAL_SIZE);
    
    private boolean isProcessed;
    private MimeHeaders headers;

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


    public boolean initialized() {
        return headers != null;
    }

    public Collection<Cookie> get() {
        if (!isProcessed) {
            isProcessed = true;
            processCookies();
        }

        return cookies;
    }

    public void setHeaders(MimeHeaders headers) {
        this.headers = headers;
    }
    
    /**
     * Recycle.
     */
    public void recycle() {
        cookies.clear();
        headers = null;
        isProcessed = false;
    }

    // code from CookieTools 
    /** Add all Cookie found in the headers of a request.
     */
    private void processCookies() {
        if (headers == null) {
            return;// nothing to process
        }        // process each "cookie" header
        int pos = 0;
        while (pos >= 0) {
            // Cookie2: version ? not needed
            pos = headers.indexOf("Cookie", pos);
            // no more cookie headers headers
            if (pos < 0) {
                break;
            }

            DataChunk cookieValue = headers.getValue(pos);
            if (cookieValue == null || cookieValue.isNull()) {
                pos++;
                continue;
            }

            // Uncomment to test the new parsing code
            if (cookieValue.getType() == DataChunk.Type.Buffer) {
                if (logger.isLoggable(Level.FINE)) {
                    log("Parsing b[]: " + cookieValue.toString());
                }

                final BufferChunk bufferChunk = cookieValue.getBufferChunk();
                CookieParserUtils.parseClientCookies(cookies, bufferChunk.getBuffer(),
                        bufferChunk.getStart(),
                        bufferChunk.getLength());
            } else {
                throw new IllegalStateException("Not implemented");
            }

            pos++;// search from the next position
        }
    }

    /**
     * EXPENSIVE!!!  only for debugging.
     */
    @Override
    public String toString() {
        return cookies.toString();
    }

    private static void log(String s) {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Cookies: {0}", s);
        }
    }

    public Cookie findByName(String cookieName) {
        final Collection<Cookie> parsedCookies = get();
        for (Cookie cookie : parsedCookies) {
            if (cookie.lazyNameEquals(cookieName)) {
                return cookie;
            }
        }

        return null;
    }
}
