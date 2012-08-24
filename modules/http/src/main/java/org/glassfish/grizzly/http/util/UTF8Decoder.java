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

import java.io.CharConversionException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;

/**
 * Moved from ByteChunk - code to convert from UTF8 bytes to chars.
 * Not used in the current tomcat3.3 : the performance gain is not very
 * big if the String is created, only if we avoid that and work only
 * on char[]. Until than, it's better to be safe. ( I tested this code
 * with 2 and 3 bytes chars, and it works fine in xerces )
 * 
 * Cut from xerces' UTF8Reader.copyMultiByteCharData() 
 *
 * @author Costin Manolache
 * @author ( Xml-Xerces )
 */
public final class UTF8Decoder extends B2CConverter {

    /**
     * Default Logger.
     */
    private final static Logger LOGGER = Grizzly.logger(UTF8Decoder.class);

    // may have state !!
    public UTF8Decoder() {
    }

    @Override
    public void recycle() {
    }

    @SuppressWarnings({"deprecation"})
    @Override
    public void convert(ByteChunk mb, CharChunk cb)
            throws IOException {
        int bytesOff = mb.getStart();
        int bytesLen = mb.getLength();
        byte bytes[] = mb.getBytes();

        int j = bytesOff;
        int end = j + bytesLen;

        while (j < end) {
            int b0 = 0xff & bytes[j];

            if ((b0 & 0x80) == 0) {
                cb.append((char) b0);
                j++;
                continue;
            }

            // 2 byte ?
            if (j++ >= end) {
                // ok, just ignore - we could throw exception
                throw new IOException("Conversion error - EOF ");
            }
            int b1 = 0xff & bytes[j];

            // ok, let's the fun begin - we're handling UTF8
            if ((0xe0 & b0) == 0xc0) { // 110yyyyy 10xxxxxx (0x80 to 0x7ff)
                int ch = ((0x1f & b0) << 6) + (0x3f & b1);
                if (debug > 0) {
                    log("Convert " + b0 + ' ' + b1 + ' ' + ch + ((char) ch));
                }

                cb.append((char) ch);
                j++;
                continue;
            }

            if (j++ >= end) {
                return;
            }
            int b2 = 0xff & bytes[j];

            if ((b0 & 0xf0) == 0xe0) {
                if ((b0 == 0xED && b1 >= 0xA0)
                        || (b0 == 0xEF && b1 == 0xBF && b2 >= 0xBE)) {
                    if (debug > 0) {
                        log("Error " + b0 + ' ' + b1 + ' ' + b2);
                    }

                    throw new IOException("Conversion error 2");
                }

                int ch = ((0x0f & b0) << 12) + ((0x3f & b1) << 6) + (0x3f & b2);
                cb.append((char) ch);
                if (debug > 0) {
                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + ch
                            + ((char) ch));
                }
                j++;
                continue;
            }

            if (j++ >= end) {
                return;
            }
            int b3 = 0xff & bytes[j];

            if ((0xf8 & b0) == 0xf0) {
                if (b0 > 0xF4 || (b0 == 0xF4 && b1 >= 0x90)) {
                    if (debug > 0) {
                        log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3);
                    }
                    throw new IOException("Conversion error ");
                }
                int ch = ((0x0f & b0) << 18) + ((0x3f & b1) << 12)
                        + ((0x3f & b2) << 6) + (0x3f & b3);

                if (debug > 0) {
                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3 + ' '
                            + ch + ((char) ch));
                }

                if (ch < 0x10000) {
                    cb.append((char) ch);
                } else {
                    cb.append((char) (((ch - 0x00010000) >> 10)
                            + 0xd800));
                    cb.append((char) (((ch - 0x00010000) & 0x3ff)
                            + 0xdc00));
                }
                j++;
            } else {
                // XXX Throw conversion exception !!!
                if (debug > 0) {
                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3);
                }
                throw new IOException("Conversion error 4");
            }
        }
    }

