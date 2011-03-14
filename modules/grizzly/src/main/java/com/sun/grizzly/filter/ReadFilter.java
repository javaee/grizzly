/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.filter;

import com.sun.grizzly.BaseSelectionKeyHandler;
import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.util.WorkerThread;
import java.net.SocketAddress;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.WeakHashMap;
import java.util.logging.Level;

import static com.sun.grizzly.Controller.Protocol.TCP;
import static com.sun.grizzly.Controller.Protocol.TLS;
import static com.sun.grizzly.Controller.Protocol.UDP;
import com.sun.grizzly.Controller.Protocol;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ReinvokeAware;
import com.sun.grizzly.SelectionKeyHandler;
import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.util.AbstractThreadPool;
import com.sun.grizzly.util.ByteBufferFactory;
import com.sun.grizzly.util.WorkerThreadImpl;
import java.util.concurrent.ExecutorService;

/**
 * Simple {@link ProtocolFilter} implementation which read the available bytes
 * and delegate the processing to the next {@link ProtocolFilter} in the {@link ProtocolChain}.
 * If no bytes are available, no new {@link ProtocolFilter} will be a invoked and
 * the connection (SelectionKey) will be cancelled. This filter can be used
 * for both UDP (reveive) and TCP (read).
 *
 * Note that all ready OP_WRITE operations will be ignored.
 *
 * @author Jeanfrancois Arcand
 */
public class ReadFilter implements ProtocolFilter, ReinvokeAware {

    private static final boolean WIN32 =
            "\\".equals(System.getProperty("file.separator"));
    private static final short MAX_ZERO_READ_COUNT = 100;

    public final static String DELAYED_CLOSE_NOTIFICATION = "delayedClose";
    public final static String UDP_SOCKETADDRESS = "socketAddress";

    private WeakHashMap<SocketChannel,Short> zeroLengthReads;


    /**
     * <tt>true</tt> if a pipelined execution is required. A pipelined execution
     * occurs when a ProtocolFilter implementation set the
     * ProtocolFilter.READ_SUCCESS as an attribute to a Context. When this
     * attribute is present, the ProtocolChain will not release the current
     * running Thread and will re-execute all its ProtocolFilter.
     */
    protected boolean continousExecution = false;

    protected int readAttempts = 3;
    
    public ReadFilter(){
        if (WIN32) {
            zeroLengthReads = new WeakHashMap<SocketChannel,Short>();
        }
    }

    /**
     * Read available bytes and delegate the processing of them to the next
     * {@link ProtocolFilter} in the {@link ProtocolChain}.
     * @return <tt>true</tt> if the next ProtocolFilter on the ProtocolChain
     *                       need to bve invoked.
     */
    public boolean execute(Context ctx) throws IOException {
        return execute(ctx, null);
    }


