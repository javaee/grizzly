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
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.utils.Charsets;

/**
 * Utility class.
 *
 * @author Alexey Stashok
 */
public class HttpUtils {
    
    private static final float[] MULTIPLIERS = new float[] {
            .1f, .01f, .001f
    };


    // ---------------------------------------------------------- Public Methods

    public static String composeContentType(final String contentType,
            final String characterEncoding) {
        
        if (characterEncoding == null) {
            return contentType;
        }

        /*
         * Remove the charset param (if any) from the Content-Type
         */
        boolean hasCharset = false;
        int semicolonIndex = -1;
        int index = contentType.indexOf(';');
        while (index != -1) {
            int len = contentType.length();
            semicolonIndex = index;
            index++;
            while (index < len && contentType.charAt(index) == ' ') {
                index++;
            }
            if (index+8 < len
                    && contentType.charAt(index) == 'c'
                    && contentType.charAt(index+1) == 'h'
                    && contentType.charAt(index+2) == 'a'
                    && contentType.charAt(index+3) == 'r'
                    && contentType.charAt(index+4) == 's'
                    && contentType.charAt(index+5) == 'e'
                    && contentType.charAt(index+6) == 't'
                    && contentType.charAt(index+7) == '=') {
                hasCharset = true;
                break;
            }
            index = contentType.indexOf(';', index);
        }

        String newContentType;
        if (hasCharset) { // Some character encoding is specified in content-type
            newContentType = contentType.substring(0, semicolonIndex);
            String tail = contentType.substring(index+8);
            int nextParam = tail.indexOf(';');
            if (nextParam != -1) {
                newContentType += tail.substring(nextParam);
            }
        } else {
            newContentType = contentType;
        }
        
        final StringBuilder sb =
                new StringBuilder(newContentType.length() + characterEncoding.length() + 9);
        return sb.append(newContentType).append(";charset=").append(characterEncoding).toString();
    }
    
    public static float convertQValueToFloat(final DataChunk dc,
                                             final int startIdx,
                                             final int stopIdx) {

        float qvalue = 0f;
        DataChunk.Type type = dc.getType();
        try {
            switch (type) {
                case String: {
                    qvalue = HttpUtils.convertQValueToFloat(dc.toString(), startIdx, stopIdx);
                    break;
                }
                case Buffer: {
                    final BufferChunk bc = dc.getBufferChunk();
                    final int offs = bc.getStart();
                    qvalue = HttpUtils.convertQValueToFloat(bc.getBuffer(), offs + startIdx, offs + stopIdx);
                    break;
                }
                case Chars: {
                    final CharChunk cc = dc.getCharChunk();
                    final int offs = cc.getStart();
                    qvalue = HttpUtils.convertQValueToFloat(cc.getChars(), offs + startIdx, offs + stopIdx);
                }
            }
        } catch (Exception e) {
            qvalue = 0f;
        }
        return qvalue;

    }


    public static float convertQValueToFloat(final Buffer buffer, 
                                             final int startIdx, 
                                             final int stopIdx) {
        float result = 0.0f;
        boolean firstDigitProcessed = false;
        int multIdx = -1;
        for (int i = 0, len = (stopIdx - startIdx); i < len; i++) {
            final char c = (char) buffer.get(i + startIdx);
            if (multIdx == -1) {
                if (firstDigitProcessed && c != '.') {
                    throw new IllegalArgumentException("Invalid qvalue, "
                            + buffer.toStringContent(Constants.DEFAULT_HTTP_CHARSET,
                            startIdx,
                            stopIdx)
                            + ", detected");
                }
                if (c == '.') {
                    multIdx = 0;
                    continue;
                }
            }
            if (Character.isDigit(c)) {
                if (multIdx == -1) {
                    result += Character.digit(c, 10);
                    firstDigitProcessed = true;
                    if (result > 1) {
                        throw new IllegalArgumentException("Invalid qvalue, "
                                + buffer.toStringContent(Constants.DEFAULT_HTTP_CHARSET,
                                                         startIdx,
                                                         stopIdx)
                                + ", detected");
                    }
                } else {
                    if (multIdx >= MULTIPLIERS.length) {
                        throw new IllegalArgumentException("Invalid qvalue, "
                                + buffer.toStringContent(Constants.DEFAULT_HTTP_CHARSET,
                                                         startIdx,
                                                         stopIdx)
                                + ", detected");
                    }
                    result += Character.digit(c, 10) * MULTIPLIERS[multIdx++];
                }
            } else {
                throw new IllegalArgumentException("Invalid qvalue, "
                        + buffer.toStringContent(Constants.DEFAULT_HTTP_CHARSET,
                                                 startIdx,
                                                 stopIdx)
                        + ", detected");
            }
        }
        return result;
    }

