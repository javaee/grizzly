/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.nio.transport;

import com.sun.grizzly.Transformer;
import java.util.concurrent.Future;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.nio.AbstractNIOConnection;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.nio.SelectorRunner;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.Interceptor;
import com.sun.grizzly.Reader;
import com.sun.grizzly.Writer;
import com.sun.grizzly.nio.tmpselectors.TemporarySelectorReader;
import com.sun.grizzly.nio.tmpselectors.TemporarySelectorWriter;
import java.io.EOFException;
import java.util.concurrent.TimeUnit;

/**
 * {@link com.sun.grizzly.Connection} implementation
 * for the {@link TCPNIOTransport}
 *
 * @author Alexey Stashok
 */
public class TCPNIOConnection extends AbstractNIOConnection {
    private static Logger logger = Grizzly.logger(TCPNIOConnection.class);

    private SocketAddress localSocketAddress;
    private SocketAddress peerSocketAddress;
    
    public TCPNIOConnection(TCPNIOTransport transport,
            SelectableChannel channel) {
        super(transport);
        
        this.channel = channel;
        readBufferSize = transport.getReadBufferSize();
        writeBufferSize = transport.getWriteBufferSize();

        resetAddresses();
    }

    @Override
    protected void setSelectionKey(SelectionKey selectionKey) {
        super.setSelectionKey(selectionKey);
    }

    @Override
    protected void setSelectorRunner(SelectorRunner selectorRunner) {
        super.setSelectorRunner(selectorRunner);
    }

    @Override
    protected void preClose() {
        try {
            transport.fireIOEvent(IOEvent.CLOSED, this);
        } catch (IOException e) {
            logger.log(Level.FINE, "Unexpected IOExcption occurred, " +
                    "when firing CLOSE event");
        }
    }

    /**
     * Returns the address of the endpoint this <tt>Connection</tt> is
     * connected to, or <tt>null</tt> if it is unconnected.
     * @return the address of the endpoint this <tt>Connection</tt> is
     *         connected to, or <tt>null</tt> if it is unconnected.
     */
    @Override
    public SocketAddress getPeerAddress() {
        return peerSocketAddress;
    }
    
    /**
     * Returns the local address of this <tt>Connection</tt>,
     * or <tt>null</tt> if it is unconnected.
     * @return the local address of this <tt>Connection</tt>,
     *      or <tt>null</tt> if it is unconnected.
     */
    @Override
    public SocketAddress getLocalAddress() {
        return localSocketAddress;
    }

    protected void resetAddresses() {
        if (channel != null) {
            if (channel instanceof SocketChannel) {
                localSocketAddress =
                        ((SocketChannel) channel).socket().getLocalSocketAddress();
                peerSocketAddress =
                        ((SocketChannel) channel).socket().getRemoteSocketAddress();
            } else if (channel instanceof ServerSocketChannel) {
                localSocketAddress =
                        ((ServerSocketChannel) channel).socket().getLocalSocketAddress();
                peerSocketAddress = null;
            }
        }
    }

    @Override
    public <M> Future<ReadResult<M, SocketAddress>> read(
            final M message,
            final CompletionHandler<ReadResult<M, SocketAddress>> completionHandler,
            final Transformer<Buffer, M> transformer,
            final Interceptor<ReadResult> interceptor) throws IOException {
        
        final TCPNIOTransport tcpNioTransport = (TCPNIOTransport) transport;

        if (isBlocking) {
            try {
                final TemporarySelectorReader reader =
                        (TemporarySelectorReader) tcpNioTransport.getTemporarySelectorIO().getReader();
                final Future future = reader.read(this, message,
                        completionHandler, transformer, interceptor,
                        readTimeoutMillis, TimeUnit.MILLISECONDS);
                return future;
            } catch (Exception e) {
                logger.log(Level.FINE, "Error occured during TemporarySelector.read", e);
                throw new EOFException();
            }
        } else {
            try {
                final Reader reader = tcpNioTransport.getAsyncQueueIO().getReader();
                final Future future = reader.read(this, message,
                        completionHandler, transformer, interceptor);
                return future;
            } catch (IOException e) {
                throw e;
            }
        }
    }

    @Override
    public <M> Future<WriteResult<M, SocketAddress>> write(
            SocketAddress dstAddress, M message,
            CompletionHandler<WriteResult<M, SocketAddress>> completionHandler,
            Transformer<M, Buffer> transformer) throws IOException {

        final TCPNIOTransport tcpNioTransport = (TCPNIOTransport) transport;

        if (isBlocking) {
            try {
                final TemporarySelectorWriter writer =
                        (TemporarySelectorWriter) tcpNioTransport.getTemporarySelectorIO().getWriter();
                final Future future = writer.write(this, dstAddress, message,
                        completionHandler, transformer, null,
                        writeTimeoutMillis, TimeUnit.MILLISECONDS);
                return future;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error writing to channel", e);
                throw new EOFException("Cause: " + e.getClass().getName() +
                        " \"" + e.getMessage() + "\"");
            }
        } else {
            try {
                final Writer writer = tcpNioTransport.getAsyncQueueIO().getWriter();
                final Future future = writer.write(this, null, message,
                        completionHandler, transformer, null);
                return future;
            } catch (IOException e) {
                throw e;
            }
        }
    }
}
    
