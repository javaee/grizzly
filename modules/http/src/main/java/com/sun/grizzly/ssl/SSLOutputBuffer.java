/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.ssl;

import com.sun.grizzly.async.AsyncQueueDataProcessor;
import com.sun.grizzly.async.AsyncQueueWriteUnit;
import com.sun.grizzly.async.AsyncWriteCallbackHandler;
import com.sun.grizzly.http.Constants;
import com.sun.grizzly.http.SocketChannelOutputBuffer;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.ByteBufferFactory;
import com.sun.grizzly.util.SSLOutputWriter;
import com.sun.grizzly.util.WorkerThread;
import com.sun.grizzly.util.buf.ByteChunk;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Future;
import java.util.logging.Level;
import javax.net.ssl.SSLEngine;


/**
 * Buffer the bytes until the {@link ByteChunk} is full or the request
 * is completed, and then delegate the SSL encryption to class 
 * {@link SSLOutputBuffer}
 * 
 * @author Jean-Francois Arcand
 */
public class SSLOutputBuffer extends SocketChannelOutputBuffer implements SecureOutputBuffer {

    /**
     * {@link AsyncWriteCallbackHandler} implementation, which is responsible
     * for returning cloned ByteBuffers to the pool
     */
    private static final AsyncWriteCallbackHandler asyncHttpWriteCallbackHandler =
            new SSLOutputBuffer.AsyncWriteCallbackHandlerImpl();

    private SSLEngine sslEngine;
    
    /**
     * Alternate constructor.
     */
    public SSLOutputBuffer(Response response, int headerBufferSize, 
                           boolean useSocketBuffer) {
        super(response,headerBufferSize,useSocketBuffer);     
    }

    /**
     * {@inheritDoc}
     */
    public SSLEngine getSSLEngine() {
        return sslEngine;
    }

    /**
     * {@inheritDoc}
     */
    public void setSSLEngine(SSLEngine sslEngine) {
        this.sslEngine = sslEngine;
    }
    
    /**
     * Flush the buffer by looping until the {@link ByteBuffer} is empty
     * using {@link SSLOutputBuffer}
     * @param bb the ByteBuffer to write.
     */   
    @Override
    public void flushChannel(ByteBuffer bb) throws IOException{
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("SSLOutputBuffer.flushChannel isAsyncHttpWriteEnabled=" +
                    isAsyncHttpWriteEnabled + " bb=" + bb);
        }

        boolean isBufferAssociated = true;
        
        // ---------- Obtain secured output ByteBuffer
        ByteBuffer securedBB = null;
        
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof WorkerThread) {
            securedBB = ((WorkerThread) currentThread).getOutputBB();
        }

        int requiredBBSize = sslEngine.getSession().getPacketBufferSize();
        if (securedBB == null || securedBB.capacity() < requiredBBSize) {
            securedBB = bufferPool.poll();

            if (securedBB == null || securedBB.capacity() < requiredBBSize) {
                securedBB = ByteBufferFactory.allocateView(
                       requiredBBSize * 2, securedBB != null && securedBB.isDirect());
            }

            securedBB.limit(securedBB.position());
            isBufferAssociated = associateWithThread(securedBB);
        }
        // ---------- Now we have secured output ByteBuffer

        if (!isAsyncHttpWriteEnabled) {
            SSLOutputWriter.flushChannel((SocketChannel) channel, bb, securedBB, sslEngine);
            if (!isBufferAssociated) { // If secured ByteBuffer is not associated with the current thread - try to return in to the pool
                bufferPool.offer(securedBB);
            } else {
                securedBB.limit(securedBB.position());
            }
        } else if (asyncQueueWriter != null) {
            checkMaxBufferSize(sslEngine);
            
            Future future = asyncQueueWriter.write(selectionKey, bb,
                    asyncHttpWriteCallbackHandler,
                    new SSLWritePreProcessor(sslEngine, securedBB),
                    asyncHttpByteBufferCloner);

            if (!future.isDone()) {
                // Replace outputBB, associated with thread
                ByteBuffer buffer = bufferPool.poll();
                requiredBBSize = sslEngine.getSession().getPacketBufferSize();
                if (buffer != null && buffer.capacity() >= requiredBBSize) {
                    // take one from pool, if it matches
                    buffer.limit(buffer.position());
                    associateWithThread(buffer);
                } else {
                    // creaate new one
                    if (buffer != null) {
                        bufferPool.offer(buffer);
                    }
                    
                    final ByteBuffer newBB = ByteBufferFactory.allocateView(
                            requiredBBSize * 2, securedBB.isDirect());
                    newBB.limit(newBB.position());
                    associateWithThread(newBB);
                }
            } else {
                securedBB.limit(securedBB.position());
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("SSLOutputBuffer.async flushChannel isDone="
                        + future.isDone());
            }

            if (!bb.hasRemaining()) {
                bb.clear();
            }
        } else {
           logger.warning(
                    "HTTPS async write is enabled, but AsyncWriter is null.");
        }
    }

    private void checkMaxBufferSize(SSLEngine sslEngine) {
        int packetBufferSize = sslEngine.getSession().getPacketBufferSize();
        if (packetBufferSize > maxBufferedBytes && maxBufferedBytes == Constants.MAX_BUFFERED_BYTES) {
            maxBufferedBytes = packetBufferSize;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSupportFileSend() {
        return false;
    }

    @Override
    public void recycle() {
        super.recycle();
        sslEngine = null;
    }
    
    private boolean associateWithThread(ByteBuffer buffer) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof WorkerThread) {
            ((WorkerThread) currentThread).setOutputBB(buffer);
            return true;
        }

        return false;
    }
    
    private static class SSLWritePreProcessor implements AsyncQueueDataProcessor {

        private final SSLEngine sslEngine;
        private final ByteBuffer securedOutputBuffer;

        public SSLWritePreProcessor(SSLEngine sslEngine, ByteBuffer securedOutputBuffer) {
            this.sslEngine = sslEngine;
            this.securedOutputBuffer = securedOutputBuffer;
        }

        public ByteBuffer getInternalByteBuffer() {
            return securedOutputBuffer;
        }

        public void process(ByteBuffer byteBuffer) throws IOException {
            if (!byteBuffer.hasRemaining() || securedOutputBuffer.hasRemaining()) {
                return;
            }

            securedOutputBuffer.clear();

            try {
                sslEngine.wrap(byteBuffer, securedOutputBuffer);
                securedOutputBuffer.flip();
            } catch (Exception e) {
                securedOutputBuffer.position(securedOutputBuffer.limit());
                throw new IOException(e.getMessage());
            }
        }
    }

    /**
     * {@link AsyncWriteCallbackHandler} implementation, which is responsible
     * for returning cloned ByteBuffers to the pool
     */
    protected static class AsyncWriteCallbackHandlerImpl
            extends SocketChannelOutputBuffer.AsyncWriteCallbackHandlerImpl {

        @Override
        protected boolean releaseAsyncWriteUnit(AsyncQueueWriteUnit unit) {
            if (unit.isCloned()) {
                SSLWritePreProcessor processor =
                        (SSLWritePreProcessor) unit.getWritePreProcessor();
                returnBuffer(processor.getInternalByteBuffer());
            }

            return super.releaseAsyncWriteUnit(unit);
        }

    }
}
