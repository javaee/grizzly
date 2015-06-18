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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.http.HttpCodecFilter;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * General HttpCodec utility methods.
 *
 * @author Alexey Stashok
 */
public class HttpCodecUtils {
    static final byte[] EMPTY_ARRAY = new byte[0];
    private static final int[] DEC = HexUtils.getDecBytes();
    
    public static void parseHost(final DataChunk hostDC,
                                 final DataChunk serverNameDC,
                                 final HttpRequestPacket request) {

        if (hostDC == null) {
            // HTTP/1.0
            // Default is what the socket tells us. Overridden if a host is
            // found/parsed
            final Connection connection = request.getConnection();
            request.setServerPort(((InetSocketAddress) connection.getLocalAddress()).getPort());
            final InetAddress localAddress = ((InetSocketAddress) connection.getLocalAddress()).getAddress();
            // Setting the socket-related fields. The adapter doesn't know
            // about socket.
            request.setLocalHost(localAddress.getHostName());
            serverNameDC.setString(localAddress.getHostName());
            return;
        }

        if (hostDC.getType() == DataChunk.Type.Bytes) {
            final ByteChunk valueBC = hostDC.getByteChunk();
            final int valueS = valueBC.getStart();
            final int valueL = valueBC.getEnd() - valueS;
            int colonPos = -1;

            final byte[] valueB = valueBC.getBuffer();
            final boolean ipv6 = (valueB[valueS] == '[');
            boolean bracketClosed = false;
            for (int i = 0; i < valueL; i++) {
                final byte b = valueB[i + valueS];
                if (b == ']') {
                    bracketClosed = true;
                } else if (b == ':') {
                    if (!ipv6 || bracketClosed) {
                        colonPos = i;
                        break;
                    }
                }
            }

            if (colonPos < 0) {
                if (!request.isSecure()) {
                    // 80 - Default HTTTP port
                    request.setServerPort(80);
                } else {
                    // 443 - Default HTTPS port
                    request.setServerPort(443);
                }

                serverNameDC.setBytes(valueB, valueS, valueS + valueL);
            } else {
                serverNameDC.setBytes(valueB, valueS, valueS + colonPos);

                int port = 0;
                int mult = 1;
                for (int i = valueL - 1; i > colonPos; i--) {
                    int charValue = DEC[(int) valueB[i + valueS]];
                    if (charValue == -1) {
                        throw new IllegalStateException(
                                String.format("Host header %s contained a non-decimal value in the port definition.",
                                              hostDC.toString()));
                    }
                    port = port + (charValue * mult);
                    mult = 10 * mult;
                }
                request.setServerPort(port);

            }
        } else {
            final BufferChunk valueBC = hostDC.getBufferChunk();
            final int valueS = valueBC.getStart();
            final int valueL = valueBC.getEnd() - valueS;
            int colonPos = -1;

            final Buffer valueB = valueBC.getBuffer();
            final boolean ipv6 = (valueB.get(valueS) == '[');
            boolean bracketClosed = false;
            for (int i = 0; i < valueL; i++) {
                final byte b = valueB.get(i + valueS);
                if (b == ']') {
                    bracketClosed = true;
                } else if (b == ':') {
                    if (!ipv6 || bracketClosed) {
                        colonPos = i;
                        break;
                    }
                }
            }

            if (colonPos < 0) {
                if (!request.isSecure()) {
                    // 80 - Default HTTTP port
                    request.setServerPort(80);
                } else {
                    // 443 - Default HTTPS port
                    request.setServerPort(443);
                }
                serverNameDC.setBuffer(valueB, valueS, valueS + valueL);
            } else {
                serverNameDC.setBuffer(valueB, valueS, valueS + colonPos);

                int port = 0;
                int mult = 1;
                for (int i = valueL - 1; i > colonPos; i--) {
                    int charValue = DEC[(int) valueB.get(i + valueS)];
                    if (charValue == -1) {
                        // Invalid character
                        throw new IllegalStateException(
                                String.format("Host header %s contained a non-decimal value in the port definition.",
                                              hostDC.toString()));
                    }
                    port = port + (charValue * mult);
                    mult = 10 * mult;
                }
                request.setServerPort(port);

            }
        }
    }
    
