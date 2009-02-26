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
import com.sun.grizzly.http.AsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.container.GrizzletAdapter;
import com.sun.grizzly.grizzlet.Grizzlet;
import com.sun.grizzly.util.ClassLoaderUtil;
import java.io.File;

/**
 * Basic startup class used when Grizzly standalone is used
 *
 * @author Jeanfrancois Arcand
 */
public class GrizzletContainer {
    
    /**
     * System property for the <code>SelectorThread</code> value.
     */
    private static final String SELECTOR_THREAD = 
                "com.sun.grizzly.selectorThread";
    
    private static final String ADAPTER = "com.sun.grizzly.adapterClass";
    
    private static int port = 8080;
    
    private static String folder = ".";
    
    private static String grizzletClassName;
    
    
    
    public GrizzletContainer() {
    }
    
    
    public static void main( String args[] ) throws Exception {       
        GrizzletContainer main = new GrizzletContainer();        
        main.start(args);
    }
    
    /**
     * Create a single <code>Grizzly</code> http listener.
     */
    private static void start(String args[]) throws Exception {
        final long t1 = System.currentTimeMillis();
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
        
        try{
            if ( args != null && args.length > 2) {
                grizzletClassName = args[2];
            }
        } catch (Exception ex){
            ex.printStackTrace();
        } 
         
        if (new File("lib").exists()){
            // Load jar under the lib directory
            Thread.currentThread().setContextClassLoader(
                    ClassLoaderUtil.createClassloader(
                        new File("lib"),GrizzletContainer.class.getClassLoader()));
        }
        
        final SelectorThread selectorThread = new SelectorThread();
        selectorThread.setAlgorithmClassName(StaticStreamAlgorithm.class.getName());     
        selectorThread.setPort(port);
        selectorThread.setWebAppRootPath(folder);
        selectorThread.setMaxThreads(5);
        selectorThread.setDisplayConfiguration(false);
        selectorThread.setEnableAsyncExecution(true);
        selectorThread.setBufferResponse(false);    
        selectorThread.setFileCacheIsEnabled(false);
        selectorThread.setLargeFileCacheEnabled(false);
        AsyncHandler asyncHandler = new DefaultAsyncHandler();
        asyncHandler.addAsyncFilter(new CometAsyncFilter()); 
        selectorThread.setAsyncHandler(asyncHandler);
            
                
        GrizzletAdapter adapter = new GrizzletAdapter("/comet"); 
        adapter.setRootFolder(folder);
        selectorThread.setAdapter(adapter);
        Grizzlet grizzlet = (Grizzlet)loadInstance(grizzletClassName);
        
        if (grizzlet == null){
            throw new IllegalStateException("Invalid Grizzlet ClassName");
        } else {
            
            System.out.println("Launching Grizzlet: " + 
                    grizzlet.getClass().getName());
            
            adapter.setGrizzlet(grizzlet);
            selectorThread.initEndpoint();
            new Thread(){
                public void run(){
                    try{
                        selectorThread.startEndpoint();
                    } catch (Throwable t){
                        t.printStackTrace();
                    }
                }
            }.start();

            System.out.println("Server startup in " + 
                                (System.currentTimeMillis() - t1) + " ms");

            synchronized(selectorThread){
                try{
                    selectorThread.wait();
                } catch (Throwable t){
                    t.printStackTrace();
                }
            }   
        }
    }

    
    /**
     * Util to load classes using reflection.
     */
    private static Object loadInstance(String clazzName){        
        Class className = null;                               
        try{                              
            className = Class.forName(clazzName,true,
                    Thread.currentThread().getContextClassLoader());
            return className.newInstance();
        } catch (Throwable t) {
            t.printStackTrace();
            // Log me 
        }   
        return null;
    }      
   
}
