/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 */

package com.sun.grizzly.websockets.frame;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.memory.BufferUtils;
import com.sun.grizzly.memory.MemoryManager;

/**
 * Fixed-length {@link Frame}, which contains length prefix before the actual payload.
 *
 * @author Alexey Stashok
 */
class FixedLengthFrame extends Frame {

    // parsing states
    private enum ParseState {TYPE, LENGTH, CONTENT, DONE};

    // the length encoding masks
    private static int[][] masks = {{0x70000000, 28}, {0xFE00000, 21},
        {0x1FC000, 14}, {0x3F80, 7}, {0x7F, 0}};

    // last parsing result
    private ParseState parseState = ParseState.TYPE;
    private int parsedLength;
    
    /**
     * Construct a fixed-length {@link Frame}.
     * 
     * @param type frame type.
     * @param buffer binary data.
     */
    public FixedLengthFrame(int type, Buffer buffer) {
        super(type, buffer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isClose() {
        return type == 0xFF && (buffer == null || buffer.remaining() == 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer serialize() {
        final MemoryManager mm = TransportFactory.getInstance().getDefaultMemoryManager();
        final Buffer startBuffer = mm.allocate(6);
        startBuffer.put((byte) (type & 0xFF));

        final int length = buffer != null ? buffer.remaining() : 0;
        encodeLength(length, startBuffer);
        startBuffer.trim();

        Buffer resultBuffer = BufferUtils.appendBuffers(mm, startBuffer, buffer);

        return resultBuffer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ParseResult parse(Buffer buffer) {
        final MemoryManager mm = TransportFactory.getInstance().getDefaultMemoryManager();

        switch(parseState) {
            case TYPE: {
                if (!buffer.hasRemaining()) {
                    return ParseResult.create(false, null);
                }

                type = (buffer.get() & 0xFF);
                parseState = ParseState.LENGTH;
            }

            case LENGTH: {
                while(buffer.hasRemaining()) {
                    int gotByte = (buffer.get() & 0xFF);
                    int sevenBits = (gotByte & 0x7F);

                    parsedLength = (parsedLength << 7) | sevenBits;

                    if (sevenBits == gotByte) { // last length byte
                        parseState = ParseState.CONTENT;
                        break;
                    }
                }

                if (parseState == ParseState.LENGTH) {
                    return ParseResult.create(false, null);
                }
            }

            case CONTENT: {

                Buffer remainder = null;
                final int remainingBytes = parsedLength - this.buffer.remaining();

                if (buffer.remaining() > remainingBytes) {
                    final int remainderLen = buffer.remaining() - remainingBytes;
                    remainder = buffer.slice(buffer.limit() - remainderLen, buffer.limit());
                    buffer.limit(buffer.limit() - remainderLen);
                }

                this.buffer = BufferUtils.appendBuffers(mm, this.buffer, buffer);

                buffer = remainder;
                if (this.buffer.remaining() < parsedLength) {
                    return ParseResult.create(false, null);
                }


                parseState = ParseState.DONE;
            }

            case DONE: return ParseResult.create(true, buffer);

            default: throw new IllegalStateException();
        }
    }

    /**
     * Encode the length prefix.
     * 
     * @param length the length.
     * @param startBuffer target {@link Buffer}.
     */
    private static void encodeLength(int length, Buffer startBuffer) {
        boolean written = false;
        for (int i=0; i<masks.length; i++) {
            int sevenBits = (length & masks[i][0]) >> masks[i][1];

            if (i == (masks.length - 1)) { // last seven bits
                startBuffer.put((byte) sevenBits);
            } else {
                if (written || sevenBits != 0) {
                    written = true;
                    sevenBits |= 0x80;
                    startBuffer.put((byte) sevenBits);
                }
            }

        }
    }
}
