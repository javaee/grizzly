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
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import static org.glassfish.grizzly.utils.Charsets.*;

/**
 * Utility class that make sure an HTTP url defined inside a {@link MessageBytes}
 * is normalized, converted and valid. It also makes sure there is no security
 * hole. Mainly, this class can be used by doing:
 * <p><pre><code>
 *
 * HttpRequestURIDecoder.decode(decodedURI, urlDecoder, encoding, b2cConverter);
 *
 * </code></pre>
 *
 * @author Jeanfrancois Arcand
 */
public class HttpRequestURIDecoder {

    protected static final boolean ALLOW_BACKSLASH = false;
    private static final boolean COLLAPSE_ADJACENT_SLASHES =
            Boolean.valueOf(System.getProperty("com.sun.enterprise.web.collapseAdjacentSlashes", "true"));
    private static final Logger LOGGER = Grizzly.logger(HttpRequestURIDecoder.class);

    /**
     * Decode the http request represented by the bytes inside {@link MessageBytes}
     * using an {@link UDecoder}.
     * @param decodedURI - The bytes to decode
     * @param urlDecoder - The urlDecoder to use to decode.
     * @throws java.lang.Exception
     */
    public static void decode(final MessageBytes decodedURI, final UDecoder urlDecoder)
            throws Exception {
        decode(decodedURI, urlDecoder, null, null);
    }

    /**
     * Decode the HTTP request represented by the bytes inside {@link MessageBytes}
     * using an {@link UDecoder}, using the specified encoding, using the specified
     * [@link B2CConverter} to decode the request.
     * @param decodedURI - The bytes to decode
     * @param urlDecoder - The urlDecoder to use to decode.
     * @param encoding the encoding value, default is UTF-8.
     * @param b2cConverter the Bytes to Char Converter.
     * @throws java.lang.Exception
     */
    public static void decode(final MessageBytes decodedURI,
            final UDecoder urlDecoder, String encoding,
            final B2CConverter b2cConverter) throws Exception {
        // %xx decoding of the URL
        urlDecoder.convert(decodedURI, false);

        if (!normalize(decodedURI)) {
            throw new IOException("Invalid URI character encoding");
        }

        if (encoding == null) {
            encoding = "utf-8";
        }

        convertURI(decodedURI, encoding, b2cConverter);

        // Check that the URI is still normalized
        if (!checkNormalize(decodedURI.getCharChunk())) {
            throw new IOException("Invalid URI character encoding");
        }
    }

    /**
     * Decode the HTTP request represented by the bytes inside {@link DataChunk}.
     * @param decodedURI - The bytes to decode
     * @throws java.io.CharConversionException
     */
    public static void decode(final DataChunk decodedURI)
            throws CharConversionException {
        decode(decodedURI, false, UTF8_CHARSET);
    }

    /**
     * Decode the HTTP request represented by the bytes inside {@link DataChunk}.
     * @param decodedURI - The bytes to decode
     * @param isSlashAllowed allow encoded slashes
     * @throws java.io.CharConversionException
     */
    public static void decode(final DataChunk decodedURI,
            final boolean isSlashAllowed) throws CharConversionException {
        decode(decodedURI, isSlashAllowed, UTF8_CHARSET);
    }

    /**
     * Decode the HTTP request represented by the bytes inside {@link DataChunk}.
     * @param decodedURI - The bytes to decode
     * @param isSlashAllowed allow encoded slashes
     * @param encoding the encoding value, default is UTF-8.
     * @throws java.io.CharConversionException
     */
    public static void decode(final DataChunk decodedURI,
            final boolean isSlashAllowed, final Charset encoding)
            throws CharConversionException {
        decode(decodedURI, decodedURI, isSlashAllowed, encoding);
    }

    /**
     * Decode the HTTP request represented by the bytes inside {@link DataChunk}.
     * @param originalURI - The bytes to decode
     * @param targetDecodedURI the target {@link DataChunk} URI will be decoded to
     * @param isSlashAllowed is '/' an allowable character
     * @param encoding the encoding value, default is UTF-8
     * @throws java.io.CharConversionException
     */
    public static void decode(final DataChunk originalURI,
            final DataChunk targetDecodedURI, final boolean isSlashAllowed,
            final Charset encoding) throws CharConversionException {

        // %xx decoding of the URL
        URLDecoder.decode(originalURI, targetDecodedURI, isSlashAllowed);

        if (!normalize(targetDecodedURI)) {
            throw new CharConversionException("Invalid URI character encoding");
        }

        convertToChars(targetDecodedURI, encoding);
    }

