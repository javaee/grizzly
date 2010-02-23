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
package com.sun.grizzly.ssl;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import java.io.IOException;
import java.util.logging.Filter;
import java.util.logging.Logger;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.GrizzlyFuture;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.filterchain.AbstractCodecFilter;
import com.sun.grizzly.memory.BufferUtils;
import com.sun.grizzly.memory.MemoryManager;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

/**
 * SSL {@link Filter} to operate with SSL encrypted data.
 *
 * @author Alexey Stashok
 */
public final class SSLFilter extends AbstractCodecFilter<Buffer, Buffer> {
    private final Attribute<CompletionHandler> handshakeCompletionHandlerAttr;
    private Logger logger = Grizzly.logger(SSLFilter.class);
    private final SSLEngineConfigurator serverSSLEngineConfigurator;
    private final SSLEngineConfigurator clientSSLEngineConfigurator;

    private volatile int dumbVolatile;

    public SSLFilter() {
        this(null, null);
    }

    /**
     * Build <tt>SSLFilter</tt> with the given {@link SSLEngineConfigurator}.
     *
     * @param serverSSLEngineConfigurator SSLEngine configurator for server side connections
     * @param clientSSLEngineConfigurator SSLEngine configurator for client side connections
     */
    public SSLFilter(SSLEngineConfigurator serverSSLEngineConfigurator,
            SSLEngineConfigurator clientSSLEngineConfigurator) {
        super(new SSLDecoderTransformer(), new SSLEncoderTransformer());

        if (serverSSLEngineConfigurator == null) {
            serverSSLEngineConfigurator = new SSLEngineConfigurator(
                    SSLContextConfigurator.DEFAULT_CONFIG.createSSLContext(),
                    false, false, false);
        }

        if (clientSSLEngineConfigurator == null) {
            clientSSLEngineConfigurator = new SSLEngineConfigurator(
                    SSLContextConfigurator.DEFAULT_CONFIG.createSSLContext(),
                    true, false, false);
        }

        this.serverSSLEngineConfigurator = serverSSLEngineConfigurator;
        this.clientSSLEngineConfigurator = clientSSLEngineConfigurator;
        handshakeCompletionHandlerAttr =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                "SSLFilter-HandshakeCompletionHandlerAttr");
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx,
            final NextAction nextAction) throws IOException {
        final Connection connection = ctx.getConnection();
        SSLEngine sslEngine = SSLUtils.getSSLEngine(connection);

        if (sslEngine != null && !SSLUtils.isHandshaking(sslEngine)) {
            return super.handleRead(ctx, nextAction);
        } else {
            if (sslEngine == null) {
                sslEngine = serverSSLEngineConfigurator.createSSLEngine();
                sslEngine.beginHandshake();
                SSLUtils.setSSLEngine(connection, sslEngine);
            }

            Buffer buffer = (Buffer) ctx.getMessage();

            buffer = doHandshakeStep(sslEngine, ctx);

            final boolean isHandshaking = SSLUtils.isHandshaking(sslEngine);
            if (!isHandshaking) {
                notifyHandshakeCompleted(connection, sslEngine);
                
                if (buffer.hasRemaining()) {
                    ctx.setMessage(buffer);
                    return super.handleRead(ctx, nextAction);
                }
            }

            return ctx.getStopAction(buffer.hasRemaining() ? buffer : null);
        }
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx, NextAction nextAction) throws IOException {
        final Connection connection = ctx.getConnection();
        SSLEngine sslEngine = SSLUtils.getSSLEngine(connection);
        if (sslEngine != null && !SSLUtils.isHandshaking(sslEngine)) {
            return super.handleWrite(ctx, nextAction);
        } else {
            throw new IllegalStateException("Handshake is not completed!");
        }
    }

    public void handshake(final Connection connection,
            final CompletionHandler<SSLEngine> completionHandler)
            throws IOException {
        handshake(connection, completionHandler, null,
                clientSSLEngineConfigurator);
    }

    public void handshake(final Connection connection,
            final CompletionHandler<SSLEngine> completionHandler,
            final Object dstAddress)
            throws IOException {
        handshake(connection, completionHandler, dstAddress,
                clientSSLEngineConfigurator);
    }

    public void handshake(final Connection connection,
            final CompletionHandler<SSLEngine> completionHandler,
            final Object dstAddress,
            final SSLEngineConfigurator sslEngineConfigurator)
            throws IOException {
        
        SSLEngine sslEngine = SSLUtils.getSSLEngine(connection);

        if (sslEngine == null) {
            sslEngine = sslEngineConfigurator.createSSLEngine();
            sslEngine.beginHandshake();
            SSLUtils.setSSLEngine(connection, sslEngine);
        } else {
            sslEngineConfigurator.configure(sslEngine);
            sslEngine.beginHandshake();
        }

        if (completionHandler != null) {
            handshakeCompletionHandlerAttr.set(connection, completionHandler);
            dumbVolatile++;
        }

        final FilterChainContext ctx = createContext(connection, IOEvent.WRITE,
                null, completionHandler);

        doHandshakeStep(sslEngine, ctx);
    }

    protected Buffer doHandshakeStep(final SSLEngine sslEngine,
            FilterChainContext context) throws SSLException, IOException {

        final Connection connection = context.getConnection();
        final Object dstAddress = context.getAddress();
        Buffer inputBuffer = (Buffer) context.getMessage();

        final boolean isLoggingFinest = logger.isLoggable(Level.FINEST);

        final SSLSession sslSession = sslEngine.getSession();
        final int packetBufferSize = sslSession.getPacketBufferSize();
        final int appBufferSize = sslSession.getApplicationBufferSize();
        
        HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();

        final MemoryManager memoryManager =
                connection.getTransport().getMemoryManager();

        while (true) {

            if (isLoggingFinest) {
                logger.finest("Loop Engine: " + sslEngine
                        + " handshakeStatus=" + sslEngine.getHandshakeStatus());
            }

            switch (handshakeStatus) {
                case NEED_UNWRAP: {

                    if (isLoggingFinest) {
                        logger.finest("NEED_UNWRAP Engine: " + sslEngine);
                    }

                    if (inputBuffer == null || !inputBuffer.hasRemaining()) {
                        return inputBuffer;
                    }

                    final SSLEngineResult sslEngineResult;

                    if (!inputBuffer.isComposite()) {
                        inputBuffer = reallocTo(memoryManager, inputBuffer,
                            sslSession.getPacketBufferSize());
                    
                        final ByteBuffer inputBB = inputBuffer.toByteBuffer();
                        
                        final Buffer outputBuffer = memoryManager.allocate(
                                appBufferSize);

                        sslEngineResult = sslEngine.unwrap(inputBB,
                                outputBuffer.toByteBuffer());
                        outputBuffer.dispose();
                        
                        if (inputBuffer.hasRemaining()) {
                            // shift remainder to the buffer position 0
                            inputBuffer.compact();
                            // trim
                            inputBuffer.trim();
                        }
                        
                    } else {
                        final Buffer tmpInputBuffer = memoryManager.allocate(
                                packetBufferSize);
                        final int pos = inputBuffer.position();
                        final int lim = inputBuffer.limit();

                        inputBuffer.limit(inputBuffer.position() +
                                Math.min(inputBuffer.remaining(), packetBufferSize));

                        tmpInputBuffer.put(inputBuffer).flip();

                        final ByteBuffer inputByteBuffer = tmpInputBuffer.toByteBuffer();

                        final Buffer outputBuffer = memoryManager.allocate(
                                appBufferSize);

                        sslEngineResult = sslEngine.unwrap(inputByteBuffer,
                                outputBuffer.toByteBuffer());

                        outputBuffer.dispose();
                        BufferUtils.setPositionLimit(inputBuffer,
                                pos + sslEngineResult.bytesConsumed(),
                                lim);
                    }

                    final Status status = sslEngineResult.getStatus();

                    if (status == Status.BUFFER_UNDERFLOW) {
                        return inputBuffer;
                    } else if (status == Status.BUFFER_OVERFLOW) {
                        throw new SSLException("Buffer overflow");
                    }

                    handshakeStatus = sslEngine.getHandshakeStatus();
                    break;
                }

                case NEED_WRAP: {
                    if (isLoggingFinest) {
                        logger.finest("NEED_WRAP Engine: " + sslEngine);
                    }

                    final Buffer buffer = memoryManager.allocate(
                            sslEngine.getSession().getPacketBufferSize());
                    buffer.allowBufferDispose(true);

                    try {
                        final SSLEngineResult result = sslEngine.wrap(
                                BufferUtils.EMPTY_BYTE_BUFFER, buffer.toByteBuffer());

                        buffer.trim();

                        final GrizzlyFuture writeFuture = context.write(dstAddress,
                                buffer, null);
                        writeFuture.markForRecycle(true);

                        handshakeStatus = sslEngine.getHandshakeStatus();
                    } catch (SSLException e) {
                        buffer.dispose();
                        throw e;
                    } catch (IOException e) {
                        buffer.dispose();
                        throw e;
                    } catch (Exception e) {
                        e.printStackTrace();
                        buffer.dispose();
                        throw new IOException("Unexpected exception", e);
                    }

                    break;
                }

                case NEED_TASK: {
                    if (isLoggingFinest) {
                        logger.finest("NEED_TASK Engine: " + sslEngine);
                    }
                    SSLUtils.executeDelegatedTask(sslEngine);
                    handshakeStatus = sslEngine.getHandshakeStatus();
                    break;
                }

                case FINISHED:
                case NOT_HANDSHAKING:
                {
                    return inputBuffer;
                }
            }

            if (handshakeStatus == HandshakeStatus.FINISHED) {
                return inputBuffer;
            }
        }
    }

    private void notifyHandshakeCompleted(final Connection connection,
            final SSLEngine sslEngine) {

        final int dumpVolatileRead = dumbVolatile;
        final CompletionHandler<SSLEngine> completionHandler =
                handshakeCompletionHandlerAttr.get(connection);
        if (completionHandler != null) {
            completionHandler.completed(sslEngine);
        }
    }
    
    private Buffer reallocTo(final MemoryManager memoryManager,
            final Buffer srcBuffer, final int newSize) {

        if (srcBuffer.capacity() >= newSize) return srcBuffer;
        
        final int oldLim = srcBuffer.limit();
        
        srcBuffer.position(srcBuffer.limit());
        final Buffer newBuffer = memoryManager.reallocate(srcBuffer, newSize);

        BufferUtils.setPositionLimit(newBuffer, newSize, newBuffer.capacity());
        newBuffer.trim();
        newBuffer.limit(oldLim);

        return newBuffer;

        
    }
    /**
     * {@inheritDoc}
     */
//    public SSLSupport createSSLSupport(Connection connection) {
//        return new SSLSupportImpl(connection,
//                sslEngineConfigurator, sslHandshaker);
//
//    }
}
