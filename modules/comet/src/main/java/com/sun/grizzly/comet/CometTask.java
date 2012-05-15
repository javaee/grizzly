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
package com.sun.grizzly.comet;

import com.sun.grizzly.Controller;
import com.sun.grizzly.Controller.Protocol;
import com.sun.grizzly.NIOContext;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.arp.AsyncProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.Task;
import com.sun.grizzly.util.SelectedKeyAttachmentLogic;
import com.sun.grizzly.util.WorkerThread;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link Task} implementation that allow Grizzly ARP to invokeCometHandler
 * {@link CometHandler} when new data (bytes) are available from the
 * {@link CometSelector}.
 *
 * @author Jeanfrancois Arcand
 * @author Gustav Trede
 */
public class CometTask extends SelectedKeyAttachmentLogic implements Runnable {

    private static final Logger logger = SelectorThread.logger();
    /**
     * The {@link CometContext} associated with this instance.
     */
    protected final CometContext cometContext;
    /**
     * The {@link CometHandler} associated with this task.
     */
    protected final CometHandler cometHandler;
    /**
     * The {@link AsyncProcessorTask}
     */
    protected AsyncProcessorTask asyncProcessorTask;
    
    /**
     * The {@link InputStream}
     */
    protected InputStream inputStream;
    
    /**
     * true if comet handler is registered for async IO in comet context.
     * used to optimize:
     * don't give simple read == -1 operations to thread pool
     */
    protected volatile boolean cometHandlerIsAsyncRegistered;
    /**
     * The current non blocking operation.
     */
    protected boolean upcoming_op_isread;
    /**
     *  true if run() should call comet context.interrupt0
     */
    protected boolean callInterrupt;

    /**
     * true, if we want to enable mechanism, which detects closed connections,
     * or false otherwise. The mentioned mechanism should be disabled if we
     * expect client to use HTTP pipelining.
     */
    protected final boolean isDetectConnectionClose;
    
    /**
     * New {@link CometTask}.
     */
    public CometTask (CometContext cometContext, CometHandler cometHandler) {
        this(cometContext, cometHandler,
                cometContext.isDetectClosedConnections() &&
                cometContext.getExpirationDelay() != 0);
    }

    /**
     * New {@link CometTask}.
     */
    public CometTask (CometContext cometContext, CometHandler cometHandler,
            boolean isDetectConnectionClose) {
        this.cometContext = cometContext;
        this.cometHandler = cometHandler;
        this.isDetectConnectionClose = isDetectConnectionClose;
    }

