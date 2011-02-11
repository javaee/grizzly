/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.aio.transport;

import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.aio.AIOConnection;
import java.io.IOException;
import java.net.StandardSocketOption;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.Future;
import java.util.logging.Level;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.PostProcessor;
import org.glassfish.grizzly.ProcessorResult.Status;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import java.util.logging.Logger;

/**
 *
 * @author oleksiys
 */
public final class TCPAIOServerConnection extends TCPAIOConnection {

    private static final Logger LOGGER = Grizzly.logger(TCPAIOServerConnection.class);

    public TCPAIOServerConnection(TCPAIOTransport transport,
            AsynchronousServerSocketChannel serverSocketChannel) {
        super(transport, serverSocketChannel);
    }

    public void listen() throws IOException {
        if (isStandalone()) return;
        
        ((AsynchronousServerSocketChannel) channel).accept(null,
                new java.nio.channels.CompletionHandler<AsynchronousSocketChannel, Object>()    {

                    @Override
                    public void completed(
                            final AsynchronousSocketChannel acceptedChannel,
                            final Object attachment) {

                        try {
                            final AIOConnection connection = onAccept(acceptedChannel);
                            // if not standalone - enable OP_READ after IOEvent.ACCEPTED will be processed
                            transport.fireIOEvent(IOEvent.ACCEPTED, connection,
                                    enableInterestPostProcessor);
                        } catch (Exception e) {
                            LOGGER.log(Level.FINE, "Exception happened, when "
                                    + "trying to accept the connection", e);
                        }

                        ((AsynchronousServerSocketChannel) channel).accept(null,
                                this);

                    }

                    @Override
                    public void failed(Throwable e, Object attachment) {
                        LOGGER.log(Level.FINE, "Exception happened, when "
                                + "trying to accept the connection", e);
                    }
                });

        notifyProbesBind(this);
    }

    @Override
    public boolean isBlocking() {
        return transport.isBlocking();
    }

    @Override
    public boolean isStandalone() {
        return transport.isStandalone();
    }

    /**
     * Accept a {@link Connection}. Could be used only in standalone mode.
     * See {@link Connection#configureStandalone(boolean)}.
     *
     * @return {@link Future}
     * @throws java.io.IOException
     */
    public GrizzlyFuture<Connection> accept() throws IOException {
        if (!isStandalone()) {
            throw new IllegalStateException("Accept could be used in standalone mode only");
        }

        final FutureImpl<Connection> acceptFuture =
                SafeFutureImpl.<Connection>create();
        
        ((AsynchronousServerSocketChannel) channel).accept(null,
                new java.nio.channels.CompletionHandler<AsynchronousSocketChannel, Object>() {

            @Override
            public void completed(
                    final AsynchronousSocketChannel acceptedChannel,
                    final Object attachment) {
                try {
                    acceptFuture.result(onAccept(acceptedChannel));
                } catch (Throwable e) {
                    failed(e, attachment);
                }
            }

            @Override
            public void failed(final Throwable e, final Object attachment) {
                acceptFuture.failure(e);
            }
            
        });
        
        if (isBlocking()) {
            try {
                acceptFuture.get();
            } catch (Exception ignored) {
            }
        }

        return acceptFuture;
    }

    private AIOConnection onAccept(
            final AsynchronousSocketChannel acceptedChannel)
            throws IOException {
        final TCPAIOConnection connection =
                configureAcceptedChannel(acceptedChannel);

        connection.resetProperties();

        notifyProbesAccept(TCPAIOServerConnection.this);

        return connection;
    }

    private TCPAIOConnection configureAcceptedChannel(
            final AsynchronousSocketChannel acceptedChannel)
            throws IOException {

        final TCPAIOTransport tcpAIOTransport = (TCPAIOTransport) transport;
        tcpAIOTransport.configureChannel(acceptedChannel);

        final TCPAIOConnection connection =
                tcpAIOTransport.obtainAIOConnection(acceptedChannel);

        if (processor != null) {
            connection.setProcessor(processor);
        }

        if (processorSelector != null) {
            connection.setProcessorSelector(processorSelector);
        }

        return connection;
    }

    @Override
    public void preClose() {
        try {
            ((TCPAIOTransport) transport).unbind(this);
        } catch (IOException e) {
            LOGGER.log(Level.FINE,
                    "Exception occurred, when unbind connection: " + this, e);
        }

        super.preClose();
    }

    @Override
    public void setReadBufferSize(final int readBufferSize) {
        final AsynchronousServerSocketChannel serverSocketChannel =
                (AsynchronousServerSocketChannel) channel;

        try {
            final int socketReadBufferSize =
                    serverSocketChannel.getOption(StandardSocketOption.SO_RCVBUF);
            if (readBufferSize != -1) {
                if (readBufferSize > socketReadBufferSize) {
                    serverSocketChannel.setOption(
                            StandardSocketOption.SO_RCVBUF, readBufferSize);
                }
            }

            this.readBufferSize = readBufferSize;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error setting read buffer size", e);
        }
    }

    @Override
    public void setWriteBufferSize(final int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
    }
//    protected final class RegisterAcceptedChannelCompletionHandler
//            extends EmptyCompletionHandler<RegisterChannelResult> {
//
//        private final FutureImpl listener;
//
//        public RegisterAcceptedChannelCompletionHandler() {
//            this(null);
//        }
//
//        public RegisterAcceptedChannelCompletionHandler(
//                FutureImpl listener) {
//            this.listener = listener;
//        }
//
//        @Override
//        public void completed(RegisterChannelResult result) {
//            try {
//                final TCPAIOTransport nioTransport = (TCPAIOTransport) transport;
//
//                nioTransport.selectorRegistrationHandler.completed(result);
//
//                final SelectionKeyHandler selectionKeyHandler =
//                        nioTransport.getSelectionKeyHandler();
//                final SelectionKey acceptedConnectionKey =
//                        result.getSelectionKey();
//            } catch (Exception e) {
//                LOGGER.log(Level.FINE, "Exception happened, when "
//                        + "trying to accept the connection", e);
//            }
//        }
//    }
    // COMPLETE, COMPLETE_LEAVE, REREGISTER, RERUN, ERROR, TERMINATE, NOT_RUN
    private final static boolean[] isRegisterMap = {true, false, true, false, false, false, true};
    // PostProcessor, which supposed to enable OP_READ interest, once Processor will be notified
    // about Connection ACCEPT
    protected final static PostProcessor enableInterestPostProcessor =
            new EnableReadPostProcessor();

    private static class EnableReadPostProcessor implements PostProcessor {

        @Override
        public void process(Context context, Status status) throws IOException {
            if (isRegisterMap[status.ordinal()]) {
                final AIOConnection aioConnection = (AIOConnection) context.getConnection();
                aioConnection.enableIOEvent(IOEvent.READ);
            }
        }
    }
}
