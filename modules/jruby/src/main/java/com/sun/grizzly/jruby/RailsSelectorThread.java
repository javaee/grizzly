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

import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.standalone.StaticStreamAlgorithm;
import java.io.IOException;
import java.util.logging.Level;

import com.sun.grizzly.http.SelectorThread;

/**
 * JRuby on rails implementation of Grizzly SelectorThread
 * @author TAKAI Naoto
 * @author Jeanfrancois Arcand
 */
public class RailsSelectorThread extends SelectorThread {
    
    private final static String NUMBER_OF_RUNTIME = 
            "com.sun.grizzly.rails.numberOfRuntime";
    
    private String jrubyLib = null;

    private int numberOfRuntime = 5;

    private RubyObjectPool pool = null;

    private String railsRoot = null;

    public String getRailsRoot() {
        return railsRoot;
    }

    @Override
    public void initEndpoint() throws IOException, InstantiationException {
        setupSystemProperties();
        initializeRubyRuntime();        
        
        setBufferResponse(false);
        
        asyncExecution = true;
        DefaultAsyncHandler asyncHandler = new DefaultAsyncHandler();
        setAsyncHandler(asyncHandler);
        
        RubyRuntimeAsyncFilter asyncFilter = new RubyRuntimeAsyncFilter();
        asyncFilter.setRubyRuntimeQueue(pool.getRubyRuntimeQueue());
        asyncHandler.addAsyncFilter(asyncFilter);
        
        adapter = new RailsAdapter(pool,asyncFilter);
        
        algorithmClassName = StaticStreamAlgorithm.class.getName();
        super.initEndpoint();  
    }

    public void setNumberOfRuntime(int numberOfRuntime) {
        this.numberOfRuntime = numberOfRuntime;
    }

    public void setRailsRoot(String railsRoot) {
        this.railsRoot = railsRoot;
    }

    @Override
    public synchronized void stopEndpoint() {
        pool.stop();

        super.stopEndpoint();
    }

    protected void initializeRubyRuntime() {
        pool = new RubyObjectPool();
        pool.setNumberOfRuntime(numberOfRuntime);
        pool.setJrubyLib(jrubyLib);
        pool.setRailsRoot(railsRoot);
        pool.setAsyncEnabled(asyncExecution);

        try {
            pool.start();
        } catch (Throwable t) {
            logger.log(Level.WARNING, t.getMessage());
        }
    }

    protected void setupSystemProperties() {
        jrubyLib = System.getProperty("jruby.lib");
        if (jrubyLib == null) {
            jrubyLib = System.getenv().get("JRUBY_HOME") + "/lib";
            System.setProperty("jruby.lib", jrubyLib);
        }
                     
        if (System.getProperty(NUMBER_OF_RUNTIME) != null){
            try{
                numberOfRuntime = Integer.parseInt(
                        System.getProperty(NUMBER_OF_RUNTIME));
            } catch (NumberFormatException ex){
                SelectorThread.logger().log(Level.WARNING, 
                                            "Invalid number of Runtime");
            }
        }
    }
}
