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

import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.ssl.SSLSelectorThread;
import com.sun.grizzly.util.ClassLoaderUtil;
import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.util.OutputWriter;
import com.sun.grizzly.util.SelectorFactory;
import com.sun.grizzly.util.net.SSLImplementation;
import com.sun.grizzly.util.res.StringManager;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

public class SelectorThreadConfig{  
    
    /**
     * System property for the selector timeout value.
     */
    private static final String SELECTOR_TIMEOUT = 
                "com.sun.grizzly.selector.timeout";
    
    /**
     * The minimum number of threads used when creating a new 
     * {@link ExecutorService}
     */
    private static final String MIN_THREAD=
               "com.sun.grizzly.minWorkerThreads";
    
    /**
     * The maximum number of threads used when creating a new 
     * {@link ExecutorService}
     */
    private static final String MAX_THREAD=
               "com.sun.grizzly.maxThreads";
    
    
    /**
     * Property used to turn on/off NIO blocking mode.
     */
    private final static String DISPLAY_CONFIGURATION=
                "com.sun.grizzly.displayConfiguration";


    private final static String MAX_KEEP_ALIVE_REQUEST =
               "com.sun.grizzly.maxKeepAliveRequests"; 


    /**
     * Is the {@link ByteBuffer} used by the <code>ReadTask</code> use
     * direct {@link ByteBuffer} or not.
     */
    private final static String DIRECT_BYTE_BUFFER_READ =
               "com.sun.grizzly.useDirectByteBuffer";    
 
    /**
     * Always attach a {@link ProcessorTask} when creating 
     * a <code>ReadTask</code>
     */
    private final static String THREAD_POOL_CLASS =
               "com.sun.grizzly.threadPoolClass";

    private final static String MAX_SELECTOR_READ_THREAD=
               "com.sun.grizzly.maxSelectorReadThread"; 
    
    private final static String BYTE_BUFFER_VIEW =
               "com.sun.grizzly.useByteBufferView"; 

    private final static String ALGORITHM_CLASS_NAME=
        "com.sun.grizzly.algorithmClassName";
    
    private final static String MAX_SELECTOR = 
        "com.sun.grizzly.maxSelectors";
    
    private final static String FACTORY_TIMEOUT = 
        "com.sun.grizzly.factoryTimeout";
    
    
    private final static String ASYNCH_HANDLER_CLASS =
        "com.sun.grizzly.asyncHandlerClass";   
    
    private final static String ASYNCH_HANDLER_PORT =
        "com.sun.grizzly.asyncHandler.ports";   
    
    private final static String SNOOP_LOGGING = 
        "com.sun.grizzly.enableSnoop";

    private final static String TEMPORARY_SELECTOR_TIMEOUT = 
        "com.sun.grizzly.readTimeout"; 
    
    private final static String WRITE_TIMEOUT = 
        "com.sun.grizzly.writeTimeout";     
    
    private final static String IDLE_THREAD_TIMEOUT = 
        "com.sun.grizzly.idleThreadTimeout";    
    
    private final static String BUFFER_RESPONSE = 
        "com.sun.grizzly.http.bufferResponse"; 
    
    private final static String OOBInline = 
        "com.sun.grizzly.OOBInline"; 
    
    private final static String MAX_BUFFERED_BYTES =
        "com.sun.grizzly.maxBufferedBytes";
    
    private final static String USE_FILE_CACHE =
        "com.sun.grizzly.useFileCache";

    
    private final static String IS_ASYNC_HTTP_WRITE =
            "com.sun.grizzly.http.asyncwrite.enabled";

    private final static String ASYNC_HTTP_WRITE_MAX_BUFFER_POOL_SIZE =
            "com.sun.grizzly.http.asyncwrite.maxBufferPoolSize";

    private final static String SSL_CONFIGURATION_WANTAUTH =
            "com.sun.grizzly.ssl.auth";

    private final static String SSL_CONFIGURATION_SSLIMPL =
            "com.sun.grizzly.ssl.sslImplementation";
    
   private final static String ASYNC_HANDLER_ENABLED_CONTEXT_PATH =
           "com.sun.grizzly.arp.asyncHandlerContextPath";

    /**
     * Do not bind on occupied port
     */
    public final static String REUSE_ADRESS =
            "com.sun.grizzly.http.reuseAddress";

   /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package,
                                 Constants.class.getClassLoader());
