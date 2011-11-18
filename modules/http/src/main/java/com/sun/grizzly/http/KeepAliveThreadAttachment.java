/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http;

import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.util.ThreadAttachment;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Add keep alive counting mechanism to the {@link ThreadAttachment}.
 * 
 * @author Jeanfrancois Arcand
 */
public class KeepAliveThreadAttachment extends ThreadAttachment{
    protected final static Logger logger = SelectorThread.logger();

    private int keepAliveCount;
     /**
     * The stats object used to gather statistics.
     */
    private KeepAliveStats keepAliveStats;

    private boolean isTimedOut;
    
    
    /**
     * Set the {@link KeepAliveStats} instance used to collect request statistic.
     * @param keepAliveStats the {@link KeepAliveStats} instance used to collect
     *  request statistic.
     */
    public void setKeepAliveStats(KeepAliveStats keepAliveStats){
        this.keepAliveStats = keepAliveStats;
    }
        
    /**
     * Increase the keep alive count by one.
     */
    public int increaseKeepAliveCount(){
        if ( keepAliveCount == 0 ){
            if (keepAliveStats != null && keepAliveStats.isEnabled()) {
                keepAliveStats.incrementCountConnections();
            }
        }
        keepAliveCount++;
        if (keepAliveStats != null && keepAliveStats.isEnabled()) {
            keepAliveStats.incrementCountHits();
        } 
        return keepAliveCount;
    }
    
    /**
     * Reset the keep alive value to 0.
     */
    public void resetKeepAliveCount(){
        if (keepAliveStats != null && keepAliveCount > 0 && keepAliveStats.isEnabled()) {
            keepAliveStats.decrementCountConnections();

            if (!isTimedOut) {
                keepAliveStats.incrementCountFlushes();
            } else {
                isTimedOut = false;
            }
        }
        keepAliveCount = 0;
    }

    public int getKeepAliveCount() {
        return keepAliveCount;
    }
    
    @Override
    public void release(SelectionKey selectionKey) {
        resetKeepAliveCount();
        super.release(selectionKey);
    }


    @Override
    public boolean timedOut(SelectionKey selectionKey) {
        if (!super.timedOut(selectionKey)) {
            return false;
        }
        
        if (keepAliveStats != null && keepAliveCount > 0 && keepAliveStats.isEnabled()) {
            isTimedOut = true;
            keepAliveStats.incrementCountTimeouts();
        }

        Thread t = activeThread();
        if (t != null) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING,
                           LogMessages.WARNING_GRIZZLY_HTTP_IDLE_THREAD_INTERRUPT(t.getName()));
            }
            
            setIdleTimeoutDelay(UNLIMITED_TIMEOUT);
            t.interrupt();
            return false;
        }
        return true;
    }
}
