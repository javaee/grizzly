/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.ssl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Connection.CloseListener;
import org.glassfish.grizzly.Connection.CloseType;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Event;
import org.glassfish.grizzly.FileTransfer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.PendingWriteQueueLimitExceededException;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.ByteBufferArray;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;

import static org.glassfish.grizzly.ssl.SSLUtils.*;

/**
 * SSL {@link Filter} to operate with SSL encrypted data.
 *
 * @author Alexey Stashok
 */
public class SSLFilter extends BaseFilter {
    private static final Logger LOGGER = Grizzly.logger(SSLFilter.class);

    private final Attribute<CompletionHandler<SSLEngine>> handshakeCompletionHandlerAttr;
    private final Attribute<FilterChainContext> initiatingContextAttr;
    private final SSLEngineConfigurator serverSSLEngineConfigurator;
    private final SSLEngineConfigurator clientSSLEngineConfigurator;
    private final boolean renegotiateOnClientAuthWant;

    private final ConnectionCloseListener closeListener = new ConnectionCloseListener();
    
    // Max bytes SSLFilter may enqueue
    protected volatile int maxPendingBytes = Integer.MAX_VALUE;

    protected WeakReference<FilterChain> filterChainRef;
    
    protected final Set<HandshakeListener> handshakeListeners =
            Collections.newSetFromMap(new ConcurrentHashMap<HandshakeListener, Boolean>());
    