    public static float convertQValueToFloat(final String string,
                                             final int startIdx,
                                             final int stopIdx) {
        float result = 0.0f;
        boolean firstDigitProcessed = false;
        int multIdx = -1;
        for (int i = 0, len = (stopIdx - startIdx); i < len; i++) {
            final char c = string.charAt(i + startIdx);
            if (multIdx == -1) {
                if (firstDigitProcessed && c != '.') {
                    throw new IllegalArgumentException("Invalid qvalue, "
                            + new String(string.toCharArray(), startIdx, stopIdx)
                            + ", detected");
                }
                if (c == '.') {
                    multIdx = 0;
                    continue;
                }
            }
            if (Character.isDigit(c)) {
                if (multIdx == -1) {
                    result += Character.digit(c, 10);
                    firstDigitProcessed = true;
                    if (result > 1) {
                        throw new IllegalArgumentException("Invalid qvalue, "
                                + new String(string.toCharArray(), startIdx, stopIdx)
                                + ", detected");
                    }
                } else {
                    if (multIdx >= MULTIPLIERS.length) {
                        throw new IllegalArgumentException("Invalid qvalue, "
                                + new String(string.toCharArray(), startIdx, stopIdx)
                                + ", detected");
                    }
                    result += Character.digit(c, 10) * MULTIPLIERS[multIdx++];
                }
            } else {
                throw new IllegalArgumentException("Invalid qvalue, "
                        + new String(string.toCharArray(), startIdx, stopIdx)
                        + ", detected");
            }
        }
        return result;
    }

    public static float convertQValueToFloat(final char[] chars,
                                                 final int startIdx,
                                                 final int stopIdx) {
            float result = 0.0f;
            boolean firstDigitProcessed = false;
            int multIdx = -1;
            for (int i = 0, len = (stopIdx - startIdx); i < len; i++) {
                final char c = chars[i + startIdx];
                if (multIdx == -1) {
                    if (firstDigitProcessed && c != '.') {
                        throw new IllegalArgumentException("Invalid qvalue, "
                                + new String(chars, startIdx, stopIdx)
                                + ", detected");
                    }
                    if (c == '.') {
                        multIdx = 0;
                        continue;
                    }
                }
                if (Character.isDigit(c)) {
                    if (multIdx == -1) {
                        result += Character.digit(c, 10);
                        firstDigitProcessed = true;
                        if (result > 1) {
                            throw new IllegalArgumentException("Invalid qvalue, "
                                    + new String(chars, startIdx, stopIdx)
                                    + ", detected");
                        }
                    } else {
                        if (multIdx >= MULTIPLIERS.length) {
                            throw new IllegalArgumentException("Invalid qvalue, "
                                    + new String(chars, startIdx, stopIdx)
                                    + ", detected");
                        }
                        result += Character.digit(c, 10) * MULTIPLIERS[multIdx++];
                    }
                } else {
                    throw new IllegalArgumentException("Invalid qvalue, "
                            + new String(chars, startIdx, stopIdx)
                            + ", detected");
                }
            }
            return result;
        }

    /**
     * Converts the specified long as a string representation to the provided byte buffer.
     *
     * This code is based off {@link Long#toString()}
     *
     * @param value the long to convert.
     * @param buffer the buffer to write the conversion result to.
     */
    public static int longToBuffer(long value, final byte[] buffer) {
        int i = buffer.length;
        
        if (value == 0) {
            buffer[--i] = (byte) '0';
            return i;
        }
        
        final int radix = 10;
        final boolean negative;
        if (value < 0) {
            negative = true;
            value = -value;
        } else {
            negative = false;
        }
        
        do {
            final int ch = '0' + (int) (value % radix);
            buffer[--i] = (byte) ch;
        } while ((value /= radix) != 0);
        if (negative) {
            buffer[--i] = (byte) '-';
        }
        return i;
    }

