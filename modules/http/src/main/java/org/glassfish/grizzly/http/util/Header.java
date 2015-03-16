/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.util;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.glassfish.grizzly.utils.Charsets;

/**
 * Enumeration of all headers as defined in <code>RFC 2616</code>.
 *
 * @since 2.1.2
 */
public enum Header {

    Accept("Accept"),
    AcceptCharset("Accept-Charset"),
    AcceptEncoding("Accept-Encoding"),
    AcceptRanges("Accept-Ranges"),
    Age("Age"),
    Allow("Allow"),
    Authorization("Authorization"),
    CacheControl("Cache-Control"),
    Cookie("Cookie"),
    Connection("Connection"),
    ContentDisposition("Content-Disposition"),
    ContentEncoding("Content-Encoding"),
    ContentLanguage("Content-Language"),
    ContentLength("Content-Length"),
    ContentLocation("Content-Location"),
    ContentMD5("Content-MD5"),
    ContentRange("Content-Range"),
    ContentType("Content-Type"),
    Date("Date"),
    ETag("ETag"),
    Expect("Expect"),
    Expires("Expires"),
    From("From"),
    Host("Host"),
    IfMatch("If-Match"),
    IfModifiedSince("If-Modified-Since"),
    IfNoneMatch("If-None-Match"),
    IfRange("If-Range"),
    IfUnmodifiedSince("If-Unmodified-Since"),
    KeepAlive("Keep-Alive"),
    LastModified("Last-Modified"),
    Location("Location"),
    MaxForwards("Max-Forwards"),
    Pragma("Pragma"),
    ProxyAuthenticate("Proxy-Authenticate"),
    ProxyAuthorization("Proxy-Authorization"),
    ProxyConnection("Proxy-Connection"),
    Range("Range"),
    Referer("Referer"),
    RetryAfter("Retry-After"),
    Server("Server"),
    SetCookie("Set-Cookie"),
    TE("TE"),
    Trailer("Trailer"),
    TransferEncoding("Transfer-Encoding"),
    Upgrade("Upgrade"),
    UserAgent("User-Agent"),
    Vary("Vary"),
    Via("Via"),
    Warnings("Warning"),
    WWWAuthenticate("WWW-Authenticate"),
    XPoweredBy("X-Powered-By"),
    HTTP2Settings("HTTP2-Settings");


    // ----------------------------------------------------------------- Statics

    private static final Map<String,Header> VALUES = new TreeMap<String,Header>(String.CASE_INSENSITIVE_ORDER);
    static {
        for (final Header h : Header.values()) {
            VALUES.put(h.toString(), h);
        }
    }

    // --------------------------------------------------------- Per Enum Fields


    private final byte[] headerNameBytes;
    private final byte[] headerNameLowerCaseBytes;
    private final String headerName;
    private final String headerNameLowerCase;
    private final int length;

    // ------------------------------------------------------------ Constructors


    private Header(final String headerName) {
        this.headerName = headerName;
        headerNameBytes = headerName.getBytes(Charsets.ASCII_CHARSET);
        
        this.headerNameLowerCase = headerName.toLowerCase(Locale.ENGLISH);
        headerNameLowerCaseBytes = headerNameLowerCase.getBytes(Charsets.ASCII_CHARSET);
        
        length = headerNameBytes.length;
    }


    // ---------------------------------------------------------- Public Methods


    /**
     * <p>
     * Returns the byte representation of this header encoded using
     * <code>ISO-8859-1</code>.
     * </p>
     *
     * @return the byte representation of this header encoded using
     *  <code>ISO-8859-1</code>.
     */
    public final byte[] getBytes() {
        return headerNameBytes;
    }

    /**
     * <p>
     * Returns the lower-case {@link String} representation of this header.
     * </p>
     *
     * @return the lower-case {@link String} representation of this header
     */
    public final String getLowerCase() {
        return headerNameLowerCase;
    }
    
