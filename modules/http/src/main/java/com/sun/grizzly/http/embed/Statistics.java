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

package com.sun.grizzly.http.embed;

import com.sun.grizzly.http.KeepAliveStats;
import com.sun.grizzly.http.StatsThreadPool;
import com.sun.grizzly.http.ThreadPoolStatistic;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.tcp.RequestGroupInfo;

/**
 * <p>This class can be used to monitor the {@link GrizzlyWebServer}. The statistics
 * available from this class are:<ul>{@link ThreadPoolStatistic}: Statistics about
 * the thread pool (called {@link ExecutorService} in Grizzly), {@link StatsThreadPool}.
 * </li><li>{@link KeepAliveStats}: Statistic about how the http keep-alive mechanism like 
 * how many times a connection was re-used, how many request were using http 1.1 keep-alive, 
 * etc.</li><li>{@link RequestGroupInfo}: Statistics about how many connection are 
 * currently supported by Grizzly, how many many bytes has been read so far, how many
 * http request with status code of 200, 404, etc.</li></ul></p>
 * 
 * <p>To use this class, just do:</p><p><pre><code>
    GrizzlyWebServer ws = new GrizzlyWebServer("/var/www");
    Statistics stats = ws.getStatistic();
    stats.startGatheringStatistics();
    ws.start();
 * </code></pre></p>
 * 
 * @author Jeanfrancois Arcand
 */
public class Statistics {

    /**
     * The underlying SelectorThread.
     */
    private SelectorThread st;
        
    
    /**
     * Are we gathering
     */
    private boolean isGathering = false;
    
    
    /**
     * Create a Statistic class powered uner the hood by the {@link SelectorThread}.
     * @param st the {@link SelectorThread}
     */
    protected Statistics(SelectorThread st){
        this.st=st;
    }
    
    
    /**
     * Return an instance of {@link ThreadPoolStatistic}, which gather information
     * about the current thread pool used by Grizzly.
     * @return an instance of {@link ThreadPoolStatistic}, which gather information
     * about the current thread pool used by Grizzly.
     */
    public ThreadPoolStatistic getThreadPoolStatistics(){
        return ((StatsThreadPool)st.getThreadPool())
                .getStatistic();
    }
    
    
    /**
     * Return an instance of {@link KeepAliveStats} , which gather information
     * about the connection and the keep-alive mechanism.
     * @return an instance of {@link KeepAliveStats} , which gather information
     * about the connection and the keep-alive mechanism.
     */
    public KeepAliveStats getKeepAliveStatistics(){
        return st.getKeepAliveStats();
    }
    
    
    /**
     * Return an instance of {@link RequestGroupInfo} , which gather information
     * about all the requests made to Grizzly.
     * @return an instance of {@link RequestGroupInfo} , which gather information
     * about all the requests made to Grizzly.
     */
    public RequestGroupInfo getRequestStatistics(){
        return st.getRequestGroupInfo();
    }
    
    
    /**
     * Start gathering statistics.
     */
    public void startGatheringStatistics(){   
        if (isGathering) return;
        isGathering = true;
        st.enableMonitoring();
    }
    
    
    /** 
     * Stop gathering statistics.
     */
    public void stopGatheringStatistics(){
        if (!isGathering) return;
        isGathering = false;        
        st.disableMonitoring();
    }
}
