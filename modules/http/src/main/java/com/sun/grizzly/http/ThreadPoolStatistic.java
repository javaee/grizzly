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

package com.sun.grizzly.http;

import com.sun.grizzly.util.ExtendedThreadPool;
import com.sun.grizzly.util.WorkerThreadImpl;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * This class is a placeholde for gathering statistic
 * from a {@link ExecutorService}
 *
 * @author Jean-Francois Arcand
 */
public class ThreadPoolStatistic {

    /**
     * The port of which we gather statistics
     */
    private int port = -1;
    
    
    /**
     * Is this object started?
     */ 
    private boolean started = false;
    
    
    /**
     * Maximum pending connection before refusing requests.
     */
    private int maxQueueSizeInBytes = -1;
    
    
    /**
     * The total number of connections queued during the lifetime of the 
     * thread pool
     */
    private int totalCount;
    
    
    /** 
     * The largest number of connections that have been in the thread pool
     * simultaneouly
     */
    private int peakCount;
    
    
    /** 
     * Total number of thread pool overflows
     */
    private int overflowCount;
    
    
    /**
     * The Thread Pool used when gathering count statistic.
     */
    private ScheduledThreadPoolExecutor countAverageExecutor;
    
    
    /**
     * Average number of connection queued in that last 1 minute
     */
    private Statistic lastMinuteStat = new Statistic(1 * 60);
    
    
    /**
     * Average number of connection queued in that last 5 minute
     */
    private Statistic lastFiveMinuteStat = new Statistic(5 * 60);
    
    
    /**
     * Average number of connection queued in that last 15 minute
     */
    private Statistic lastFifteenMinuteStat = new Statistic(15 * 60);
    
    
    /**
     * Placeholder to gather statistics.
     */
    private ConcurrentHashMap<Integer,Statistic> stats = 
            new ConcurrentHashMap<Integer,Statistic>();

    /**
     * The thread pools whose stats are being collected
     */
    private ExecutorService threadPool;
 
    
    /**
     * <code>Future</code> instance in case we need to stop this object.
     */
    private Future futures[] = new Future[3];


    /**
     * Total number of connections that have been accepted.
     */
    private int totalAcceptCount;
    
    
    /**
     * <tt>Map</tt> of open pipeline connections
     */
    private ConcurrentHashMap<SelectableChannel,Long> openConnections =
            new ConcurrentHashMap<SelectableChannel,Long>();

    // -------------------------------------------------------------------//
    
    
    /**
     * Constructor
     *
     * @param port Port number for which thread pool (connection) stats will be
     * gathered
     */
    public ThreadPoolStatistic(int port) {
        this.port = port;
        
        countAverageExecutor = new ScheduledThreadPoolExecutor(3,
            new ThreadFactory(){
                public Thread newThread(Runnable r) {
                    return new WorkerThreadImpl(new ThreadGroup("Grizzly"),r);
                }
        });    
    }
    
    
    /**
     * Start gathering statistics.
     */
    public void start(){    
        if ( started ) return;
        
        futures[0] = countAverageExecutor.scheduleAtFixedRate(lastMinuteStat, 1 , 
                lastMinuteStat.getSeconds(), TimeUnit.SECONDS);
        futures[1] = countAverageExecutor.scheduleAtFixedRate(lastFiveMinuteStat, 1 , 
                lastFiveMinuteStat.getSeconds(), TimeUnit.SECONDS);
        futures[2] = countAverageExecutor.scheduleAtFixedRate(lastFifteenMinuteStat, 1 , 
                lastFifteenMinuteStat.getSeconds(), TimeUnit.SECONDS);    
        
        stats.put(lastMinuteStat.getSeconds(), lastMinuteStat);
        stats.put(lastFiveMinuteStat.getSeconds(), lastFiveMinuteStat);
        stats.put(lastFifteenMinuteStat.getSeconds(), lastFifteenMinuteStat);
        
        started = true;
    }
    
    
    /**
     * Stop gathering statistics.
     */
    public void stop(){
        if ( !started ) return;
        
        for (Future future: futures){
            future.cancel(true);
        }

        stats.clear();
        // Executor service should also be stoped
        countAverageExecutor.shutdown();
        started = false;
    }    
    
    
    /**
     * Gather {@link ExecutorService} statistic.
     */
    public boolean gather(int queueLength){
        if ( queueLength == maxQueueSizeInBytes){
            overflowCount++;
            return false;
        }
       
        if ( queueLength > 0 )
            totalCount++;
        
        // Track peak of this thread pool
        if (queueLength > peakCount) {
            peakCount = queueLength;
        } 
        return true;
    }
 
    
    /**
     * Total number of thread pool overflow
     */
    public int getCountOverflows(){
        return overflowCount;
    }
     
     
    /**
     * Gets the largest number of connections that were in the queue
     * simultaneously.
     *
     * @return Largest number of connections that were in the queue
     * simultaneously
     */    
    public int getPeakQueued(){
       return peakCount;
    }    
    

