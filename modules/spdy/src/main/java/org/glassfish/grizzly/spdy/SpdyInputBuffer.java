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

import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.HttpBrokenContent;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.utils.DataStructures;

/**
 *
 * @author oleksiys
 */
final class SpdyInputBuffer {
    private static final Logger LOGGER = Grizzly.logger(SpdyInputBuffer.class);
    
    private static final long NULL_CONTENT_LENGTH = Long.MIN_VALUE;
    
    private static final Termination FIN_TERMINATION =
            new Termination(TerminationType.FIN, "Frame with the FIN flag");
    
    private final AtomicInteger inputQueueSize = new AtomicInteger();
    private final BlockingQueue<InputElement> inputQueue =
            DataStructures.getLTQInstance(InputElement.class);
    
    private final AtomicBoolean isInputClosed = new AtomicBoolean();
    private volatile Termination terminationFlag;
    
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
        if (spdyStream.isProcessingComplete ||
                !spdyStream.getInputHttpHeader().isExpectContent()) {
            return;
        }
        
        if (isTerminated()) {
            spdySession.sendMessageUpstream(spdyStream, 
                    buildBrokenHttpContent(new EOFException(terminationFlag.description)));
            
            return;
        }
        
        expectInputSwitch.set(true);
        
        final int readyBuffersCount = inputQueueSize.get();
        
