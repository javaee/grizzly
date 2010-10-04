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
package com.sun.grizzly.util;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory used to dispatch/share {@link Selector}.
 *
 * @author Scott Oaks
 * @author Jean-Francois Arcand
 * @author gustav trede
 */
public final class SelectorFactory {

    private static final int MISS_THRESHOLD = 10000;
    public static final int DEFAULT_MAX_SELECTORS = 20;
    /**
     * The number of {@link Selector} to create.
     */
    private static volatile int maxSelectors = DEFAULT_MAX_SELECTORS;
    /**
     * Cache of {@link Selector}
     */
    private final static Queue<Selector> selectors =
            DataStructures.getCLQinstance(Selector.class);
    
    /**
     * The current number of Selectors in the pool.
     */
    private final static AtomicInteger poolSize = new AtomicInteger();

    /**
     * Number of times poll execution didn't find the available selector in the pool.
     */
    private final static AtomicInteger missesCounter = new AtomicInteger();

    /**
     * Set max selector pool size.
     * @param size max pool size
     */
    public static void setMaxSelectors(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size < 0");
        }

        missesCounter.set(0);
        maxSelectors = size;
    }

    /**
     * Returns max selector pool size
     * @return max pool size
     */
    public static int getMaxSelectors() {
        return maxSelectors;
    }

    /**
     * Please ensure to use try finally around get and return of selector so avoid leaks.
     * Get a exclusive {@link Selector}
     * @return {@link Selector}
     */
    public static Selector getSelector() {
        Selector selector = selectors.poll();

        if (selector != null) {
            poolSize.decrementAndGet();
        } else {
            try {
                selector = Utils.openSelector();
            } catch (IOException e) {
               LoggerUtils.getLogger().log(Level.WARNING,
                         "SelectorFactory. Can not create a selector", e);
            }
            
            final int missesCount = missesCounter.incrementAndGet();
            if (missesCount % MISS_THRESHOLD == 0) {
                LoggerUtils.getLogger().log(Level.WARNING,
                        "SelectorFactory. Pool encounters a lot of misses {0}. "
                        + "Increase default {1} pool size",
                        new Object[] {missesCount, maxSelectors});
            }
        }

        return selector;
    }

    /**
     * Please ensure to use try finally around get and return of selector so avoid leaks.
     * Return the {@link Selector} to the cache
     * @param s {@link Selector}
     */
    public static void returnSelector(Selector s) {
        if (poolSize.getAndIncrement() < maxSelectors) {
            selectors.offer(s);
        } else {
            poolSize.decrementAndGet();
            closeSelector(s);
        }
    }

    /**
     * Executes <code>Selector.selectNow()</code> and returns
     * the {@link Selector} to the cache
     */
    public static void selectNowAndReturnSelector(Selector s) {
        try {
            s.selectNow();
            returnSelector(s);
        } catch (IOException e) {
            Logger logger = LoggerUtils.getLogger();
            logger.log(Level.WARNING,
                    "Unexpected problem when releasing temporary Selector", e);
            
            closeSelector(s);
        }
    }

    private static void closeSelector(Selector s) {
        try {
            s.close();
        } catch (IOException ignored) {
            // We are not interested
        }
    }
}