    /**
     * Converts the normalized the HTTP request represented by the bytes inside
     * {@link DataChunk} to chars representation, using the passed encoding.
     * @param decodedURI - The bytes to decode
     * @param encoding the encoding value, default is UTF-8.
     * @throws java.io.CharConversionException
     */
    public static void convertToChars(final DataChunk decodedURI,
            Charset encoding) throws CharConversionException {
        if (encoding == null) {
            encoding = UTF8_CHARSET;
        }

        decodedURI.toChars(encoding);

        // Check that the URI is still normalized
        if (!checkNormalize(decodedURI.getCharChunk())) {
            throw new CharConversionException("Invalid URI character encoding");
        }
    }
    
    /**
     * Convert a URI using the specified encoding, using the specified
     * [@link B2CConverter} to decode the request.
     * @param uri - The bytes to decode
     * @param encoding the encoding value
     * @param b2cConverter the Bytes to Char Converter.
     * @throws java.lang.Exception
     */
    private static void convertURI(final MessageBytes uri, final String encoding,
            B2CConverter b2cConverter) throws Exception {

        final ByteChunk bc = uri.getByteChunk();
        final CharChunk cc = uri.getCharChunk();
        cc.allocate(bc.getLength(), -1);

        if (encoding != null && encoding.trim().length() != 0
                && !"ISO-8859-1".equalsIgnoreCase(encoding)) {
            try {
                if (b2cConverter == null) {
                    b2cConverter = new B2CConverter(encoding);
                }
            } catch (IOException e) {
                // Ignore
                LOGGER.severe("Invalid URI encoding; using HTTP default");
            }
            if (b2cConverter != null) {
                try {
                    b2cConverter.convert(bc, cc);
                    uri.setChars(cc.getBuffer(), cc.getStart(),
                            cc.getLength());
                    return;
                } catch (IOException e) {
                    LOGGER.severe("Invalid URI character encoding; trying ascii");
                    cc.recycle();
                }
            }
        }

        // Default encoding: fast conversion
        final byte[] bbuf = bc.getBuffer();
        final char[] cbuf = cc.getBuffer();
        int start = bc.getStart();
        for (int i = 0; i < bc.getLength(); i++) {
            cbuf[i] = (char) (bbuf[i + start] & 0xff);
        }
        uri.setChars(cbuf, 0, bc.getLength());

    }

    /**
     * Normalize URI.
     * <p>
     * This method normalizes "\", "//", "/./" and "/../". This method will
     * return false when trying to go above the root, or if the URI contains
     * a null byte.
     *
     * @param uriMB URI to be normalized
     * @return <tt>true</tt> if normalization was successful, or <tt>false</tt> otherwise
     */
    public static boolean normalize(MessageBytes uriMB) {

        int type = uriMB.getType();
        if (type == MessageBytes.T_CHARS) {
            return normalizeChars(uriMB.getCharChunk());
        } else {
            return normalizeBytes(uriMB.getByteChunk());
        }
    }

    /**
     * Normalize URI.
     * <p>
     * This method normalizes "\", "//", "/./" and "/../". This method will
     * return false when trying to go above the root, or if the URI contains
     * a null byte.
     *
     * @param dataChunk URI to be normalized
     * @return <tt>true</tt> if normalization was successful, or <tt>false</tt> otherwise
     */
    public static boolean normalize(final DataChunk dataChunk) {

        switch (dataChunk.getType()) {
            case Bytes:
                return normalizeBytes(dataChunk.getByteChunk());
            case Buffer:
                return normalizeBuffer(dataChunk.getBufferChunk());
            case String:
                try {
                    dataChunk.toChars(null);
                } catch (CharConversionException unexpected) {
                    // should never occur
                    throw new IllegalStateException("Unexpected exception", unexpected);
                }
                // pass to Chars case
            case Chars:
                return normalizeChars(dataChunk.getCharChunk());
            default:
                throw new NullPointerException();
        }
    }