    /**
     * <p>
     * Returns the lower-case byte representation of this header encoded using
     * <code>ISO-8859-1</code>.
     * </p>
     *
     * @return the lower-case byte representation of this header encoded using
     *  <code>ISO-8859-1</code>.
     */
    public final byte[] getLowerCaseBytes() {
        return headerNameLowerCaseBytes;
    }

    /**
     * <p>
     * Returns the length this header encoded using <code>ISO-8859-1</code>.
     * </p>
     *
     * @return the length this header encoded using <code>ISO-8859-1</code>.
     */
    public final int getLength() {
        return length;
    }
    
    /**
     * <p>
     * Returns the name of the header properly hyphenated if necessary.
     * </p>
     *
     * @return Returns the name of the header properly hyphenated if necessary.
     */
    @Override
    public final String toString() {
        return headerName;
    }

    /**
     * <p>
     * Returns the US-ASCII encoded byte representation of this <code>Header</code>.
     * </p>
     * @return the US-ASCII encoded byte representation of this <code>Header</code>.
     */
    public final byte[] toByteArray() {
        return headerNameBytes;
    }

    /**
     * <p>
     * Attempts to find a HTTP header by it's standard textual definition which
     * may differ from value value returned by {@link #name}.  Note that this
     * search is case insensitive.
     * </p>
     *
     * @param name the name of the <code>Header</code> to attempt to find.
     *
     * @return the <code>Header</code> for the specified text representation.
     *  If no <code>Header</code> matches or if the specified argument is
     *  <code>null/zero-length</code>, this method returns </code>null</code>.
     */
    public static Header find(final String name) {

        if (name == null || name.isEmpty()) {
            return null;
        }
        return VALUES.get(name);

    }


    // --------------------------------------------------------- Private Methods


    private boolean equalsIgnoreCase(final byte[] b) {

        final int len = headerNameBytes.length;
        if (b == null ||  len != b.length) {
            return false;
        }

        for (int i = 0; i < len; i++) {
            if (headerNameLowerCaseBytes[i] != Ascii.toLower(b[i])) {
                return false;
            }
        }
        return true;

    }


    // ---------------------------------------------------------- Nested Classes


//    private static final class RecyclableHeapBuffer extends HeapBuffer implements Cacheable {
//
//        private static final ThreadCache.CachedTypeIndex<RecyclableHeapBuffer> CACHE_IDX =
//            ThreadCache.obtainIndex(RecyclableHeapBuffer.class, 64);
//
//        // -------------------------------------------------------- Constructors
//
//
//        RecyclableHeapBuffer(byte[] heap, int offset, int cap) {
//            super(heap, offset, cap);
//            allowBufferDispose(true);
//        }
//
//
//        // --------------------------------------------- Methods from HeapBuffer
//
//        @Override
//        public boolean isReadOnly() {
//            return true;
//        }
//
//        @Override
//        public void dispose() {
//            recycle();
//        }
//
//        // ---------------------------------------------- Methods from Cacheable
//
//
//        @Override
//        public void recycle() {
//            this.heap = null;
//            this.byteBuffer = null;
//            this.offset = 0;
//            this.cap = 0;
//            this.lim = 0;
//            ThreadCache.putToCache(CACHE_IDX, this);
//        }
//
//
//        // ----------------------------------------------------- Private Methods
//
//
//        private static RecyclableHeapBuffer create(final byte[] heap) {
//
//            RecyclableHeapBuffer b = ThreadCache.takeFromCache(CACHE_IDX);
//            if (b == null) {
//                b = new RecyclableHeapBuffer(heap, 0, heap.length);
//            } else {
//                b.init(heap);
//            }
//            return b;
//
//        }
//
//        private void init(byte[] heap) {
//
//            this.heap = heap;
//            this.offset = 0;
//            this.cap = heap.length;
//            this.lim = cap;
//
//        }
//
//    }

}
