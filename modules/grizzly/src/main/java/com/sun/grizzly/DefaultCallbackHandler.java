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

package com.sun.grizzly;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;

/**
 * Default {@link CallbackHandler} implementation that implements the connect
 * operations, and delegate the read and write operations to its associated
 * {@link SelectorHandler} {@link ProtocolChain}, like the default 
 * {@link SelectorHandler} is doing server side component. The code below
 * can be used for by {@link ConnectorHandler} to manipulater the read
 * and write operation of a non blocking client implementation.
 * <p>
 * A {@link ConnectorHandler} can use this {@link CallbackHandler} and delegate
 * the processing of the bytes to a {@link ProtocolChain}
 * </p>
 * <p><pre><code>
 *       Controller sel = new Controller();
 *       sel.setProtocolChainInstanceHandler(new DefaultProtocolChainInstanceHandler(){
 *           public ProtocolChain poll() {
 *               ProtocolChain protocolChain = protocolChains.poll();
 *               if (protocolChain == null){
 *                   protocolChain = new DefaultProtocolChain();
 *                   protocolChain.addFilter(new ReadWriteFilter());
 *                   protocolChain.addFilter(new LogFilter());
 *               }
 *               return protocolChain;
 *           }
 *       });
 *       TCPConnectorHandler tcph = Controller.acquireConnectorHandler(Protocol.TCP);
 *       tcph.connect(....);
 *
 * </code></pre></p><p> With the above example, all read and write operations
 * will be handled by the {@link ProtocolChain} instead of having to be
 * implemented inside the {@link CallbackHandler#onRead} and 
 * {@link CallbackHandler#onWrite}    
 * </p>
 * @author Jeanfrancois Arcand
 */
public class DefaultCallbackHandler implements SSLCallbackHandler<Context> {

    /**
     * Associated {@link ConnectorHandler}
     */
    private ConnectorHandler connectorHandler;

    
    /**
     * <tt>true</tt> if delegation is enabled.
     */
    private boolean delegateToProtocolChain = true;
   
    
    /**
     * Create a {@link CallbackHandler} that delegate the read and write
     * operation to the {@link ProtocolChain} associated with the 
     * {@link ConnectorHandler}
     * @param connectorHandler An instance of {@link ConnectorHandler}
     */
    public DefaultCallbackHandler(ConnectorHandler connectorHandler) {
        this(connectorHandler,true);
    }
    
    
    /**
     * Create a {@link CallbackHandler} that delegate the read and write
     * operation to the {@link ProtocolChain} associated with the 
     * {@link ConnectorHandler}. Delegation is disabled when 
     * @param connectorHandler An instance of {@link ConnectorHandler}
     * @param delegateToProtocolChain true to delegate the read/write operation 
     *        to a {@link ProtocolChain}
     */
    public DefaultCallbackHandler(ConnectorHandler connectorHandler,boolean delegateToProtocolChain) {
        this.connectorHandler = connectorHandler;
        this.delegateToProtocolChain = delegateToProtocolChain;
    }
    
    
    /**
     * Execute the non blocking connect operation.
     * @param ioEvent an {@link IOEvent} representing the current state 
     * of the OP_CONNECT operations.
     */
    public void onConnect(IOEvent<Context> ioEvent) {
        SelectionKey key = ioEvent.attachment().getSelectionKey();
        if (connectorHandler instanceof AbstractConnectorHandler) {
            ((AbstractConnectorHandler) connectorHandler).setUnderlyingChannel(
                    key.channel());
        }
        
        try {
            connectorHandler.finishConnect(key);
            ioEvent.attachment().getSelectorHandler().register(key,
                    SelectionKey.OP_READ);
        } catch (IOException ex) {
            Controller.logger().severe(ex.getMessage());
        }
    }

    
    /**
     * Delegate the processing of the read operation to the {@link IOEvent{
     * associated {@link ProtocolChain}
     * @param ioEvent an {@link IOEvent} representing the current state of the 
     * OP_CONNECT operations.
     */
    public void onRead(IOEvent<Context> ioEvent) {
        if (!delegateToProtocolChain) return;
        Context context = ioEvent.attachment();
        try {
            context.getProtocolChain().execute(context);
        } catch (Exception ex) {
            Controller.logger().log(Level.SEVERE, "Read/Write operation failed.", ex);
        }
    }

    
    /**
     * Delegate the processing of the write operation to the {@link IOEvent{
     * associated {@link ProtocolChain}
     * @param ioEvent an {@link IOEvent} representing the current state of the 
     * OP_CONNECT operations.
     */   
    public void onWrite(IOEvent<Context> ioEvent) {
        onRead(ioEvent);
    }

    
    /**
     * By default, do nothing.
     * @param ioEvent an {@link IOEvent} representing the current state of the 
     * handshake operations.
     */
    public void onHandshake(IOEvent<Context> ioEvent) {        
    }

    
    /**
     * Return <tt>true></tt> if delegation to the {@link ProtocolChain} is enabled.
     * @return <tt>true></tt> if delegation to the {@link ProtocolChain} is enabled.
     */
    public boolean isDelegateToProtocolChain() {
        return delegateToProtocolChain;
    }

    
    /**
     * Set to <tt>true></tt> to enable delagation of the read and write operations
     * to a {@link ProtocolChain} <tt>true></tt> to enable delagation of the read and write operations
     * to a {@link ProtocolChain}
     * @param delegateToProtocolChain
     */
    public void setDelegateToProtocolChain(boolean delegateToProtocolChain) {
        this.delegateToProtocolChain = delegateToProtocolChain;
    }
}