    public static int checkEOL(final HttpCodecFilter.HeaderParsingState parsingState, final Buffer input) {
        final int offset = parsingState.offset;
        final int avail = input.limit() - offset;

        final byte b1;
        final byte b2;

        if (avail >= 2) { // if more than 2 bytes available
            final short s = input.getShort(offset);
            b1 = (byte) (s >>> 8);
            b2 = (byte) (s & 0xFF);
        } else if (avail == 1) {  // if one byte available
            b1 = input.get(offset);
            b2 = -1;
        } else {
            return -2;
        }

        return checkCRLF(parsingState, b1, b2);
    }


    public static int checkEOL(final HttpCodecFilter.HeaderParsingState parsingState,
                               final byte[] input, final int end) {
        final int arrayOffs = parsingState.arrayOffset;
        final int offset = arrayOffs + parsingState.offset;
        final int avail = Math.min(parsingState.packetLimit + arrayOffs, end) - offset;

        final byte b1;
        final byte b2;

        if (avail >= 2) { // if more than 2 bytes available
            b1 = input[offset];
            b2 = input[offset + 1];
        } else if (avail == 1) {  // if one byte available
            b1 = input[offset];
            b2 = -1;
        } else {
            return -2;
        }

        return checkCRLF(parsingState, b1, b2);
    }

    public static boolean findEOL(final HttpCodecFilter.HeaderParsingState state, final Buffer input) {
        int offset = state.offset;
        final int limit = Math.min(input.limit(), state.packetLimit);

        while (offset < limit) {
            final byte b = input.get(offset);
            if (b == Constants.CR) {
                state.checkpoint = offset;
            } else if (b == Constants.LF) {
                if (state.checkpoint == -1) {
                    state.checkpoint = offset;
                }

                state.offset = offset + 1;
                return true;
            }

            offset++;
        }

        state.offset = offset;

        return false;
    }

    public static boolean findEOL(final HttpCodecFilter.HeaderParsingState state,
                                  final byte[] input, final int end) {
        final int arrayOffs = state.arrayOffset;
        int offset = arrayOffs + state.offset;

        final int limit = Math.min(end, arrayOffs + state.packetLimit);

        while (offset < limit) {
            final byte b = input[offset];
            if (b == Constants.CR) {
                state.checkpoint = offset - arrayOffs;
            } else if (b == Constants.LF) {
                if (state.checkpoint == -1) {
                    state.checkpoint = offset - arrayOffs;
                }

                state.offset = offset + 1 - arrayOffs;
                return true;
            }

            offset++;
        }

        state.offset = offset - arrayOffs;

        return false;
    }

    public static int findSpace(final Buffer input, int offset,
                                final int packetLimit) {
        final int limit = Math.min(input.limit(), packetLimit);
        while (offset < limit) {
            final byte b = input.get(offset);
            if (isSpaceOrTab(b)) {
                return offset;
            }

            offset++;
        }

        return -1;
    }

    public static int findSpace(final byte[] input, int offset,
                                final int end, final int packetLimit) {
        final int limit = Math.min(end, packetLimit);
        while (offset < limit) {
            final byte b = input[offset];
            if (isSpaceOrTab(b)) {
                return offset;
            }

            offset++;
        }

        return -1;
    }

    public static int skipSpaces(final Buffer input, int offset,
                                 final int packetLimit) {
        final int limit = Math.min(input.limit(), packetLimit);
        while (offset < limit) {
            final byte b = input.get(offset);
            if (isNotSpaceAndTab(b)) {
                return offset;
            }

            offset++;
        }

        return -1;
    }

    public static int skipSpaces(final byte[] input, int offset,
                                 final int end, final int packetLimit) {
        final int limit = Math.min(end, packetLimit);
        while (offset < limit) {
            final byte b = input[offset];
            if (isNotSpaceAndTab(b)) {
                return offset;
            }

            offset++;
        }

        return -1;
    }