// --------------------------------------------------------- Static -----//


   /**
     * Read systems properties and configure the {@link SelectorThread}.
     */
    @SuppressWarnings("empty-statement")
    protected static void configureProperties(SelectorThread selectorThread){
        if (System.getProperty(SELECTOR_TIMEOUT) != null){
            try{
                selectorThread.setSelectorTimeout(
                        Integer.parseInt(System.getProperty(SELECTOR_TIMEOUT)));
            } catch (NumberFormatException ex){
                SelectorThread.logger().log(Level.WARNING, sm.getString("selectorThread.invalidSelectorTimeout"));
            }
        }

        if (System.getProperty(TEMPORARY_SELECTOR_TIMEOUT) != null){
            try{
                int timeout =  Integer.parseInt(
                        System.getProperty(TEMPORARY_SELECTOR_TIMEOUT));
                InputReader.setDefaultReadTimeout(timeout);
            } catch (NumberFormatException ex){
                SelectorThread.logger().log(Level.WARNING, 
                                            sm.getString("selectorThread.invalidReadTimeout"));
            }
        }             

        if (System.getProperty(WRITE_TIMEOUT) != null){
            try{
                int timeout =  Integer.parseInt(
                        System.getProperty(WRITE_TIMEOUT));
                OutputWriter.setDefaultWriteTimeout(timeout);
            } catch (NumberFormatException ex){
                SelectorThread.logger().log(Level.WARNING, 
                                            sm.getString("selectorThread.invalidWriteTimeout"));
            }
        } 
        
        if (System.getProperty(IDLE_THREAD_TIMEOUT) != null){
            try{
                int timeout =  Integer.parseInt(
                        System.getProperty(IDLE_THREAD_TIMEOUT));
                selectorThread.setTransactionTimeout(timeout);
            } catch (NumberFormatException ex){
                SelectorThread.logger().log(Level.WARNING, 
                                            sm.getString("selectorThread.invalidWriteTimeout"));
            }
        }
        
        String threadPoolClassname = null;
        int minWorkerThreads = StatsThreadPool.DEFAULT_MIN_THREAD_COUNT;
        int maxWorkerThreads = StatsThreadPool.DEFAULT_MAX_THREAD_COUNT;

        if (System.getProperty(THREAD_POOL_CLASS)!= null){
            threadPoolClassname = System.getProperty(THREAD_POOL_CLASS);
            selectorThread.setThreadPoolClassname(threadPoolClassname);
        }

        if (System.getProperty(MIN_THREAD) != null){
            try{
                minWorkerThreads =
                    Integer.parseInt(System.getProperty(MIN_THREAD));
                selectorThread.setCoreThreads(minWorkerThreads);
            } catch (NumberFormatException ex){
                SelectorThread.logger().log(Level.WARNING,sm.getString("selectorThread.invalidMinThreads"));
            }
        }    
        
        
        if (System.getProperty(MAX_THREAD) != null){
            try{
                maxWorkerThreads =
                    Integer.parseInt(System.getProperty(MAX_THREAD));
                selectorThread.setMaxThreads(maxWorkerThreads);
            } catch (NumberFormatException ex){
                SelectorThread.logger().log(Level.WARNING, sm.getString("selectorThread.invalidMaxThreads"));
            }
        }  
        
        if (System.getProperty(DISPLAY_CONFIGURATION)!= null){
            selectorThread.displayConfiguration = Boolean.valueOf(System.getProperty(DISPLAY_CONFIGURATION));
        }

        if (System.getProperty(REUSE_ADRESS) != null){
            selectorThread.setReuseAddress(Boolean.valueOf(System.getProperty(REUSE_ADRESS)));
        }
        
        if (System.getProperty(ASYNCH_HANDLER_PORT) != null){
            String ports = System.getProperty(ASYNCH_HANDLER_PORT);
            StringTokenizer st = new StringTokenizer(ports,",");
            while(st.hasMoreTokens()){
                
                if ( st.nextToken()
                        .equals(String.valueOf(selectorThread.getPort()))
                        && System.getProperty(ASYNCH_HANDLER_CLASS)!= null){
                    
                    selectorThread.asyncHandler = (AsyncHandler)
                        ClassLoaderUtil.load(
                            System.getProperty(ASYNCH_HANDLER_CLASS)); 
                    selectorThread.asyncExecution = true;
                }
            }
        }           
        
        if (System.getProperty(DIRECT_BYTE_BUFFER_READ)!= null){
            selectorThread.useDirectByteBuffer = Boolean.valueOf(System.getProperty(DIRECT_BYTE_BUFFER_READ));
        }        
       
        if (System.getProperty(MAX_KEEP_ALIVE_REQUEST) != null){
            try{
                selectorThread.setMaxKeepAliveRequests(Integer.parseInt(System.getProperty(MAX_KEEP_ALIVE_REQUEST)));
            } catch (NumberFormatException ex){
                ;
            }
        }
        
        if (System.getProperty(ALGORITHM_CLASS_NAME)!= null){
            selectorThread.algorithmClassName = System.getProperty(ALGORITHM_CLASS_NAME);
        }   

        if (System.getProperty(BYTE_BUFFER_VIEW)!= null){
            selectorThread.useByteBufferView = Boolean.valueOf(System.getProperty(BYTE_BUFFER_VIEW));
        }    
             
        if (System.getProperty(MAX_SELECTOR_READ_THREAD) != null){
            try{
                selectorThread.readThreadsCount = 
                  Integer.parseInt(System.getProperty(MAX_SELECTOR_READ_THREAD));
            } catch (NumberFormatException ex){
                ;
            }
        }
        
        if (System.getProperty(MAX_SELECTOR) != null){
            try{
                SelectorFactory.setMaxSelectors(Integer.parseInt(System.getProperty(MAX_SELECTOR)));
            } catch (NumberFormatException ignored){
            }
        } 
        
        if (System.getProperty(SNOOP_LOGGING)!= null){
            SelectorThread.setEnableNioLogging(Boolean.valueOf(System.getProperty(SNOOP_LOGGING)));
        }   
        
        if (System.getProperty(BUFFER_RESPONSE)!= null){
            selectorThread.setBufferResponse(Boolean.valueOf(System.getProperty(BUFFER_RESPONSE)));
        }    
        
        if (System.getProperty(OOBInline)!= null){
            selectorThread.oOBInline = Boolean.valueOf(System.getProperty(OOBInline));
        }
        
        if (System.getProperty(MAX_BUFFERED_BYTES) != null){
            try{
                SocketChannelOutputBuffer.setMaxBufferedBytes( 
                    Integer.parseInt(System.getProperty(MAX_BUFFERED_BYTES)));
            } catch (NumberFormatException ex){
                SelectorThread.logger().log(Level.WARNING, sm.getString("selectorThread.invalidMaxBufferedBytes"));
            }
        }

        if (System.getProperty(USE_FILE_CACHE)!= null){
            selectorThread.setFileCacheIsEnabled(Boolean.valueOf(System.getProperty(USE_FILE_CACHE)));
            selectorThread.setLargeFileCacheEnabled(selectorThread.isFileCacheEnabled());
        }

        if (System.getProperty(IS_ASYNC_HTTP_WRITE) != null) {
            selectorThread.setAsyncHttpWriteEnabled(Boolean.getBoolean(IS_ASYNC_HTTP_WRITE));
        }

        if (System.getProperty(ASYNC_HTTP_WRITE_MAX_BUFFER_POOL_SIZE) != null) {
            SocketChannelOutputBuffer.setMaxBufferPoolSize(Integer.getInteger(
                    ASYNC_HTTP_WRITE_MAX_BUFFER_POOL_SIZE, -1));
        }

        String auth = System.getProperty(SSL_CONFIGURATION_WANTAUTH);
        if (auth != null) {
            if (selectorThread instanceof SSLSelectorThread){
                if ("want".equalsIgnoreCase(auth.trim())){
                    ((SSLSelectorThread)selectorThread).setWantClientAuth(true);
                } else if ("need".equalsIgnoreCase(auth.trim())){
                    ((SSLSelectorThread)selectorThread).setNeedClientAuth(true);
                } 
            }
        }

        if (System.getProperty(SSL_CONFIGURATION_SSLIMPL) != null) {
            SSLImplementation sslImplementation = (SSLImplementation)
                    ClassLoaderUtil.load(System.getProperty(SSL_CONFIGURATION_SSLIMPL));
            if (selectorThread instanceof SSLSelectorThread){
                ((SSLSelectorThread)selectorThread).setSSLImplementation(sslImplementation);
            }
        }

        String list = System.getProperty(ASYNC_HANDLER_ENABLED_CONTEXT_PATH);
        if (list != null){
            StringTokenizer st = new StringTokenizer(list, ",");
            while (st.hasMoreTokens()){
                selectorThread.addAsyncEnabledContextPath(st.nextToken());
            }
        }
    }

    
    /**
     * Configure properties on {@link SelectorThread}
     */
    public static void configure(SelectorThread selectorThread){
        configureProperties(selectorThread);
    }
}
