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

package com.sun.grizzly.nio.tmpselectors;

import com.sun.grizzly.Grizzly;
import com.sun.grizzly.nio.SelectorFactory;
import java.io.IOException;
import java.nio.channels.Selector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author oleksiys
 */
public class TemporarySelectorPool {
    private static Logger logger = Grizzly.logger(TemporarySelectorPool.class);
    
    public static final int DEFAULT_SELECTORS_COUNT = 32;
    
    /**
     * The timeout before we exit.
     */
    public long timeout = 5000;
    
    
    /**
     * The number of <code>Selector</code> to create.
     */
    private int size;
    
    private boolean isClosed;
    
    /**
     * Cache of <code>Selector</code>
     */
    private ArrayBlockingQueue<Selector> selectors;
    
    /**
     * Read write access lock
     */
    private ReentrantReadWriteLock readwriteLock;

    public TemporarySelectorPool() {
        this(DEFAULT_SELECTORS_COUNT);
    }
    
    public TemporarySelectorPool(int selectorsCount) {
        readwriteLock = new ReentrantReadWriteLock();
        this.size = selectorsCount;
    }

    public int size() {
        return size;
    }

    public void setSize(int size) throws IOException {
        readwriteLock.writeLock().lock();
        try {
            if (isClosed) return;
        
            if (selectors != null && selectors.size() != size) {
                reallocateQueue(size);
            }
            
            this.size = size;
        } finally {
            readwriteLock.writeLock().unlock();
        }
    }

    public Selector poll() throws IOException {
        if (selectors == null) {
            initializeQueue();
        }
        
        readwriteLock.readLock().lock();
        try {
            if (isClosed) return null;
            
            return selectors.poll(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return null;
        } finally {
            readwriteLock.readLock().unlock();
        }
    }

    public void offer(Selector selector) {
        readwriteLock.readLock().lock();
        try {
            if (selectors == null || isClosed || !selectors.offer(selector)) {
                closeSelector(selector);
            }
        } finally {
            readwriteLock.readLock().unlock();
        }
    }
    
    public void close() {
        readwriteLock.writeLock().lock();
        try {
            if (selectors == null) return;
            
            for(Selector selector : selectors) {
                closeSelector(selector);
            }
            selectors.clear();
            
        } finally {
            isClosed = true;
            selectors = null;
            
            readwriteLock.writeLock().unlock();
        }
    }

    /**
     * Initialize Selector queue only if it was not initialized before
     * 
     * @throws java.io.IOException
     */
    private void initializeQueue() throws IOException {
        readwriteLock.writeLock().lock();
        try {
            
            if (!isClosed && selectors == null) {
                reallocateQueue(size);
            }
            
        } finally {
            readwriteLock.writeLock().unlock();
        }
    }
    
    private void reallocateQueue(int newSelectorsCount) throws IOException {
        ArrayBlockingQueue<Selector> newSelectors = 
                new ArrayBlockingQueue<Selector>(newSelectorsCount);
        
        int newCopiedAmount = 0;
        
        // If old Selectors queue is not null
        if (selectors != null) {
            boolean oldHasRemainder = true;
            
            // Copy as much as possible Selectors from the old queue to the new one
            for(int i=0; i<newSelectorsCount; i++) {
                Selector toCopy = selectors.poll();
                if (toCopy == null) {
                    oldHasRemainder = false;
                    break;
                }

                newSelectors.add(toCopy);
                newCopiedAmount++;
            }

            // If there are Selectors left in the old queue - close them
            if (oldHasRemainder) {
                for(Selector selector : selectors) {
                    closeSelector(selector);
                }
            }
            
            selectors.clear();
        }
        
        // Complete new Selector queue initialization
        for(int i=newCopiedAmount; i<newSelectorsCount; i++) {
            newSelectors.add(SelectorFactory.instance().create());
        }
        
        selectors = newSelectors;
        size = newSelectorsCount;
    }


    private void closeSelector(Selector selector) {
        try {
            selector.close();
        } catch (IOException e) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "TemporarySelectorFactory: error " +
                        "occurred when trying to close the Selector", e);
            }
        }
    }
}