        if (readyBuffersCount > 0 &&
                expectInputSwitch.getAndSet(false)) {
            passPayloadUpstream(null, readyBuffersCount);
        }
    }
    
    void offer(final Buffer data, final boolean isLast) {
        if (isInputClosed.get()) {
            data.tryDispose();
            
            return;
        }
        
        offer0(new InputElement(data,
                isLast | checkContentLength(data.remaining()), false));
    }

    private void offer0(final InputElement inputElement) {
        if (expectInputSwitch.getAndSet(false)) {
            passPayloadUpstream(inputElement, inputQueueSize.get());
        } else {
            inputQueue.offer(inputElement);
            inputQueueSize.incrementAndGet();
            
            final int readyBuffersCount = inputQueueSize.get();

            if (readyBuffersCount > 0 &&
                    expectInputSwitch.getAndSet(false)) {
                passPayloadUpstream(null, readyBuffersCount);
            }
        }
    }

    private void passPayloadUpstream(final InputElement inputElement,
            int readyBuffersCount) {
        
        try {
            if (readyBuffersCount == -1) {
                readyBuffersCount = inputQueueSize.get();
            }
            
            Buffer payload = null;
            if (readyBuffersCount > 0) {
                payload = poll0();
                assert payload != null;
            }
            
            if (inputElement != null) {
                final Buffer data = inputElement.toBuffer();
                if (!inputElement.isService) {
                    payload = Buffers.appendBuffers(spdySession.getMemoryManager(),
                            payload, data);
                    sendWindowUpdate(data);
                } else if (payload == null) {
                    payload = Buffers.EMPTY_BUFFER;
                }
                
                checkEOF(inputElement);
            }
            
            final HttpContent content = buildHttpContent(payload);
            spdySession.sendMessageUpstream(spdyStream, content);
        } catch (IOException e) {
            // Should never be thrown
            LOGGER.log(Level.WARNING, "Unexpected IOException: {0}", e.getMessage());
        }
    }
    
    HttpContent poll() throws IOException {
        return buildHttpContent(poll0());
    }
    
    private Buffer poll0() throws IOException {
        if (isTerminated()) {
            return Buffers.EMPTY_BUFFER;
        }
        
        Buffer buffer;
        InputElement inputElement;
        
        final int inputQueueSizeNow = inputQueueSize.getAndSet(0);
        
        if (inputQueueSizeNow <= 0) {
            try {
                inputElement = inputQueue.poll(spdySession.getConnection()
                        .getBlockingReadTimeout(TimeUnit.MILLISECONDS),
                        TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new IOException("Blocking read was interrupted");
            }

            if (inputElement == null) {
                throw new IOException("Blocking read timeout");
            } else {
                // Due to asynchronous inputQueueSize update - the inputQueueSizeNow may be < 0.
                // It means the inputQueueSize.getAndSet(0); above, may unitentionally increase the counter.
                // So, once we read a Buffer - we have to properly restore the counter value.
                // Normally it had to be inputQueueSize.decremenetAndGet(); , but we have to
                // take into account fact described above.
                inputQueueSize.addAndGet(inputQueueSizeNow - 1);

                checkEOF(inputElement);
                buffer = inputElement.toBuffer();
            }
        } else if (inputQueueSizeNow == 1) {
            inputElement = inputQueue.poll();
            
            checkEOF(inputElement);
            buffer = inputElement.toBuffer();
        } else {
            final CompositeBuffer compositeBuffer =
                    CompositeBuffer.newBuffer(spdySession.getMemoryManager());

            for (int i = 0; i < inputQueueSizeNow; i++) {
                final InputElement currentElement = inputQueue.poll();
                checkEOF(currentElement);
                
                if (!currentElement.isService) {
                    compositeBuffer.append(currentElement.toBuffer());
                }
                
                if (currentElement.isLast) {
                    break;
                }
            }
            compositeBuffer.allowBufferDispose(true);
            compositeBuffer.allowInternalBuffersDispose(true);

            buffer = compositeBuffer;
        }
        
        sendWindowUpdate(buffer);

        return buffer;
    }
    
    void terminate() {
        if (close(terminationFlag)) {
            // Don't wait for Termination input to be polled - assign it right here
            terminationFlag = new Termination(TerminationType.FORCED, "Terminated");
        }
    }
    
    void close() {
        close(spdyStream.closeTypeFlag.get() == CloseType.REMOTELY ?
                new Termination(TerminationType.PEER_CLOSE, "Closed by peer") :
                new Termination(TerminationType.LOCAL_CLOSE, "Closed locally"));
    }
    
    private boolean close(final Termination termination) {
        if (isInputClosed.compareAndSet(false, true)) {
            offer0(new InputElement(termination, true, true));
            return true;
        }
        
        return false;
    }
    
    boolean isTerminated() {
        return terminationFlag != null;
    }
    
    private void checkEOF(final InputElement inputElement) {
        if (inputElement.isLast) {
            if (!inputElement.isService) {
                terminationFlag = FIN_TERMINATION;
            } else {
                terminationFlag = (Termination) inputElement.content;
            }
            
            isInputClosed.set(true);

            // NOTIFY STREAM
            spdyStream.onInputClosed();
        }
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
    
    private boolean checkContentLength(final int newDataChunkSize) {
        if (remainingContentLength == NULL_CONTENT_LENGTH) {
            remainingContentLength = spdyStream.getInputHttpHeader().getContentLength();
        }
        
        if (remainingContentLength >= 0) {
            remainingContentLength -= newDataChunkSize;
            if (remainingContentLength == 0) {
                return true;
            } else if (remainingContentLength < 0) {
                // Peer sent more bytes than specified in the content-length
                throw new IllegalStateException("SpdyStream #" + spdyStream.getStreamId() +
                        ": peer is sending data beyound specified content-length limit");
            }
        }
        
        return false;
    }

    private HttpContent buildHttpContent(final Buffer payload) {
        final Termination localTermination = terminationFlag;
        final boolean isFin = localTermination == FIN_TERMINATION;
        
        final HttpContent httpContent;
        
        if (payload.hasRemaining() || localTermination == null || isFin) {
            spdyStream.getInputHttpHeader().setExpectContent(!isFin);
            httpContent = HttpContent.builder(spdyStream.getInputHttpHeader())
                    .content(payload)
                    .last(isFin)
                    .build();
        } else {
            httpContent = buildBrokenHttpContent(
                    new EOFException(terminationFlag.description));
        }
        
        return httpContent;
    }

    private HttpContent buildBrokenHttpContent(final Throwable t) {
        spdyStream.getInputHttpHeader().setExpectContent(false);
        return HttpBrokenContent.builder(spdyStream.getInputHttpHeader())
                .error(t)
                .build();
    }
    
    private static final class InputElement {
        private final Object content;
        private final boolean isLast;
        
        private final boolean isService;

        public InputElement(final Object content, final boolean isLast,
                final boolean isService) {
            this.content = content;
            this.isLast = isLast;
            this.isService = isService;
        }
        
        private Buffer toBuffer() {
            return !isService ? (Buffer) content : Buffers.EMPTY_BUFFER;
        }
    }
    
    private enum TerminationType {
        FIN, RST, LOCAL_CLOSE, PEER_CLOSE, FORCED
    }
    
    private static class Termination {
        private final TerminationType type;
        private final String description;

        public Termination(final TerminationType type, final String description) {
            this.type = type;
            this.description = description;
        }
    }
}