//    public void convert(BufferChunk bc, CharChunk cc)
//            throws IOException {
//        final int bytesOff = bc.getStart();
//        final int bytesLen = bc.getLength();
//        final Buffer bytes = bc.getBuffer();
//
//        int j = bytesOff;
//        int end = j + bytesLen;
//
//        while (j < end) {
//            int b0 = 0xff & bytes.get(j);
//
//            if ((b0 & 0x80) == 0) {
//                cc.append((char) b0);
//                j++;
//                continue;
//            }
//
//            // 2 byte ?
//            if (j++ >= end) {
//                // ok, just ignore - we could throw exception
//                throw new CharConversionException("Conversion error - EOF ");
//            }
//            int b1 = 0xff & bytes.get(j);
//
//            // ok, let's the fun begin - we're handling UTF8
//            if ((0xe0 & b0) == 0xc0) { // 110yyyyy 10xxxxxx (0x80 to 0x7ff)
//                int ch = ((0x1f & b0) << 6) + (0x3f & b1);
//                if (debug > 0) {
//                    log("Convert " + b0 + ' ' + b1 + ' ' + ch + ((char) ch));
//                }
//
//                cc.append((char) ch);
//                j++;
//                continue;
//            }
//
//            if (j++ >= end) {
//                return;
//            }
//            int b2 = 0xff & bytes.get(j);
//
//            if ((b0 & 0xf0) == 0xe0) {
//                if ((b0 == 0xED && b1 >= 0xA0)
//                        || (b0 == 0xEF && b1 == 0xBF && b2 >= 0xBE)) {
//                    if (debug > 0) {
//                        log("Error " + b0 + ' ' + b1 + ' ' + b2);
//                    }
//
//                    throw new CharConversionException("Conversion error 2");
//                }
//
//                int ch = ((0x0f & b0) << 12) + ((0x3f & b1) << 6) + (0x3f & b2);
//                cc.append((char) ch);
//                if (debug > 0) {
//                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + ch
//                            + ((char) ch));
//                }
//                j++;
//                continue;
//            }
//
//            if (j++ >= end) {
//                return;
//            }
//            int b3 = 0xff & bytes.get(j);
//
//            if ((0xf8 & b0) == 0xf0) {
//                if (b0 > 0xF4 || (b0 == 0xF4 && b1 >= 0x90)) {
//                    if (debug > 0) {
//                        log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3);
//                    }
//                    throw new CharConversionException("Conversion error ");
//                }
//                int ch = ((0x0f & b0) << 18) + ((0x3f & b1) << 12)
//                        + ((0x3f & b2) << 6) + (0x3f & b3);
//
//                if (debug > 0) {
//                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3 + ' '
//                            + ch + ((char) ch));
//                }
//
//                if (ch < 0x10000) {
//                    cc.append((char) ch);
//                } else {
//                    cc.append((char) (((ch - 0x00010000) >> 10)
//                            + 0xd800));
//                    cc.append((char) (((ch - 0x00010000) & 0x3ff)
//                            + 0xdc00));
//                }
//                j++;
//            } else {
//                // XXX Throw conversion exception !!!
//                if (debug > 0) {
//                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3);
//                }
//                throw new CharConversionException("Conversion error 4");
//            }
//        }
//    }
//
//    /**
//     * Converts the {@link BufferChunk} to char[] using UTF8 encoding.
//     * @param bc source {@link BufferChunk}
//     * @param c dest. char array
//     * @param offset initial offset in the dest. char array
//     * @return the "end" offset in the char array. (last char was added at c[end - 1])
//     * @throws IOException
//     */
//    public int convert(final BufferChunk bc, final char[] c, int offset)
//            throws IOException {
//        final int bytesOff = bc.getStart();
//        final int bytesLen = bc.getLength();
//        final Buffer bytes = bc.getBuffer();
//
//        int j = bytesOff;
//        int end = j + bytesLen;
//
//        while (j < end) {
//            int b0 = 0xff & bytes.get(j);
//
//            if ((b0 & 0x80) == 0) {
//                c[offset++] = (char) b0;
//                j++;
//                continue;
//            }
//
//            // 2 byte ?
//            if (j++ >= end) {
//                // ok, just ignore - we could throw exception
//                throw new CharConversionException("Conversion error - EOF ");
//            }
//            int b1 = 0xff & bytes.get(j);
//
//            // ok, let's the fun begin - we're handling UTF8
//            if ((0xe0 & b0) == 0xc0) { // 110yyyyy 10xxxxxx (0x80 to 0x7ff)
//                int ch = ((0x1f & b0) << 6) + (0x3f & b1);
//                if (debug > 0) {
//                    log("Convert " + b0 + ' ' + b1 + ' ' + ch + ((char) ch));
//                }
//
//                c[offset++] = (char) ch;
//                j++;
//                continue;
//            }
//
//            if (j++ >= end) {
//                return offset;
//            }
//            int b2 = 0xff & bytes.get(j);
//
//            if ((b0 & 0xf0) == 0xe0) {
//                if ((b0 == 0xED && b1 >= 0xA0)
//                        || (b0 == 0xEF && b1 == 0xBF && b2 >= 0xBE)) {
//                    if (debug > 0) {
//                        log("Error " + b0 + ' ' + b1 + ' ' + b2);
//                    }
//
//                    throw new CharConversionException("Conversion error 2");
//                }
//
//                int ch = ((0x0f & b0) << 12) + ((0x3f & b1) << 6) + (0x3f & b2);
//                c[offset++] = (char) ch;
//                if (debug > 0) {
//                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + ch
//                            + ((char) ch));
//                }
//                j++;
//                continue;
//            }
//
//            if (j++ >= end) {
//                return offset;
//            }
//            int b3 = 0xff & bytes.get(j);
//
//            if ((0xf8 & b0) == 0xf0) {
//                if (b0 > 0xF4 || (b0 == 0xF4 && b1 >= 0x90)) {
//                    if (debug > 0) {
//                        log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3);
//                    }
//                    throw new CharConversionException("Conversion error ");
//                }
//                int ch = ((0x0f & b0) << 18) + ((0x3f & b1) << 12)
//                        + ((0x3f & b2) << 6) + (0x3f & b3);
//
//                if (debug > 0) {
//                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3 + ' '
//                            + ch + ((char) ch));
//                }
//
//                if (ch < 0x10000) {
//                    c[offset++] = (char) ch;
//                } else {
//                    c[offset++] = (char) (((ch - 0x00010000) >> 10) + 0xd800);
//                    c[offset++] = (char) (((ch - 0x00010000) & 0x3ff) + 0xdc00);
//                }
//                j++;
//            } else {
//                // XXX Throw conversion exception !!!
//                if (debug > 0) {
//                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3);
//                }
//                throw new CharConversionException("Conversion error 4");
//            }
//        }
//        
//        return offset;
//    }

    /**
     * Converts the {@link Buffer} to char[] using UTF8 encoding.
     * @param buffer source {@link Buffer}
     * @param srcOff offset in source {@link Buffer}
     * @param c dest. char array
     * @param dstOff initial offset in the dest. char array
     * @param length number of bytes to convert
     * @return the "end" offset in the char array. (last char was added at c[end - 1])
     * @throws IOException
     */
    public int convert(final Buffer buffer, final int srcOff,
            final char[] c, int dstOff, final int length)
            throws IOException {
//        final int bytesOff = bc.getStart();
//        final int bytesLen = bc.getLength();
//        final Buffer bytes = bc.getBuffer();

        int j = srcOff;
        int end = j + length;

        while (j < end) {
            int b0 = 0xff & buffer.get(j);

            if ((b0 & 0x80) == 0) {
                c[dstOff++] = (char) b0;
                j++;
                continue;
            }

            // 2 byte ?
            if (j++ >= end) {
                // ok, just ignore - we could throw exception
                throw new CharConversionException("Conversion error - EOF ");
            }
            int b1 = 0xff & buffer.get(j);

            // ok, let's the fun begin - we're handling UTF8
            if ((0xe0 & b0) == 0xc0) { // 110yyyyy 10xxxxxx (0x80 to 0x7ff)
                int ch = ((0x1f & b0) << 6) + (0x3f & b1);
                if (debug > 0) {
                    log("Convert " + b0 + ' ' + b1 + ' ' + ch + ((char) ch));
                }

                c[dstOff++] = (char) ch;
                j++;
                continue;
            }

            if (j++ >= end) {
                return dstOff;
            }
            int b2 = 0xff & buffer.get(j);

            if ((b0 & 0xf0) == 0xe0) {
                if ((b0 == 0xED && b1 >= 0xA0)
                        || (b0 == 0xEF && b1 == 0xBF && b2 >= 0xBE)) {
                    if (debug > 0) {
                        log("Error " + b0 + ' ' + b1 + ' ' + b2);
                    }

                    throw new CharConversionException("Conversion error 2");
                }

                int ch = ((0x0f & b0) << 12) + ((0x3f & b1) << 6) + (0x3f & b2);
                c[dstOff++] = (char) ch;
                if (debug > 0) {
                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + ch
                            + ((char) ch));
                }
                j++;
                continue;
            }

            if (j++ >= end) {
                return dstOff;
            }
            int b3 = 0xff & buffer.get(j);

            if ((0xf8 & b0) == 0xf0) {
                if (b0 > 0xF4 || (b0 == 0xF4 && b1 >= 0x90)) {
                    if (debug > 0) {
                        log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3);
                    }
                    throw new CharConversionException("Conversion error ");
                }
                int ch = ((0x0f & b0) << 18) + ((0x3f & b1) << 12)
                        + ((0x3f & b2) << 6) + (0x3f & b3);

                if (debug > 0) {
                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3 + ' '
                            + ch + ((char) ch));
                }

                if (ch < 0x10000) {
                    c[dstOff++] = (char) ch;
                } else {
                    c[dstOff++] = (char) (((ch - 0x00010000) >> 10) + 0xd800);
                    c[dstOff++] = (char) (((ch - 0x00010000) & 0x3ff) + 0xdc00);
                }
                j++;
            } else {
                // XXX Throw conversion exception !!!
                if (debug > 0) {
                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3);
                }
                throw new CharConversionException("Conversion error 4");
            }
        }
        
        return dstOff;
    }

    /**
     * Converts the {@link Buffer} to char[] using UTF8 encoding.
     * @param buffer source {@link Buffer}
     * @param srcOff offset in source {@link Buffer}
     * @param c dest. char array
     * @param dstOff initial offset in the dest. char array
     * @param length number of bytes to convert
     * @return the "end" offset in the char array. (last char was added at c[end - 1])
     * @throws IOException
     */
    public int convert(final byte[] buffer, final int srcOff,
            final char[] c, int dstOff, final int length)
            throws IOException {
//        final int bytesOff = bc.getStart();
//        final int bytesLen = bc.getLength();
//        final Buffer bytes = bc.getBuffer();

        int j = srcOff;
        int end = j + length;

        while (j < end) {
            int b0 = 0xff & buffer[j];

            if ((b0 & 0x80) == 0) {
                c[dstOff++] = (char) b0;
                j++;
                continue;
            }

            // 2 byte ?
            if (j++ >= end) {
                // ok, just ignore - we could throw exception
                throw new CharConversionException("Conversion error - EOF ");
            }
            int b1 = 0xff & buffer[j];

            // ok, let's the fun begin - we're handling UTF8
            if ((0xe0 & b0) == 0xc0) { // 110yyyyy 10xxxxxx (0x80 to 0x7ff)
                int ch = ((0x1f & b0) << 6) + (0x3f & b1);
                if (debug > 0) {
                    log("Convert " + b0 + ' ' + b1 + ' ' + ch + ((char) ch));
                }

                c[dstOff++] = (char) ch;
                j++;
                continue;
            }

            if (j++ >= end) {
                return dstOff;
            }
            int b2 = 0xff & buffer[j];

            if ((b0 & 0xf0) == 0xe0) {
                if ((b0 == 0xED && b1 >= 0xA0)
                        || (b0 == 0xEF && b1 == 0xBF && b2 >= 0xBE)) {
                    if (debug > 0) {
                        log("Error " + b0 + ' ' + b1 + ' ' + b2);
                    }

                    throw new CharConversionException("Conversion error 2");
                }

                int ch = ((0x0f & b0) << 12) + ((0x3f & b1) << 6) + (0x3f & b2);
                c[dstOff++] = (char) ch;
                if (debug > 0) {
                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + ch
                            + ((char) ch));
                }
                j++;
                continue;
            }

            if (j++ >= end) {
                return dstOff;
            }
            int b3 = 0xff & buffer[j];

            if ((0xf8 & b0) == 0xf0) {
                if (b0 > 0xF4 || (b0 == 0xF4 && b1 >= 0x90)) {
                    if (debug > 0) {
                        log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3);
                    }
                    throw new CharConversionException("Conversion error ");
                }
                int ch = ((0x0f & b0) << 18) + ((0x3f & b1) << 12)
                        + ((0x3f & b2) << 6) + (0x3f & b3);

                if (debug > 0) {
                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3 + ' '
                            + ch + ((char) ch));
                }

                if (ch < 0x10000) {
                    c[dstOff++] = (char) ch;
                } else {
                    c[dstOff++] = (char) (((ch - 0x00010000) >> 10) + 0xd800);
                    c[dstOff++] = (char) (((ch - 0x00010000) & 0x3ff) + 0xdc00);
                }
                j++;
            } else {
                // XXX Throw conversion exception !!!
                if (debug > 0) {
                    log("Convert " + b0 + ' ' + b1 + ' ' + b2 + ' ' + b3);
                }
                throw new CharConversionException("Conversion error 4");
            }
        }
        
        return dstOff;
    }

    private static final int debug = 1;

    @Override
    void log(String s) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "UTF8Decoder: {0}", s);
        }
    }
}
