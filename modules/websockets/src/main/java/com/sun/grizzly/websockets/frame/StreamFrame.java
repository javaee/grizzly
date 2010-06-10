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
 *
 * @author oleksiys
 */
class StreamFrame extends Frame {
    private enum DecodeState {TYPE, CONTENT, DONE};
    
    private DecodeState decodeState = DecodeState.TYPE;

    public StreamFrame(int type, Buffer buffer) {
        super(type, buffer);
    }

    @Override
    public final boolean isClose() {
        return false;
    }

    @Override
    public Buffer encode() {
        final MemoryManager mm = TransportFactory.getInstance().getDefaultMemoryManager();
        final Buffer startBuffer = mm.allocate(1);
        startBuffer.put(0, (byte) (type & 0xFF));
        
        Buffer resultBuffer = BufferUtils.appendBuffers(mm, startBuffer, buffer);

        final Buffer endBuffer = mm.allocate(1);
        endBuffer.put(0, (byte) 0xFF);

        resultBuffer = BufferUtils.appendBuffers(mm, resultBuffer, endBuffer);
        return resultBuffer;
    }

    @Override
    public DecodeResult decode(Buffer buffer) {
        final MemoryManager mm = TransportFactory.getInstance().getDefaultMemoryManager();

        switch(decodeState) {
            case TYPE: {
                if (!buffer.hasRemaining()) {
                    return DecodeResult.create(false, null);
                }

                type = (buffer.get() & 0xFF);
                decodeState = DecodeState.CONTENT;
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

                    if (startPos - buffer.position() > 1) { // If last buffer had some content, not just 0xFF
                        BufferUtils.setPositionLimit(buffer,
                                startPos, buffer.position() - 1);
                        this.buffer = BufferUtils.appendBuffers(mm,
                                this.buffer, buffer);
                    }

                    buffer = remainder;
                    decodeState = DecodeState.DONE;
                } else {
                    if (startPos != buffer.position()) {
                        buffer.position(startPos);
                        this.buffer = BufferUtils.appendBuffers(mm,
                                this.buffer, buffer);
                    }

                    return DecodeResult.create(false, null);
                }
            }

            case DONE: return DecodeResult.create(true, buffer);

            default: throw new IllegalStateException();
        }
    }
}
