/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
 */

package org.glassfish.grizzly.memory;

import java.nio.ByteBuffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.threadpool.WorkerThread;

/**
 *
 * @author oleksiys
 */
public class DefaultMemoryManager extends ByteBufferViewManager {
    public static final int DEFAULT_MAX_BUFFER_SIZE = 1024 * 64;
    
    private static Attribute<BufferInfo> threadBufferAttribute =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("ThreadBuffer");

    private int maxThreadBufferSize = DEFAULT_MAX_BUFFER_SIZE;

    public int getMaxThreadBufferSize() {
        return maxThreadBufferSize;
    }

    public void setMaxThreadBufferSize(int maxThreadBufferSize) {
        this.maxThreadBufferSize = maxThreadBufferSize;
    }

    @Override
    public ByteBufferWrapper allocate(int size) {
        if (isWorkerThread()) {
            BufferInfo bufferInfo = getThreadBuffer();

            if (bufferInfo == null) {
                return super.allocate(size);
            }

            ByteBuffer byteBuffer = bufferInfo.buffer;
            if (byteBuffer.remaining() >= size) {
                ByteBuffer slicedByteBuffer = slice(byteBuffer, size);
                bufferInfo.lastAllocatedBuffer = slicedByteBuffer;
                return wrap(slicedByteBuffer);
            } else {
                return super.allocate(size);
            }

        } else {
            return super.allocate(size);
        }
    }

    @Override
    public ByteBufferWrapper reallocate(ByteBufferWrapper oldBuffer, int newSize) {
        return super.reallocate(oldBuffer, newSize);
    }

    @Override
    public void release(ByteBufferWrapper buffer) {
        if (isWorkerThread()) {
            ByteBuffer underlyingBuffer = buffer.underlying();
            
            BufferInfo bufferInfo = getThreadBuffer();

            if (prepend(bufferInfo, underlyingBuffer)) return;

            if (bufferInfo == null ||
                    (bufferInfo.buffer.capacity() < underlyingBuffer.remaining() &&
                    bufferInfo.buffer.capacity() < maxThreadBufferSize)) {

                boolean isNewBufferInfo = false;
                if (bufferInfo == null) {
                    bufferInfo = new BufferInfo();
                    isNewBufferInfo = true;
                }
                
                underlyingBuffer.clear();
                bufferInfo.buffer = underlyingBuffer;
                bufferInfo.lastAllocatedBuffer = null;

                if (isNewBufferInfo) {
                    setThreadBuffer(bufferInfo);
                }
                
                return;
            }

        }
        super.release(buffer);
    }

    public int getReadyThreadBufferSize() {
        if (isWorkerThread()) {
            BufferInfo bi = getThreadBuffer();
            if (bi != null) {
                return bi.buffer.remaining();
            }
        }

        return 0;
    }


    @Override
    protected ByteBufferWrapper wrap(ByteBuffer byteBuffer) {
        return new TrimAwareWrapper(this, byteBuffer);
    }


    private BufferInfo getThreadBuffer() {
        if (!isWorkerThread()) return null;

        WorkerThread workerThread = (WorkerThread) Thread.currentThread();
        AttributeHolder holder = workerThread.getAttributes();
        if (holder == null) return null;

        return threadBufferAttribute.get(holder);
    }

    private void setThreadBuffer(BufferInfo bufferInfo) {
        if (!isWorkerThread()) return;

        WorkerThread workerThread = (WorkerThread) Thread.currentThread();
        threadBufferAttribute.set(workerThread.obtainAttributes(), bufferInfo);
    }

    private boolean prepend(BufferInfo bufferInfo, ByteBuffer underlyingBuffer) {
        if (bufferInfo != null &&
                bufferInfo.lastAllocatedBuffer == underlyingBuffer) {
            bufferInfo.lastAllocatedBuffer = null;
            ByteBuffer chunk = bufferInfo.buffer;
            chunk.position(chunk.position() - underlyingBuffer.capacity());
            return true;
        }

        return false;
    }

    private boolean isWorkerThread() {
        return Thread.currentThread() instanceof WorkerThread;
    }

    public class BufferInfo {
        public ByteBuffer buffer;
        public ByteBuffer lastAllocatedBuffer;
    }

    public class TrimAwareWrapper extends ByteBufferWrapper {

        public TrimAwareWrapper(ByteBufferManager memoryManager,
                ByteBuffer underlyingByteBuffer) {
            super(memoryManager, underlyingByteBuffer);
        }

        @Override
        public void trim() {
            if (isWorkerThread()) {
                visible.flip();

                int sizeToReturn = visible.capacity() - visible.limit();
                if (sizeToReturn > 0) {
                    BufferInfo bufferInfo = getThreadBuffer();
                    ByteBuffer originalByteBuffer = visible;
                    visible = visible.slice();
                    if (bufferInfo == null) {
                        bufferInfo = new BufferInfo();
                        bufferInfo.buffer = originalByteBuffer;
                        bufferInfo.buffer.position(originalByteBuffer.limit());
                        bufferInfo.buffer.limit(originalByteBuffer.capacity());
                        bufferInfo.lastAllocatedBuffer = visible;
                        setThreadBuffer(bufferInfo);

                    } else if (bufferInfo.lastAllocatedBuffer == originalByteBuffer) {
                        bufferInfo.buffer.position(
                                bufferInfo.buffer.position() - sizeToReturn);
                    }
                }
            } else {
                super.trim();
            }
        }
    }
}
