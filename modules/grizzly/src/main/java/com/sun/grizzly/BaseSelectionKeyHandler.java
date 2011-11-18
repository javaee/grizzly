/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.util.ConnectionCloseHandler;
import com.sun.grizzly.util.ConnectionCloseHandlerNotifier;
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
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArraySet;
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
public class BaseSelectionKeyHandler implements SelectionKeyHandler, ConnectionCloseHandlerNotifier {

    protected Logger logger = Controller.logger();

    private Collection<ConnectionCloseHandler> cchSet =
            new CopyOnWriteArraySet<ConnectionCloseHandler>();
    
    /**
     * Associated {@link SelectorHandler}
     */
    protected SelectorHandler selectorHandler;

    
    public BaseSelectionKeyHandler() {
    }
   
    
    public BaseSelectionKeyHandler(SelectorHandler selectorHandler) {
        this.selectorHandler = selectorHandler;
    }

    
    public SelectorHandler getSelectorHandler() {
        return selectorHandler;
    } 


    public void setSelectorHandler(SelectorHandler selectorHandler) {
        this.selectorHandler = selectorHandler;
    }


    public void process(SelectionKey key) {
        Object attachment = key.attachment();
        
        // NOTE: If attachment == null, instanceof will evaluate to false.
        //       If attachment == null does not generate NullPointerException
        if (attachment instanceof SelectionKeyActionAttachment) {
            ((SelectionKeyActionAttachment) attachment).process(key);
        }
    }


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


    public void register(SelectionKey key, int selectionKeyOps) {
        doRegisterKey(key, selectionKeyOps);
    }

    
    /**
     * Registers {@link SelectionKey} to handle certain operations
     */
    protected void doRegisterKey(SelectionKey key, int selectionKeyOps) {
        if (key.isValid()) {
            key.interestOps(key.interestOps() | selectionKeyOps);
        }        
    }


    public void register(SelectableChannel channel, int selectionKeyOps) throws ClosedChannelException {
        if (!channel.isOpen()) {
            return;
        }
        Selector selector = selectorHandler.getSelector();
        channel.register(selector, selectionKeyOps);
    }

    public void register(SelectableChannel channel, int selectionKeyOps,
            Object attachment) throws ClosedChannelException {
        if (!channel.isOpen()) {
            return;
        }
        Selector selector = selectorHandler.getSelector();
        channel.register(selector, selectionKeyOps, attachment);
    }


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


    public void expire(Iterator<SelectionKey> keyIterator) {
        // Do nothing.
    }


    /**
     * if SelectionKey is valid, its canceled .
     * {@link #doAfterKeyCancel(SelectionKey)} is called even if key is invalid.
     * @param key {@link SelectionKey} to cancel
     */
    public void cancel(SelectionKey key) {
        if (key != null ) {
            doAfterKeyCancel(key);
        }
    }

    /**
     * Performed when a key is canceled.<br>
     * closes the channel and notifies {@link ConnectionCloseHandler } ,
     * if SelectionKey.attachment() instanceof {@link SelectionKeyAttachment} then
     * its release method is called.
     */
    protected void doAfterKeyCancel(SelectionKey key){
        try {
            if (selectorHandler != null) {
                selectorHandler.closeChannel(key.channel());
            } else {
                closeChannel(key.channel());
            }
        } finally {
            notifyLocallyClose(key);
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
     * @param key a {@link SelectionKey}
     */
    public void notifyRemotlyClose(SelectionKey key) {
        for (ConnectionCloseHandler handler : cchSet) {
            handler.remotelyClosed(key);
        }
    }
    
    /**
     * Notify a {@link ConnectionCloseHandler} that a remote connection
     * has been closed.
     *
     * @param key a {@link SelectionKey}
     */
    public void notifyLocallyClose(SelectionKey key) {
        for (ConnectionCloseHandler handler : cchSet) {
            handler.locallyClosed(key);
        }
    }

    
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
     * Adds the the {@link ConnectionCloseHandler} to a set.
     * @param cch {@link ConnectionCloseHandler}
     */
    public void setConnectionCloseHandler(ConnectionCloseHandler cch) {
        cchSet.add(cch);
    }

    /**
     * Removes the the {@link ConnectionCloseHandler} from a set.
     * @param cch {@link ConnectionCloseHandler}
     */
    public void removeConnectionCloseHandler(ConnectionCloseHandler cch) {
        cchSet.remove(cch);
    }

    public void copyTo(Copyable copy) {
        BaseSelectionKeyHandler copyHandler = (BaseSelectionKeyHandler) copy;
        copyHandler.selectorHandler = selectorHandler;
        copyHandler.cchSet = cchSet;
    }
    
    public Logger getLogger() {
        return logger;
    }


    public void setLogger(Logger logger) {
        this.logger = logger;
    }
}
