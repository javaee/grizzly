/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.websockets.frame;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.NIOTransportBuilder;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * Stream {@link Frame} implementation, which terminates by 0xFF byte.
 *
 * @author Alexey Stashok
 */
class StreamFrame extends Frame {
    // parsing states
    private enum ParseState {TYPE, CONTENT, DONE}

    // last parsing result
    private ParseState parseState = ParseState.TYPE;

    /**
     * Construct a stream {@link Frame}.
     *
     * @param type frame type.
     * @param buffer binary data.
     */
    public StreamFrame(int type, Buffer buffer) {
        super(type, buffer);
    }

    /**
     * Always returns <tt>false</tt> for stream frame.
     * @return always returns <tt>false</tt> for stream frame.
     */
    @Override
    public final boolean isClose() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer serialize() {
        final MemoryManager mm = NIOTransportBuilder.DEFAULT_MEMORY_MANAGER;
        final Buffer startBuffer = mm.allocate(1);
        startBuffer.put(0, (byte) (type & 0xFF));
        
        Buffer resultBuffer = Buffers.appendBuffers(mm, startBuffer, buffer);

        final Buffer endBuffer = mm.allocate(1);
        endBuffer.put(0, (byte) 0xFF);

        resultBuffer = Buffers.appendBuffers(mm, resultBuffer, endBuffer);
        return resultBuffer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ParseResult parse(Buffer buffer) {
        final MemoryManager mm = NIOTransportBuilder.DEFAULT_MEMORY_MANAGER;

        switch(parseState) {
            case TYPE: {
                if (!buffer.hasRemaining()) {
                    return ParseResult.create(false, null);
                }

                type = (buffer.get() & 0xFF);
                parseState = ParseState.CONTENT;
            }

            case CONTENT: {
                int startPos = buffer.position();
                boolean foundEOF = false;

                while(buffer.hasRemaining()) {
                    if ((buffer.get() & 0xFF) == 0xFF) {
                        foundEOF = true;
                        break;
                    }
                }

                if (foundEOF) {
                    Buffer remainder = null;
                    
                    if (buffer.hasRemaining()) {
                        remainder = buffer.slice();
                        buffer.limit(buffer.position());
                    }

                    if (buffer.position() - startPos > 1) { // If last buffer had some content, not just 0xFF
                        Buffers.setPositionLimit(buffer,
                                startPos, buffer.position() - 1);
                        this.buffer = Buffers.appendBuffers(mm,
                                this.buffer, buffer);
                    }

                    buffer = remainder;
                    parseState = ParseState.DONE;
                } else {
                    if (startPos != buffer.position()) {
                        buffer.position(startPos);
                        this.buffer = Buffers.appendBuffers(mm,
                                this.buffer, buffer);
                    }

                    return ParseResult.create(false, null);
                }
            }

            case DONE: return ParseResult.create(true, buffer);

            default: throw new IllegalStateException();
        }
    }
}
