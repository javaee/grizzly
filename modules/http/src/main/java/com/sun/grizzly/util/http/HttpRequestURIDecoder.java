/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.util.http;

import com.sun.grizzly.util.buf.B2CConverter;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.CharChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.buf.UDecoder;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class that make sure an HTTP url defined inside a {@link MessagesBytes}
 * is normalized, converted and valid. It also makes sure there is no security
 * hole. Mainly, this class can be used by doing:
 * <p><pre><code>
 * 
 * HttpRequestURIDecoder.decode(decodedURI, urlDecoder, encoding, b2cConverter);
 * 
 * </code></pre></code>
 * 
 * @author Jeanfrancois Arcand
 */
public class HttpRequestURIDecoder {

    protected static final boolean ALLOW_BACKSLASH = false;
    private static final boolean COLLAPSE_ADJACENT_SLASHES = 
            Boolean.valueOf(System.getProperty("com.sun.enterprise.web.collapseAdjacentSlashes"
            , "true")).booleanValue();
    private static Logger log = Logger.getLogger(
            HttpRequestURIDecoder.class.getName());

    /**
     * Decode the http request represented by the bytes inside {@link MessageBytes}
     * using an {@link UDecoder}. 
     * @param decodedURI - The bytes to decode
     * @param urlDecoder - The urlDecoder to use to decode.
     * @throws java.lang.Exception
     */
    public final static void decode(MessageBytes decodedURI, UDecoder urlDecoder)
            throws Exception {
        decode(decodedURI, urlDecoder, null, null);
    }

