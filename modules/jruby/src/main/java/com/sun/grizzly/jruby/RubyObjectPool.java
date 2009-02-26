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
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.jruby;

import com.sun.grizzly.http.SelectorThread;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.jruby.Ruby;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.load.LoadService;

/**
 * An object pool for ruby runtime.
 * 
 * @author TAKAI Naoto
 */
public class RubyObjectPool {

    /** How long to wait before given up. */
    private static long DEFAULT_TIMEOUT = 1000L;

    /** JRUBY_LIB directory */
    private String jrubyLib = null;

    /** The number of runtime. */
    private int numberOfRuntime = 5;

    /** Runtime queue */
    private BlockingQueue<Ruby> queue = new LinkedBlockingQueue<Ruby>();

    /** RAILS_ROOT directory */
    private String railsRoot = null;
    
    
    /**
     * Is Grizzly ARP enabled.
     */
    private boolean asyncEnabled = false;

    /**
     * Retrives ruby runtime from the object pool.
     * 
     * @return JRuby runtime.
     */
    public Ruby borrowRuntime() {
        if (!isAsyncEnabled()){
            try {
                return queue.poll(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            return queue.poll();
        }
    }

    /**
     * Returns runtime to the object pool.
     * 
     * @param runtime
     */
    public void returnRuntime(Ruby runtime) {
        queue.offer(runtime);
    }

    /**
     * Sets JRUBY_LIB directory.
     * 
     * @param jrubyLib
     *            JRUBY_LIB directory.
     */
    public void setJrubyLib(String jrubyLib) {
        this.jrubyLib = jrubyLib;
    }

    /**
     * Sets the number of pooling runtime.
     * 
     * @param numberOfRuntime
     *            the number of runtime.
     */
    public void setNumberOfRuntime(int numberOfRuntime) {
        this.numberOfRuntime = numberOfRuntime;
    }

    /**
     * Sets RAILS_ROOT directory.
     * 
     * @param railsRoot
     *            RAILS_ROOT directory.
     */
    public void setRailsRoot(String railsRoot) {
        this.railsRoot = railsRoot;
    }

    /**
     * Starts the object pool.
     */
    public void start() {
        try {
            if (jrubyLib == null || railsRoot == null) {
                throw new IllegalStateException("jrubyLib or railsRoot can not be null.");
            }
            int pnum = Runtime.getRuntime().availableProcessors();
            ExecutorService exec = Executors.newFixedThreadPool(pnum + 1);
     
            StringBuffer buf = new StringBuffer();
            for (int i = 0; i < numberOfRuntime; i++) {
                exec.execute(new Runnable(){
                    public void run() {
                        long startTime = System.currentTimeMillis();
                        Ruby runtime = initializeRubyRuntime();
                        loadRubyLibraries(runtime);
                        SelectorThread.logger().log(Level.INFO, 
                            "Rails instance instantiation took : " + 
                            (System.currentTimeMillis() - startTime) + "ms");
                        queue.offer(runtime);
                    }
                });
            }
            exec.shutdown();
            exec.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Shutdowns the object pool.
     */
    public void stop() {
        for (Ruby ruby : queue) {
            ruby.tearDown();
        }
        queue.clear();
    }

    protected Ruby initializeRubyRuntime() {
        ArrayList<String> libs = new ArrayList<String>();
        libs.add("META-INF/jruby.home/lib/ruby/site_ruby/1.8");
        return JavaEmbedUtils.initialize(libs);
    }

    protected void loadRubyLibraries(Ruby runtime) {
        LoadService loadService = runtime.getLoadService();

        // load rails
        loadService.require(railsRoot + "/config/environment.rb");
    }

    /**
     * Gets JRUBY_LIB directory.
     * 
     * @return JRUBY_LIB directory.
     */
    public String getJrubyLib() {
        return jrubyLib;
    }

    /**
     * Gets the number of directory.
     * 
     * @return the number of directory;
     */
    public int getNumberOfRuntime() {
        return numberOfRuntime;
    }

    /**
     * Gets RAILS_ROOT directory.
     * 
     * @return RAILS_ROOT directory.
     */
    public String getRailsRoot() {
        return railsRoot;
    }
    
    protected BlockingQueue<Ruby> getRubyRuntimeQueue(){
        return queue;
    }

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }
}