    /**
     * Read available bytes to the specific {@link ByteBuffer} and delegate
     * the processing of them to the next ProtocolFilter in the ProtocolChain.
     * @return <tt>true</tt> if the next ProtocolFilter on the ProtocolChain
     *                       need to bve invoked.
     */
    protected boolean execute(Context ctx, ByteBuffer byteBuffer) throws IOException {

        if (ctx.getCurrentOpType() == Context.OpType.OP_WRITE){
            if (Controller.logger().isLoggable(Level.FINE)){
                Controller.logger().fine("ReadFilter cannont handle OP_WRITE");
            }
            return false;
        }
        
        if (byteBuffer == null) {
            byteBuffer = ((WorkerThread)Thread.currentThread()).getByteBuffer();
            
            int size = WorkerThreadImpl.DEFAULT_BYTE_BUFFER_SIZE;
            ByteBufferFactory.ByteBufferType bbt= WorkerThreadImpl.DEFAULT_BYTEBUFFER_TYPE;
            
            if (ctx.getSelectorHandler().getThreadPool() instanceof AbstractThreadPool){
                AbstractThreadPool tp =
                        (AbstractThreadPool)ctx.getSelectorHandler().getThreadPool();
                size = tp.getInitialByteBufferSize();
                bbt = tp.getByteBufferType();
            }
            
            if (byteBuffer == null) {
                byteBuffer = ByteBufferFactory.allocateView(size,
                        bbt == ByteBufferFactory.ByteBufferType.DIRECT);
                ((WorkerThread)Thread.currentThread()).setByteBuffer(byteBuffer);
            }
        }

        if (!byteBuffer.hasRemaining()){
            throw new IllegalStateException("ByteBuffer is full: " + byteBuffer);
        }


        boolean invokeNextFilter = true;
        int count = 0;
        int nRead = 0;
        SocketAddress socketAddress = null;
        Exception exception = null;
        SelectionKey key = ctx.getSelectionKey();
        
        Protocol protocol = ctx.getProtocol();
        try {
            int loop = 0;
            if (protocol == TCP || protocol == TLS){
                SocketChannel channel = (SocketChannel)key.channel();

                // As soon as bytes are ready, invoke the next ProtocolFilter.
                while ((count = channel.read(byteBuffer)) > -1) {

                    nRead += count;
                    // Avoid calling the Selector.
                    if (++loop >= readAttempts){
                        if (nRead == 0 && ctx.getKeyRegistrationState()
                                != Context.KeyRegistrationState.NONE){
                            ctx.setAttribute(ProtocolFilter.SUCCESSFUL_READ,
                                             Boolean.FALSE);
                            invokeNextFilter = false;
                        }
                        break;
                    }
                    if (WIN32) {
                        checkEmptyRead(channel, nRead);
                    }
                }
            } else if (protocol == UDP){
                DatagramChannel datagramChannel = (DatagramChannel)key.channel();
                socketAddress = datagramChannel.receive(byteBuffer);
                ctx.getSelectorHandler().register(key, SelectionKey.OP_READ);
            }
        } catch (IOException ex) {
            exception = ex;
            log("ReadFilter.execute",ex);
        } catch (RuntimeException ex) {
            exception = ex;
            log("ReadFilter.execute",ex);
        } finally {
            final SelectionKeyHandler skh =
                    ctx.getSelectorHandler().getSelectionKeyHandler();
            if (skh instanceof BaseSelectionKeyHandler){
                ((WorkerThread)Thread.currentThread())
                            .getAttachment().setAttribute("ConnectionCloseHandlerNotifier", skh);
            }   

            if (exception != null){
                ctx.setAttribute(Context.THROWABLE,exception);
                if (protocol != UDP){
                    ctx.setKeyRegistrationState(
                        Context.KeyRegistrationState.CANCEL);
                }

                if (nRead <= 0) {
                    invokeNextFilter = false;
                    if (skh instanceof BaseSelectionKeyHandler) {
                        ((BaseSelectionKeyHandler) skh).notifyRemotlyClose(key);
                    }
                } else {
                    ctx.setAttribute(DELAYED_CLOSE_NOTIFICATION, Boolean.TRUE);
                }
            } else if (count == -1 && protocol != UDP){
                ctx.setKeyRegistrationState(
                        Context.KeyRegistrationState.CANCEL);
                if (nRead <= 0) {
                    invokeNextFilter = false;
                    if (skh instanceof BaseSelectionKeyHandler) {
                        ((BaseSelectionKeyHandler) skh).notifyRemotlyClose(key);
                    }
                } else {
                    ctx.setAttribute(DELAYED_CLOSE_NOTIFICATION, Boolean.TRUE);
                }
            } else if (socketAddress == null && protocol == UDP ){
                ctx.setKeyRegistrationState(Context.KeyRegistrationState.REGISTER);
                invokeNextFilter = false;
            } else if (protocol == UDP) {
                ctx.setAttribute(UDP_SOCKETADDRESS,socketAddress);
            }
        }
        return invokeNextFilter;
    }


