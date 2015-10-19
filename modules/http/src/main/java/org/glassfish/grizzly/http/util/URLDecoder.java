/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.io.UnsupportedEncodingException;
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
        decodeAscii(dataChunk, dataChunk, allowEncodedSlash);
    }

    /**
     * URLDecode the {@link DataChunk}
     */
    public static void decode(final DataChunk srcDataChunk,
            final DataChunk dstDataChunk, final boolean allowEncodedSlash)
            throws CharConversionException {
        decodeAscii(srcDataChunk, dstDataChunk, allowEncodedSlash);
    }
    
   /**
     * URLDecode the {@link DataChunk}
     */
    public static void decode(final DataChunk srcDataChunk,
            final DataChunk dstDataChunk, final boolean allowEncodedSlash,
            final String enc)
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
                        decode(srcDataChunk.toString(), allowEncodedSlash, enc));
                return;
            }
            case Chars:
            {
                final CharChunk srcCharChunk = srcDataChunk.getCharChunk();
                final CharChunk dstCharChunk = dstDataChunk.getCharChunk();
                
                // If it's the same buffer - we don't want to call allocate,
                // because it resets the ByteChunk length
                if (dstDataChunk != srcDataChunk) {
                    dstCharChunk.ensureCapacity(srcCharChunk.getLength());
                    decode(srcCharChunk, dstCharChunk, allowEncodedSlash, enc);
                } else {
                    decode(srcCharChunk, srcCharChunk, allowEncodedSlash, enc);
                }

                dstDataChunk.setChars(dstCharChunk.getChars(),
                        dstCharChunk.getStart(), dstCharChunk.getEnd());
                return;
            }
            default: throw new NullPointerException();
        }
    }

   /**
     * URLDecode the {@link DataChunk}
     */
    public static void decodeAscii(final DataChunk srcDataChunk,
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
                        decodeAscii(srcDataChunk.toString(), allowEncodedSlash));
                return;
            }
            case Chars:
            {
                final CharChunk srcCharChunk = srcDataChunk.getCharChunk();
                final CharChunk dstCharChunk = dstDataChunk.getCharChunk();
                
                // If it's the same buffer - we don't want to call allocate,
                // because it resets the ByteChunk length
                if (dstDataChunk != srcDataChunk) {
                    dstCharChunk.ensureCapacity(srcCharChunk.getLength());
                    decodeAscii(srcCharChunk, dstCharChunk, allowEncodedSlash);
                } else {
                    decodeAscii(srcCharChunk, srcCharChunk, allowEncodedSlash);
                }

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
                    throw new IllegalArgumentException(
                         "URLDecoder: Illegal hex characters in escape (%) pattern - %"
                                                + (char) b1 + "" + (char) b2);
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
                    throw new IllegalArgumentException(
                            "URLDecoder: Illegal hex characters in escape (%) pattern - %"
                                    + (char) b1 + "" + (char) b2);
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
                    throw new IllegalArgumentException(
                            "URLDecoder: Illegal hex characters in escape (%) pattern - %"
                                    + (char) b1 + "" + (char) b2);
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
                    throw new IllegalArgumentException(
                            "URLDecoder: Illegal hex characters in escape (%) pattern - %"
                                    + (char) b1 + "" + (char) b2);
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
        decodeAscii(charChunk, charChunk, allowEncodedSlash);
    }

    /**
     * URLDecode the {@link CharChunk}
     */
    public static void decode(final CharChunk srcCharChunk,
            final CharChunk dstCharChunk,
            final boolean allowEncodedSlash) throws CharConversionException {
        decodeAscii(srcCharChunk, dstCharChunk, allowEncodedSlash);
    }
    
    /**
     * URLDecode the {@link CharChunk}
     */
    public static void decode(final CharChunk srcCharChunk,
            final CharChunk dstCharChunk,
            final boolean allowEncodedSlash,
            String enc) throws CharConversionException {

        byte[] bytes = null;
        final char[] srcBuffer = srcCharChunk.getBuffer();
        final int srcStart = srcCharChunk.getStart();
        final int srcEnd = srcCharChunk.getEnd();
        final int srcLen = srcEnd - srcStart;
        
        final char[] dstBuffer = dstCharChunk.getBuffer();

        int idx = dstCharChunk.getStart();
        int j = srcStart;
        while(j < srcEnd) {
            char c = srcBuffer[j];

            if (c == '+') {
                dstBuffer[idx++] = ' ';
                j++;
            } else if (c != '%') {
                dstBuffer[idx++] = c;
                j++;
            } else {
                /*
                 * Starting with this instance of %, process all
                 * consecutive substrings of the form %xy. Each
                 * substring %xy will yield a byte. Convert all
                 * consecutive  bytes obtained this way to whatever
                 * character(s) they represent in the provided
                 * encoding.
                 */

                try {

                    // (numChars-i)/3 is an upper bound for the number
                    // of remaining bytes
                    if (bytes == null)
                        bytes = new byte[(srcLen-j)/3];
                    int pos = 0;

                    while (((j + 2) < srcLen)
                            && (c == '%')) {
                        
                        final char c1 = srcBuffer[j + 1];
                        final char c2 = srcBuffer[j + 2];

                        if (!HexUtils.isHexDigit(c1) || !HexUtils.isHexDigit(c2)) {
                            throw new IllegalArgumentException(
                                    "URLDecoder: Illegal hex characters in escape (%) pattern - %"
                                            + c1 + "" + c2);
                        }
                        
                        int v = x2c(c1, c2);
                        if (v < 0) {
                            throw new IllegalArgumentException("URLDecoder: Illegal hex characters in escape (%) pattern - negative value");
                        }
                        bytes[pos++] = (byte) v;
                        j += 3;
                        if (j < srcLen) {
                            c = srcBuffer[j];
                        }
                    }

                    // A trailing, incomplete byte encoding such as
                    // "%x" will cause an exception to be thrown

                    if ((j < srcLen) && (c == '%')) {
                        throw new IllegalArgumentException(
                                "URLDecoder: Incomplete trailing escape (%) pattern");
                    }
                    final String decodedChunk = new String(bytes, 0, pos, enc);
                    if (!allowEncodedSlash && (decodedChunk.indexOf('/') != -1)) {
                        throw new CharConversionException("Encoded slashes are not allowed");
                    }
                    
                    final int chunkLen = decodedChunk.length();
                    decodedChunk.getChars(0, chunkLen, dstBuffer, idx);
                    idx += chunkLen;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "URLDecoder: Illegal hex characters in escape (%) pattern - "
                            + e.getMessage());
                } catch (UnsupportedEncodingException ignored) {
                }
            }
        }

        dstCharChunk.setEnd(idx);
    }
    
    /**
     * URLDecode the {@link CharChunk}
     */
    public static void decodeAscii(final CharChunk srcCharChunk,
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
                    throw new IllegalArgumentException(
                            "URLDecoder: Illegal hex characters in escape (%) pattern - %"
                                    + c1 + "" + c2);
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
    
    /**
     * URLDecode the {@link String}
     */
    public static String decode(final String str) throws CharConversionException {
        return decodeAscii(str, true);
    }

    /**
     * URLDecode the {@link String}
     */
    public static String decode(final String str, final boolean allowEncodedSlash)
            throws CharConversionException {
        return decodeAscii(str, allowEncodedSlash);
    }

    /**
     * URLDecode the {@link String}
     */
    public static String decode(String s, final boolean allowEncodedSlash,
            String enc) throws CharConversionException {
        boolean needToChange = false;
        int numChars = s.length();
        StringBuilder sb = new StringBuilder(numChars > 500 ? numChars / 2 : numChars);
        int i = 0;

        char c;
        byte[] bytes = null;
        while (i < numChars) {
            c = s.charAt(i);
            switch (c) {
            case '+':
                sb.append(' ');
                i++;
                needToChange = true;
                break;
            case '%':
                /*
                 * Starting with this instance of %, process all
                 * consecutive substrings of the form %xy. Each
                 * substring %xy will yield a byte. Convert all
                 * consecutive  bytes obtained this way to whatever
                 * character(s) they represent in the provided
                 * encoding.
                 */

                try {

                    // (numChars-i)/3 is an upper bound for the number
                    // of remaining bytes
                    if (bytes == null)
                        bytes = new byte[(numChars-i)/3];
                    int pos = 0;

                    while (((i + 2) < numChars)
                            && (c == '%')) {
                        int v = Integer.parseInt(s.substring(i + 1, i + 3), 16);
                        if (v < 0) {
                            throw new IllegalArgumentException("URLDecoder: Illegal hex characters in escape (%) pattern - negative value");
                        }
                        bytes[pos++] = (byte) v;
                        i += 3;
                        if (i < numChars) {
                            c = s.charAt(i);
                        }
                    }

                    // A trailing, incomplete byte encoding such as
                    // "%x" will cause an exception to be thrown

                    if ((i < numChars) && (c == '%')) {
                        throw new IllegalArgumentException(
                                "URLDecoder: Incomplete trailing escape (%) pattern");
                    }
                    final String decodedChunk = new String(bytes, 0, pos, enc);
                    if (!allowEncodedSlash && (decodedChunk.indexOf('/') != -1)) {
                        throw new CharConversionException("Encoded slashes are not allowed");
                    }

                    sb.append(decodedChunk);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "URLDecoder: Illegal hex characters in escape (%) pattern - "
                            + e.getMessage());
                } catch (UnsupportedEncodingException ignored) {
                }
                needToChange = true;
                break;
            default:
                sb.append(c);
                i++;
                break;
            }
        }

        return (needToChange? sb.toString() : s);
    }
    
    public static String decodeAscii(final String str, final boolean allowEncodedSlash)
            throws CharConversionException {
        
        if (str == null) {
            return null;
        }

        int mPos = 0;        // mark position
        int strPos = 0;
        int strLen = str.length();
        StringBuilder dec = null;    // decoded string output
        
        while (strPos < strLen) {
            // process next metacharacter
            final char metaChar = str.charAt(strPos);
            final boolean isPlus = (metaChar == '+');
            final boolean isNorm = !(isPlus || (metaChar == '%'));
            
            if (isNorm) {
                strPos++;
            } else {
                if (dec == null) {
                    dec = new StringBuilder(strLen);
                }
                
                // if there were non-metacharacters, copy them all as a block
                if (mPos < strPos) {
                    dec.append(str, mPos, strPos);
                }
                
                if (isPlus) {
                    dec.append(' ');
                    strPos++;
                } else {
                    final char res = (char) Integer.parseInt(str.substring(strPos + 1, strPos + 3), 16);
                    if (!allowEncodedSlash && (res == '/')) {
                        throw new CharConversionException("Encoded slashes are not allowed");
                    }
                    dec.append(res);
                    strPos += 3;
                }
                
                mPos = strPos;
            }
        }

        if (dec != null) {
            // copy the normal characters remainder (if any)
            if (mPos < strPos) {
                dec.append(str, mPos, strPos);
            }
            
            return dec.toString();
        }
        
        // all characters were normal
        return str;
    }
    
    private static int x2c(byte b1, byte b2) {
        return (HexUtils.hexDigit2Dec(b1) << 4) + HexUtils.hexDigit2Dec(b2);
    }

    private static int x2c(int c1, int c2) {
        return (HexUtils.hexDigit2Dec(c1) << 4) + HexUtils.hexDigit2Dec(c2);
    }
}