    /**
     * Converts the specified long as a string representation to the provided buffer.
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

    /**
     * Filter non-printable ASCII characters.
     * 
     * @param message
     */
    public static DataChunk filterNonPrintableCharacters(DataChunk message) {

        if (message == null || message.isNull())
            return (null);

        try {
            message.toChars(Charsets.ASCII_CHARSET);
        } catch (CharConversionException ignored) {

        }
        final CharChunk charChunk = message.getCharChunk();
        final char[] content = charChunk.getChars();
        
        final int start = charChunk.getStart();
        final int end = charChunk.getEnd();
        

        for (int i = start; i < end; i++) {
            char c = content[i];
            if ((c <= 31 && c != 9) || c == 127 || c > 255) {
                content[i] = ' ';
            }
        }
        
        return message;
    }
    
    /**
     * Filter the specified message string for characters that are sensitive
     * in HTML.  This avoids potential attacks caused by including JavaScript
     * codes in the request URL that is often reported in error messages.
     *
     * @param message The message string to be filtered
     */
    public static DataChunk filter(DataChunk message) {

        if (message == null || message.isNull())
            return (null);

        try {
            message.toChars(Charsets.ASCII_CHARSET);
        } catch (CharConversionException ignored) {

        }
        
        final CharChunk charChunk = message.getCharChunk();
        final char[] content = charChunk.getChars();

        StringBuilder result = null;
        for (int i = charChunk.getStart(), end = charChunk.getEnd(); i < end; i++) {
            switch (content[i]) {
                case '<':
                    if (result == null) {
                        result = new StringBuilder(content.length + 50);
                        result.append(content, 0, i);
                    }
                    result.append("&lt;");
                    break;
                case '>':
                    if (result == null) {
                        result = new StringBuilder(content.length + 50);
                        result.append(content, 0, i);
                    }
                    result.append("&gt;");
                    break;
                case '&':
                    if (result == null) {
                        result = new StringBuilder(content.length + 50);
                        result.append(content, 0, i);
                    }
                    result.append("&amp;");
                    break;
                case '"':
                    if (result == null) {
                        result = new StringBuilder(content.length + 50);
                        result.append(content, 0, i);
                    }
                    result.append("&quot;");
                    break;
                default:
                    char c = content[i];
                    if ((c <= 31 && c != 9) || c == 127 || c > 255) {
                        if (result == null) {
                            result = new StringBuilder(content.length + 50);
                            result.append(content, 0, i);
                        }
                        result.append("&#").append((int) c).append(';');
                    } else if (result != null) {
                        result.append(c);
                    }

            }
        }
        if (result != null) {
            final int len = result.length();
            final char[] finalResult = new char[len];
            result.getChars(0, len, finalResult, 0);
            message.setChars(finalResult, 0, finalResult.length);
        }
        return message;
    }

    /**
     * Filter the specified message string for characters that are sensitive
     * in HTML.  This avoids potential attacks caused by including JavaScript
     * codes in the request URL that is often reported in error messages.
     *
     * @param message The message string to be filtered
     */
    public static String filter(final String message) {

        if (message == null)
            return (null);

        StringBuilder result = null;
        final int len = message.length();
        
        for (int i = 0; i < len; i++) {
            final char c = message.charAt(i);
            switch (c) {
                case '<':
                    if (result == null) {
                        result = new StringBuilder(len + 50);
                        result.append(message, 0, i);
                    }
                    result.append("&lt;");
                    break;
                case '>':
                    if (result == null) {
                        result = new StringBuilder(len + 50);
                        result.append(message, 0, i);
                    }
                    result.append("&gt;");
                    break;
                case '&':
                    if (result == null) {
                        result = new StringBuilder(len + 50);
                        result.append(message, 0, i);
                    }
                    result.append("&amp;");
                    break;
                case '"':
                    if (result == null) {
                        result = new StringBuilder(len + 50);
                        result.append(message, 0, i);
                    }
                    result.append("&quot;");
                    break;
                default:
                    if ((c <= 31 && c != 9) || c == 127 || c > 255) {
                        if (result == null) {
                            result = new StringBuilder(len + 50);
                            result.append(message, 0, i);
                        }
                        result.append("&#").append((int) c).append(';');
                    } else if (result != null) {
                        result.append(c);
                    }
            }
        }
        
        return result == null
                ? message
                : result.toString();
    }    
}