    public static int indexOf(final Buffer input, int offset,
                              final byte b, final int packetLimit) {
        final int limit = Math.min(input.limit(), packetLimit);
        while (offset < limit) {
            final byte currentByte = input.get(offset);
            if (currentByte == b) {
                return offset;
            }

            offset++;
        }

        return -1;
    }

    public static Buffer getLongAsBuffer(final MemoryManager memoryManager,
                                         final long length) {
        final Buffer b = memoryManager.allocate(20);
        b.allowBufferDispose(true);
        HttpUtils.longToBuffer(length, b);
        return b;
    }

    public static Buffer put(final MemoryManager memoryManager,
                             Buffer dstBuffer,
                             final byte[] tempBuffer,
                             final DataChunk chunk) {

        if (chunk.isNull()) return dstBuffer;

        if (chunk.getType() == DataChunk.Type.Bytes) {
            final ByteChunk byteChunk = chunk.getByteChunk();
            return put(memoryManager, dstBuffer, byteChunk.getBuffer(),
                    byteChunk.getStart(), byteChunk.getLength());
        } else if (chunk.getType() == DataChunk.Type.Buffer) {
            final BufferChunk bc = chunk.getBufferChunk();
            final int length = bc.getLength();
            dstBuffer = checkAndResizeIfNeeded(memoryManager, dstBuffer, length);

            dstBuffer.put(bc.getBuffer(), bc.getStart(), length);

            return dstBuffer;
        } else {
            return put(memoryManager, dstBuffer, tempBuffer, chunk.toString());
        }
    }


    public static Buffer put(final MemoryManager memoryManager,
                             Buffer dstBuffer,
                             final byte[] tempBuffer,
                             final String s) {
        final int size = s.length();
        dstBuffer = checkAndResizeIfNeeded(memoryManager, dstBuffer, size);

        if (dstBuffer.hasArray()) {
            @SuppressWarnings("MismatchedReadAndWriteOfArray")
            final byte[] array = dstBuffer.array();
            final int arrayOffs = dstBuffer.arrayOffset();
            int pos = arrayOffs + dstBuffer.position();

            // Make sure custom Strings do not contain service symbols
            for (int i = 0; i < size; i++) {
                byte b = (byte) (s.charAt(i));
                array[pos++] = isNonPrintableUsAscii(b) ? Constants.SP : b;
            }

            dstBuffer.position(pos - arrayOffs);
        } else {
            fastAsciiEncode(s, tempBuffer, dstBuffer);
        }

        return dstBuffer;
    }

    public static Buffer put(final MemoryManager memoryManager,
                             Buffer dstBuffer, final byte[] array) {
        return put(memoryManager, dstBuffer, array, 0, array.length);
    }

    public static Buffer put(final MemoryManager memoryManager,
                             Buffer dstBuffer, final byte[] array, final int off, final int len) {

        dstBuffer = checkAndResizeIfNeeded(memoryManager, dstBuffer, len);

        dstBuffer.put(array, off, len);

        return dstBuffer;
    }

    public static Buffer put(final MemoryManager memoryManager,
                             Buffer dstBuffer, final Buffer buffer) {

        final int addSize = buffer.remaining();

        dstBuffer = checkAndResizeIfNeeded(memoryManager, dstBuffer, addSize);

        dstBuffer.put(buffer);

        return dstBuffer;
    }

    public static Buffer put(final MemoryManager memoryManager,
                             Buffer dstBuffer, final byte value) {

        if (!dstBuffer.hasRemaining()) {
            dstBuffer = resizeBuffer(memoryManager, dstBuffer, 1);
        }

        dstBuffer.put(value);

        return dstBuffer;
    }

    @SuppressWarnings({"unchecked"})
    public static Buffer resizeBuffer(final MemoryManager memoryManager,
                                      final Buffer buffer, final int grow) {

        return memoryManager.reallocate(buffer, Math.max(
                buffer.capacity() + grow,
                (buffer.capacity() * 3) / 2 + 1));
    }


