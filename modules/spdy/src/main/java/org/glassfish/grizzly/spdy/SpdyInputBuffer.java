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
package org.glassfish.grizzly.spdy;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.DataStructures;

/**
 *
 * @author oleksiys
 */
final class SpdyInputBuffer {
    private static final Logger LOGGER = Grizzly.logger(SpdyInputBuffer.class);
    
    private static final long NULL_CONTENT_LENGTH = Long.MIN_VALUE;
    
    private static final Buffer LAST_BUFFER = Buffers.wrap(
            MemoryManager.DEFAULT_MEMORY_MANAGER, new byte[] {'l', 'a', 's', 't'});

    private final AtomicInteger inputQueueSize = new AtomicInteger();
    private final BlockingQueue<Buffer> inputQueue =
            DataStructures.getLTQInstance(Buffer.class);
    
    private final AtomicBoolean isInputClosed = new AtomicBoolean();
    private volatile boolean isTerminated;
    
    private final SpdyStream spdyStream;
    private final SpdySession spdySession;
    
    private final AtomicBoolean expectInputSwitch = new AtomicBoolean();
    
    private final AtomicInteger unackedReadBytes  = new AtomicInteger();
    
    private long remainingContentLength = NULL_CONTENT_LENGTH;
    
    SpdyInputBuffer(final SpdyStream spdyStream) {
        this.spdyStream = spdyStream;
        spdySession = spdyStream.getSpdySession();
    }
    
    void onReadEventComplete() {
        if (isTerminated()) {
            return;
        }
        
        expectInputSwitch.set(true);
        
        final int readyBuffersCount = inputQueueSize.get();
        
        if (readyBuffersCount > 0 &&
                expectInputSwitch.getAndSet(false)) {
            passPayloadUpstream(null, false, readyBuffersCount);
        }
    }
    
    void offer(final Buffer data, boolean isLast) {
        if (isInputClosed.get()) {
            throw new IllegalStateException("SpdyStream input is closed");
        }
        
        if (remainingContentLength == NULL_CONTENT_LENGTH) {
            remainingContentLength = spdyStream.getInputHttpHeader().getContentLength();
        }
        
        if (remainingContentLength >= 0) {
            remainingContentLength -= data.remaining();
            if (remainingContentLength == 0) {
                isLast = true;
            } else if (remainingContentLength < 0) {
                // Peer sent more bytes than specified in the content-length
                LOGGER.log(Level.FINE, "SpdyStream #{0} has been terminated: peer sent more data than specified in content-length",
                        spdyStream.getStreamId());
                terminate();
                return;
            }
        }
        
        if (expectInputSwitch.getAndSet(false)) {
            passPayloadUpstream(data, isLast, inputQueueSize.get());
        } else {
            inputQueue.offer(data);
            inputQueueSize.incrementAndGet();
            if (isLast) {
                close();
            }
            
            final int readyBuffersCount = inputQueueSize.get();

            if (readyBuffersCount > 0 &&
                    expectInputSwitch.getAndSet(false)) {
                passPayloadUpstream(null, false, readyBuffersCount);
            }
        }
    }


    private void passPayloadUpstream(final Buffer data, boolean isLast,
            int readyBuffersCount) {
        
        if (isLast) {
            isInputClosed.set(true);
            isTerminated = true;
        }
        
        HttpContent content;
        
        try {
            if (readyBuffersCount == -1) {
                readyBuffersCount = inputQueueSize.get();
            }
            
            Buffer payload = null;
            if (readyBuffersCount > 0) {
                payload = poll();
                assert payload != null;
            }
            
            if (data != null) {
                payload = Buffers.appendBuffers(spdySession.getMemoryManager(),
                        payload, data);
                
                if (isLast) {
                    processFin();
                }
                
                sendWindowUpdate(data);
            } else {
                isLast = isTerminated;
            }
            
            content = HttpContent.builder(spdyStream.getInputHttpHeader())
                    .content(payload)
                    .last(isLast)
                    .build();
            
        } catch (IOException e) {
            content = HttpContent.builder(spdyStream.getInputHttpHeader())
                                .content(Buffers.EMPTY_BUFFER)
                                .last(true)
                                .build();            
        }
        
        spdySession.sendMessageUpstream(spdyStream, content);
    }
    
    Buffer poll() throws IOException {
        if (isTerminated) {
            return Buffers.EMPTY_BUFFER;
        }
        
        Buffer buffer;
        
        final int inputQueueSizeNow = inputQueueSize.getAndSet(0);
        
        if (inputQueueSizeNow <= 0) {
            try {
                buffer = inputQueue.poll(spdySession.getConnection()
                        .getBlockingReadTimeout(TimeUnit.MILLISECONDS),
                        TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new IOException("Blocking read was interrupted");
            }

            if (buffer == null) {
                throw new IOException("Blocking read timeout");
            } else {
                // Due to asynchronous inputQueueSize update - the inputQueueSizeNow may be < 0.
                // It means the inputQueueSize.getAndSet(0); above, may unitentionally increase the counter.
                // So, once we read a Buffer - we have to properly restore the counter value.
                // Normally it had to be inputQueueSize.decremenetAndGet(); , but we have to
                // take into account fact described above.
                inputQueueSize.addAndGet(inputQueueSizeNow - 1);

                if (buffer == LAST_BUFFER) {
                    processFin();
                    buffer = Buffers.EMPTY_BUFFER;
                }
            }
        } else if (inputQueueSizeNow == 1) {
            buffer = inputQueue.poll();
            
            if (buffer == LAST_BUFFER) {
                processFin();
                buffer = Buffers.EMPTY_BUFFER;
            }

        } else {
            final CompositeBuffer compositeBuffer =
                    CompositeBuffer.newBuffer(spdySession.getMemoryManager());

            for (int i = 0; i < inputQueueSizeNow; i++) {
                final Buffer currentBuffer = inputQueue.poll();
                if (currentBuffer == LAST_BUFFER) {
                    processFin();
                    break;
                }

                compositeBuffer.append(currentBuffer);
            }
            compositeBuffer.allowBufferDispose(true);
            compositeBuffer.allowInternalBuffersDispose(true);

            buffer = compositeBuffer;
        }
        sendWindowUpdate(buffer);

        return buffer;
    }
    
    void terminate() {
        isTerminated = true;
        close();
    }
    
    void close() {
        if (isInputClosed.compareAndSet(false, true)) {
            inputQueue.offer(LAST_BUFFER);
            inputQueueSize.incrementAndGet();
        }
    }
    
    boolean isTerminated() {
        return isTerminated;
    }
    
    private void processFin() {
        isTerminated = true;
        spdyStream.getInputHttpHeader().setExpectContent(false);
        
        // NOTIFY STREAM
        spdyStream.onInputClosed();
    }

    private void sendWindowUpdate(final Buffer data) {
        sendWindowUpdate(data, false);
    }
    
    private void sendWindowUpdate(final Buffer data, final boolean isForce) {
        final int currentUnackedBytes =
                unackedReadBytes.addAndGet(data != null ? data.remaining() : 0);
        final int windowSize = spdySession.getLocalInitialWindowSize();
        
        if (currentUnackedBytes > 0 &&
                ((currentUnackedBytes > (windowSize / 2)) || isForce) &&
                unackedReadBytes.compareAndSet(currentUnackedBytes, 0)) {
            
            spdyStream.outputSink.writeWindowUpdate(currentUnackedBytes);
        }
    }
}
