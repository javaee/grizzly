/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly;

import com.sun.grizzly.async.AsyncQueueWritable;
import com.sun.grizzly.async.AsyncQueueReadable;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * Client side interface used to implement non blocking client operation. 
 * Implementation of this class must make sure the following methods are 
 * invoked in that order:
 * 
 * (1) connect()
 * (2) read() or write().
 * 
 *
 * @param E a {@link SelectorHandler}
 * @param P a {@link CallbackHandler}
 * @author Jeanfrancois Arcand
 */
public interface ConnectorHandler<E extends SelectorHandler, P extends CallbackHandler> extends Handler, Closeable, AsyncQueueWritable, AsyncQueueReadable {
    
    
     /**
     * A token decribing the protocol supported by an implementation of this
     * interface
     * @return <code>Controller.Protocol</code>
      */
    public Controller.Protocol protocol();  
    
    
    /**
     * Connect to hostname:port. When an aysnchronous event happens (e.g 
     * OP_READ or OP_WRITE), the {@link Controller} will invoke 
     * the CallBackHandler.
     * @param remoteAddress remote address to connect
     * @param callbackHandler the handler invoked by the Controller when 
     *        an non blocking operation is ready to be handled.
     * @param e {@link SelectorHandler}
     * @throws java.io.IOException 
     */
    public void connect(SocketAddress remoteAddress, 
                        P callbackHandler,
                        E e) throws IOException;

    
    /**
     * Connect to hostname:port. When an aysnchronous event happens (e.g 
     * OP_READ or OP_WRITE), the {@link Controller} will invoke 
     * the CallBackHandler.
     * @param remoteAddress remote address to connect 
     * @param callbackHandler the handler invoked by the Controller when 
     *        an non blocking operation is ready to be handled.
     * @throws java.io.IOException
     */    
    public void connect(SocketAddress remoteAddress, 
                        P callbackHandler) throws IOException;
    
    
    /**
     * Connect to hostname:port. Internally an instance of Controller and
     * its default SelectorHandler will be created everytime this method is 
     * called. This method should be used only and only if no external 
     * Controller has been initialized.
     * @param remoteAddress remote address to connect
     * @throws java.io.IOException 
     */
    public void connect(SocketAddress remoteAddress)
        throws IOException;         
    
    
    /**
     * Connect to hostname:port. When an aysnchronous event happens (e.g 
     * OP_READ or OP_WRITE), the {@link Controller} will invoke 
     * the CallBackHandler.
     * @param remoteAddress remote address to connect 
     * @param localAddress local address to bind
     * @param callbackHandler the handler invoked by the Controller when 
     *        an non blocking operation is ready to be handled. 
     * @param e {@link SelectorHandler}
     * @throws java.io.IOException
     */
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress, 
                        P callbackHandler,
                        E e) throws IOException;

    
    /**
     * Connect to hostname:port. When an aysnchronous event happens (e.g 
     * OP_READ or OP_WRITE), the {@link Controller} will invoke 
     * the CallBackHandler.
     * @param remoteAddress remote address to connect
     * @param localAddress local address to bind
     * @param callbackHandler the handler invoked by the Controller when 
     *        an non blocking operation is ready to be handled.
     * @throws java.io.IOException 
     */    
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress, 
                        P callbackHandler) throws IOException;
    
    
    /**
     * Connect to hostname:port. Internally an instance of Controller and
     * its default SelectorHandler will be created everytime this method is 
     * called. This method should be used only and only if no external 
     * Controller has been initialized.
     * @param remoteAddress remote address to connect
     * @param localAddress local address to bind
     * @throws java.io.IOException 
     */
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress)
        throws IOException;
    
    
    /**
     * Read bytes. If blocking is set to <tt>true</tt>, a pool of temporary
     * {@link Selector} will be used to read bytes.
     * @param byteBuffer The byteBuffer to store bytes.
     * @param blocking <tt>true</tt> if a a pool of temporary Selector
     *        is required to handle a blocking read.
     * @return number of bytes read
     * @throws java.io.IOException 
     */
    public long read(ByteBuffer byteBuffer, boolean blocking) throws IOException;

    
    /**
     * Writes bytes. If blocking is set to <tt>true</tt>, a pool of temporary
     * {@link Selector} will be used to writes bytes.
     * @param byteBuffer The byteBuffer to write.
     * @param blocking <tt>true</tt> if a a pool of temporary Selector
     *        is required to handle a blocking write.
     * @return number of bytes written
     * @throws java.io.IOException 
     */    
    public long write(ByteBuffer byteBuffer, boolean blocking) throws IOException;  
    
    
    /**
     * Close the underlying connection.
     * @throws java.io.IOException 
     */
    public void close() throws IOException;
    
    
    /**
     * Decide how the OP_CONNECT final steps are handled.
     * @param key {@link SelectionKey}
     */
    public void finishConnect(SelectionKey key)  throws IOException;
    
    
    /**
     * Set the {@link Controller} associated with this instance.
     * @param controller {@link Controller}
     */
    public void setController(Controller controller);
    
    
    /**
     * Return the {@link Controller}
     * @return 
     */
    public Controller getController();
    
    
    /**
     * Method returns {@link SelectorHandler}, which manages this 
     * {@link ConnectorHandler}
     * @return {@link SelectorHandler}
     */
    public E getSelectorHandler();

    /**
     * Method returns {@link ConnectorHandler}'s underlying channel
     * @return channel
     */
    public SelectableChannel getUnderlyingChannel();
    
    /**
     * Returns {@link ConnectorHandler}'s callback handler instance,
     * which is used to process occuring events
     * 
     * @return callback handler
     */
    public P getCallbackHandler();
    
    /**
     * Sets {@link ConnectorHandler}'s callback handler instance,
     * which is used to process occuring events
     * 
     * @param callbackHandler handler
     */
    public void setCallbackHandler(P callbackHandler);

    /**
     * Is the underlying channel connected.
     *
     * @return <tt>true</tt> if connected, otherwise <tt>false</tt>
     */
    public boolean isConnected();
}
