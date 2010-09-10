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

package com.sun.grizzly.http.util;

import com.sun.grizzly.Buffer;
import java.io.CharConversionException;

/**
 *
 * @author Alexey Stashok
 */
public class URLDecoder {
    public static void decode(final BufferChunk bufferChunk)
            throws CharConversionException {
        decode(bufferChunk, true);
    }
    
    /**
     * URLDecode the {@link BufferChunk}
     */
    public static void decode(final BufferChunk bufferChunk,
            final boolean allowEncodedSlash) throws CharConversionException {

        final Buffer buffer = bufferChunk.getBuffer();
        int start = bufferChunk.getStart();
        int end = bufferChunk.getEnd();

        int idx = start;
        for (int j = start; j < end; j++, idx++) {
            final byte b = buffer.get(j);

            if (b == '+') {
                buffer.put(idx , (byte) ' ');
            } else if (b != '%') {
                buffer.put(idx, b);
            } else {
                // read next 2 digits
                if (j + 2 >= end) {
                    throw new IllegalStateException("Unexpected termination");
                }
                byte b1 = buffer.get(j + 1);
                byte b2 = buffer.get(j + 2);
                
                if (!HexUtils.isHexDigit(b1) || !HexUtils.isHexDigit(b2)) {
                    throw new IllegalStateException("isHexDigit");
                }

                j += 2;
                int res = x2c(b1, b2);
                if (!allowEncodedSlash && (res == '/')) {
                    throw new CharConversionException("Encoded slashes are not allowed");
                }
                buffer.put(idx, (byte) res);
            }
        }

        bufferChunk.setEnd(idx);
    }

    // XXX Old code, needs to be replaced !!!!
    // 
    public static String decode(final String str) throws CharConversionException {
        return decode(str, true);
    }

    public static String decode(final String str, final boolean allowEncodedSlash)
            throws CharConversionException {
        
        if (str == null) {
            return null;
        }

        int strPos;

        if ((strPos = str.indexOf('%')) < 0) {
            return str;
        }

        int strLen = str.length();
        StringBuilder dec = new StringBuilder(strLen);    // decoded string output

        while (strPos < strLen) {
            int laPos;        // lookahead position

            // look ahead to next URLencoded metacharacter, if any
            for (laPos = strPos; laPos < strLen; laPos++) {
                char laChar = str.charAt(laPos);
                if (laChar == '%') {
                    break;
                }
            }

            // if there were non-metacharacters, copy them all as a block
            if (laPos > strPos) {
                dec.append(str.substring(strPos, laPos));
                strPos = laPos;
            }

            // shortcut out of here if we're at the end of the string
            if (strPos >= strLen) {
                break;
            }

            // process next metacharacter
            char metaChar = str.charAt(strPos);
            if (metaChar == '+') {
                dec.append(' ');
                strPos++;
            } else if (metaChar == '%') {
                // We throw the original exception - the super will deal with
                // it
                //                try {
                final char res = (char) Integer.parseInt(str.substring(strPos + 1, strPos + 3), 16);
                if (!allowEncodedSlash && (res == '/')) {
                    throw new CharConversionException("Encoded slashes are not allowed");
                }
                dec.append(res);
                strPos += 3;
            }
        }

        return dec.toString();
    }

    private static int x2c(byte b1, byte b2) {
        return (HexUtils.hexDigit2Dec(b1) << 4) + HexUtils.hexDigit2Dec(b2);
    }
}