    /**
     * Check that the URI is normalized following character decoding.
     * <p>
     * This method checks for "\", 0, "//", "/./" and "/../". This method will
     * return false if sequences that are supposed to be normalized are still
     * present in the URI.
     *
     * @param uriCC URI to be checked (should be chars)
     * @return <tt>true</tt> if the uriCC represents a normalized URI, or <tt>false</tt> otherwise
     */
    public static boolean checkNormalize(final CharChunk uriCC) {

        char[] c = uriCC.getChars();
        int start = uriCC.getStart();
        int end = uriCC.getEnd();

        int pos;

        // Check for '\' and 0
        for (pos = start; pos < end; pos++) {
            if (c[pos] == '\\') {
                return false;
            }
            if (c[pos] == 0) {
                return false;
            }
        }

        if (COLLAPSE_ADJACENT_SLASHES) {
            // Check for "//"
            for (pos = start; pos < (end - 1); pos++) {
                if (c[pos] == '/') {
                    if (c[pos + 1] == '/') {
                        return false;
                    }
                }
            }
        }

        // Check for ending with "/." or "/.."
        if (((end - start) >= 2) && (c[end - 1] == '.')) {
            if ((c[end - 2] == '/')
                    || ((c[end - 2] == '.')
                    && (c[end - 3] == '/'))) {
                return false;
            }
        }

        // Check for "/./"
        return uriCC.indexOf("/./", 0, 3, 0) < 0;

    }

    public static boolean normalizeChars(final CharChunk uriCC) {
        char[] c = uriCC.getChars();
        int start = uriCC.getStart();
        int end = uriCC.getEnd();

        // URL * is acceptable
        if ((end - start == 1) && c[start] == '*') {
            return true;
        }

        int pos;
        int index;

        // Replace '\' with '/'
        // Check for null char
        for (pos = start; pos < end; pos++) {
            if (c[pos] == '\\') {
                if (ALLOW_BACKSLASH) {
                    c[pos] = '/';
                } else {
                    return false;
                }
            }
            if (c[pos] == (char) 0) {
                return false;
            }
        }

        // The URL must start with '/'
        if (c[start] != '/') {
            return false;
        }

        // Replace "//" with "/"
        if (COLLAPSE_ADJACENT_SLASHES) {
            for (pos = start; pos < (end - 1); pos++) {
                if (c[pos] == '/') {
                    while ((pos + 1 < end) && (c[pos + 1] == '/')) {
                        copyChars(c, pos, pos + 1, end - pos - 1);
                        end--;
                    }
                }
            }
        }

        // If the URI ends with "/." or "/..", then we append an extra "/"
        // Note: It is possible to extend the URI by 1 without any side effect
        // as the next character is a non-significant WS.
        if (((end - start) > 2) && (c[end - 1] == '.')) {
            if ((c[end - 2] == '/') || ((c[end - 2] == '.') && (c[end - 3] == '/'))) {
                c[end] = '/';
                end++;
            }
        }

        uriCC.setEnd(end);

        index = 0;

        // Resolve occurrences of "/./" in the normalized path
        while (true) {
            index = uriCC.indexOf("/./", 0, 3, index);
            if (index < 0) {
                break;
            }
            copyChars(c, start + index, start + index + 2,
                    end - start - index - 2);
            end = end - 2;
            uriCC.setEnd(end);
        }

        index = 0;

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            index = uriCC.indexOf("/../", 0, 4, index);
            if (index < 0) {
                break;
            }
            // Prevent from going outside our context
            if (index == 0) {
                return false;
            }
            int index2 = -1;
            for (pos = start + index - 1; (pos >= 0) && (index2 < 0); pos--) {
                if (c[pos] == '/') {
                    index2 = pos;
                }
            }
            copyChars(c, start + index2, start + index + 3,
                    end - start - index - 3);
            end = end + index2 - index - 3;
            uriCC.setEnd(end);
            index = index2;
        }

        uriCC.setChars(c, start, end);