    /**
     * Decode the http request represented by the bytes inside {@link MessageBytes}
     * using an {@link UDecoder}, using the specified encoding, using the specified 
     * [@link B2CConverter} to decode the request. 
     * @param decodedURI - The bytes to decode
     * @param urlDecoder - The urlDecoder to use to decode.
     * @param encoding the encoding value, default is utf-8.
     * @param b2cConverter the Bytes to Char Converter.
     * @throws java.lang.Exception
     */
    public final static void decode(MessageBytes decodedURI, UDecoder urlDecoder,
            String encoding, B2CConverter b2cConverter) throws Exception {
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
        if (!checkNormalize(decodedURI)) {
            throw new IOException("Invalid URI character encoding");
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
    private final static void convertURI(MessageBytes uri, String encoding,
            B2CConverter b2cConverter)
            throws Exception {

        ByteChunk bc = uri.getByteChunk();
        CharChunk cc = uri.getCharChunk();
        cc.allocate(bc.getLength(), -1);

        if (encoding != null) {
            try {
                if (b2cConverter == null) {
                    b2cConverter = new B2CConverter(encoding);
                }
            } catch (IOException e) {
                // Ignore
                log.severe("Invalid URI encoding; using HTTP default");
            }
            if (b2cConverter != null) {
                try {
                    b2cConverter.convert(bc, cc, cc.getBuffer().length - cc.getEnd());
                    uri.setChars(cc.getBuffer(), cc.getStart(),
                            cc.getLength());
                    return;
                } catch (IOException e) {
                    log.severe("Invalid URI character encoding; trying ascii");
                    cc.recycle();
                }
            }
        }

        // Default encoding: fast conversion
        byte[] bbuf = bc.getBuffer();
        char[] cbuf = cc.getBuffer();
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
     */
    public static boolean normalize(MessageBytes uriMB) {

        int type = uriMB.getType();
        if (type == MessageBytes.T_CHARS) {
            return normalizeChars(uriMB);
        } else {
            return normalizeBytes(uriMB);
        }
    }


    /**
     * Check that the URI is normalized following character decoding.
     * <p>
     * This method checks for "\", 0, "//", "/./" and "/../". This method will
     * return false if sequences that are supposed to be normalized are still 
     * present in the URI.
     * 
     * @param uriMB URI to be checked (should be chars)
     */
    public static boolean checkNormalize(MessageBytes uriMB) {

        CharChunk uriCC = uriMB.getCharChunk();
        char[] c = uriCC.getChars();
        int start = uriCC.getStart();
        int end = uriCC.getEnd();

        int pos = 0;

        // Check for '\' and 0
        for (pos = start; pos < end; pos++) {
            if (c[pos] == '\\') {
                return false;
            }
            if (c[pos] == 0) {
                return false;
            }
        }

        // Check for "//"
        for (pos = start; pos < (end - 1); pos++) {
            if (c[pos] == '/') {
                if (c[pos + 1] == '/') {
                    return false;
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
        if (uriCC.indexOf("/./", 0, 3, 0) >= 0) {
            return false;
        }

        return true;

    }


    /**
      * Check that the URI is normalized following character decoding.
      * <p>
      * This method checks for "\", 0, "//", "/./" and "/../". This method will
      * return false if sequences that are supposed to be normalized are still 
      * present in the URI.
      * 
      * @param uriMB URI to be checked (should be chars)
      */
    private static boolean normalizeBytes(MessageBytes uriMB) {

        ByteChunk uriBC = uriMB.getByteChunk();
        byte[] b = uriBC.getBytes();
        int start = uriBC.getStart();
        int end = uriBC.getEnd();

        // An empty URL is not acceptable
        if (start == end) {
            return false;
        }

        // URL * is acceptable
        if ((end - start == 1) && b[start] == (byte) '*') {
            return true;
        }

        int pos = 0;
        int index = 0;

        // Replace '\' with '/'
        // Check for null byte
        for (pos = start; pos < end; pos++) {
            if (b[pos] == (byte) '\\') {
                if (ALLOW_BACKSLASH) {
                    b[pos] = (byte) '/';
                } else {
                    return false;
                }
            }
            if (b[pos] == (byte) 0) {
                return false;
            }
        }

        // The URL must start with '/'
        if (b[start] != (byte) '/') {
            return false;
        }

        // Replace "//" with "/"
        if (COLLAPSE_ADJACENT_SLASHES) {
            for (pos = start; pos < (end - 1); pos++) {
                if (b[pos] == (byte) '/') {
                    while ((pos + 1 < end) && (b[pos + 1] == (byte) '/')) {
                        copyBytes(b, pos, pos + 1, end - pos - 1);
                        end--;
                    }
                }
            }
        }

        // If the URI ends with "/." or "/..", then we append an extra "/"
        // Note: It is possible to extend the URI by 1 without any side effect
        // as the next character is a non-significant WS.
        if (((end - start) > 2) && (b[end - 1] == (byte) '.')) {
            if ((b[end - 2] == (byte) '/') || ((b[end - 2] == (byte) '.') && (b[end - 3] == (byte) '/'))) {
                b[end] = (byte) '/';
                end++;
            }
        }

        uriBC.setEnd(end);

        index = 0;

        // Resolve occurrences of "/./" in the normalized path
        while (true) {
            index = uriBC.indexOf("/./", 0, 3, index);
            if (index < 0) {
                break;
            }
            copyBytes(b, start + index, start + index + 2,
                    end - start - index - 2);
            end = end - 2;
            uriBC.setEnd(end);
        }

        index = 0;

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            index = uriBC.indexOf("/../", 0, 4, index);
            if (index < 0) {
                break;
            }
            // Prevent from going outside our context
            if (index == 0) {
                return false;
            }
            int index2 = -1;
            for (pos = start + index - 1; (pos >= 0) && (index2 < 0); pos--) {
                if (b[pos] == (byte) '/') {
                    index2 = pos;
                }
            }
            copyBytes(b, start + index2, start + index + 3,
                    end - start - index - 3);
            end = end + index2 - index - 3;
            uriBC.setEnd(end);
            index = index2;
        }

        uriBC.setBytes(b, start, end);

        return true;

    }

    private static boolean normalizeChars(MessageBytes uriMB) {

        CharChunk uriCC = uriMB.getCharChunk();
        char[] c = uriCC.getChars();
        int start = uriCC.getStart();
        int end = uriCC.getEnd();

        // URL * is acceptable
        if ((end - start == 1) && c[start] == (char) '*') {
            return true;
        }

        int pos = 0;
        int index = 0;

        // Replace '\' with '/'
        // Check for null char
        for (pos = start; pos < end; pos++) {
            if (c[pos] == (char) '\\') {
                if (ALLOW_BACKSLASH) {
                    c[pos] = (char) '/';
                } else {
                    return false;
                }
            }
            if (c[pos] == (char) 0) {
                return false;
            }
        }

        // The URL must start with '/'
        if (c[start] != (char) '/') {
            return false;
        }

        // Replace "//" with "/"
        if (COLLAPSE_ADJACENT_SLASHES) {
            for (pos = start; pos < (end - 1); pos++) {
                if (c[pos] == (char) '/') {
                    while ((pos + 1 < end) && (c[pos + 1] == (char) '/')) {
                        copyChars(c, pos, pos + 1, end - pos - 1);
                        end--;
                    }
                }
            }
        }

        // If the URI ends with "/." or "/..", then we append an extra "/"
        // Note: It is possible to extend the URI by 1 without any side effect
        // as the next character is a non-significant WS.
        if (((end - start) > 2) && (c[end - 1] == (char) '.')) {
            if ((c[end - 2] == (char) '/') || ((c[end - 2] == (char) '.') && (c[end - 3] == (char) '/'))) {
                c[end] = (char) '/';
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
                if (c[pos] == (char) '/') {
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
        log.info(message);
    }

    /**
     * Log a message on the Logger associated with our Container (if any)
     *
     * @param message Message to be logged
     * @param throwable Associated exception
     */
    protected void log(String message, Throwable throwable) {
        log.log(Level.SEVERE, message, throwable);
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
}
