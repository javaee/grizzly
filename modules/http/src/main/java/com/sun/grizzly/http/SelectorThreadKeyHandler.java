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
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.http;

import com.sun.grizzly.DefaultSelectionKeyHandler;
import com.sun.grizzly.util.Copyable;
import com.sun.grizzly.util.ThreadAttachment;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Default HTTP SelectionKeyHandler implementation
 * 
 * @author Jean-Francois Arcand
 * @author Alexey Stashok
 */
public class SelectorThreadKeyHandler extends DefaultSelectionKeyHandler {
    /**
     * Banned SelectionKey registration.
     */
    protected ConcurrentLinkedQueue<SelectionKey> bannedKeys =
        new ConcurrentLinkedQueue<SelectionKey>(); 
       
    private SelectorThread selectorThread;

    public SelectorThreadKeyHandler() {
    }

    public SelectorThreadKeyHandler(SelectorThread selectorThread) {
        this.selectorThread = selectorThread;
    }

    @Override
    public void copyTo(Copyable copy) {
        SelectorThreadKeyHandler copyHandler = (SelectorThreadKeyHandler) copy;
        copyHandler.selectorThread = selectorThread;
    }

    @Override
    public void doRegisterKey(SelectionKey key, int ops, long currentTime) {
        if (!key.isValid() || !selectorThread.getKeepAliveCounter().trap(key)) {
            selectorThread.cancelKey(key);
            return;
        }
        
        // If the SelectionKey is used for continuation, do not allow
        // the key to be registered.
        if (selectorThread.getEnableAsyncExecution() && !bannedKeys.isEmpty() 
                && bannedKeys.remove(key)){
            return;
        }
        
        key.interestOps(key.interestOps() | ops);
        Object attachment = key.attachment();
        // By default, attachment a null.
        if (attachment == null) {
            key.attach(currentTime);
        } else if (attachment instanceof ThreadAttachment) {
            ((ThreadAttachment) attachment).setTimeout(currentTime);
        }
    }

    @Override
    public void expire(Iterator<SelectionKey> keys) {
        super.expire(keys);
        if (selectorThread.isMonitoringEnabled()) {
            selectorThread.getRequestGroupInfo().decreaseCountOpenConnections();
        }
    }

    @Override
    public void cancel(SelectionKey key) {
        if (key != null) {
            selectorThread.getKeepAliveCounter().untrap(key);
        }
        super.cancel(key);
    }
    
    
    /**
     * Add a <code>SelectionKey</code> to the banned list of SelectionKeys. 
     * A SelectionKey is banned when new registration aren't allowed on the 
     * Selector.
     */
    public void addBannedSelectionKey(SelectionKey key){
        bannedKeys.offer(key);
    }
}