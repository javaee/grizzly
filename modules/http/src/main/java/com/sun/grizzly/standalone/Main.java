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

import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.tcp.DynamicContentAdapter;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.util.ClassLoaderUtil;
import java.io.File;

/**
 * Basic startup class used when Grizzly standalone is used
 *
 * @author Jeanfrancois Arcand
 */
public class Main {
    
    /**
     * System property for the <code>SelectorThread</code> value.
     */
    private static final String SELECTOR_THREAD = "com.sun.grizzly.selectorThread";
    
    private static final String ENABLE_RCM = "com.sun.grizzly.enableRCM";
    
    private static final String ENABLE_ASYNC = "com.sun.grizzly.enableARP";
    
    private static final String ADAPTER = "com.sun.grizzly.adapterClass";
    
    static int port = 8080;
    
    static String folder = ".";
    
    public Main() {
    }
    
    
    public static void main( String args[] ) throws Exception {       
        Main main = new Main();        
        Main.start(args);
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
                        new File("lib"),Main.class.getClassLoader()));
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
        selectorThread.setPort(port);
        
        String adapterClass =  System.getProperty(ADAPTER);
        Adapter adapter;
        if (adapterClass == null){
            adapter = new StaticResourcesAdapter(folder);
        } else {
            adapter = (Adapter)loadInstance(adapterClass);
        }
        
        if (adapter instanceof StaticResourcesAdapter){
            ((StaticResourcesAdapter)adapter).setRootFolder(folder);
        }
        
        if (adapter instanceof DynamicContentAdapter){
            ((DynamicContentAdapter)adapter).setContextRoot("/");
        }
        
        boolean enableRcm = Boolean.valueOf(System.getProperty(ENABLE_RCM));
        selectorThread.enableRcmSupport(enableRcm);
        
        boolean enableAsync = Boolean.valueOf(System.getProperty(ENABLE_ASYNC));
        selectorThread.setEnableAsyncExecution(enableAsync);
        if (enableAsync){
            selectorThread.setAsyncHandler(new DefaultAsyncHandler());
        }
        
        selectorThread.setAdapter(adapter);
        selectorThread.setDisplayConfiguration(true);
        selectorThread.listen();
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
            t.getCause().printStackTrace();
            t.printStackTrace();
            // Log me 
        }   
        return null;
    }      
   
}
