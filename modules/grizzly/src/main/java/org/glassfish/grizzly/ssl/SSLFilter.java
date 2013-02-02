/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Filter;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Connection.CloseListener;
import org.glassfish.grizzly.Connection.CloseType;
import org.glassfish.grizzly.FileTransfer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.PendingWriteQueueLimitExceededException;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainContext.Operation;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.utils.Exceptions;

import static org.glassfish.grizzly.ssl.SSLUtils.*;

/**
 * SSL {@link Filter} to operate with SSL encrypted data.
 *
 * @author Alexey Stashok
 */
public class SSLFilter extends SSLBaseFilter {
    private static final Logger LOGGER = Grizzly.logger(SSLFilter.class);

    private final Attribute<SSLHandshakeContext> handshakeContextAttr;
    private final SSLEngineConfigurator clientSSLEngineConfigurator;

    private final ConnectionCloseListener closeListener = new ConnectionCloseListener();
    
    // Max bytes SSLFilter may enqueue
    protected volatile int maxPendingBytes = Integer.MAX_VALUE;

    
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
        
        super(serverSSLEngineConfigurator, renegotiateOnClientAuthWant);

        if (clientSSLEngineConfigurator == null) {
            this.clientSSLEngineConfigurator = new SSLEngineConfigurator(
                    SSLContextConfigurator.DEFAULT_CONFIG.createSSLContext(),
                    true, false, false);
        } else {
            this.clientSSLEngineConfigurator = clientSSLEngineConfigurator;
        }

        handshakeContextAttr =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                "SSLFilter-SSLHandshakeContextAttr");
    }

    // ----------------------------------------------------- Methods from Filter

    @Override
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();

        if (ctx.getMessage() instanceof FileTransfer) {
            throw new IllegalStateException("TLS operations not supported with SendFile messages");
        }

        synchronized (connection) {
            final SSLConnectionContext sslCtx =
                    obtainSslConnectionContext(connection);
            
            final SSLEngine sslEngine = sslCtx.getSslEngine();
            if (sslEngine != null && !isHandshaking(sslEngine)) {
                return sslCtx.isServerMode() ?
                        super.handleWrite(ctx) :
                        accurateWrite(ctx, true);
            } else {
                if (sslEngine == null) {
                    handshake(connection,
                            null,
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
        handshake(connection, completionHandler, dstAddress,
                sslEngineConfigurator, createContext(connection, Operation.WRITE));
    }

    protected void handshake(final Connection<?> connection,
                          final CompletionHandler<SSLEngine> completionHandler,
                          final Object dstAddress,
                          final SSLEngineConfigurator sslEngineConfigurator,
                          final FilterChainContext context)
    throws IOException {
        final SSLConnectionContext sslCtx = obtainSslConnectionContext(connection);
        SSLEngine sslEngine = sslCtx.getSslEngine();
        
        if (sslEngine == null) {
            sslEngine = sslEngineConfigurator.createSSLEngine();
            sslCtx.configure(sslEngine);
        } else {
            sslEngineConfigurator.configure(sslEngine);
        }
        
        notifyHandshakeStart(connection);
        sslEngine.beginHandshake();

        handshakeContextAttr.set(connection,
                new SSLHandshakeContext(connection, completionHandler));
        connection.addCloseListener(closeListener);

        synchronized(connection) {
            final Buffer buffer = doHandshakeStep(sslCtx, context, null);
            assert (buffer == null);
        }
    }

    // --------------------------------------------------------- Private Methods


    /**
     * Has to be called in synchronized(connection) {...} block.
     */
    private NextAction accurateWrite(final FilterChainContext ctx,
                                     final boolean isHandshakeComplete)
    throws IOException {

        final Connection connection = ctx.getConnection();
        SSLHandshakeContext handshakeContext =
                handshakeContextAttr.get(connection);

        if (isHandshakeComplete && handshakeContext == null) {
            return super.handleWrite(ctx);
        } else {
            if (handshakeContext == null) {
                handshakeContext = new SSLHandshakeContext(connection, null);
                handshakeContextAttr.set(connection, handshakeContext);
            }
            
            if (!handshakeContext.add(ctx)) {
                return super.handleWrite(ctx);
            }
        }

        return ctx.getSuspendAction();
    }

    @Override
    protected void notifyHandshakeComplete(final Connection<?> connection,
                                          final SSLEngine sslEngine) {

        final SSLHandshakeContext handshakeContext =
                handshakeContextAttr.get(connection);
        if (handshakeContext != null) {
            connection.removeCloseListener(closeListener);
            handshakeContext.completed(sslEngine);
            handshakeContextAttr.remove(connection);
        }
        
        super.notifyHandshakeComplete(connection, sslEngine);
    }

    @Override
    protected void notifyHandshakeFailed(Connection connection, Throwable t) {
        final SSLHandshakeContext handshakeContext =
                handshakeContextAttr.get(connection);
        handshakeContext.failed(t);
    }
    
    // ----------------------------------------------------------- Inner Classes


    private final class SSLHandshakeContext {

        private CompletionHandler<SSLEngine> completionHandler;
        
        private final Connection connection;
        private List<FilterChainContext> pendingWriteContexts;
        private int sizeInBytes = 0;
        
        private IOException error;
        private boolean isComplete;
        
        public SSLHandshakeContext(final Connection connection,
                final CompletionHandler<SSLEngine> completionHandler) {
            this.connection = connection;
            this.completionHandler = completionHandler;            
        }

        /**
         * Has to be called in synchronized(connection) {...} scope.
         */
        public boolean add(FilterChainContext context) throws IOException {
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

            if (pendingWriteContexts == null) {
                pendingWriteContexts = new LinkedList<FilterChainContext>();
            }

            pendingWriteContexts.add(context);

            return true;
        }
        
        public void completed(SSLEngine result) {
            try {
                synchronized (connection) {
                    isComplete = true;
                    
                    final CompletionHandler<SSLEngine> completionHandlerLocal =
                            completionHandler;
                    completionHandler = null;
                    
                    if (completionHandlerLocal != null) {
                        completionHandlerLocal.completed(result);
                    }
                    
                    final List<FilterChainContext> pendingWriteContextsLocal =
                            pendingWriteContexts;
                    pendingWriteContexts = null;
                    
                    if (pendingWriteContextsLocal != null) {
                        for (FilterChainContext ctx : pendingWriteContextsLocal) {
                            ctx.resume();
                        }

                        pendingWriteContextsLocal.clear();
                        sizeInBytes = 0;
                    }
                }
            } catch (Exception e) {
                failed(e);
            }
        }

        public void failed(Throwable throwable) {
            synchronized(connection) {
                error = Exceptions.makeIOException(throwable);
                
                final CompletionHandler<SSLEngine> completionHandlerLocal =
                        completionHandler;
                completionHandler = null;
                    
                if (completionHandlerLocal != null) {
                    completionHandlerLocal.failed(throwable);
                }
                
                final List<FilterChainContext> pendingWriteContextsLocal =
                        pendingWriteContexts;
                pendingWriteContexts = null;
                
                if (pendingWriteContextsLocal != null) {
                    for (FilterChainContext ctx : pendingWriteContextsLocal) {
                        ctx.resume();
                    }
                    
                    pendingWriteContextsLocal.clear();
                    sizeInBytes = 0;
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
        public void onClosed(final Connection connection, final CloseType type)
                throws IOException {
            final SSLHandshakeContext handshakeContext =
                    handshakeContextAttr.get(connection);
            if (handshakeContext != null) {
                handshakeContext.failed(new java.io.EOFException());
                handshakeContextAttr.remove(connection);
            }
        }
    }
}
