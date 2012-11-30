/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.spdy.frames;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;

import static org.glassfish.grizzly.spdy.Constants.SPDY_VERSION;

public class SynStreamFrame extends SpdyFrame {

    public static final int TYPE = 1;

    private static final Marshaller MARSHALLER = new SynStreamFrameMarshaller();

    protected int streamId;
    protected int associatedToStreamId;
    protected int priority;
    protected int slot;
    protected Buffer compressedHeaders;
    private boolean dispose;


    // ------------------------------------------------------------ Constructors


    public SynStreamFrame(final SpdyHeader header) {
        super(header);
        streamId = header.buffer.getInt() & 0x7FFFFFFF;
        associatedToStreamId = header.buffer.getInt() & 0x7FFFFFFF;
        final int tmpInt = header.buffer.getShort() & 0xFFFF;
        priority = tmpInt >> 13;
        slot = tmpInt & 0xFF;

    }


    public SynStreamFrame() {
    }


    // ---------------------------------------------------------- Public Methods


    public int getStreamId() {
        return streamId;
    }

    public void setStreamId(int streamId) {
        if (header == null) {
            this.streamId = streamId;
        }
    }

    public int getAssociatedToStreamId() {
        return associatedToStreamId;
    }

    public void setAssociatedToStreamId(int associatedToStreamId) {
        if (header == null) {
            this.associatedToStreamId = associatedToStreamId;
        }
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        if (header == null) {
            this.priority = priority;
        }
    }

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        if (header == null) {
            this.slot = slot;
        }
    }

    public Buffer getCompressedHeaders() {
        if (compressedHeaders == null) {
            compressedHeaders = (header.buffer.isComposite())
                                            ? header.buffer.slice()
                                            : header.buffer;
            dispose = header.buffer == compressedHeaders;
        }
        return compressedHeaders;
    }

    public void setCompressedHeaders(Buffer compressedHeaders) {
        if (header == null) {
            this.compressedHeaders = compressedHeaders;
        }
    }

    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        streamId = 0;
        associatedToStreamId = 0;
        priority = 0;
        slot = 0;
        if (dispose) {
            compressedHeaders.dispose();
        }
        compressedHeaders = null;
        super.recycle();
    }


    // -------------------------------------------------- Methods from SpdyFrame


    @Override
    public Marshaller getMarshaller() {
        return MARSHALLER;
    }


    // ---------------------------------------------------------- Nested Classes


    private static final class SynStreamFrameMarshaller implements Marshaller {

        @Override
        public Buffer marshall(final SpdyFrame frame, final MemoryManager memoryManager) {
            SynStreamFrame synStreamFrame = (SynStreamFrame) frame;

            final Buffer frameBuffer = allocateHeapBuffer(memoryManager, 18);

            frameBuffer.putInt(0x80000000 | (SPDY_VERSION << 16) | TYPE);  // C | SPDY_VERSION | SYN_STREAM_FRAME

            final int flags = synStreamFrame.last ? 1 : 0;

            frameBuffer.putInt((flags << 24) | (synStreamFrame.compressedHeaders.remaining() + 10)); // FLAGS | LENGTH
            frameBuffer.putInt(synStreamFrame.streamId & 0x7FFFFFFF); // STREAM_ID
            frameBuffer.putInt(synStreamFrame.associatedToStreamId & 0x7FFFFFFF); // ASSOCIATED_TO_STREAM_ID
            frameBuffer.putShort((short) ((synStreamFrame.priority << 13) | (synStreamFrame.slot & 0xFF))); // PRI | UNUSED | SLOT
            frameBuffer.trim();

            CompositeBuffer cb = CompositeBuffer.newBuffer(memoryManager, frameBuffer);
            cb.append(synStreamFrame.compressedHeaders);
            cb.allowBufferDispose(true);
            cb.allowInternalBuffersDispose(true);
            return cb;
        }

    } // END SynStreamFrameMarshaller

}
