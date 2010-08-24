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

package com.sun.enterprise.web.connector.grizzly.comet;

import com.sun.enterprise.web.connector.grizzly.SelectorThread;
import com.sun.enterprise.web.connector.grizzly.NioProvider;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NIO {@link Selector} allowing {@link CometHandler} to receive
 * non-blocking requests bytes during request polling.
 *
 * @author Jeanfrancois Arcand
 */
public class CometSelector {

    // Workaround for Issue 555
    private long lastSpinTimestamp;
    private int emptySpinCounter;
    /**
     * The threshold for detecting selector.select spin on linux,
     * used for enabling workaround to prevent server from hanging.
     */
    private final static int spinRateTreshold = 2000;

    /**
     * Enable workaround Linux spinning Selector
     */
    static final boolean isLinux =
            System.getProperty("os.name").equalsIgnoreCase("linux") &&
                !System.getProperty("java.version").startsWith("1.7");

    /**
     * The {@link CometEngine} singleton
     */
    protected CometEngine cometEngine;


    /**
     * The {@link Selector}
     */
    private Selector selector;


    /**
     * Logger.
     */
    private Logger logger = SelectorThread.logger();


    /**
     * The list of {@link SelectionKey} to register with the
     * {@link Selector}
     */
    private ConcurrentHashMap<SelectionKey,CometTask> keysToRegister
        = new ConcurrentHashMap<SelectionKey,CometTask>();


    /**
     * New {@link CometSelector}
     * @param cometEngine The {@link CometEngine} singleton
     */
    public CometSelector(CometEngine cometEngine) {
        this.cometEngine = cometEngine;
    }


    /**
     * Start the {@link Selector} running on its
     * Thread.
     */
    public void start() throws InterruptedException{
        final CountDownLatch isStartedLatch = new CountDownLatch(1);

        new Thread("CometSelector"){
            {
                setDaemon(true);
            }

            @Override
                public void run(){
                    try{
                        selector = Selector.open();
                    } catch(IOException ex){
                        // Most probably a fd leak.
                        logger.log(Level.SEVERE,"CometSelector.open()",ex);
                        return;
                    }
                    isStartedLatch.countDown();
                    while (true){
                        SelectionKey key = null;
                        Set readyKeys;
                        Iterator<SelectionKey> iterator;
                        int selectorState = 0;

                        try{
                            selectorState = 0;

                            try{
                                selectorState = selector.select(1000);
                            } catch (CancelledKeyException ex){
                                if (logger.isLoggable(Level.FINEST)){
                                    logger.log(Level.FINEST,"CometSelector.open()",ex);
                                }
                            }

                            readyKeys = selector.selectedKeys();

                            // JDK issue.
                            if (readyKeys.size() != 0 && isLinux) {
                                resetSpinCounter();
                            } else if (isLinux){
                                long sr = getSpinRate();
                                if (sr > spinRateTreshold) {
                                    workaroundSelectorSpin();
                                }
                            }

                            iterator = readyKeys.iterator();
                            CometTask cometTask;
                            while (iterator.hasNext()) {
                                key = iterator.next();
                                iterator.remove();
                                try {
                                    if (key.isValid()) {
                                        cometTask = (CometTask) key.attachment();
                                        if (key.isReadable()) {
                                            key.interestOps(key.interestOps() &
                                                    (~SelectionKey.OP_READ));
                                            cometTask.upcoming_op =
                                                    CometTask.OP_EVENT.READ;
                                        }

                                        if (key.isWritable()) {
                                            key.interestOps(key.interestOps() &
                                                    (~SelectionKey.OP_WRITE));
                                            cometTask.upcoming_op =
                                                    CometTask.OP_EVENT.WRITE;
                                        }

                                        if (cometTask != null &&
                                                cometTask.getSelectionKey() != null &&
                                                cometTask.getSelectionKey().attachment() == null) {
                                            cometTask.execute();
                                        } else {
                                            key.cancel();
                                        }
                                    } else {
                                        cancelKey(key);
                                    }
                                } catch (Exception e) {
                                    try {
                                        cancelKey(key);
                                    } catch (Exception ee) {
                                        logger.log(Level.SEVERE, "CometSelector", ee);
                                    }
                                }
                            }

                            Iterator<Entry<SelectionKey, CometTask>> entries =
                                keysToRegister.entrySet().iterator();
                            /**
                             * The mainKey is the SelectionKey returned by the
                             * Selector used in the SelectorThread class.
                             */
                            SelectionKey mainKey;
                            SocketChannel channel;
                            while (entries.hasNext()) {
                                final Entry<SelectionKey, CometTask> entry = entries.next();
                                entries.remove();

                                mainKey = entry.getKey();
                                cometTask = entry.getValue();

                                try {
                                    channel = (SocketChannel) mainKey.channel();
                                    if (mainKey.isValid() && channel.isOpen()) {
                                        key = channel.register(selector,
                                                SelectionKey.OP_READ);
                                        cometTask.setCometKey(key);
                                        key.attach(cometTask);
                                    } else {
                                        cancelCometTask(cometTask);
                                    }
                                } catch (Exception e) {
                                    cancelCometTask(cometTask);
                                }
                            }


                            expireIdleKeys();

                            if (selectorState <= 0){
                                selector.selectedKeys().clear();
                            }
                        } catch (Throwable t){
                            if (key != null){
                                try{
                                    cancelKey(key);
                                } catch (Throwable t2){
                                    logger.log(Level.SEVERE,"CometSelector",t2);
                                }
                            }

                            if (selectorState <= 0){
                                selector.selectedKeys().clear();
                            }

                            if (logger.isLoggable(Level.FINEST)){
                                logger.log(Level.FINEST,"CometSelector",t);
                            }
                        }
                    }
                }
        }.start();
        isStartedLatch.await();
    }


