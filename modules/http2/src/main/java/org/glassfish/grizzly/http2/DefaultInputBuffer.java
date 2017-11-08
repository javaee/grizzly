/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.http2;

import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.HttpBrokenContent;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;

import static org.glassfish.grizzly.http2.Termination.IN_FIN_TERMINATION;

/**
 *
 * @author oleksiys
 */
class DefaultInputBuffer implements StreamInputBuffer {
    private static final Logger LOGGER = Grizzly.logger(StreamInputBuffer.class);
    
    private static final long NULL_CONTENT_LENGTH = Long.MIN_VALUE;
    
    private static final AtomicIntegerFieldUpdater<DefaultInputBuffer> inputQueueSizeUpdater =
            AtomicIntegerFieldUpdater.newUpdater(DefaultInputBuffer.class, "inputQueueSize");            
    @SuppressWarnings("unused")
    private volatile int inputQueueSize;

    private final BlockingQueue<InputElement> inputQueue = new LinkedTransferQueue<>();

    // true, if the input is closed
    private final AtomicBoolean inputClosed = new AtomicBoolean();
    
    // the termination flag. When is not null contains the reason why input was terminated.
    // when the flag is not null - poll0() will return -1.
    private static final AtomicReferenceFieldUpdater<DefaultInputBuffer, Termination> closeFlagUpdater =
            AtomicReferenceFieldUpdater.newUpdater(DefaultInputBuffer.class, Termination.class, "closeFlag");
    @SuppressWarnings("unused")
    private volatile Termination closeFlag;
    
    private final Object terminateSync = new Object();
    
    private final Http2Stream stream;
    private final Http2Session http2Session;
    
    private final Object expectInputSwitchSync = new Object();
    private boolean expectInputSwitch;
    
    private long remainingContentLength = NULL_CONTENT_LENGTH;
    
    DefaultInputBuffer(final Http2Stream stream) {
        this.stream = stream;
        http2Session = stream.getHttp2Session();
    }
    
    /**
     * The method will be invoked once upstream completes READ operation processing.
     * Here we have to simulate NIO OP_READ event re-registration.
     */
    @Override
    public void onReadEventComplete() {
        // If Http2Stream processing is complete and we don't expect more content - just return
        if (stream.isProcessingComplete ||
                !stream.getInputHttpHeader().isExpectContent()) {
            return;
        }
        
        // If input stream has been terminated - send error message upstream
        if (isClosed()) {
            http2Session.sendMessageUpstream(stream,
                    buildBrokenHttpContent(
                        new EOFException(closeFlag.getDescription())));
            
            return;
        }
        
        // Switch on the "expect more input" flag
        switchOnExpectInput();
        
        // Check if we have more input data to process - try to obtain the
        // expectInputSwitch again and process data
        final int queueSize;
        if ((queueSize = switchOffExpectInputIfQueueNotEmpty()) > 0) {
            passPayloadUpstream(null, queueSize);
        }
    }
    
    /**
     * The method is called, when new input data arrives.
     */
    @Override
    public boolean offer(final Buffer data, final boolean isLast) {
        if (inputClosed.get()) {
            // if input is closed - just ignore the message
            data.tryDispose();
            
            return false;
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "{0}: offer {1} isLast={2}",
                    new Object[]{stream.getId(), data, isLast});
        }
        final boolean isLastData = isLast | checkContentLength(data.remaining());
        
        // create InputElement and add it to the input queue
        // we double check if this is the last frame (considering content-length header if any)
        final InputElement element = new InputElement(data, isLastData, false);
        offer0(element);
        
        if (isLastData) {
            // mark the input buffer as closed
            inputClosed.set(true);
        }
        
        // if the stream had been terminated by this time but the element wasn't
        // read - dispose the buffer and return false
        if (isClosed() && inputQueue.remove(element)) {
            data.tryDispose();
            return false;
        }
        
