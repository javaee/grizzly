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

package com.sun.grizzly.standalone;

import com.sun.grizzly.comet.CometAsyncFilter;
import com.sun.grizzly.cometd.standalone.CometdAdapter;
import com.sun.grizzly.http.AsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.util.ClassLoaderUtil;
import java.io.File;
import java.util.logging.Level;

/**
 * Basic startup class used when Grizzly standalone is used
 *
 * @author Jeanfrancois Arcand
 */
public class Cometd {
    
    /**
     * System property for the <code>SelectorThread</code> value.
     */
    private static final String SELECTOR_THREAD = 
                "com.sun.grizzly.selectorThread";
    
    private static final String ADAPTER = "com.sun.grizzly.adapterClass";
    
    static int port = 8080;
    
    static String folder = "../../examples/comet/cometd-chat/target/cometd-chat/";
    
    public Cometd() {
    }
    
    
    public static void main( String args[] ) throws Exception {       
        Cometd main = new Cometd();        
        main.start(args);
    }

    
    /**
     * Create a single <code>Grizzly</code> http listener.
     */
    private static void start(String args[]) throws Exception {
        
        try{
            if ( args != null && args.length > 0) {
                port = Integer.parseInt(args[0]);
            }
        } catch (Exception ex){
            ex.printStackTrace();
        }
         
        try{
            if ( args != null && args.length > 1) {
                folder = args[1];
            }
        } catch (Exception ex){
            ex.printStackTrace();
        }       

        if (new File("lib").exists()){
            // Load jar under the lib directory
            Thread.currentThread().setContextClassLoader(
                    ClassLoaderUtil.createClassloader(
                        new File("lib"),Cometd.class.getClassLoader()));
        }
        
        SelectorThread selectorThread = null;
        String selectorThreadClassname = System.getProperty(SELECTOR_THREAD);
        if ( selectorThreadClassname != null){
            selectorThread = (SelectorThread)loadInstance(selectorThreadClassname);
        } else {
            selectorThread = new SelectorThread();
            selectorThread
                    .setAlgorithmClassName(StaticStreamAlgorithm.class.getName());
        }        
        
        if (!new File(folder).exists()){
            System.out.println("Invalid root folder: " + folder);
            folder = ".";
        }
        
        selectorThread.setPort(port);
        selectorThread.setWebAppRootPath(folder);
        
        String adapterClass =  System.getProperty(ADAPTER);
        Adapter adapter;
        if (adapterClass == null){
            adapter = new CometdAdapter();
            ((CometdAdapter)adapter).setRootFolder(folder);
        } else {
            adapter = (Adapter)loadInstance(adapterClass);
        }
        
        selectorThread.setAdapter(adapter);
        selectorThread.setDisplayConfiguration(true);
        selectorThread.setEnableAsyncExecution(true);
        selectorThread.setBufferResponse(false);    
        selectorThread.setFileCacheIsEnabled(false);
        selectorThread.setLargeFileCacheEnabled(false);
        AsyncHandler asyncHandler = new DefaultAsyncHandler();
        asyncHandler.addAsyncFilter(new CometAsyncFilter()); 
        selectorThread.setAsyncHandler(asyncHandler);
            
        SelectorThread.logger()
            .log(Level.INFO,"Enabling Grizzly ARP Comet support.");
        
        selectorThread.initEndpoint();
        selectorThread.startEndpoint();
    }
   
    
    /**
     * Util to load classes using reflection.
     */
    private static Object loadInstance(String property){        
        Class className = null;                               
        try{                              
            className = Class.forName(property,true,
                    Thread.currentThread().getContextClassLoader());
            return className.newInstance();
        } catch (Throwable t) {
            t.printStackTrace();
            // Log me 
        }   
        return null;
    }      
   
}
