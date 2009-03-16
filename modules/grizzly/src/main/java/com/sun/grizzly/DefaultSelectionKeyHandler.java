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

import com.sun.grizzly.util.Copyable;
import com.sun.grizzly.util.SelectionKeyAttachment;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.logging.Level;


/**
 * Default implementation of a SelectionKey Handler. By default, this
 * class will attach a Long to a SelectionKey in order to calculate the
 * time a SelectionKey can stay active. By default, a SelectionKey will be
 * active for 30 seconds. If during that 30 seconds the client isn't pushing
 * bytes (or closing the connection). the SelectionKey will be expired and
 * its channel closed.
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultSelectionKeyHandler extends BaseSelectionKeyHandler {


    /**
     * Next time the exprireKeys() will delete keys.
     */
    protected long nextKeysExpiration = 0;
    
    
    /*
     * Number of seconds before idle keep-alive connections expire
     */
    protected long timeout = 30 * 1000L;

    
    public DefaultSelectionKeyHandler() {
    }
   
    
    public DefaultSelectionKeyHandler(SelectorHandler selectorHandler) {
        super(selectorHandler);
    }

   
    /**
     * {@inheritDoc}
     */
    @Override
    public void copyTo(Copyable copy) {
        super.copyTo(copy);
        DefaultSelectionKeyHandler copyHandler = (DefaultSelectionKeyHandler) copy;
        copyHandler.timeout = timeout;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(SelectionKey key) {
        super.process(key);
        removeExpirationStamp(key);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void postProcess(SelectionKey key) {
        super.postProcess(key);
        addExpirationStamp(key);
    }
    
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void register(Iterator<SelectionKey> keyIterator, int selectionKeyOps) {
        long currentTime = System.currentTimeMillis();
        SelectionKey key;
        while (keyIterator.hasNext()) {
            key = keyIterator.next();
            keyIterator.remove();
            doRegisterKey(key, selectionKeyOps, currentTime);
        }
    }
    
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void register(SelectionKey key, int selectionKeyOps) {
        doRegisterKey(key, selectionKeyOps, System.currentTimeMillis());
    }
    
    /**
     * Registers {@link SelectionKey} to handle certain operations
     */
    protected void doRegisterKey(SelectionKey key, int selectionKeyOps,
            long currentTime) {
        if (!key.isValid()) {
            return;
        }

            key.interestOps(key.interestOps() | selectionKeyOps);
            addExpirationStamp(key);
        }

    /**
     * {@inheritDoc}
     */
    @Override
    public void register(SelectableChannel channel, int ops) 
            throws ClosedChannelException {
        if (!channel.isOpen()) {
            return;
        }

        Selector selector = selectorHandler.getSelector();
        SelectionKey key = channel.keyFor(selector);
        long time = System.currentTimeMillis();
        
        if (key == null) {
            key = channel.register(selector, ops, time);
        } else {
            doRegisterKey(key, ops, time);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("empty-statement")
    public void register(SelectionKey key, long currentTime){
       ;
    }
    
    
    /**
     * @deprecated
     */
    @Override
    @SuppressWarnings("empty-statement")
    public void expire(SelectionKey key, long currentTime) {
        ;
    }
    
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void expire(Iterator<SelectionKey> iterator) {
        if (timeout <= 0) return;
        
        long currentTime = System.currentTimeMillis();
        if (currentTime < nextKeysExpiration) {
            return;
        }
        nextKeysExpiration = currentTime + timeout;        
                
        
        SelectionKey key;
        while (iterator.hasNext()) {
            key = iterator.next();
            
            if (!key.isValid()){
                continue;
            }


            long expire = getExpirationStamp(key);
            if (expire != SelectionKeyAttachment.UNLIMITED_TIMEOUT){
                if (currentTime - expire >= timeout) {
                    cancel(key);
                } else if (expire + timeout < nextKeysExpiration) {
                    nextKeysExpiration = expire + timeout;
                }
            }
        }
    }
    
    
    public long getTimeout() {
        return timeout;
    }
    
    
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
    
    
    /**
     * Removes expiration timeout stamp from the {@link SelectionKey} 
     * depending on its attachment
     * 
     * @param {@link SelectionKey}
     */
    private void removeExpirationStamp(SelectionKey key) {
        Object attachment = key.attachment();
        if (attachment != null) {
            if (attachment instanceof Long) {
                key.attach(null);
            } else if (attachment instanceof SelectionKeyAttachment) {
                ((SelectionKeyAttachment) attachment).setTimeout(
                        SelectionKeyAttachment.UNLIMITED_TIMEOUT);
            }
        }
    }
    
    /**
     * Adds expiration timeout stamp to the {@link SelectionKey} 
     * depending on its attachment
     * 
     * @param {@link SelectionKey}
     */
    protected void addExpirationStamp(SelectionKey key) {
        long currentTime = System.currentTimeMillis();
        Object attachment = key.attachment();
        if (attachment == null) {
            key.attach(currentTime);
        } else if (attachment instanceof SelectionKeyAttachment) {
            ((SelectionKeyAttachment) attachment).setTimeout(currentTime);
        }    
    }
    
    /**
     * Gets expiration timeout stamp from the {@link SelectionKey} 
     * depending on its attachment
     * 
     * @param {@link SelectionKey}
     */
    private long getExpirationStamp(SelectionKey key) {
        Object attachment = key.attachment();
        if (attachment != null) {
            try {

                // This is extremely bad to invoke instanceof here but 
                // since the framework expose the SelectionKey, an application
                // can always attach an object on the SelectionKey and we 
                // can't predict the type of the attached object.                                
                if (attachment instanceof Long) {
                    return (Long) attachment;
                } else if (attachment instanceof SelectionKeyAttachment) {
                    return ((SelectionKeyAttachment) attachment).getTimeout();
                }
            } catch (ClassCastException ex) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, 
                            "Invalid SelectionKey attachment", ex);
                }
            }
        }

        return SelectionKeyAttachment.UNLIMITED_TIMEOUT;
    }
}
