/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.nio.tmpselectors;

import org.glassfish.grizzly.Grizzly;
import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.nio.Selectors;

/**
 *
 * @author oleksiys
 */
public class TemporarySelectorPool {
    private static final Logger LOGGER = Grizzly.logger(TemporarySelectorPool.class);
    
    public static final int DEFAULT_SELECTORS_COUNT = 32;

    private static final int MISS_THRESHOLD = 10000;
    
    /**
     * The max number of <code>Selector</code> to create.
     */
    private volatile int maxPoolSize;
    
    private final AtomicBoolean isClosed;
    
    /**
     * Cache of <code>Selector</code>
     */
    private final Queue<Selector> selectors;

    /**
     * The current number of Selectors in the pool.
     */
    private final AtomicInteger poolSize;

    /**
     * Number of times poll execution didn't find the available selector in the pool.
     */
    private final AtomicInteger missesCounter;
    
    private final SelectorProvider selectorProvider;
    
    public TemporarySelectorPool(final SelectorProvider selectorProvider) {
        this(selectorProvider, DEFAULT_SELECTORS_COUNT);
    }
    
    public TemporarySelectorPool(final SelectorProvider selectorProvider,
            final int selectorsCount) {
        this.selectorProvider = selectorProvider;
        this.maxPoolSize = selectorsCount;
        isClosed = new AtomicBoolean();
        selectors = new ConcurrentLinkedQueue<Selector>();
        poolSize = new AtomicInteger();
        missesCounter = new AtomicInteger();
    }

    public synchronized int size() {
        return maxPoolSize;
    }

    public synchronized void setSize(int size) throws IOException {
        if (isClosed.get()) {
            return;
        }

        missesCounter.set(0);
        this.maxPoolSize = size;
    }

    public SelectorProvider getSelectorProvider() {
        return selectorProvider;
    }
    
    public Selector poll() throws IOException {
        Selector selector = selectors.poll();

        if (selector != null) {
            poolSize.decrementAndGet();
        } else {
            try {
                selector = Selectors.newSelector(selectorProvider);
            } catch (IOException e) {
               LOGGER.log(Level.WARNING,
                       LogMessages.WARNING_GRIZZLY_TEMPORARY_SELECTOR_POOL_CREATE_SELECTOR_EXCEPTION(),
                       e);
            }

            final int missesCount = missesCounter.incrementAndGet();
            if (missesCount % MISS_THRESHOLD == 0) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_TEMPORARY_SELECTOR_POOL_MISSES_EXCEPTION(missesCount, maxPoolSize));
            }
        }

        return selector;
    }

    public void offer(Selector selector) {
        if (selector == null) {
            return;
        }
        
        final boolean wasReturned;

        if (poolSize.getAndIncrement() < maxPoolSize
                && (selector = checkSelector(selector)) != null) {

            selectors.offer(selector);
            wasReturned = true;
        } else {
            poolSize.decrementAndGet();
            
            if (selector == null) {
                return;
            }
            
            wasReturned = false;
        }

        if (isClosed.get()) {
            if (selectors.remove(selector)) {
                closeSelector(selector);
            }
        } else if (!wasReturned) {
            closeSelector(selector);
        }
    }
    
    public synchronized void close() {
        if (!isClosed.getAndSet(true)) {
            Selector selector;
            while ((selector = selectors.poll()) != null) {
                closeSelector(selector);
            }
        }
    }

    private void closeSelector(Selector selector) {
        try {
            selector.close();
        } catch (IOException e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "TemporarySelectorFactory: error " +
                        "occurred when trying to close the Selector", e);
            }
        }
    }

    private Selector checkSelector(final Selector selector) {
        try {
            selector.selectNow();
            return selector;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_TEMPORARY_SELECTOR_POOL_SELECTOR_FAILURE_EXCEPTION(),
                    e);
            try {
                return Selectors.newSelector(selectorProvider);
            } catch (IOException ee) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_TEMPORARY_SELECTOR_POOL_CREATE_SELECTOR_EXCEPTION(),
                        ee);
            }
        }

        return null;
    }
}