    /**
     * Gets the maximum size of the connection queue
     *
     * @return Maximum size of the connection queue
     */    
    public int getMaxQueued() {
        return maxQueueSizeInBytes;
    }


    /**
     * Gets the total number of connections that have been accepted.
     *
     * @return Total number of connections that have been accepted.
     */
    public int getCountTotalConnections() {
        return totalAcceptCount;
    }

    
    /**
     * Set the maximum pending connection this {@link ExecutorService}
     * can handle.
     */
    public void setQueueSizeInBytes(int maxQueueSizeInBytesCount){
        this.maxQueueSizeInBytes = maxQueueSizeInBytesCount;
    }
    
    
    /**
     * Get the maximum pending connection this {@link ExecutorService}
     * can handle.
     */
    public int getQueueSizeInBytes(){
        return maxQueueSizeInBytes;
    }
        

    /** 
     * Gets the total number of connections that have been queued.
     *
     * A given connection may be queued multiple times, so
     * <code>counttotalqueued</code> may be greater than or equal to
     * <code>counttotalconnections</code>.
     *
     * @return Total number of connections that have been queued
     */        
    public int getCountTotalQueued() {
        return totalCount;
    }
    

    /**
     * Gets the number of connections currently in the queue
     *
     * @return Number of connections currently in the queue
     */    
    public int getCountQueued() {
        int size = 0;

        if (threadPool != null && threadPool instanceof ExtendedThreadPool) {
            size = ((ExtendedThreadPool) threadPool).getQueueSize();
        }

        return size;
    }

   
    /**
     * Gets the total number of ticks that connections have spent in the
     * queue.
     * 
     * A tick is a system-dependent unit of time.
     *
     * @return Total number of ticks that connections have spent in the
     * queue
     */
    public int getTicksTotalQueued() {
        return -1; // Not supported
    }

    
    /** 
     * Gets the average number of connections queued in the last 1 minute
     *
     * @return Average number of connections queued in the last 1 minute
     */    
    public int getCountQueued1MinuteAverage() {
        return getCountAverage(1);
    }


    /** 
     * Gets the average number of connections queued in the last 5 minutes
     *
     * @return Average number of connections queued in the last 5 minutes
     */    
    public int getCountQueued5MinuteAverage() {
        return getCountAverage(5);
    }


    /** 
     * Gets the average number of connections queued in the last 15 minutes
     *
     * @return Average number of connections queued in the last 15 minutes
     */    
    public int getCountQueued15MinuteAverage() {
        return getCountAverage(15);
    }


    // -------------------------------------------------------------------//
    // Package protected methods

    public void incrementTotalAcceptCount() {
        totalAcceptCount++;
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    // -------------------------------------------------------------------//
    // Private methods

    /**
     * Gets the average number of connection queued in the last
     * <code>minutes</code> minutes.
     *
     * @param minutes The number of minutes for which the average number of
     * connections queued is requested
     * 
     * @return Average number of connections queued
     */
    private int getCountAverage(int minutes){
        Statistic stat = stats.get((minutes * 60));
        return (stat == null ? 0 : stat.average());
    }

    
    /**
     * Utility class to track average count.
     */
    class Statistic implements Runnable{
                
        int lastCount = 0;
        int average = 0;
        int seconds;
             
        public Statistic(int seconds){
            this.seconds = seconds;
        }

                               
        public void run() {
            average = totalCount - lastCount;
            lastCount = totalCount;
        }  
        
        public int average(){
            return average;
        }   
        
        
        public int getSeconds(){
            return seconds;
        }
    }
    
    /**
     * Increase the number of open connections, which are being handled by the
     * <tt>Pipeline</tt>
     *
     * @param channel just open channel
     * @return <tt>true</tt>, if channel was added, <tt>false</tt> if channel
     * was already counted in the statistics
     */
    public boolean incrementOpenConnectionsCount(SelectableChannel channel) {
        Long prevValue = 
                openConnections.putIfAbsent(channel, System.currentTimeMillis());
        return prevValue == null;
    }

    /**
     * Decrease the number of open connections, which are being handled by the
     * <tt>Pipeline</tt>
     *
     * @param channel just closed channel
     * @return <tt>true</tt>, if channel was removed from statics of open 
     * channels, <tt>false</tt> if channel is not in the statistics (probably
     * removed from statics earlier)
     */
    public boolean decrementOpenConnectionsCount(SelectableChannel channel) {
        Long prevValue = openConnections.remove(channel);
        return prevValue != null;
    }

    /**
     * Get the current number of open channels, which are being handled by the
     * pipeline
     * 
     * @return the current number of open channels, which are being handled 
     * by the pipeline
     */
    public int getOpenConnectionsCount() {
        return openConnections.size();
    }    
}
