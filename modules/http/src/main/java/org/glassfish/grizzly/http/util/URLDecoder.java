/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

import java.io.CharConversionException;
import org.glassfish.grizzly.Buffer;

/**
 *
 * @author Alexey Stashok
 */
public class URLDecoder {
    public static void decode(final DataChunk dataChunk)
            throws CharConversionException {
        decode(dataChunk, true);
    }

   /**
     * URLDecode the {@link DataChunk}
     */
    public static void decode(final DataChunk dataChunk,
            final boolean allowEncodedSlash) throws CharConversionException {
        decode(dataChunk, dataChunk, allowEncodedSlash);
    }
    
   /**
     * URLDecode the {@link DataChunk}
     */
    public static void decode(final DataChunk srcDataChunk,
            final DataChunk dstDataChunk, final boolean allowEncodedSlash)
            throws CharConversionException {
        switch (srcDataChunk.getType()) {
            case Bytes:
            {
                final ByteChunk srcByteChunk = srcDataChunk.getByteChunk();
                
                final ByteChunk dstByteChunk = dstDataChunk.getByteChunk();

                // If it's the same buffer - we don't want to call allocate,
                // because it resets the ByteChunk length
                if (dstByteChunk != srcByteChunk) {
                    dstByteChunk.allocate(srcByteChunk.getLength(), -1);
                }

                decode(srcByteChunk, dstByteChunk, allowEncodedSlash);
                return;
            }
            case Buffer:
            {
                final BufferChunk srcBufferChunk = srcDataChunk.getBufferChunk();

                // If it's the same buffer - we don't want to call allocate,
                // because it resets the ByteChunk length
                if (dstDataChunk != srcDataChunk) {
                    final ByteChunk dstByteChunk = dstDataChunk.getByteChunk();
                    dstByteChunk.allocate(srcBufferChunk.getLength(), -1);
                    decode(srcBufferChunk, dstByteChunk, allowEncodedSlash);
                } else {
                    decode(srcBufferChunk, srcBufferChunk, allowEncodedSlash);
                }
                return;
            }
            case String:
            {
                dstDataChunk.setString(
                        decode(srcDataChunk.toString(), allowEncodedSlash));
                return;
            }
            case Chars:
            {
                final CharChunk srcCharChunk = srcDataChunk.getCharChunk();
                final CharChunk dstCharChunk = dstDataChunk.getCharChunk();
                dstCharChunk.ensureCapacity(srcCharChunk.getLength());
                decode(srcCharChunk, dstCharChunk, allowEncodedSlash);
                dstDataChunk.setChars(dstCharChunk.getChars(),
                        dstCharChunk.getStart(), dstCharChunk.getEnd());
                return;
            }
            default: throw new NullPointerException();
        }
    }

    /**
     * URLDecode the {@link ByteChunk}
     */
    public static void decode(final ByteChunk byteChunk,
            final boolean allowEncodedSlash) throws CharConversionException {
        decode(byteChunk, byteChunk, allowEncodedSlash);
    }

    /**
     * URLDecode the {@link ByteChunk}
     */
    public static void decode(final ByteChunk srcByteChunk,
            final ByteChunk dstByteChunk,
            final boolean allowEncodedSlash) throws CharConversionException {

        final byte[] srcBuffer = srcByteChunk.getBuffer();
        final int srcStart = srcByteChunk.getStart();
        final int srcEnd = srcByteChunk.getEnd();

        final byte[] dstBuffer = dstByteChunk.getBuffer();
        int idx = dstByteChunk.getStart();
        
        for (int j = srcStart; j < srcEnd; j++, idx++) {
            final byte b = srcBuffer[j];

            if (b == '+') {
                dstBuffer[idx] = (byte) ' ';
            } else if (b != '%') {
                dstBuffer[idx] = b;
            } else {
                // read next 2 digits
                if (j + 2 >= srcEnd) {
                    throw new IllegalStateException("Unexpected termination");
                }
                byte b1 = srcBuffer[j + 1];
                byte b2 = srcBuffer[j + 2];
                
                if (!HexUtils.isHexDigit(b1) || !HexUtils.isHexDigit(b2)) {
                    throw new IllegalStateException("isHexDigit");
                }

                j += 2;
                final int res = x2c(b1, b2);
                if (!allowEncodedSlash && (res == '/')) {
                    throw new CharConversionException("Encoded slashes are not allowed");
                }
                dstBuffer[idx] = (byte) res;
            }
        }

        dstByteChunk.setEnd(idx);
    }
    
    /**
     * URLDecode the {@link BufferChunk}
     */
    public static void decode(final BufferChunk srcBufferChunk,
            final ByteChunk dstByteChunk,
            final boolean allowEncodedSlash) throws CharConversionException {

        final Buffer srcBuffer = srcBufferChunk.getBuffer();
        final int srcStart = srcBufferChunk.getStart();
        final int srcEnd = srcBufferChunk.getEnd();

        final byte[] dstBuffer = dstByteChunk.getBuffer();
        int idx = dstByteChunk.getStart();
        
        for (int j = srcStart; j < srcEnd; j++, idx++) {
            final byte b = srcBuffer.get(j);

            if (b == '+') {
                dstBuffer[idx] = (byte) ' ';
            } else if (b != '%') {
                dstBuffer[idx] = b;
            } else {
                // read next 2 digits
                if (j + 2 >= srcEnd) {
                    throw new IllegalStateException("Unexpected termination");
                }
                byte b1 = srcBuffer.get(j + 1);
                byte b2 = srcBuffer.get(j + 2);
                
                if (!HexUtils.isHexDigit(b1) || !HexUtils.isHexDigit(b2)) {
                    throw new IllegalStateException("isHexDigit");
                }

                j += 2;
                final int res = x2c(b1, b2);
                if (!allowEncodedSlash && (res == '/')) {
                    throw new CharConversionException("Encoded slashes are not allowed");
                }
                dstBuffer[idx] = (byte) res;
            }
        }

        dstByteChunk.setEnd(idx);
    }

