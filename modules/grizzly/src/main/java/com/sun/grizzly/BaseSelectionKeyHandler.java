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

package com.sun.grizzly;

import com.sun.grizzly.util.ConnectionCloseHandler;
import com.sun.grizzly.util.Copyable;
import com.sun.grizzly.util.SelectionKeyActionAttachment;
import com.sun.grizzly.util.SelectionKeyAttachment;
import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is an implementation of a SelectionKeyHandler which does 
 * not use the SelectionKey attachment, does not expire keys or utilize 
 * a keep-alive mechanism. However, this is currently not the 
 * SelectionKeyHandler provisioned by default with Grizzly's Controller. 
 * Hence for an application to use this SelectionKeyHandler, Grizzly's
 * Controller must be explicitly configured to use this SelectionKeyHandler
 * implementation.
 * 
 * @author Charlie Hunt
 */
public class BaseSelectionKeyHandler implements SelectionKeyHandler {

    protected Logger logger = Controller.logger();

    private ConnectionCloseHandler cch = new ConnectionCloseHandler() {

        public void locallyClosed(SelectionKey key) {
            if (logger.isLoggable(Level.FINE)){
                logger.fine(key + " is being locally cancelled");
            }
        }

        public void remotlyClosed(SelectionKey key) {
            if (logger.isLoggable(Level.FINE)){
                logger.fine(key + " is being remotly cancelled (connection closed)");
            }
        }
    };
    
    /**
     * Associated {@link SelectorHandler}
     */
    protected SelectorHandler selectorHandler;

    
    public BaseSelectionKeyHandler() {
    }
   
    
    public BaseSelectionKeyHandler(SelectorHandler selectorHandler) {
        this.selectorHandler = selectorHandler;
    }

    
    /**
     * {@inheritDoc}
     */
    public SelectorHandler getSelectorHandler() {
        return selectorHandler;
    } 


    /**
     * {@inheritDoc}
     */
    public void setSelectorHandler(SelectorHandler selectorHandler) {
        this.selectorHandler = selectorHandler;
    }


    /**
     * {@inheritDoc}
     */
    public void process(SelectionKey key) {
        Object attachment = key.attachment();
        
        // NOTE: If attachment == null, instanceof will evaluate to false.
        //       If attachment == null does not generate NullPointerException
        if (attachment instanceof SelectionKeyActionAttachment) {
            ((SelectionKeyActionAttachment) attachment).process(key);
        }
    }


    /**
     * {@inheritDoc}
     */
    public void postProcess(SelectionKey key) {
        Object attachment = key.attachment();
        
        // NOTE: If attachment == null, instanceof will evaluate to false.
        //       If attachment == null does not generate NullPointerException
        if (attachment instanceof SelectionKeyActionAttachment) {
            ((SelectionKeyActionAttachment) attachment).postProcess(key);
        }
    }


    /**
     * @deprecated
     */
    public void register(SelectionKey key, long currentTime) {
        throw new UnsupportedOperationException(getClass().getName() + 
                        " use of this API not allowed.");
    }


    /**
     * {@inheritDoc}
     */
    public void register(SelectionKey key, int selectionKeyOps) {
        doRegisterKey(key, selectionKeyOps);
    }

    
    /**
     * Registers {@link SelectionKey} to handle certain operations
     */
    protected void doRegisterKey(SelectionKey key, int selectionKeyOps) {
        if (!key.isValid()) {
            return;
        }
        key.interestOps(key.interestOps() | selectionKeyOps);
    }


    /**
     * {@inheritDoc}
     */
    public void register(SelectableChannel channel, int selectionKeyOps) throws ClosedChannelException {
        if (!channel.isOpen()) {
            return;
        }
        Selector selector = selectorHandler.getSelector();
        SelectionKey key = channel.keyFor(selector);
        if (key == null) {
            channel.register(selector, selectionKeyOps);
        } else {
            doRegisterKey(key, selectionKeyOps);
        }
    }


