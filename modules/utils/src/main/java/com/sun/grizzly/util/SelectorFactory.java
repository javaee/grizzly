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
package com.sun.grizzly.util;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory used to dispatch/share {@link Selector}.
 *
 * @author Scott Oaks
 * @author Jean-Francois Arcand
 * @author gustav trede
 */
public class SelectorFactory{
    
    public static final int DEFAULT_MAX_SELECTORS = 20;
    
        
    /**
     * The number of {@link Selector} to create.
     */
    private static volatile int maxSelectors = DEFAULT_MAX_SELECTORS;
    
    /**
     * Cache of {@link Selector}
     */
    private final static LinkedTransferQueue<Selector> selectors
            = new LinkedTransferQueue<Selector>();
    
    
    /**
     * have we created the Selector instances.
     */
    private static volatile boolean initialized = false;

    /**
     * Set max selector pool size.
     * @param size max pool size
     */
    public final static void setMaxSelectors(int size) throws IOException {
        synchronized(selectors) {
            int toadd = (initialized ?size-maxSelectors:maxSelectors);
            if (toadd>0){
                int a = toadd;
                while(toadd-->0){
                    selectors.add(createSelector());                    
                }
                LoggerUtils.getLogger().severe("**  CREATED SELECTOR in cache **************************** "+ a);
            }else{
                reduce(-toadd);
            }
            maxSelectors = size;
            initialized = true;
        }
    }

    /**
     * Changes the Selector cache size
     * @param delta
     * @throws java.io.IOException
     */
    public final static void changeSelectorsBy(int delta) throws IOException{
        synchronized(selectors) {
            setMaxSelectors(maxSelectors+delta);
        }
    }


    /**
     * Returns max selector pool size
     * @return max pool size
     */
    public final static int getMaxSelectors() {
        return maxSelectors;
    }
    
    /**
     * Please ensure to use try finally around get and return of selector so avoid leaks.
     * Get a exclusive {@link Selector}
     * @return {@link Selector}
     */
    public final static Selector getSelector() {
        if (!initialized){
            try{
                setMaxSelectors(maxSelectors);
            } catch (IOException ex) {
                LoggerUtils.getLogger().log(Level.WARNING,"static init of SelectorFactory failed",ex);
            }
        }
        Selector selector = selectors.poll();
        if (selector == null){
            LoggerUtils.getLogger().warning("Temp Selector leak detected. cachelimit: "+maxSelectors);
        }
        return selector;
    }

    /**
     * Please ensure to use try finally around get and return of selector so avoid leaks.
     * Return the {@link Selector} to the cache
     * @param s {@link Selector}
     */
    public final static void returnSelector(Selector s) {
        selectors.offer(s);
    }

    /**
     * Executes <code>Selector.selectNow()</code> and returns 
     * the {@link Selector} to the cache
     */
    public final static void selectNowAndReturnSelector(Selector s) {
        try { 
            s.selectNow();
            returnSelector(s);
        } catch(IOException e) {
            Logger logger = LoggerUtils.getLogger();
            logger.log(Level.WARNING, 
                    "Unexpected problem when releasing temporary Selector", e);
            try {
                s.close();
            } catch(IOException ee) {
                // We are not interested
            }
            
            try {
                reimburseSelector();
            } catch(IOException ee) {
                logger.log(Level.WARNING,
                        "Problematic Selector could not be reimbursed!", ee);
            }
        }
    }

    /**
     * Add Selector to the cache.
     * This method could be called to reimberse a lost or problematic Selector.
     * 
     * @throws java.io.IOException
     */
    public final static void reimburseSelector() throws IOException {
        returnSelector(createSelector());
    }

    /**
     * Creeate Selector
     * @return Selector
     * @throws java.io.IOException
     */
    protected static Selector createSelector() throws IOException {
        return Selector.open();
    }    

    /**
     * Decrease {@link Selector} pool size
     */
    private static void reduce(int tokill) {
        while(tokill-->0){
            try {
                Selector selector = selectors.poll();
                selector.close();
            } catch(IOException e) {
                Logger logger = LoggerUtils.getLogger();
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "SelectorFactory.reduce", e);
                }
            }
        }
    }

}