    /**
     * URLDecode the {@link ByteChunk}
     */
    public static void decode(final ByteChunk srcByteChunk,
            final BufferChunk dstBufferChunk,
            final boolean allowEncodedSlash) throws CharConversionException {

        final byte[] srcBuffer = srcByteChunk.getBuffer();
        final int srcStart = srcByteChunk.getStart();
        final int srcEnd = srcByteChunk.getEnd();

        final Buffer dstBuffer = dstBufferChunk.getBuffer();

        int idx = dstBufferChunk.getStart();
        for (int j = srcStart; j < srcEnd; j++, idx++) {
            final byte b = srcBuffer[j];

            if (b == '+') {
                dstBuffer.put(idx , (byte) ' ');
            } else if (b != '%') {
                dstBuffer.put(idx, b);
            } else {
                // read next 2 digits
                if (j + 2 >= srcEnd) {
                    throw new IllegalStateException("Unexpected termination");
                }
                byte b1 = srcBuffer[j + 1];
                byte b2 = srcBuffer[j + 2];
                
                if (!HexUtils.isHexDigit(b1) || !HexUtils.isHexDigit(b2)) {
                    throw new IllegalStateException("isHexDigit");
                }

                j += 2;
                final int res = x2c(b1, b2);
                if (!allowEncodedSlash && (res == '/')) {
                    throw new CharConversionException("Encoded slashes are not allowed");
                }
                dstBuffer.put(idx, (byte) res);
            }
        }

        dstBufferChunk.setEnd(idx);
    }
    
    /**
     * URLDecode the {@link BufferChunk}
     */
    public static void decode(final BufferChunk bufferChunk,
            final boolean allowEncodedSlash) throws CharConversionException {
        decode(bufferChunk, bufferChunk, allowEncodedSlash);
    }

    /**
     * URLDecode the {@link BufferChunk}
     */
    public static void decode(final BufferChunk srcBufferChunk,
            final BufferChunk dstBufferChunk,
            final boolean allowEncodedSlash) throws CharConversionException {

        final Buffer srcBuffer = srcBufferChunk.getBuffer();
        final int srcStart = srcBufferChunk.getStart();
        final int srcEnd = srcBufferChunk.getEnd();

        final Buffer dstBuffer = dstBufferChunk.getBuffer();

        int idx = dstBufferChunk.getStart();
        for (int j = srcStart; j < srcEnd; j++, idx++) {
            final byte b = srcBuffer.get(j);

            if (b == '+') {
                dstBuffer.put(idx , (byte) ' ');
            } else if (b != '%') {
                dstBuffer.put(idx, b);
            } else {
                // read next 2 digits
                if (j + 2 >= srcEnd) {
                    throw new IllegalStateException("Unexpected termination");
                }
                byte b1 = srcBuffer.get(j + 1);
                byte b2 = srcBuffer.get(j + 2);
                
                if (!HexUtils.isHexDigit(b1) || !HexUtils.isHexDigit(b2)) {
                    throw new IllegalStateException("isHexDigit");
                }

                j += 2;
                final int res = x2c(b1, b2);
                if (!allowEncodedSlash && (res == '/')) {
                    throw new CharConversionException("Encoded slashes are not allowed");
                }
                dstBuffer.put(idx, (byte) res);
            }
        }

        dstBufferChunk.setEnd(idx);
    }

    /**
     * URLDecode the {@link CharChunk}
     */
    public static void decode(final CharChunk charChunk,
            final boolean allowEncodedSlash) throws CharConversionException {
        decode(charChunk, charChunk, allowEncodedSlash);
    }

    /**
     * URLDecode the {@link CharChunk}
     */
    public static void decode(final CharChunk srcCharChunk,
            final CharChunk dstCharChunk,
            final boolean allowEncodedSlash) throws CharConversionException {

        final char[] srcBuffer = srcCharChunk.getBuffer();
        final int srcStart = srcCharChunk.getStart();
        final int srcEnd = srcCharChunk.getEnd();

        final char[] dstBuffer = dstCharChunk.getBuffer();

        int idx = dstCharChunk.getStart();
        for (int j = srcStart; j < srcEnd; j++, idx++) {
            final char c = srcBuffer[j];

            if (c == '+') {
                dstBuffer[idx] = ' ';
            } else if (c != '%') {
                dstBuffer[idx] = c;
            } else {
                // read next 2 digits
                if (j + 2 >= srcEnd) {
                    throw new IllegalStateException("Unexpected termination");
                }
                final char c1 = srcBuffer[j + 1];
                final char c2 = srcBuffer[j + 2];

                if (!HexUtils.isHexDigit(c1) || !HexUtils.isHexDigit(c2)) {
                    throw new IllegalStateException("isHexDigit");
                }

                j += 2;
                final int res = x2c(c1, c2);
                if (!allowEncodedSlash && (res == '/')) {
                    throw new CharConversionException("Encoded slashes are not allowed");
                }
                dstBuffer[idx] = (char) res;
            }
        }

        dstCharChunk.setEnd(idx);
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

    private static int x2c(int c1, int c2) {
        return (HexUtils.hexDigit2Dec(c1) << 4) + HexUtils.hexDigit2Dec(c2);
    }
}
