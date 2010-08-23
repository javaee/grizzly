/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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