    /**
     * If no bytes were available, close the connection by cancelling the
     * SelectionKey. If bytes were available, register the SelectionKey
     * for new bytes.
     *
     * @return <tt>true</tt> if the previous ProtocolFilter postExecute method
     *         needs to be invoked.
     */
    public boolean postExecute(Context ctx) throws IOException {
        final SelectorHandler selectorHandler =
                ctx.getSelectorHandler();
        final SelectionKey key = ctx.getSelectionKey();
        final Context.KeyRegistrationState state = ctx.getKeyRegistrationState();
        final Protocol protocol = ctx.getProtocol();

        try{
            //For UDP, we don't have to do anything as the OP_READ operations
            //as already been handled, and cencelling the key is not allowed.
            if (protocol == UDP){
                return true;
            }


            ProtocolChain protocolChain = ctx.getProtocolChain();

            // Check if both Filter and ProtocolChain are
            // set to reinvoke the protocol chain
            boolean isReinvoke = continousExecution &&
                    (protocolChain instanceof ReinvokeAware) &&
                    ((ReinvokeAware) protocolChain).isContinuousExecution();

            // The ProtocolChain associated with this ProtocolFilter will re-invoke
            // the execute method. Do not register the SelectionKey in that case
            // to avoid thread races.
            if (isReinvoke
                    && state == Context.KeyRegistrationState.REGISTER
                    && Boolean.FALSE !=
                        ctx.getAttribute(ProtocolFilter.SUCCESSFUL_READ)){
                ctx.setAttribute(ProtocolFilter.SUCCESSFUL_READ,
                                 Boolean.TRUE);
            } else {
                Boolean isDelayedNotification =
                        (Boolean) ctx.removeAttribute(DELAYED_CLOSE_NOTIFICATION);
                if (isDelayedNotification == Boolean.TRUE) {
                    final SelectionKeyHandler skh =
                            ctx.getSelectorHandler().getSelectionKeyHandler();
                    
                    if (skh instanceof BaseSelectionKeyHandler) {
                        ((BaseSelectionKeyHandler) skh).notifyRemotlyClose(key);
                    }
                }

                if (state == Context.KeyRegistrationState.CANCEL){
                    selectorHandler.addPendingKeyCancel(key);
                } else if (state == Context.KeyRegistrationState.REGISTER){
                    selectorHandler.register(key, SelectionKey.OP_READ);
                }
            }
            return true;
        } finally {
            ctx.removeAttribute(Context.THROWABLE);
            ctx.removeAttribute(UDP_SOCKETADDRESS);
        }
    }


    /**
     * Set to <tt>true</tt> if the current {@link ExecutorService} can
     * re-execute its ProtocolFilter(s) after a successful execution. Enabling
     * this property is useful for protocol that needs to support pipelined
     * message requests as the ProtocolFilter are automatically re-executed,
     * avoiding the overhead of releasing the current Thread, registering
     * back the SelectionKey to the {@link SelectorHandler} and waiting for a new
     * NIO event.
     *
     * Some protocols (like http) can get the http headers in one
     * SocketChannel.read, parse the message and then get the next http message
     * on the second SocketChannel.read(). Not having to release the Thread
     * and re-execute the ProtocolFilter greatly improve performance.
     * @param continousExecution true to enable continuous execution.
     *        (default is false).
     */
    public void setContinuousExecution(boolean continousExecution){
        this.continousExecution = continousExecution;
    }


    /**
     * Return <tt>true</tt> if the current {@link ExecutorService} can
     * re-execute its ProtocolFilter after a successful execution.
     */
    public boolean isContinuousExecution(){
        return continousExecution;
    }

    /**
     * Get the number of attempts the {@link ReadFilter} will try to read a data
     * from a channel.
     * 
     * @return the number of attempts the {@link ReadFilter} will try to read a data
     * from a channel.
     */
    public int getReadAttempts() {
        return readAttempts;
    }

    /**
     * Set the number of attempts the {@link ReadFilter} will try to read a data
     * from a channel.
     *
     * @param readAttempts the number of attempts the {@link ReadFilter} will 
     * try to read a data from a channel.
     */
    public void setReadAttempts(int readAttempts) {
        if (readAttempts < 1) {
            throw new IllegalArgumentException("The readAttempts parameter should be >= 1");
        }
        
        this.readAttempts = readAttempts;
    }


    /**
     * Log a message/exception.
     * @param msg <code>String</code>
     * @param t <code>Throwable</code>
     */
    protected void log(String msg,Throwable t){
        if (Controller.logger().isLoggable(Level.FINE)){
            Controller.logger().log(Level.FINE, msg, t);
        }
    }


    protected final void checkEmptyRead(final SocketChannel channel,
                                        final int size) {

        synchronized (zeroLengthReads) {
            if (size == 0) {
                Short count = zeroLengthReads.get(channel);
                if (count == null) {
                    count = 1;
                } else {
                    count++;
                }
                zeroLengthReads.put(channel, count);
                if (count >= MAX_ZERO_READ_COUNT) {
                    try {
                        channel.close();
                    } catch (IOException ignored) {
                    } finally {
                        zeroLengthReads.remove(channel);
                    }
                }
            } else {
                zeroLengthReads.remove(channel);
            }
        }

    }

}
