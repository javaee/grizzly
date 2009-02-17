/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.aio;

import com.sun.grizzly.ConnectorHandler;
import com.sun.grizzly.Controller;
import com.sun.grizzly.Handler;
import com.sun.grizzly.ProtocolChainInstanceHandler;
import com.sun.grizzly.util.AttributeHolder;
import com.sun.grizzly.util.Copyable;
import com.sun.grizzly.util.State;
import com.sun.grizzly.util.SupportStateHolder;
import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutorService;


/**
 * Simple AIO based interface implemented by all supported transport.
 * 
 * @author Jeanfrancois Arcand
 */
public interface AIOHandler extends Handler, Copyable, 
        AttributeHolder, SupportStateHolder<State> {  
    
    /**
     * A token decribing the protocol supported by an implementation of this
     * interface
     * @return AIOHandler supported protocol
     */
    public Controller.Protocol protocol();
    

    /**
     * Pause this {@link AIOHandler}
     */
    public void start();    
    
    
    /**
     * Pause this {@link AIOHandler}
     */
    public void pause();
    
    
    /**
     * Resume this {@link AIOHandler}
     */
    public void resume();
    
    
    /**
     * Shutdown this instance.
     */
    public void shutdown();
    
    
    
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
     * Return the {@link ExecutorService} used to execute this
     * {@link AIOHandler}'s {@link SelectionKey} ops
     * @return The thread pool to use, or null if the {@link Controller}'s
     * {@link ExecutorService} should be used.
     */
    public ExecutorService getThreadPool();
    
    
    /**
     * Set the {@link ExecutorService} used to execute this
     * {@link AIOHandler}'s {@link SelectionKey} ops
     * @param The thread pool to use, or null if the {@link Controller}'s
     * {@link ExecutorService} should be used.
     */
    public void setThreadPool(ExecutorService threadPool);

    
    
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
     * Configure the channel operations.
     * @param channel {@link AsynchronousSocketChannel} to configure
     * @throws java.io.IOException on possible configuration related error
     */
    public void configureChannel(AsynchronousSocketChannel channel) throws IOException;   
    
    
    /**
     * Set the {@link AsynchronousChannelGroup} used when creating {@link AsynchronousSocketChannel}.
     */ 
    public void setAynchronousChannelGroup(AsynchronousChannelGroup group);

}
