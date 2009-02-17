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
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
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
