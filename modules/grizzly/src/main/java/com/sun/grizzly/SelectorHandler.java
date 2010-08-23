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

import com.sun.grizzly.async.AsyncQueueReader;
import com.sun.grizzly.async.AsyncQueueWriter;
import com.sun.grizzly.tcp.PendingIOhandler;
import com.sun.grizzly.util.AttributeHolder;
import com.sun.grizzly.util.Copyable;
import com.sun.grizzly.util.State;
import com.sun.grizzly.util.SupportStateHolder;
import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * A SelectorHandler handles all java.nio.channels.Selector operations. 
 * One or more instance of a Selector are handled by SelectorHandler. 
 * The logic for processing of SelectionKey interest (OP_ACCEPT,OP_READ, etc.)
 * is usually defined using an instance of SelectorHandler.
 *
 * @author Jeanfrancois Arcand
 */
public interface SelectorHandler extends Handler, Copyable, 
        AttributeHolder, SupportStateHolder<State>, PendingIOhandler {

    /**
     * A token decribing the protocol supported by an implementation of this
     * interface
     * @return SelectorHandler supported protocol
     */
    public Controller.Protocol protocol();
    
    
    /**
     * Gets the underlying selector.
     * @return underlying {@link Selector}
     */
    public Selector getSelector();
    
    
    /**
     * Sets the underlying {@link Selector}
     * @param selector underlying {@link Selector}
     */
    public void setSelector(Selector selector);

    /**
     * Returns {@link SelectionKey}, which represents binding between 
     * the {@link SelectableChannel} and this <tt>SelectorHandler</tt>
     * 
     * @param channel {@link SelectableChannel}
     * @return {@link SelectionKey}, which represents binding between
     * the {@link SelectableChannel} and this <tt>SelectorHandler</tt>
     */
    public SelectionKey keyFor(SelectableChannel channel);

    
    /**
     * The SelectionKey that has been registered.
     * @return{@link Set} of {@link SelectionKey}
     */
    public Set<SelectionKey> keys();
    
    
    /**
     * Is the underlying Selector open.
     * @return true / false
     */
    public boolean isOpen();
    
    
    /**
     * Pause this {@link SelectorHandler}
     */
    public void pause();
    
    
    /**
     * Resume this {@link SelectorHandler}
     */
    public void resume();
    
    
    /**
     * Shutdown this instance.
     */
    public void shutdown();
    
    
    /**
     * This method is garantee to always be called before operation 
     * Selector.select().
     * @param controllerCtx {@link Context}
     * @throws java.io.IOException 
     */
    public void preSelect(Context controllerCtx) throws IOException;
    
        
    /**
     * Invoke the Selector.select() method.
     * @param controllerCtx 
     * @return{@link Set} of {@link SelectionKey}
     * @throws java.io.IOException 
     */    
    public Set<SelectionKey> select(Context controllerCtx) throws IOException;
    
    
    /**
     * This method is garantee to always be called after operation 
     * Selector.select().
     * @param controllerCtx {@link Context}
     * @throws java.io.IOException 
     */
    public void postSelect(Context controllerCtx) throws IOException;
     
    
    /**
     * Register the {@link SelectableChannel} on the {@link Selector}.
     * @param key 
     * @param ops interested operations
     */
    public void register(SelectableChannel channel,int ops);

    /**
     * Register the {@link SelectableChannel} on the {@link Selector}.
     * @param key
     * @param ops interested operations
     * @param attachment
     */
    public void register(SelectableChannel channel, int ops, Object attachment);

    /**
     * Register the SelectionKey on the Selector.
     * @param key 
     * @param ops interested operations
     */
    public void register(SelectionKey key,int ops);
    
    
    /**
     * Accepts connection, without registering it for reading or writing
     * @param key
     * @return accepted {@link SelectableChannel}
     * @throws java.io.IOException
     */
    public SelectableChannel acceptWithoutRegistration(SelectionKey key)
            throws IOException;

    
    /**
     * Handle OP_ACCEPT.
     * @param key {@link SelectionKey}
     * @param controllerCtx {@link Context}
     * @return true if and only if the ProtocolChain must be invoked after
     *              executing this method.
     * @throws java.io.IOException 
     */
    public boolean onAcceptInterest(SelectionKey key,Context controllerCtx)
        throws IOException;    

    /**
     * Handle OP_READ.
     * @param key {@link SelectionKey}
     * @param controllerCtx {@link Context}
     * @return true if and only if the ProtocolChain must be invoked after
     *              executing this method.
     * @throws java.io.IOException 
     */   
    public boolean onReadInterest(SelectionKey key,Context controllerCtx)
        throws IOException;    
 
    
    /**
     * Handle OP_WRITE.
     * @param key {@link SelectionKey}
     * @param controllerCtx {@link Context}
     * @return true if and only if the ProtocolChain must be invoked after
     *              executing this method.
     * @throws java.io.IOException 
     */   
    public boolean onWriteInterest(SelectionKey key,Context controllerCtx)
        throws IOException; 
    
    
    /**
     * Handle OP_CONNECT.
     * @param key {@link SelectionKey}
     * @param controllerCtx {@link Context}
     * @return true if and only if the ProtocolChain must be invoked after
     *              executing this method.
     * @throws java.io.IOException 
     */    
    public boolean onConnectInterest(SelectionKey key,Context controllerCtx)
        throws IOException; 
    
    
    /**
     * Return an instance of the {@link ConnectorHandler}
     * @return {@link ConnectorHandler}
     */
    public ConnectorHandler acquireConnectorHandler();
    
    
    /**
     * Release a ConnectorHandler.
     * @param connectorHandler {@link ConnectorHandler}
     */
    public void releaseConnectorHandler(ConnectorHandler connectorHandler);
    
    
    /**
     * Configure the channel operations.
     * @param channel {@link SelectableChannel} to configure
     * @throws java.io.IOException on possible configuration related error
     */
    public void configureChannel(SelectableChannel channel) throws IOException;
    
    
    /**
     * Returns {@link AsyncQueueReader} associated with this 
     * {@link SelectorHandler}. Method will return null, if this 
     * {@link SelectorHandler} is not running.
     * 
     * @return {@link AsyncQueueReader}
     */
    public AsyncQueueReader getAsyncQueueReader();

    
    /**
     * Returns {@link AsyncQueueWriter} associated with this 
     * {@link SelectorHandler}. Method will return null, if this 
     * {@link SelectorHandler} is not running.
     * 
     * @return {@link AsyncQueueWriter}
     */
    public AsyncQueueWriter getAsyncQueueWriter();

        
    /**
     * Return the {@link ExecutorService} used to execute this
     * {@link SelectorHandler}'s {@link SelectionKey} ops
     * @return The thread pool to use, or null if the {@link Controller}'s
     * {@link ExecutorService} should be used.
     */
    public ExecutorService getThreadPool();
    
    
    /**
     * Set the {@link ExecutorService} used to execute this
     * {@link SelectorHandler}'s {@link SelectionKey} ops
     * @param The thread pool to use, or null if the {@link Controller}'s
     * {@link ExecutorService} should be used.
     */
    public void setThreadPool(ExecutorService threadPool);

    
    /**
     * Get the preffered SelectionKeyHandler implementation for this SelectorHandler.
     */
    public Class<? extends SelectionKeyHandler> getPreferredSelectionKeyHandler();
    

    /**
     * Get the SelectionKeyHandler associated with this SelectorHandler.
     */
    public SelectionKeyHandler getSelectionKeyHandler();

    
    /**
     * Set SelectionKeyHandler associated with this SelectorHandler.
     */
    public void setSelectionKeyHandler(SelectionKeyHandler selectionKeyHandler);
    
    
    /**
     * Set the {@link ProtocolChainInstanceHandler} to use for 
     * creating instance of {@link ProtocolChain}.
     */
    public void setProtocolChainInstanceHandler(
            ProtocolChainInstanceHandler protocolChainInstanceHandler);
    
    
    /**
     * Return the {@link ProtocolChainInstanceHandler}
     */
    public ProtocolChainInstanceHandler getProtocolChainInstanceHandler();
    
    /**
     * Closes {@link SelectableChannel}
     */
    public void closeChannel(SelectableChannel channel);

}