    /**
     * Performs doTask() or cometContext.interrupt0
     */
    public void run() {
        if (callInterrupt) {
            CometEngine.getEngine().interrupt0(this, true);
        } else {
            try {
                doTask();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getIdleTimeoutDelay() {
        final long expirationDelay = cometContext.getExpirationDelay();
        return expirationDelay > 0 ? expirationDelay : -1;
    }

    /**
     * This should never be called for for comet, due to we are nulling the attachment
     * and completely overriding the selector.select logic.<br>
     * called by grizzly when the selection key is canceled and its socket closed.<br>
     *
     * @param selectionKey
     */
    @Override
    public void release(SelectionKey selectionKey) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean timedOut(SelectionKey key) {
        CometEngine.getEngine().interrupt(this, true);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleSelectedKey(SelectionKey selectionKey) {
        if (!selectionKey.isValid()) {
            CometEngine.getEngine().interrupt(this, true);
            return false;
        }
        if (cometHandlerIsAsyncRegistered) {
            if (selectionKey.isReadable()) {
                selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_READ);
                upcoming_op_isread = true;
            }
            if (selectionKey.isWritable()) {
                selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
                upcoming_op_isread = false;
            }
            asyncProcessorTask.getThreadPool().execute(this);
        } else {
            if (selectionKey.isReadable()) {
                return !checkIfClientClosedConnection(selectionKey);
            }
        }

        return false;
    }

    /**
     * checks if client has closed the connection.
     * the check is done by trying to read 1 byte that is thrown away.
     * only used for non async registered comet handler.
     * @param mainKey
     */
    private boolean checkIfClientClosedConnection(SelectionKey mainKey) {
        boolean isClosed = true;
        try {
            final int read = inputStream.read();
//            final int read = ((SocketChannel) mainKey.channel()).read(ByteBuffer.allocate(1));
            isClosed = (read == -1);
        } catch (IOException ignored) {
            isClosed = true;
        } finally {
            if (isClosed) {
                CometEngine.getEngine().interrupt(this, true);
            }
        }
        
        return isClosed;
    }

    /**
     * Notify the {@link CometHandler} that bytes are available for read.
     * The notification will invoke all {@link CometContext}
     */
    public void doTask() throws IOException {
        // The CometHandler has been resumed.
        if (!cometContext.isActive(cometHandler)) {
            return;
        }
        /**
         * The CometHandler in that case is **always** invoked using this
         * thread so we can re-use the Thread attribute safely.
         */
        ByteBuffer byteBuffer = null;
        boolean connectionClosed = false;
        boolean clearBuffer = true;
        final SelectionKey key = getSelectionKey();
        try {
            byteBuffer = ((WorkerThread) Thread.currentThread()).getByteBuffer();
            if (byteBuffer == null) {
                byteBuffer = ByteBuffer.allocate(asyncProcessorTask.getSelectorThread().getBufferSize());
                ((WorkerThread) Thread.currentThread()).setByteBuffer(byteBuffer);
            } else {
                byteBuffer.clear();
            }

            SocketChannel socketChannel = (SocketChannel) key.channel();
            if (upcoming_op_isread) {
                /*
                 * We must execute the first read to prevent client abort.
                 */
                int nRead = socketChannel.read(byteBuffer);
                if (nRead == -1) {
                    connectionClosed = true;
                } else {
                    /*
                     * This is an HTTP pipelined request. We need to resume
                     * the continuation and invoke the http parsing
                     * request code.
                     */
                    if (!cometHandlerIsAsyncRegistered) {
                        /**
                         * Something when wrong, most probably the CometHandler
                         * has been resumed or removed by the Comet implementation.
                         */
                        if (!cometContext.isActive(cometHandler)) {
                            return;
                        }

                        // Before executing, make sure the connection is still
                        // alive. This situation happens with SSL and there
                        // is not a cleaner way fo handling the browser closing
                        // the connection.
                        nRead = socketChannel.read(byteBuffer);
                        if (nRead == -1) {
                            connectionClosed = true;
                            return;
                        }
                        //resume without remove:
                        try {
                            cometHandler.onInterrupt(cometContext.eventInterrupt);
                        } catch (IOException e) {
                        }
                        CometEngine.cometEngine.flushPostExecute(this, false);

                        clearBuffer = false;

                        Controller controller = getSelectorThread().getController();
                        ProtocolChain protocolChain =
                                controller.getProtocolChainInstanceHandler().poll();
                        NIOContext ctx = (NIOContext) controller.pollContext();
                        ctx.setController(controller);
                        ctx.setSelectionKey(key);
                        ctx.setProtocolChain(protocolChain);
                        ctx.setProtocol(Protocol.TCP);
                        protocolChain.execute(ctx);
                    } else {
                        byteBuffer.flip();
                        CometReader reader = new CometReader();
                        reader.setNRead(nRead);
                        reader.setByteBuffer(byteBuffer);
                        CometEvent event = new CometEvent(CometEvent.READ, cometContext);
                        event.attach(reader);
                        cometContext.invokeCometHandler(event, cometHandler);
                        reader.setByteBuffer(null);

                        // This Reader is now invalid. Any attempt to use
                        // it will results in an IllegalStateException.
                        reader.setReady(false);
                    }
                }
            } else {
                CometEvent event = new CometEvent(CometEvent.WRITE, cometContext);
                CometWriter writer = new CometWriter();
                writer.setChannel(socketChannel);
                event.attach(writer);
                cometContext.invokeCometHandler(event, cometHandler);

                // This Writer is now invalid. Any attempt to use
                // it will results in an IllegalStateException.
                writer.setReady(false);
            }
        } catch (IOException ex) {
            connectionClosed = true;
            // Bug 6403933 & GlassFish 2013
            if (SelectorThread.logger().isLoggable(Level.FINEST)) {
                SelectorThread.logger().log(Level.FINEST, "Comet exception", ex);
            }
        } catch (Throwable t) {
            connectionClosed = true;
            SelectorThread.logger().log(Level.SEVERE, "Comet exception", t);
        } finally {
            cometHandlerIsAsyncRegistered = false;

            // Bug 6403933
            if (connectionClosed) {
                asyncProcessorTask.getSelectorThread().cancelKey(key);
            }

            if (clearBuffer && byteBuffer != null) {
                byteBuffer.clear();
            }
        }
    }

    /**
     * sets the comet task async interest flag in the comet task
     * @param 
     */
    public void setComethandlerIsAsyncRegistered(boolean cometHandlerIsAsyncRegistered) {
        this.cometHandlerIsAsyncRegistered = cometHandlerIsAsyncRegistered;
    }

    /**
     * returns true if the comet handler is registered for async io
     * @return
     */
    public boolean isComethandlerAsyncRegistered() {
        return cometHandlerIsAsyncRegistered;
    }

    /**
     * Return the {@link CometContext} associated with this instance.
     * @return CometContext the {@link CometContext} associated with this
     *         instance.
     */
    public CometContext getCometContext() {
        return cometContext;
    }

    /**
     * returns the {@link AsyncProcessorTask }
     * @return {@lnk AsyncProcessorTask }
     */
    public AsyncProcessorTask getAsyncProcessorTask() {
        return asyncProcessorTask;
    }

    /**
     * sets the {@link AsyncProcessorTask } 
     * @param   {@link AsyncProcessorTask }
     */
    public void setAsyncProcessorTask(AsyncProcessorTask asyncProcessorTask) {
        this.asyncProcessorTask = asyncProcessorTask;
        if (asyncProcessorTask != null) {
            inputStream = asyncProcessorTask.getProcessorTask().getInputStream();
        }
    }

    /**
     * returns selection key
     * @return
     */
    public SelectionKey getSelectionKey() {
        return asyncProcessorTask.getAsyncExecutor().getProcessorTask().getSelectionKey();
    }

    /**
     * returns the {@link AsyncProcessorTask }
     * @return {@link AsyncProcessorTask }
     */
    private SelectorThread getSelectorThread() {
        return asyncProcessorTask.getSelectorThread();
    }

    /**
     *  returns the {@link CometHandler }
     * @return {@link CometHandler }
     */
    public CometHandler getCometHandler() {
        return cometHandler;
    }

    /**
     * Returns <tt>true</tt> if connection terminate detection is on.
     * If this feature is on - HTTP pipelining can not be used.
     */
    public boolean isDetectConnectionClose() {
        return isDetectConnectionClose;
    }
}