        return true;

    }

    // ------------------------------------------------------ Protected Methods
    /**
     * Copy an array of bytes to a different position. Used during
     * normalization.
     */
    protected static void copyBytes(byte[] b, int dest, int src, int len) {
        System.arraycopy(b, src, b, dest, len);
    }

    /**
     * Copy an array of chars to a different position. Used during
     * normalization.
     */
    private static void copyChars(char[] c, int dest, int src, int len) {
        System.arraycopy(c, src, c, dest, len);
    }

    /**
     * Log a message on the Logger associated with our Container (if any)
     *
     * @param message Message to be logged
     */
    protected void log(String message) {
        LOGGER.info(message);
    }

    /**
     * Log a message on the Logger associated with our Container (if any)
     *
     * @param message Message to be logged
     * @param throwable Associated exception
     */
    protected void log(String message, Throwable throwable) {
        LOGGER.log(Level.SEVERE, message, throwable);
    }

    /**
     * Character conversion of the a US-ASCII MessageBytes.
     */
    protected void convertMB(MessageBytes mb) {

        // This is of course only meaningful for bytes
        if (mb.getType() != MessageBytes.T_BYTES) {
            return;
        }

        ByteChunk bc = mb.getByteChunk();
        CharChunk cc = mb.getCharChunk();
        cc.allocate(bc.getLength(), -1);

        // Default encoding: fast conversion
        byte[] bbuf = bc.getBuffer();
        char[] cbuf = cc.getBuffer();
        int start = bc.getStart();
        for (int i = 0; i < bc.getLength(); i++) {
            cbuf[i] = (char) (bbuf[i + start] & 0xff);
        }
        mb.setChars(cbuf, 0, bc.getLength());

    }
    private static final int STATE_CHAR = 0;
    private static final int STATE_SLASH = 1;
    private static final int STATE_PERCENT = 2;
    private static final int STATE_SLASHDOT = 3;
    private static final int STATE_SLASHDOTDOT = 4;

    public static boolean normalizeBytes(final ByteChunk bc) {
        byte[] bs = bc.getBytes();
        int start = bc.getStart();
        int end = bc.getEnd();

        // An empty URL is not acceptable
        if (start == end) {
            return false;
        }

        // URL * is acceptable
        if ((end - start == 1) && bs[start] == (byte) '*') {
            return true;
        }

        // If the URI ends with "/." or "/..", then we append an extra "/"
        // Note: It is possible to extend the URI by 1 without any side effect
        // as the next character is a non-significant WS.
        if (((end - start) > 2) && (bs[end - 1] == (byte) '.')) {
            if ((bs[end - 2] == (byte) '/') || ((bs[end - 2] == (byte) '.') && (bs[end - 3] == (byte) '/'))) {
                bs[end] = (byte) '/';
                end++;
            }
        }

        int state = STATE_CHAR;
        int srcPos = start;

        int lastSlash = -1;
        int parentSlash = -1;

        for (int pos = start; pos < end; pos++) {
            if (bs[pos] == (byte) 0) {
                return false;
            }
            if (bs[pos] == (byte) '\\') {
                if (ALLOW_BACKSLASH) {
                    bs[pos] = (byte) '/';
                } else {
                    return false;
                }
            }
            if (bs[pos] == '/') {
                if (state == STATE_CHAR) {
                    state = STATE_SLASH;
                    bs[srcPos] = bs[pos];
                    parentSlash = lastSlash;
                    lastSlash = srcPos;
                    srcPos++;
                } else if (state == STATE_SLASH) {
                    // This is '//'. Ignore if COLLAPSE_ADJACENT_SLASHES is true.
                    // What is the behavior for '/../' patterns if collapse is false.
                    // Ignoring for now.
                    if (!COLLAPSE_ADJACENT_SLASHES) {
                        srcPos++;
                    }
                } else if (state == STATE_SLASHDOT) {
                    // This is '/./' ==> move the srcPos one position back
                    srcPos--;
                } else if (state == STATE_SLASHDOTDOT) {
                    // This is '/../' ==> search backward to reset lastSlash and parentSlash
                    if (parentSlash == -1) {
                        // This is an error
//                        System.out.print("Incorrect URI");
                        return false;
                    } else {
                        lastSlash = parentSlash;
                        srcPos = parentSlash;
                        // Find the parentSlash
                        parentSlash = -1;
                        for (int i = lastSlash - 1; i >= start; i--) {
                            if (bs[i] == '/') {
                                parentSlash = i;
                                break;
                            }
                        }
                    }
                    state = STATE_SLASH;
                    bs[srcPos++] = bs[pos];
                }
            } else if (bs[pos] == '.') {
                if (state == STATE_CHAR) {
                    bs[srcPos++] = bs[pos];
                } else if (state == STATE_SLASH) {
                    state = STATE_SLASHDOT;
                    bs[srcPos++] = bs[pos];
                } else if (state == STATE_SLASHDOT) {
                    state = STATE_SLASHDOTDOT;
                    bs[srcPos++] = bs[pos];
                }
            } else {
                state = STATE_CHAR;
                bs[srcPos++] = bs[pos];
            }
        }

        bc.setEnd(srcPos);
        return true;
    }

    public static boolean normalizeBuffer(final BufferChunk bc) {
        final Buffer bs = bc.getBuffer();
        final int start = bc.getStart();
        int end = bc.getEnd();

        // An empty URL is not acceptable
        if (start == end) {
            return false;
        }

        // URL * is acceptable
        if ((end - start == 1) && bs.get(start) == (byte) '*') {
            return true;
        }

        // If the URI ends with "/." or "/..", then we append an extra "/"
        // Note: It is possible to extend the URI by 1 without any side effect
        // as the next character is a non-significant WS.
        if (((end - start) > 2) && (bs.get(end - 1) == (byte) '.')) {
            final byte b = bs.get(end - 2);
            if (b == (byte) '/'
                    || (b == (byte) '.'
                    && bs.get(end - 3) == (byte) '/')) {
                bs.put(end, (byte) '/');
                end++;
            }
        }

        int state = STATE_CHAR;
        int srcPos = start;

        int lastSlash = -1;
        int parentSlash = -1;

        for (int pos = start; pos < end; pos++) {
            final byte b = bs.get(pos);
            if (b == (byte) 0) {
                return false;
            }
            if (b == (byte) '\\') {
                if (ALLOW_BACKSLASH) {
                    bs.put(pos, (byte) '/');
                } else {
                    return false;
                }
            }

            if (b == '/') {
                if (state == STATE_CHAR) {
                    state = STATE_SLASH;
                    bs.put(srcPos, b);
                    parentSlash = lastSlash;
                    lastSlash = srcPos;
                    srcPos++;
                } else if (state == STATE_SLASH) {
                    // This is '//'. Ignore if COLLAPSE_ADJACENT_SLASHES is true.
                    // What is the behavior for '/../' patterns if collapse is false.
                    // Ignoring for now.
                    if (!COLLAPSE_ADJACENT_SLASHES) {
                        srcPos++;
                    }
                } else if (state == STATE_SLASHDOT) {
                    // This is '/./' ==> move the srcPos one position back
                    srcPos--;
                } else if (state == STATE_SLASHDOTDOT) {
                    // This is '/../' ==> search backward to reset lastSlash and parentSlash
                    if (parentSlash == -1) {
                        // This is an error
//                        System.out.print("Incorrect URI");
                        return false;
                    } else {
                        lastSlash = parentSlash;
                        srcPos = parentSlash;
                        // Find the parentSlash
                        parentSlash = -1;
                        for (int i = lastSlash - 1; i >= start; i--) {
                            if (bs.get(i) == '/') {
                                parentSlash = i;
                                break;
                            }
                        }
                    }
                    state = STATE_SLASH;
                    bs.put(srcPos++, b);
                }
            } else if (b == '.') {
                if (state == STATE_CHAR) {
                    bs.put(srcPos++, b);
                } else if (state == STATE_SLASH) {
                    state = STATE_SLASHDOT;
                    bs.put(srcPos++, b);
                } else if (state == STATE_SLASHDOT) {
                    state = STATE_SLASHDOTDOT;
                    bs.put(srcPos++, b);
                }
            } else {
                state = STATE_CHAR;
                bs.put(srcPos++, b);
            }
        }

        bc.setEnd(srcPos);
        return true;
    }
}