    /**
     * Expires registered {@link SelectionKey}. If a
     * {@link SelectionKey} is expired, the request will be resumed and the
     * HTTP request will complete,
     */
    protected void expireIdleKeys(){
        Set<SelectionKey> readyKeys = selector.keys();
        if (readyKeys.isEmpty()){
            return;
        }
        long current = System.currentTimeMillis();
        Iterator<SelectionKey> iterator = readyKeys.iterator();
        SelectionKey key;
        while (iterator.hasNext()) {
            key = iterator.next();

            try {
                final CometTask cometTask = (CometTask) key.attachment();

                if (cometTask == null) {
                    continue;
                }

                if (cometTask.getExpirationDelay() == -1) {
                    continue;
                }

                final long expire = cometTask.getExpireTime();
                if (current - expire >= cometTask.getExpirationDelay()) {
                    cancelKey(key);
                }

                /**
                 * The connection has been resumed since the timeout is
                 * re-attached to the SelectionKey so cancel the Comet key.
                 */
                if (cometTask.getSelectionKey() != null &&
                        cometTask.getSelectionKey().attachment() instanceof Long) {
                    key.cancel();
                    cometEngine.interrupt(key);
                }
            } catch (Exception e) {
                cancelKey(key);
            }
        }
    }


    /**
     * Cancel a {@link SelectionKey}, and delegate the request
     * polling interruption to the {@link CometEngine}
     * @param key the expired {@link SelectionKey}
     */
    protected synchronized void cancelKey(SelectionKey key){
        if (key == null) return;

        try{
            CometTask cometTask = (CometTask)key.attachment();
            if (cometTask != null){
                cancelCometTask(cometTask);
            } else {
                cometEngine.interrupt(key);
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE,"CometSelector",t);
        } finally {
            key.attach(null);
        }
        key.cancel();
    }

    protected synchronized void cancelCometTask(CometTask cometTask){
        if (cometTask == null) return;

        try{
            SelectorThread st = cometTask.getSelectorThread();
            SelectionKey mainKey = cometTask.getSelectionKey();
            if (cometTask.getCometContext() != null) {
                cometTask.getCometContext().interrupt(cometTask);
            }

            cometEngine.interrupt(cometTask);
            st.cancelKey(mainKey);
        } catch (Throwable t) {
            logger.log(Level.SEVERE,"CometSelector",t);
        }
    }

    /**
     * Register the {@link SelectionKey} to the {@link Selector}. We
     * cannot register the {@link SelectionKey} directy on the
     * {@link Selector} because there is a deadlock in the VM (see bug XXX).
     */
    public void registerKey(SelectionKey key, CometTask cometTask){
        if (key == null || cometTask == null || !key.isValid() || selector == null) return;

        cometTask.setExpireTime(System.currentTimeMillis());
        keysToRegister.put(key, cometTask);
        selector.wakeup();
    }


    /**
     * Wakes up the {@link Selector}
     */
    public void wakeup(){
        selector.wakeup();
    }

    /**
     * Return the SelectionKey associated with this channel.
     */
    public SelectionKey cometKeyFor(SelectableChannel channel){
        NioProvider nioP = NioProvider.getProvider();
        if (nioP == null) {
            return channel.keyFor(selector);
        } else {
            return nioP.keyFor(channel, selector);
        }
    }


    public void resetSpinCounter(){
        emptySpinCounter  = 0;
    }

    public int getSpinRate(){
        if (emptySpinCounter++ == 0){
            lastSpinTimestamp = System.nanoTime();
        } else if (emptySpinCounter == 1000) {
            long deltatime = System.nanoTime() - lastSpinTimestamp;
            int contspinspersec = (int) (1000 * 1000000000L / deltatime);
            emptySpinCounter  = 0;
            return contspinspersec;
        }
        return 0;
    }

    private void workaroundSelectorSpin() throws IOException {
        Selector newSelector = Selector.open();

        Set<SelectionKey> keys = selector.keys();
        for (SelectionKey key : keys) {
            try {
                key.channel().register(newSelector, key.interestOps(), key.attachment());
            } catch (Exception e) {
            }
        }
        try {
            selector.close();
        } catch (Exception e) {
        }
        selector = newSelector;
    }
}
