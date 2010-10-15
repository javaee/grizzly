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
 */

package org.glassfish.grizzly.http.util;

import org.glassfish.grizzly.Buffer;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class.
 * 
 * @author Alexey Stashok
 */
public class Utils {

    private static final ConcurrentHashMap<String, Charset> charsetAliasMap =
            new ConcurrentHashMap<String, Charset>();

    private static final HashMap<Integer,DataChunk> statusMessages =
            new HashMap<Integer,DataChunk>(HttpStatus.values().length);
    private static final HashMap<Integer,DataChunk> statusCodes =
            new HashMap<Integer,DataChunk>(HttpStatus.values().length);
    static {
        for (final HttpStatus status : HttpStatus.values()) {
            statusMessages.put(status.getStatusCode(), status.getReasonPhraseDC());
            statusCodes.put(status.getStatusCode(), status.getStatusDC());
        }
    }


    // ---------------------------------------------------------- Public Methods


    /**
     * Lookup a {@link Charset} by name.
     * Fixes Charset concurrency issue (http://paul.vox.com/library/post/the-mysteries-of-java-character-set-performance.html)
     *
     * @param charsetName
     * @return {@link Charset}
     */
    public static Charset lookupCharset(String charsetName) {
        Charset charset = charsetAliasMap.get(charsetName);
        if (charset == null) {
            charset = Charset.forName(charsetName);
            charsetAliasMap.putIfAbsent(charsetName, charset);
        }

        return charset;
    }


    /**
     * @param httpStatus HTTP status code
     * @return the standard HTTP message associated with the specified
     *  status code.  If there is no message for the specified <code>httpStatus</code>,
     *  <code>null</code> shall be returned.
     */
    public static DataChunk getHttpStatusMessage(final int httpStatus) {

        return statusMessages.get(httpStatus);
        
    }


    /**
     * @param httpStatus HTTP status code
     * @return {@link DataChunk} representation of the status.
     */
    public static DataChunk getHttpStatus(final int httpStatus) {

        return statusCodes.get(httpStatus);

    }

    /**
     * Converts the specified long as a string representation to the provided
     * buffer.
     *
     * This code is based off {@link Long#toString()}
     *
     * @param value the long to convert.
     * @param buffer the buffer to write the conversion result to.
     */
    public static void longToBuffer(long value, final Buffer buffer) {
        if (value == 0) {
            buffer.put(0, (byte) '0');
            buffer.limit(1);
            return;
        }

        final int radix = 10;
        final boolean negative;
        if (value < 0) {
            negative = true;
            value = -value;
        } else {
            negative = false;
        }

        int position = buffer.limit();

        do {
            final int ch = '0' + (int) (value % radix);
            buffer.put(--position, (byte) ch);
        } while ((value /= radix) != 0);

        if (negative) {
            buffer.put(--position, (byte) '-');
        }
        buffer.position(position);

    }

}