    // ------------------------------------------------------------ Constructors


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
        this(serverSSLEngineConfigurator, clientSSLEngineConfigurator, true);
    }


    /**
     * Build <tt>SSLFilter</tt> with the given {@link SSLEngineConfigurator}.
     *
     * @param serverSSLEngineConfigurator SSLEngine configurator for server side connections
     * @param clientSSLEngineConfigurator SSLEngine configurator for client side connections
     */
    public SSLFilter(SSLEngineConfigurator serverSSLEngineConfigurator,
                     SSLEngineConfigurator clientSSLEngineConfigurator,
                     boolean renegotiateOnClientAuthWant) {
        
        this.renegotiateOnClientAuthWant = renegotiateOnClientAuthWant;
        if (serverSSLEngineConfigurator == null) {
            this.serverSSLEngineConfigurator = new SSLEngineConfigurator(
                    SSLContextConfigurator.DEFAULT_CONFIG.createSSLContext(),
                    false, false, false);
        } else {
            this.serverSSLEngineConfigurator = serverSSLEngineConfigurator;
        }

        if (clientSSLEngineConfigurator == null) {
            this.clientSSLEngineConfigurator = new SSLEngineConfigurator(
                    SSLContextConfigurator.DEFAULT_CONFIG.createSSLContext(),
                    true, false, false);
        } else {
            this.clientSSLEngineConfigurator = clientSSLEngineConfigurator;
        }

        handshakeCompletionHandlerAttr =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                "SSLFilter-HandshakeCompletionHandlerAttr");
        initiatingContextAttr =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                     "SSLFilter-HandshakingInitiatingContextAttr"
                );
    }

    @Override
    public void onFilterChainConstructed(final FilterChain filterChain) {
        super.onFilterChainConstructed(filterChain);
        filterChainRef = new WeakReference<FilterChain>(filterChain);
    }

    public void addHandshakeListener(final HandshakeListener listener) {
        handshakeListeners.add(listener);
    }
    
    public void removeHandshakeListener(final HandshakeListener listener) {
        handshakeListeners.remove(listener);
    }
    
    // ----------------------------------------------------- Methods from Filter


    @Override
    public NextAction handleEvent(FilterChainContext ctx, Event event) throws IOException {
        if (event.type() == CertificateEvent.TYPE) {
            final CertificateEvent ce = (CertificateEvent) event;
            ce.certs = getPeerCertificateChain(getSSLEngine(ctx.getConnection()),
                                               ctx,
                                               ce.needClientAuth);
            return ctx.getStopAction();
        }
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx)
    throws IOException {
        final Connection connection = ctx.getConnection();
        SSLEngine sslEngine = getSSLEngine(connection);

        if (sslEngine != null && !isHandshaking(sslEngine)) {
            return decode(ctx);
        } else {
            if (sslEngine == null) {
                sslEngine = serverSSLEngineConfigurator.createSSLEngine();
                sslEngine.beginHandshake();
                setSSLEngine(connection, sslEngine);
                notifyHandshakeStart(connection);
            }

            final Buffer buffer = doHandshakeStep(sslEngine, ctx, (Buffer) ctx.getMessage());

            final boolean hasRemaining = buffer.hasRemaining();
            
            final boolean isHandshaking = isHandshaking(sslEngine);
            if (!isHandshaking) {
                notifyHandshakeComplete(connection, sslEngine);

                if (hasRemaining) {
                    ctx.setMessage(buffer);
                    return decode(ctx);
                }
            }

            return ctx.getStopAction(hasRemaining ? buffer : null);
        }
    }

    @Override
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();

        if (ctx.getMessage() instanceof FileTransfer) {
            throw new IllegalStateException("TLS operations not supported with SendFile messages");
        }

        synchronized (connection) {
            final SSLEngine sslEngine = getSSLEngine(connection);
            if (sslEngine != null && !isHandshaking(sslEngine)) {
                return accurateWrite(ctx, true);
            } else {
                if (sslEngine == null) {
                    initiatingContextAttr.set(connection, ctx);
                    handshake(connection,
                            new PendingWriteCompletionHandler(connection),
                            null, clientSSLEngineConfigurator, ctx);
                }

                return accurateWrite(ctx, false);
            }
        }
    }


    // ---------------------------------------------------------- Public Methods

    /**
     * @return the maximum number of bytes that may be queued to be written
     *  to a particular {@link Connection}.
     * This value is related to the situation when we try to send application
     * data before SSL handshake completes, so the data should be stored and
     * sent on wire once handshake will be completed.
     */
    public int getMaxPendingBytesPerConnection() {
        return maxPendingBytes;
    }

    /**
     * Configures the maximum number of bytes that may be queued to be written
     * for a particular {@link Connection}.
     * This value is related to the situation when we try to send application
     * data before SSL handshake completes, so the data should be stored and
     * sent on wire once handshake will be completed.
     *
     * @param maxPendingBytes maximum number of bytes that may be queued to be
     *  written for a particular {@link Connection}
     */
    public void setMaxPendingBytesPerConnection(final int maxPendingBytes) {
        this.maxPendingBytes = maxPendingBytes;
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
        // Try to find corresponding FilterChain
        
        // 1) Check the connection processor
        FilterChain filterChain = null;
        int idx = -1;
        if (connection.getProcessor() instanceof FilterChain) {
            filterChain = (FilterChain) connection.getProcessor();
            idx = filterChain.indexOf(this);
        }
        
        
        if (idx == -1 && filterChainRef != null) {
            filterChain = filterChainRef.get();
            if (filterChain != null) {
                idx = filterChain.indexOf(this);
            }
        }
        
        if (idx == -1) {
            throw new IllegalStateException("Can't construct FilterChainContext");
        }
        
        final FilterChainContext context = filterChain.obtainFilterChainContext(
                connection, idx, 0, idx);
        handshake(connection, completionHandler, dstAddress,
                sslEngineConfigurator, context);
    }

    protected void handshake(final Connection connection,
                          final CompletionHandler<SSLEngine> completionHandler,
                          final Object dstAddress,
                          final SSLEngineConfigurator sslEngineConfigurator,
                          final FilterChainContext context)
    throws IOException {
        SSLEngine sslEngine = getSSLEngine(connection);

        if (sslEngine == null) {
            sslEngine = sslEngineConfigurator.createSSLEngine();
            sslEngine.beginHandshake();
            setSSLEngine(connection, sslEngine);
        } else {
            sslEngineConfigurator.configure(sslEngine);
            sslEngine.beginHandshake();
        }

        notifyHandshakeStart(connection);

        if (completionHandler != null) {
            handshakeCompletionHandlerAttr.set(connection, completionHandler);
            connection.addCloseListener(closeListener);
        }

        doHandshakeStep(sslEngine, context, null);
    }

    // ------------------------------------------------------- Protected Methods

    protected NextAction decode(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        final Buffer input = ctx.getMessage();
        
        final SSLEngine sslEngine = getSSLEngine(connection);
        if (sslEngine == null) {
            throw new IllegalStateException("SSL handshake is not complete");
        }

        final int expectedLength;
        try {
            expectedLength = getSSLPacketSize(input);
            if (expectedLength == -1 || input.remaining() < expectedLength) {
                return ctx.getStopAction(input);
            }
        } catch (SSLException e) {
            throw new IllegalStateException(e);
        }
        
        final Buffer targetBuffer = allowDispose(ctx.getMemoryManager().allocate(
                    sslEngine.getSession().getApplicationBufferSize()));

        try {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "SSLDecoder engine: {0} input: {1} output: {2}",
                        new Object[]{sslEngine, input, targetBuffer});
            }

            final int pos = input.position();
            final SSLEngineResult sslEngineResult;
            if (!input.isComposite()) {
                sslEngineResult = sslEngine.unwrap(input.toByteBuffer(),
                        targetBuffer.toByteBuffer());
            } else {
                final ByteBuffer originalByteBuffer =
                        input.toByteBuffer(pos,
                        pos + expectedLength);

                sslEngineResult = sslEngine.unwrap(originalByteBuffer,
                        targetBuffer.toByteBuffer());
            }
            
            input.position(pos + sslEngineResult.bytesConsumed());
            targetBuffer.position(sslEngineResult.bytesProduced());


            final SSLEngineResult.Status status = sslEngineResult.getStatus();

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "SSLDecoderr done engine: {0} result: {1} input: {2} output: {3}",
                        new Object[]{sslEngine, sslEngineResult, input, targetBuffer});
            }

            if (status == SSLEngineResult.Status.OK) {
                targetBuffer.trim();

                ctx.setMessage(targetBuffer);
                
                return ctx.getInvokeAction(extractInputRemainder(input));
            } else if (status == SSLEngineResult.Status.CLOSED) {
                targetBuffer.dispose();
                ctx.setMessage(Buffers.EMPTY_BUFFER);
                
                return ctx.getInvokeAction(extractInputRemainder(input));
            } else {
                targetBuffer.dispose();

                if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    return ctx.getStopAction(input);
    
                } else /*if (status == SSLEngineResult.Status.BUFFER_OVERFLOW)*/ {
                    throw new IllegalStateException("Buffer overflow during unwrap operation");
                }
            }
        } catch (SSLException e) {
            targetBuffer.dispose();
            throw new IllegalStateException(e);
        }
    }    

    protected NextAction encode(final FilterChainContext ctx) {
        final Connection connection = ctx.getConnection();
        final Buffer input = ctx.getMessage();
        
        final SSLEngine sslEngine = getSSLEngine(connection);
        if (sslEngine == null) {
            throw new IllegalStateException("SSL handshake is not complete");
        }

        synchronized (connection) {   // synchronize parallel writers here

            Buffer targetBuffer = null;
            Buffer currentTargetBuffer;

            final MemoryManager memoryManager = ctx.getMemoryManager();
            final ByteBufferArray originalByteBufferArray =
                    input.toByteBufferArray();
            boolean restore = false;
            IllegalStateException error = null;
                    
            for (int i = 0; i < originalByteBufferArray.size(); i++) {
                final int pos = input.position();
                final ByteBuffer originalByteBuffer = originalByteBufferArray.getArray()[i];

                currentTargetBuffer = allowDispose(memoryManager.allocate(
                        sslEngine.getSession().getPacketBufferSize()));

                final ByteBuffer currentTargetByteBuffer =
                        currentTargetBuffer.toByteBuffer();

                try {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "SSLEncoder engine: {0} input: {1} output: {2}",
                                new Object[]{sslEngine, originalByteBuffer, currentTargetByteBuffer});
                    }

                    final SSLEngineResult sslEngineResult =
                            sslEngine.wrap(originalByteBuffer,
                            currentTargetByteBuffer);

                    // If the position of the original message hasn't changed,
                    // update the position now.
                    if (pos == input.position()) {
                        restore = true;
                        input.position(pos + sslEngineResult.bytesConsumed());
                    }

                    final SSLEngineResult.Status status = sslEngineResult.getStatus();

                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "SSLEncoder done engine: {0} result: {1} input: {2} output: {3}",
                                new Object[]{sslEngine, sslEngineResult, originalByteBuffer, currentTargetByteBuffer});
                    }
                    
                    if (status == SSLEngineResult.Status.OK) {
                        currentTargetBuffer.position(sslEngineResult.bytesProduced());
                        currentTargetBuffer.trim();
                        targetBuffer = Buffers.appendBuffers(memoryManager,
                                targetBuffer, currentTargetBuffer);

                    } else if (status == SSLEngineResult.Status.CLOSED) {
                        error = new IllegalStateException("SSLEngine is CLOSED");
                    } else if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        error = new IllegalStateException(
                                "Buffer underflow during wrap operation");
                    } else if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        error = new IllegalStateException(
                                "Buffer overflow during wrap operation");
                    }
                } catch (SSLException e) {
                    error = new IllegalStateException(e);
                }

                if (error != null) {
                    disposeBuffers(currentTargetBuffer, targetBuffer);
                    originalByteBufferArray.restore();
                    throw error;
                }
                
                if (originalByteBuffer.hasRemaining()) { // Keep working with the current source ByteBuffer
                    i--;
                }
            }
            
            assert !input.hasRemaining();

            if (restore) {
                originalByteBufferArray.restore();
            }
            originalByteBufferArray.recycle();

            input.tryDispose();
            
            ctx.setMessage(allowDispose(targetBuffer));
            return ctx.getInvokeAction();
        }
    }

    protected Buffer doHandshakeStep(final SSLEngine sslEngine,
                                     final FilterChainContext context,
                                     final Buffer inputBuffer)
    throws IOException {

        final Connection connection = context.getConnection();
        final Object dstAddress = context.getAddress();

        final boolean isLoggingFinest = LOGGER.isLoggable(Level.FINEST);
        try {
            synchronized (connection) {

                HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();

                while (true) {

                    if (isLoggingFinest) {
                        LOGGER.log(Level.FINEST, "Loop Engine: {0} handshakeStatus={1}",
                                new Object[]{sslEngine, sslEngine.getHandshakeStatus()});
                    }

                    switch (handshakeStatus) {
                        case NEED_UNWRAP: {

                            if (isLoggingFinest) {
                                LOGGER.log(Level.FINEST, "NEED_UNWRAP Engine: {0}", sslEngine);
                            }

                            if (inputBuffer == null || !inputBuffer.hasRemaining()) {
                                return inputBuffer;
                            }

                            final SSLEngineResult sslEngineResult =
                                    handshakeUnwrap(connection, sslEngine, inputBuffer);

                            inputBuffer.shrink();
                            
                            if (sslEngineResult == null) {
                                return inputBuffer;
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
                                LOGGER.log(Level.FINEST, "NEED_WRAP Engine: {0}", sslEngine);
                            }

                            final Buffer buffer = handshakeWrap(connection, sslEngine);

                            try {
                                context.write(dstAddress, buffer, null);

                                handshakeStatus = sslEngine.getHandshakeStatus();
                            } catch (Exception e) {
                                buffer.dispose();
                                throw new IOException("Unexpected exception", e);
                            }

                            break;
                        }

                        case NEED_TASK: {
                            if (isLoggingFinest) {
                                LOGGER.log(Level.FINEST, "NEED_TASK Engine: {0}", sslEngine);
                            }
                            executeDelegatedTask(sslEngine);
                            handshakeStatus = sslEngine.getHandshakeStatus();
                            break;
                        }

                        case FINISHED:
                        case NOT_HANDSHAKING: {
                            return inputBuffer;
                        }
                    }

                    if (handshakeStatus == HandshakeStatus.FINISHED) {
                        initiatingContextAttr.remove(connection);
                        return inputBuffer;
                    }
                }
            }
        } catch (IOException ioe) {
            final FilterChainContext ictx = initiatingContextAttr.get(connection);
            try {
                if (ictx != null) {
                    ictx.getFilterChain().fail(ictx, ioe);
                }
            } finally {
                initiatingContextAttr.remove(connection);
            }
            throw ioe;
        }
    }


    /**
     * Performs an SSL renegotiation.
     *
     * @param sslEngine the {@link SSLEngine} associated with this
     *  this renegotiation request.
     * @param context the {@link FilterChainContext} associated with this
     *  this renegotiation request.
     *
     * @throws IOException if an error occurs during SSL renegotiation.
     */
    protected void renegotiate(final SSLEngine sslEngine,
                               final FilterChainContext context)
                               throws IOException {

        if (sslEngine.getWantClientAuth() && !renegotiateOnClientAuthWant) {
            return;
        }
        final boolean authConfigured =
                (sslEngine.getWantClientAuth()
                        || sslEngine.getNeedClientAuth());
        if (!authConfigured) {
            sslEngine.setNeedClientAuth(true);
        }
        final Connection c = context.getConnection();
        sslEngine.getSession().invalidate();
        try {
                sslEngine.beginHandshake();
                notifyHandshakeStart(c);
            } catch (SSLHandshakeException e) {
                // If we catch SSLHandshakeException at this point it may be due
                // to an older SSL peer that hasn't made its SSL/TLS renegotiation
                // secure.  This will be the case with Oracle's VM older than
                // 1.6.0_22 or native applications using OpenSSL libraries
                // older than 0.9.8m.
                //
                // What we're trying to accomplish here is an attempt to detect
                // this issue and log a useful message for the end user instead
                // of an obscure exception stack trace in the server's log.
                // Note that this probably will only work on Oracle's VM.
                if (e.toString().toLowerCase().contains("insecure renegotiation")) {
                    if (LOGGER.isLoggable(Level.SEVERE)) {
                        LOGGER.severe("Secure SSL/TLS renegotiation is not " +
                                "supported by the peer.  This is most likely due" +
                                " to the peer using an older SSL/TLS " +
                                "implementation that does not implement RFC 5746.");
                    }
                    // we could return null here and let the caller
                    // decided what to do, but since the SSLEngine will
                    // close the channel making further actions useless,
                    // we'll report the entire cause.
                }
                throw e;
            }

        try {
            // write the initial handshake bytes to the client
            final Buffer buffer = handshakeWrap(c, sslEngine);

            try {
                context.write(context.getAddress(), buffer, null);
            } catch (Exception e) {
                buffer.dispose();
                throw new IOException("Unexpected exception", e);
            }

            // read the bytes returned by the client
            ReadResult result = context.read();
            Buffer m = (Buffer) result.getMessage();
//            context.setMessage(m);
            while (isHandshaking(sslEngine)) {
                doHandshakeStep(sslEngine, context, m);
                // if the current buffer's content has been consumed by the
                // SSLEngine, then we need to issue another read to continue
                // the handshake.  Continue doing so until handshaking is
                // complete
                if (!m.hasRemaining() && isHandshaking(sslEngine)) {
                    result = context.read();
                    m = (Buffer) result.getMessage();
//                    context.setMessage(m);
                }
            }

        } catch (Throwable t) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Error during handshake", t);
            }
        } finally {
            if (!authConfigured) {
                sslEngine.setNeedClientAuth(false);
            }
        }
    }


    /**
     * <p>
     * Obtains the certificate chain for this SSL session.  If no certificates
     * are available, and <code>needClientAuth</code> is true, an SSL renegotiation
     * will be be triggered to request the certificates from the client.
     * </p>
     *
     * @param sslEngine the {@link SSLEngine} associated with this
     *  certificate request.
     * @param context the {@link FilterChainContext} associated with this
     *  this certificate request.
     * @param needClientAuth determines whether or not SSL renegotiation will
     *  be attempted to obtain the certificate chain.
     *
     * @return the certificate chain as an <code>Object[]</code>.  If no
     *  certificate chain can be determined, this method will return
     *  <code>null</code>.
     *
     * @throws IOException if an error occurs during renegotiation.
     */
    protected Object[] getPeerCertificateChain(final SSLEngine sslEngine,
                                               final FilterChainContext context,
                                               final boolean needClientAuth)
    throws IOException {

        Certificate[] certs = getPeerCertificates(sslEngine);
        if (certs != null) {
            return certs;
        }

        if (needClientAuth) {
            renegotiate(sslEngine, context);
        }

        certs = getPeerCertificates(sslEngine);

        if (certs == null) {
            return null;
        }

        X509Certificate[] x509Certs = extractX509Certs(certs);

        if (x509Certs == null || x509Certs.length < 1) {
            return null;
        }

        return x509Certs;
    }


    // --------------------------------------------------------- Private Methods


    private NextAction accurateWrite(final FilterChainContext ctx,
                                     final boolean isHandshakeComplete)
    throws IOException {

        final Connection connection = ctx.getConnection();

        final CompletionHandler<SSLEngine> completionHandler =
                handshakeCompletionHandlerAttr.get(connection);
        final boolean isPendingHandler = completionHandler instanceof PendingWriteCompletionHandler;

        if (isHandshakeComplete && !isPendingHandler) {
            return doWrite(ctx);
        } else if (isPendingHandler) {
            if (!((PendingWriteCompletionHandler) completionHandler).add(ctx)) {
                return doWrite(ctx);
            }
        } else {
            // Check one more time whether handshake is completed
            final SSLEngine sslEngine = getSSLEngine(connection);
            if (sslEngine != null && !isHandshaking(sslEngine)) {
                return doWrite(ctx);
            }

            throw new IllegalStateException("Handshake is not completed!");
        }

        return ctx.getSuspendAction();
    }

    @SuppressWarnings("unchecked")
    private NextAction doWrite(final FilterChainContext ctx)
            throws IOException {
        final NextAction nextAction = encode(ctx);
        if (nextAction.type() != 0) { // Is it InvokeAction?
            // if not
            throw new IllegalStateException("Unexpected next action type: " +
                    nextAction.type());
        }
        
        final Buffer message = ctx.getMessage();
        final Object address = ctx.getAddress();
        final FilterChainContext.TransportContext transportContext =
                ctx.getTransportContext();
        
        ctx.write(address, message,
                transportContext.getCompletionHandler(),
                transportContext.getLifeCycleHandler(),
                transportContext.getMessageCloner(),
                transportContext.isBlocking());

        return ctx.getStopAction();
    }
    
    private X509Certificate[] extractX509Certs(final Certificate[] certs) {
        final X509Certificate[] x509Certs = new X509Certificate[certs.length];
        for(int i = 0, len = certs.length; i < len; i++) {
            if( certs[i] instanceof X509Certificate ) {
                x509Certs[i] = (X509Certificate)certs[i];
            } else {
                try {
                    final byte [] buffer = certs[i].getEncoded();
                    final CertificateFactory cf =
                            CertificateFactory.getInstance("X.509");
                    ByteArrayInputStream stream = new ByteArrayInputStream(buffer);
                    x509Certs[i] = (X509Certificate)
                    cf.generateCertificate(stream);
                } catch(Exception ex) {
                    LOGGER.log(Level.INFO,
                               "Error translating cert " + certs[i],
                               ex);
                    return null;
                }
            }

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Cert #{0} = {1}", new Object[] {i, x509Certs[i]});
            }
        }
        return x509Certs;
    }

    private Certificate[] getPeerCertificates(final SSLEngine sslEngine) {
        try {
            return sslEngine.getSession().getPeerCertificates();
        } catch( Throwable t ) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE,"Error getting client certs", t);
            }
            return null;
        }
    }

    private void notifyHandshakeStart(final Connection connection) {
        if (!handshakeListeners.isEmpty()) {
            for (final HandshakeListener listener : handshakeListeners) {
                listener.onStart(connection);
            }
        }
    }
    
    private void notifyHandshakeComplete(final Connection connection,
                                          final SSLEngine sslEngine) {

        final CompletionHandler<SSLEngine> completionHandler =
                handshakeCompletionHandlerAttr.get(connection);
        if (completionHandler != null) {
            connection.removeCloseListener(closeListener);
            completionHandler.completed(sslEngine);
            handshakeCompletionHandlerAttr.remove(connection);
        }
        
        if (!handshakeListeners.isEmpty()) {
            for (final HandshakeListener listener : handshakeListeners) {
                listener.onComplete(connection);
            }
        }
    }

    private static Buffer extractInputRemainder(Buffer input) {
        final Buffer remainder;
        
        if (!input.hasRemaining()) {
            remainder = null;
        } else {
            remainder = input.split(input.position());
        }
        
        input.tryDispose();
        return remainder;
    }

    private static void disposeBuffers(final Buffer currentBuffer, final Buffer bigBuffer) {
        if (currentBuffer != null) {
            currentBuffer.dispose();
        }

        if (bigBuffer != null) {
            bigBuffer.allowBufferDispose(true);
            if (bigBuffer.isComposite()) {
                ((CompositeBuffer) bigBuffer).allowInternalBuffersDispose(true);
            }
            
            bigBuffer.dispose();
        }
    }
    
    private static Buffer allowDispose(final Buffer buffer) {
        buffer.allowBufferDispose(true);
        if (buffer.isComposite()) {
            ((CompositeBuffer) buffer).allowInternalBuffersDispose(true);
        }
        
        return buffer;
    }

    // ----------------------------------------------------------- Inner Classes


    private final class PendingWriteCompletionHandler
            extends EmptyCompletionHandler<SSLEngine> {

        private final Connection connection;
        private final List<FilterChainContext> pendingWriteContexts;
        private int sizeInBytes = 0;
        
        private IOException error;
        private boolean isComplete;
        
        public PendingWriteCompletionHandler(Connection connection) {
            this.connection = connection;
            pendingWriteContexts = new LinkedList<FilterChainContext>();
        }

        public boolean add(FilterChainContext context) throws IOException {
            synchronized(connection) {
                if (error != null) throw error;
                if (isComplete) return false;
                final Buffer buffer = context.getMessage();

                final int newSize = sizeInBytes + buffer.remaining();
                if (newSize > maxPendingBytes) {
                    throw new PendingWriteQueueLimitExceededException(
                            "Max queued data limit exceeded: "
                            + newSize + '>' + maxPendingBytes);
                }
                
                sizeInBytes = newSize;
                pendingWriteContexts.add(context);

                return true;
            }
        }
        
        @Override
        public void completed(SSLEngine result) {
            try {
                synchronized (connection) {
                    isComplete = true;
                    for (FilterChainContext ctx : pendingWriteContexts) {
                        ctx.resume();
                    }
                    
                    pendingWriteContexts.clear();
                    sizeInBytes = 0;
                }
            } catch (Exception e) {
                failed(e);
            }
        }

        @Override
        public void cancelled() {
            failed(new CancellationException());
        }

        @Override
        public void failed(Throwable throwable) {
            synchronized(connection) {
                if (throwable instanceof IOException) {
                    error = (IOException) throwable;
                } else {
                    error = new IOException(throwable);
                }
            }

            connection.closeSilently();
        }        
    }

    /**
     * Close listener, which is used to notify handshake completion handler about
     * failure, if <tt>Connection</tt> will be unexpectedly closed.
     */
    private final class ConnectionCloseListener implements CloseListener {
        @Override
        public void onClosed(final Connection connection, final CloseType type) throws IOException {
            final CompletionHandler<SSLEngine> completionHandler =
                    handshakeCompletionHandlerAttr.remove(connection);
            if (completionHandler != null) {
                completionHandler.failed(new java.io.EOFException());
            }
        }
    }


    // ---------------------------------------------------------- Nested Classes


    public static class CertificateEvent implements Event {

        private static final String TYPE = "CERT_EVENT";

        private Object[] certs;

        private final boolean needClientAuth;


        // -------------------------------------------------------- Constructors


        public CertificateEvent(final boolean needClientAuth) {
            this.needClientAuth = needClientAuth;
        }


        // --------------------------------------- Methods from FilterChainEvent


        @Override
        public final Object type() {
            return TYPE;
        }


        // ------------------------------------------------------ Public Methods


        public Object[] getCertificates() {
            return certs;
        }

    } // END CertificateEvent
    
    public static interface HandshakeListener {
        public void onStart(Connection connection);
        public void onComplete(Connection connection);
    }
}
