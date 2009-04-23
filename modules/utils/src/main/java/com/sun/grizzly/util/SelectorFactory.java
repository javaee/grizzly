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
import java.util.EmptyStackException;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory used to dispatch/share {@link Selector}.
 *
 * @author Scott Oaks
 * @author Jean-Francois Arcand
 */
public class SelectorFactory{
    
    public static final int DEFAULT_MAX_SELECTORS = 20;
    
    /**
     * The timeout before we exit.
     */
    public static long timeout = 5000;
    
    
    /**
     * The number of {@link Selector} to create.
     */
    private static int maxSelectors = DEFAULT_MAX_SELECTORS;
    
    
    /**
     * Cache of {@link Selector}
     */
    private final static Stack<Selector> selectors = new Stack<Selector>();
    
    
    /**
     * have we created the Selector instances.
     */
    private static boolean initialized = false;
    
    /**
     * Set max selector pool size.
     * @param size max pool size
     */
    public final static void setMaxSelectors(int size) throws IOException {
        synchronized(selectors) {
            if (size > maxSelectors || !initialized) {
                // if not initialized yet - grow cache by size
                if (!initialized) maxSelectors = 0;
                
                grow(size);
            }  else if (size < maxSelectors) {
                reduce(size);
            }
            
            maxSelectors = size;
            initialized = true;
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
     * Get a exclusive {@link Selector}
     * @return {@link Selector}
     */
    public final static Selector getSelector() {
        synchronized(selectors) {
            if (!initialized) {
                try {
                    setMaxSelectors(maxSelectors);
                } catch (IOException e) {
                    Logger logger = LoggerUtils.getLogger();
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "SelectorFactory lazy initialization", e);
                    }
                }
            }
            
            Selector s = null;
            try {
                if ( selectors.size() != 0 )
                    s = selectors.pop();
            } catch (EmptyStackException ex){}
                       
            int attempts = 0;
            try{
                while (s == null && attempts < 2) {
                    selectors.wait(timeout);
                    try {
                        if ( selectors.size() != 0 )
                            s = selectors.pop();
                    } catch (EmptyStackException ex){
                        break;
                    }
                    attempts++;
                }
            } catch (InterruptedException ex){}
            return s;
        }
    }


    /**
     * Return the {@link Selector} to the cache
     * @param s {@link Selector}
     */
    public final static void returnSelector(Selector s) {
        synchronized(selectors) {
            selectors.push(s);
            if (selectors.size() == 1)
                selectors.notify();
        }
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
     * Increase {@link Selector} pool size
     */
    private static void grow(int size) throws IOException {
        for(int i=0; i<size - maxSelectors; i++) {
            selectors.add(createSelector());
        }
    }

    /**
     * Decrease {@link Selector} pool size
     */
    private static void reduce(int size) {
        for(int i=0; i<maxSelectors - size; i++) {
            try {
                Selector selector = selectors.pop();
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
