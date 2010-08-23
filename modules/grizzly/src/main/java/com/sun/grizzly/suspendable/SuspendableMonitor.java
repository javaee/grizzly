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

package com.sun.grizzly.suspendable;

import com.sun.grizzly.Controller;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.grizzly.suspendable.SuspendableFilter.KeyHandler;
import com.sun.grizzly.util.DataStructures;
import com.sun.grizzly.util.Utils;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.util.Queue;

/**
 * A secondary {@link Selector} used to keep the state of a suspended
 * connection ({@link SelectionKey}). See  {@link SuspendableFilter} for more info.
 *
 * TODO: Add Pipelining/Multiplexing support.
 * @author Jeanfrancois Arcand
 */
public class SuspendableMonitor implements Runnable {

    /**
     * The {@link Selector}
     */
    private final Selector selector;
    

    private final Queue<KeyHandler> keysToRegister =
            DataStructures.getCLQinstance(KeyHandler.class);
    
    /**
     * Logger.
     */
    private final Logger logger = Controller.logger();

    /**
     * Start a new Thread with a Selector running.
     */
    public SuspendableMonitor() {
        Selector sel = null;
        try {
            sel = Utils.openSelector();
        } catch (IOException ex) {
            throw new RuntimeException("SuspendableMonitor.open()",ex);
        }
        this.selector = sel;
    }


    @SuppressWarnings("empty-statement")
     public void run() {
        while (true) {
            SelectionKey foreignKey = null;
            KeyHandler kh = null;                    
            int selectorState = 0;

            try {
                try {
                    selectorState = selector.select(1000);
                } catch (CancelledKeyException ex) {
                    ;
                }

                Iterator<KeyHandler> keys = 
                        keysToRegister.iterator();

                SelectableChannel channel;
                while (keys.hasNext()){
                    kh = keys.next();
                    channel =  kh.getKey().channel();
                    if (kh.getKey().isValid() && channel.isOpen()) {
                        foreignKey = channel
                            .register(selector,SelectionKey.OP_READ,kh);  
                        kh.setForeignKey(foreignKey);
                        keys.remove();
                    } 
                }  

                expireIdleKeys();

                if (selectorState <= 0) {
                    selector.selectedKeys().clear();
                }
            } catch (Throwable t) {
                logger.log(Level.SEVERE,"SuspendableMonitor",t);
                try{
                    if (kh != null) {
                        try {
                            interrupted(kh.getKey());
                        } catch (Throwable t2) {
                            logger.log(Level.SEVERE, "SuspendableMonitor", t2);
                        }
                    }

                    if (selectorState <= 0) {
                        selector.selectedKeys().clear();
                    }
                } catch (Throwable t2){
                    logger.log(Level.SEVERE, "SuspendableMonitor", t2);
                }
            }
        }
    }


    /**
     * Expire the SelectionKey?
     */
    protected void expireIdleKeys() {
        Set<SelectionKey> readyKeys = selector.keys();
        if (readyKeys.isEmpty()) {
            return;
        }
        long current = System.currentTimeMillis();
        Iterator<SelectionKey> iterator = readyKeys.iterator();
        SelectionKey key;
        while (iterator.hasNext()) {
            key = iterator.next();
            KeyHandler kh = (KeyHandler) key.attachment();
            if (kh == null) {
                return;
            }

            long expire = kh.getRegistrationTime();

            if (expire == -1){
                continue;
            }

            if (current - expire >= kh.getSuspendableHandler().getExpireTime()) {
                kh.setRegistrationTime(-1);
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Expiring: " 
                            + key + " attachment: " + key.attachment());
                }
                try {
                    kh.getSuspendableHandler().getSuspendableHandler()
                            .expired(kh.getSuspendableHandler().getAttachment());
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINE) && kh != null) {
                        logger.log(Level.FINE, "Interrupting: " + t);
                    }
                }
                kh.getSuspendableHandler().getSuspendableFilter()
                        .resume(kh.getKey());                        
            }
        }
    }

    /**
     * Interrupt a suspended SelectionKey that have timed out.
     */
    protected void interrupted(SelectionKey key) {
        key.cancel();

        KeyHandler kh = (KeyHandler) key.attachment();
        kh.getSuspendableHandler().getSelectorHandler()
                .getSelectionKeyHandler().cancel(kh.getKey());
        if (logger.isLoggable(Level.FINE) && kh != null) {
            logger.log(Level.FINE, "Interrupting: " + kh.getKey());
        }

        if (kh != null) {
            kh.getSuspendableHandler().getSuspendableHandler().
                    interupted(kh.getSuspendableHandler().getAttachment());
            kh.getSuspendableHandler().getSuspendableFilter()
                    .suspendedKeys.remove(kh.getKey());
        }
    }
    
    /**
     * Suspend the {@link ReadableChannel} represented by this {@link SuspendableFilter.KeyHandler}
     * by registering it on secondary Selector.
     * @param kh The KeyHandler which hold the current SelectionKey.
     */
    protected void suspend(KeyHandler kh)
            throws ClosedChannelException {
        try{            
            kh.setRegistrationTime(System.currentTimeMillis());
            if (kh.getForeignKey() == null){
                keysToRegister.offer(kh);
                selector.wakeup();
            }
        } catch (Throwable ex){
            logger.log(Level.SEVERE,"suspend exception: " + kh.getKey(), ex);
        }
    }
}