        return true;
    }

    /**
     * The private method, which adds new InputElement to the input queue
     */
    private void offer0(final InputElement inputElement) {
        if (switchOffExpectInput()) {
            // if "expect more input" switch is on - pass current input queue content upstream
            passPayloadUpstream(inputElement, inputQueueSize);
        } else {
            // if "expect more input" switch is off - enqueue the element
            if (!inputQueue.offer(inputElement)) {
                // Should never happen, but findbugs complains
                throw new IllegalStateException("New element can't be added");
            }

            inputQueueSizeUpdater.incrementAndGet(this);

            final int readyBuffersCount;

            // double check if "expect more input" flag is still off
            if ((readyBuffersCount = switchOffExpectInputIfQueueNotEmpty()) > 0) {
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
                readyBuffersCount = inputQueueSize;
            }
            
            Buffer payload = null;
            if (readyBuffersCount > 0) {
                // if the input queue is not empty - get its elements
                payload = poll0();
                assert payload != null;
            }
            
            if (inputElement != null) {
                // if extra input element is not null - try to append it
                final Buffer data = inputElement.toBuffer();
                if (!inputElement.isService) {
                    // if this is element containing payload
                    // append input queue and extra input element contents
                    payload = Buffers.appendBuffers(http2Session.getMemoryManager(),
                            payload, data);
                    
                    // notify peer that data.remaining() has been read (update window)
                    http2Session.ackConsumedData(stream, bufSz(data));
                } else if (payload == null) {
                    payload = data;
                }
                
                // check if the extra input element is EOF
                checkEOF(inputElement);
            }
            
            // build HttpContent based on payload
            final HttpContent content = buildHttpContent(payload);
            // send it upstream
            http2Session.sendMessageUpstreamWithParseNotify(stream, content);
        } catch (IOException e) {
            // Should never be thrown
            LOGGER.log(Level.WARNING, "Unexpected IOException: {0}", e.getMessage());
        }
    }
    
    /**
     * Retrieves available input buffer payload, waiting up to the
     * {@link Connection#getReadTimeout(java.util.concurrent.TimeUnit)}
     * wait time if necessary for payload to become available.
     * 
     * @throws IOException if an error occurs with the poll operation.
     */
    @Override
    public HttpContent poll() throws IOException {
        return buildHttpContent(poll0());
    }
    
    /**
     * Retrieves available input buffer payload, waiting up to the
     * {@link Connection#getReadTimeout(java.util.concurrent.TimeUnit)}
     * wait time if necessary for payload to become available.
     * 
     * @throws IOException if an error occurs with the poll operation.
     */
    private Buffer poll0() throws IOException {
        if (isClosed()) {
            // if input is terminated - return empty buffer
            return Buffers.EMPTY_BUFFER;
        }
        
        Buffer buffer;
        synchronized (terminateSync) { // most of the time it will be uncontended sync
            InputElement inputElement;

            // get the current input queue size
            final int inputQueueSizeNow = inputQueueSizeUpdater.getAndSet(this, 0);

            if (inputQueueSizeNow <= 0) {
                // if there is no element available - block
                try {
                    inputElement = inputQueue.poll(http2Session.getConnection()
                            .getReadTimeout(TimeUnit.MILLISECONDS),
                            TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new IOException("Blocking read was interrupted");
                }

                if (inputElement == null) {
                    // timeout expired
                    throw new IOException("Blocking read timeout");
                } else {
                    // Due to asynchronous inputQueueSize update - the inputQueueSizeNow may be < 0.
                    // It means the inputQueueSize.getAndSet(0); above, may unintentionally increase the counter.
                    // So, once we read a Buffer - we have to properly restore the counter value.
                    // Normally it had to be inputQueueSize.decrementAndGet(); , but we have to
                    // take into account fact described above.
                    inputQueueSizeUpdater.addAndGet(this, inputQueueSizeNow - 1);

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
                        CompositeBuffer.newBuffer(http2Session.getMemoryManager());

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
        }
        
        // send window_update notification
        http2Session.ackConsumedData(stream, bufSz(buffer));

        return buffer;
    }    

    /**
     * Graceful input buffer close.
     * 
     * Marks the input buffer as closed by adding Termination input element to the input queue.
     */
    @Override
    public void close(final Termination termination) {
        if (inputClosed.compareAndSet(false, true)) {
            final Termination.TerminationType type = termination.getType();
            if (termination.isSessionClosed()) {
                return;
            }
            offer0(new InputElement(termination, true, true));
        }
    }

    /**
     * Forcibly closes the input buffer.
     * 
     * All the buffered data will be discarded.
     */
    @Override
    public void terminate(final Termination termination) {
        final boolean isSet = closeFlagUpdater.compareAndSet(this, null, termination);
        
        if (inputClosed.compareAndSet(false, true)) {
            final Termination.TerminationType type = termination.getType();
            if (!termination.isSessionClosed()) {
                offer0(new InputElement(termination, true, true));
            }
        }
        
        if (isSet) {
            
            int szToRelease = 0;
            synchronized (terminateSync) {
                // remove all elements from the queue,
                // count the data amount, which hasn't been read and
                // release correspondent number of bytes in the session
                // control flow window
                InputElement element;
                
                while ((element = inputQueue.poll()) != null) {
                    if (!element.isService) {
                        final Buffer buffer = element.toBuffer();
                        szToRelease += buffer.remaining();
                        buffer.tryDispose();
                    }
                }
            }
            
            if (szToRelease > 0) {
                http2Session.ackConsumedData(szToRelease);
            }
            
            stream.onInputClosed();
        }
    }
    
    /**
     * Returns <tt>true</tt> if the <tt>InputBuffer</tt> has been closed.
     */
    @Override
    public boolean isClosed() {
        return closeFlag != null;
    }
    
    /**
     * Checks if the passed InputElement is input buffer EOF element.
     * @param inputElement the {@link InputElement} to check EOF status against.
     */
    private void checkEOF(final InputElement inputElement) {
        // first of all it has to be the last element
        if (inputElement.isLast) {
            
            final Termination termination = !inputElement.isService
                                      ? IN_FIN_TERMINATION
                                      : (Termination) inputElement.content;
            
            if (closeFlagUpdater.compareAndSet(this, null, termination)) {

                // Let termination run some logic if needed.
                termination.doTask();

                // NOTIFY Http2Stream
                stream.onInputClosed();
            }
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
            remainingContentLength = stream.getInputHttpHeader().getContentLength();
        }
        
        if (remainingContentLength >= 0) {
            remainingContentLength -= newDataChunkSize;
            if (remainingContentLength == 0) {
                return true;
            } else if (remainingContentLength < 0) {
                // Peer sent more bytes than specified in the content-length
                throw new IllegalStateException("Http2Stream #" + stream.getId() +
                        ": peer is sending data beyond specified content-length limit");
            }
        }
        
        return false;
    }

    private boolean switchOffExpectInput() {
        synchronized (expectInputSwitchSync) {
            if (expectInputSwitch) {
                expectInputSwitch = false;
                return true;
            }
            
            return false;
        }
    }

    private int switchOffExpectInputIfQueueNotEmpty() {
        synchronized (expectInputSwitchSync) {
            final int queueSize;
            if (expectInputSwitch && (queueSize = inputQueueSize) > 0) {
                expectInputSwitch = false;
                return queueSize;
            }
            
            return 0;
        }
    }

    private void switchOnExpectInput() {
        synchronized (expectInputSwitchSync) {
            expectInputSwitch = true;
        }
    }
    
    /**
     * Builds {@link HttpContent} based on passed payload {@link Buffer}.
     * If the payload size is <tt>0</tt> and the input buffer has been terminated -
     * return {@link HttpBrokenContent}.
     */
    private HttpContent buildHttpContent(final Buffer payload) {
        final Termination localTermination = closeFlag;
        final boolean isFin = localTermination == IN_FIN_TERMINATION;
        
        final HttpContent httpContent;
        
        // if payload size is not 0 or this is FIN payload
        if (payload.hasRemaining() || localTermination == null || isFin) {
            final HttpHeader inputHttpHeader = stream.getInputHttpHeader();
            
            inputHttpHeader.setExpectContent(!isFin);
            httpContent = HttpContent.builder(inputHttpHeader)
                    .content(payload)
                    .last(isFin)
                    .build();
        } else {
            // create broken HttpContent
            httpContent = buildBrokenHttpContent(
                    new EOFException(localTermination.getDescription()));
        }
        
        return httpContent;
    }

    private HttpContent buildBrokenHttpContent(final Throwable t) {
        stream.getInputHttpHeader().setExpectContent(false);
        return HttpBrokenContent.builder(stream.getInputHttpHeader())
                .error(t)
                .build();
    }

    private static int bufSz(final Buffer buffer) {
        return buffer != null ? buffer.remaining() : 0;
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
    }}