    public static boolean isNotSpaceAndTab(final byte b) {
        return (b != Constants.SP && b != Constants.HT);
    }

    public static boolean isSpaceOrTab(final byte b) {
        return (b == Constants.SP || b == Constants.HT);
    }
    
    /**
     * Converts the a {@link CharSequence} to a byte array, eliminating all the
     * unprintable US-ASCII symbols by replacing them with spaces (' ').
     * 
     * @param s {@link CharSequence}
     * @return a converted byte array, where all the char sequence's unprintable
     *         US-ASCII symbols have been replaced with spaces (' ')
     */
    public static byte[] toCheckedByteArray(final CharSequence s) {
        final byte[] array = new byte[s.length()];
        return toCheckedByteArray(s, array, 0);
    }
    
    /**
     * Serializes the passed {@link CharSequence} into a passed byte array starting
     * from a given offset.
     * All the unprintable US-ASCII symbols will be replaced with spaces (' ').
     * 
     * @param s {@link CharSequence}
     * @param dstArray the byte array to be used to convert the CharSequence into
     * @param arrayOffs the offset in the byte array, where the serialization
     *                  will be started
     * @return the passed dstArray
     * 
     * @throws IllegalArgumentException if there is no enough space in the dstArray
     *         to serialize the CharSequence
     */
    public static byte[] toCheckedByteArray(final CharSequence s,
            final byte[] dstArray, final int arrayOffs) {
        if (dstArray == null) {
            throw new NullPointerException();
        }
        
        final int strLen = s.length();
        
        if (arrayOffs + strLen > dstArray.length) {
            throw new IllegalArgumentException("Not enough space in the array");
        }
        
        for (int i = 0; i < strLen; i++) {
            final int c = s.charAt(i);
            dstArray[i + arrayOffs] = isNonPrintableUsAscii(c) ?
                    Constants.SP : (byte) c;
        }
        
        return dstArray;
    }
    
    /**
     * Returns <tt>true</tt> if the passed symbol code represents a non-printable
     * US-ASCII symbol in range [Integer.MIN_VALUE; 9) U (9; 31] U [127; Integer.MAX_VALUE].
     * 
     * @param ub the symbol code to check
     * @return <tt>true</tt> if the passed symbol code represents a non-printable
     *         US-ASCII symbol in range [Integer.MIN_VALUE; 9) U (9; 31] U [127; Integer.MAX_VALUE]
     */
    public static boolean isNonPrintableUsAscii(final int ub) {
        return ((ub <= 31 && ub != 9) || ub >= 127);
    }
    
    private static void fastAsciiEncode(final String s,
                                        byte[] tempBuffer,
                                        final Buffer dstBuffer) {
        int totalLen = s.length();
        if (tempBuffer == null) {
            tempBuffer = new byte[totalLen];
        }
        int count = 0;
        while (count < totalLen) {
            int len = Math.min(totalLen - count, tempBuffer.length);
            for (int i = 0; i < len; i++) {
                int c = s.charAt(count);
                tempBuffer[i] = isNonPrintableUsAscii(c) ? Constants.SP : (byte) c;
                count++;
            }
            dstBuffer.put(tempBuffer, 0, len);
        }
    }

    private static int checkCRLF(HttpCodecFilter.HeaderParsingState parsingState, byte b1, byte b2) {
        if (b1 == Constants.CR) {
            if (b2 == Constants.LF) {
                parsingState.offset += 2;
                return 0;
            } else if (b2 == -1) {
                return -2;
            }
        } else if (b1 == Constants.LF) {
            parsingState.offset++;
            return 0;
        }

        return -1;
    }

    private static Buffer checkAndResizeIfNeeded(MemoryManager memoryManager, Buffer dstBuffer, int length) {
        if (dstBuffer.remaining() < length) {
            dstBuffer = resizeBuffer(memoryManager, dstBuffer, length);
        }
        return dstBuffer;
    }
}