    /**
     * {@inheritDoc}
     */
    public void register(Iterator<SelectionKey> keyIterator, int selectionKeyOps) {
        SelectionKey key;
        while (keyIterator.hasNext()) {
            key = keyIterator.next();
            keyIterator.remove();
            doRegisterKey(key, selectionKeyOps);
        }
    }


    /**
     * @deprecated
     */
    public void expire(SelectionKey key, long currentTime) {
        // Do nothing.
    }


    /**
     * {@inheritDoc}
     */
    public void expire(Iterator<SelectionKey> keyIterator) {
        // Do nothing.
    }


    /**
     * if SelectionKey is valid, its canceled .
     * {@link doAfterKeyCancel(SelectionKey key)} is called even if key is invalid.
     * @param key {@link SelectionKey} to cancel
     */
    public void cancel(SelectionKey key) {
        if (key != null ) {
            if (key.isValid()){
                key.cancel();
            }
            doAfterKeyCancel(key);
        }
    }

    /**
     * performed when a key is canceled.<br>
     * closes the channel and notifies {@link ConnectionCloseHandler } ,
     * if SelectionKey.attachment() instanceof {@link SelectionKeyAttachment} then
     * its release method is called.
     */
    protected void doAfterKeyCancel(SelectionKey key){
        try{
            if (selectorHandler != null) {
                selectorHandler.closeChannel(key.channel());
            } else {
                closeChannel(key.channel());
            }
        }finally{
            cch.locallyClosed(key);
            Object attachment = key.attach(null);
            if (attachment instanceof SelectionKeyAttachment) {
                ((SelectionKeyAttachment) attachment).release(key);
            }            
        }
    }

    
    /**
     * Notify a {@link ConnectionCloseHandler} that a remote connection
     * has been closed.
     * 
     * @param key a {@link Selectionkey}
     */
    public void notifyRemotlyClose(SelectionKey key){
        cch.remotlyClosed(key);
    }
    

    /**
     * {@inheritDoc}
     */
    public void close(SelectionKey key) {
        doAfterKeyCancel(key);
    }

    /**
     * Close the underlying {@link SelectableChannel}
     * @param channel
     */
    private void closeChannel(SelectableChannel channel) {
        try {
            if (channel instanceof SocketChannel) {
                Socket socket = ((SocketChannel) channel).socket();
                try {
                    if (!socket.isInputShutdown()) {
                        socket.shutdownInput();
                    }
                } catch (IOException ex) {
                    Logger.getLogger(BaseSelectionKeyHandler.class.getName()).
                               log(Level.FINE, null, ex);
                }
                try {
                    if (!socket.isOutputShutdown()) {
                        socket.shutdownOutput();
                    }
                } catch (IOException ex) {
                    Logger.getLogger(BaseSelectionKeyHandler.class.getName()).
                               log(Level.FINE, null, ex);
                }
                try {
                    socket.close();
                } catch (IOException ex) {
                    Logger.getLogger(BaseSelectionKeyHandler.class.getName()).
                               log(Level.FINE, null, ex);
                }
            }
            channel.close();
        } catch (IOException ex) {
            Logger.getLogger(BaseSelectionKeyHandler.class.getName()).
                       log(Level.FINE, null, ex);
        }
    }

    /**
     * Return the {@link ConnectionClosedHandler}.
     * @return the {@link ConnectionClosedHandler}
     */
    public ConnectionCloseHandler getConnectionCloseHandler() {
        return cch;
    }

    /**
     * Set the the {@link ConnectionClosedHandler}
     * @param cch {@link ConnectionClosedHandler}
     */
    public void setConnectionCloseHandler(ConnectionCloseHandler cch) {
        this.cch = cch;
    }

    /**
     * {@inheritDoc}
     */
    public void copyTo(Copyable copy) {
        BaseSelectionKeyHandler copyHandler = (BaseSelectionKeyHandler) copy;
        copyHandler.selectorHandler = selectorHandler;
        copyHandler.cch = cch;
    }
    
    public Logger getLogger() {
        return logger;
    }


    public void setLogger(Logger logger) {
        this.logger = logger;
    }
}
