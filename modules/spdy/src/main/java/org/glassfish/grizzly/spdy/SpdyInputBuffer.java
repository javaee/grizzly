/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.HttpBrokenContent;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.spdy.SpdyStream.Termination;
import org.glassfish.grizzly.spdy.SpdyStream.TerminationType;
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
    
    // true, if the input is closed
    private final AtomicBoolean isInputClosed = new AtomicBoolean();
    // the termination flag. When is not null contains the reason why input was terminated
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
    
    /**
     * The method will be invoked once upstream completes READ operation processing.
     * Here we have to simulate NIO OP_READ event re-registration.
     */
    void onReadEventComplete() {
        // If SpdyStream processing is complete and we don't expect more content - just return
        if (spdyStream.isProcessingComplete ||
                !spdyStream.getInputHttpHeader().isExpectContent()) {
            return;
        }
        
        // If input stream has been terminated - send error message upstream
        if (isTerminated()) {
            spdySession.sendMessageUpstream(spdyStream, 
                    buildBrokenHttpContent(new EOFException(terminationFlag.getDescription())));
            
            return;
        }
        
        // Switch on the "expect more input" flag
        expectInputSwitch.set(true);
        
        final int readyBuffersCount = inputQueueSize.get();
        
        // If we have more input data to process and "expect more input" flag is still on -
        // pass the data upstream
        if (readyBuffersCount > 0 &&
                expectInputSwitch.getAndSet(false)) {
            passPayloadUpstream(null, readyBuffersCount);
        }
    }
    
    /**
     * The method is called, when new input data arrives.
     */
    void offer(final Buffer data, final boolean isLast) {
        if (isInputClosed.get()) {
            // if input is closed - just ignore the message
            data.tryDispose();
            
            return;
        }
        
        // create InputElement and add it to the input queue
        // we double check if this is the last frame (considering content-length header if any)
        offer0(new InputElement(data,
                isLast | checkContentLength(data.remaining()), false));
    }

    /**
     * The private method, which adds new InputElement to the input queue
     */
    private void offer0(final InputElement inputElement) {
        if (expectInputSwitch.getAndSet(false)) {
            // if "expect more input" switch is on - pass current input queue content upstream
            passPayloadUpstream(inputElement, inputQueueSize.get());
        } else {
            // if "expect more input" switch is off - enqueue the element
            inputQueue.offer(inputElement);
            inputQueueSize.incrementAndGet();
            
            final int readyBuffersCount = inputQueueSize.get();

            // double check if "expect more input" flag is still off
            if (readyBuffersCount > 0 &&
                    expectInputSwitch.getAndSet(false)) {
                // if not - pass the input queue content upstream
                passPayloadUpstream(null, readyBuffersCount);
            }
        }
    }

    /**
     * Sends the available input data upstream.
     * 
     * @param inputElement {@link InputElement} element to be appended to the current input queue content and sent upstream
     * @param readyBuffersCount the current input queue size (-1 if we don't have this information at the moment).
     */
    private void passPayloadUpstream(final InputElement inputElement,
            int readyBuffersCount) {
        
        try {
            if (readyBuffersCount == -1) {
                readyBuffersCount = inputQueueSize.get();
            }
            
            Buffer payload = null;
            if (readyBuffersCount > 0) {
                // if the input queue is not empty - get its elements
                payload = poll0();
                assert payload != null;
            }
            
            if (inputElement != null) {
                // if extra input elemenet is not null - try to append it
                final Buffer data = inputElement.toBuffer();
                if (!inputElement.isService) {
                    // if this is element containing payload
                    // append input queue and extra input element contents
                    payload = Buffers.appendBuffers(spdySession.getMemoryManager(),
                            payload, data);
                    
                    // notify peer that data.remaining() has been read (update window)
                    sendWindowUpdate(data);
                } else if (payload == null) {
                    payload = data;
                }
                
                // check if the extra input element is EOF
                checkEOF(inputElement);
            }
            
            // build HttpContent based on payload
            final HttpContent content = buildHttpContent(payload);
            // send it upstream
            spdySession.sendMessageUpstream(spdyStream, content);
        } catch (IOException e) {
            // Should never be thrown
            LOGGER.log(Level.WARNING, "Unexpected IOException: {0}", e.getMessage());
        }
    }
    
    /**
     * Retrieves available input buffer payload, waiting up to the
     * {@link Connection#getBlockingReadTimeout(java.util.concurrent.TimeUnit)}
     * wait time if necessary for payload to become available.
     * 
     * @throws IOException
     */
    HttpContent poll() throws IOException {
        return buildHttpContent(poll0());
    }
    
    /**
     * Retrieves available input buffer payload, waiting up to the
     * {@link Connection#getBlockingReadTimeout(java.util.concurrent.TimeUnit)}
     * wait time if necessary for payload to become available.
     * 
     * @throws IOException
     */
    private Buffer poll0() throws IOException {
        if (isTerminated()) {
            // if input is terminated - return empty buffer
            return Buffers.EMPTY_BUFFER;
        }
        
        Buffer buffer;
        InputElement inputElement;
        
        // get the current input queue size
        final int inputQueueSizeNow = inputQueueSize.getAndSet(0);
        
        if (inputQueueSizeNow <= 0) {
            // if there is no element available - block
            try {
                inputElement = inputQueue.poll(spdySession.getConnection()
                        .getBlockingReadTimeout(TimeUnit.MILLISECONDS),
                        TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new IOException("Blocking read was interrupted");
            }

            if (inputElement == null) {
                // timeout expired
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
            // if there is one element available
            inputElement = inputQueue.poll();
            
            checkEOF(inputElement);
            buffer = inputElement.toBuffer();
        } else {
            // if there are more than 1 elements available
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
        
        
        // send window_update notification
        sendWindowUpdate(buffer);

        return buffer;
    }
    
    /**
     * Same as {@link #close()}, but sets the terminate flag w/o waiting for
     * consumer to poll Termination input element.
     */
    void terminate() {
        final Termination terminationFlagLocal =
                new Termination(TerminationType.FORCED, "Terminated");
        
        if (close(terminationFlagLocal)) {
            // Don't wait for Termination input to be polled - assign it right here
            this.terminationFlag = terminationFlagLocal;
        }
    }
    
    /**
     * Same as {@link #close()}, but sets the terminate flag w/o waiting for
     * consumer to poll Termination input element.
     */
    void terminate(final Termination terminationFlag) {
        if (close(terminationFlag)) {
            // Don't wait for Termination input to be polled - assign it right here
            this.terminationFlag = terminationFlag;
        }
    }

    /**
     * Marks the input buffer as closed by adding Termination input element to the input queue.
     */
    void close() {
        close(spdyStream.closeTypeFlag.get() == CloseType.REMOTELY ?
                new Termination(TerminationType.PEER_CLOSE, "Closed by peer") :
                new Termination(TerminationType.LOCAL_CLOSE, "Closed locally"));
    }
    
    /**
     * Marks the input buffer as closed by adding Termination input element to the input queue.
     */
    private boolean close(final Termination termination) {
        if (isInputClosed.compareAndSet(false, true)) {
            offer0(new InputElement(termination, true, true));
            return true;
        }
        
        return false;
    }

    /**
     * Returns <tt>true</tt> if the <tt>InputBuffer</tt> has been closed.
     */
    boolean isTerminated() {
        return terminationFlag != null;
    }
    
    /**
     * Checks if the passed InputElement is input buffer EOF element.
     * @param inputElement 
     */
    private void checkEOF(final InputElement inputElement) {
        // first of all it has to be the last element
        if (inputElement.isLast) {
            // create appropriate terminationFlag info
            terminationFlag = !inputElement.isService
                    ? FIN_TERMINATION
                    : (Termination) inputElement.content;
            
            // mark the input buffer as closed
            isInputClosed.set(true);

            // NOTIFY SpdyStream
            spdyStream.onInputClosed();
        }
    }

    /**
     * Sends WINDOW_UPDATE message to the peer based on data, which has been processed.
     */
    private void sendWindowUpdate(final Buffer data) {
        sendWindowUpdate(data, false);
    }
    
    /**
     * Sends WINDOW_UPDATE message to the peer based on data, which has been processed.
     * @param data 
     * @param isForce if <tt>true</tt> the WINDOW_UPDATE message will be sent regardless of the current unacked bytes count.
     */
    private void sendWindowUpdate(final Buffer data, final boolean isForce) {
        final int currentUnackedBytes =
                unackedReadBytes.addAndGet(data != null ? data.remaining() : 0);
        final int windowSize = spdyStream.getLocalWindowSize();
        
        // if not forced - send update window message only in case currentUnackedBytes > windowSize / 2
        if (currentUnackedBytes > 0 &&
                ((currentUnackedBytes > (windowSize / 2)) || isForce) &&
                unackedReadBytes.compareAndSet(currentUnackedBytes, 0)) {
            
            spdyStream.outputSink.writeWindowUpdate(currentUnackedBytes);
        }
    }
    
    /**
     * Based on content-length header (which we may have or may not), double
     * check if the payload we've just got is last.
     * 
     * @param newDataChunkSize the number of bytes we've just got.
     * @return <tt>true</tt> if we don't expect more content, or <tt>false</tt> if
     * we do expect more content or we're not sure because content-length header
     * was not specified by peer.
     */
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

    /**
     * Builds {@link HttpContent} based on passed payload {@link Buffer}.
     * If the payload size is <tt>0</tt> and the input buffer has been terminated -
     * return {@link HttpBrokenContent}.
     */
    private HttpContent buildHttpContent(final Buffer payload) {
        final Termination localTermination = terminationFlag;
        final boolean isFin = localTermination == FIN_TERMINATION;
        
        final HttpContent httpContent;
        
        // if payload size is not 0 or this is FIN payload
        if (payload.hasRemaining() || localTermination == null || isFin) {
            final HttpHeader inputHttpHeader = spdyStream.getInputHttpHeader();
            
            inputHttpHeader.setExpectContent(!isFin);
            httpContent = HttpContent.builder(inputHttpHeader)
                    .content(payload)
                    .last(isFin)
                    .build();
        } else {
            // create broken HttpContent
            httpContent = buildBrokenHttpContent(
                    new EOFException(terminationFlag.getDescription()));
        }
        
        return httpContent;
    }

    private HttpContent buildBrokenHttpContent(final Throwable t) {
        spdyStream.getInputHttpHeader().setExpectContent(false);
        return HttpBrokenContent.builder(spdyStream.getInputHttpHeader())
                .error(t)
                .build();
    }
    
    /**
     * Class represent input queue element
     */
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
}
