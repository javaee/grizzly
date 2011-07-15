/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.web.portunif;

import com.sun.enterprise.web.connector.grizzly.ByteBufferInputStream;
import com.sun.enterprise.web.connector.grizzly.DefaultReadTask;
import com.sun.enterprise.web.connector.grizzly.ConcurrentQueue;
import com.sun.enterprise.web.connector.grizzly.KeepAlivePipeline;
import com.sun.enterprise.web.connector.grizzly.SecureSelector;
import com.sun.enterprise.web.connector.grizzly.SelectorThread;
import com.sun.enterprise.web.connector.grizzly.Task;
import com.sun.enterprise.web.connector.grizzly.TaskBase;
import com.sun.enterprise.web.connector.grizzly.TaskEvent;
import com.sun.enterprise.web.connector.grizzly.ssl.SSLPipeline;
import com.sun.enterprise.web.connector.grizzly.ssl.SSLReadTask;
import com.sun.enterprise.web.connector.grizzly.ssl.SSLSelectorThread;
import com.sun.enterprise.web.portunif.util.ProtocolInfo;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.StringTokenizer;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.ServerSocketFactory;


/**
 * This <code>Pipeline</code> goal is to try to determine the TCP protocol used
 * (http, soap, https, etc.). First, all <code>ProtocolFinder</code> are executed
 * trying to determine which protocol is used. Once the protocol is found, the
 * associated <code>ProtocolHandler</code>.
 * 
 * You can add ProtocolHandler and ProtocolFinder by doing 
 * (from a SelectorThread)
 * 
 * SelectorThread st = SelectorThread.getSelector(port);
 * PortUnificationPipeline php = (PortUnificationPipeline)st.getReadPipeline();
 * php.addProtocolFinder(..);
 * php.addProtocolHandler(...);
 * 
 * @author Jeanfrancois Arcand
 */
public class PortUnificationPipeline extends SSLPipeline{  
    private final static int MAX_RETRY = Integer.getInteger("com.sun.enterprise.web.portunif.PortUnificationPipeline.maxRetry", 2);

    public final static String PROTOCOL_FINDERS = 
            "com.sun.enterprise.web.connector.grizzly.protocolFinders";
    
    public final static String PROTOCOL_HANDLERS = 
            "com.sun.enterprise.web.connector.grizzly.protocolHandlers";

    /**
     * List of available <code>ProtocolHandler</code>.
     */
    private ConcurrentHashMap<String, ProtocolHandler> protocolHandlers 
            = new ConcurrentHashMap<String,ProtocolHandler>();

    
    /**
     * List of available <code>ProtocolFinder</code>.
     */
    private Queue<ProtocolFinder> protocolFinders
            = new ConcurrentQueue<ProtocolFinder>("PortUnificationPipeline.protocolFinders");
    
    
    /**
     * List of available <code>ProtocolFinder</code>.
     */
    private Map<SelectionKey,ProtocolHandler> mappedProtocols = 
            Collections.synchronizedMap(
                new WeakHashMap<SelectionKey,ProtocolHandler>());
    
    
    /**
     * The SSLContext used for handshaking.
     */
    private SSLContext sslContext;
    
    
    /**
     * Is the protocol of this listener configured
     */
    private boolean isSet = false;
    
    
    /**
     * Is the protocol used secure.
     */
    private boolean isRequestedTransportSecure = false;
    
    
    /**
     * A cached list of PUTask.
     */
    private Queue<PUTask> puTasks =
        new ConcurrentQueue<PUTask>("PortUnificationPipeline.puTasks");
    
    
    /**
     * Logger
     */
    private static Logger logger = SelectorThread.getLogger();
    
    // ----------------------------------------------------------------------//
    
    
    public PortUnificationPipeline(){
        super();
        loadFinders();
        loadHandlers();
    }
    
    
    /**
     * Seek for the TCP protocol used. First all ProtocolFinder will be invoked.
     * If the protocol is found, the associated ProtocolHandler will be executed.
     * The execution of this method will occurs on the same thread and the 
     * main Selector (SelectorThread).
     *
     * @param task An implementation of <code>ReadTask</code>
     */
    @Override
    public void addTask(Task task) {    

        // Performance optimization used when a single protocol
        // is supported by this Pipeline.
        ProtocolHandler protocolHandler = null;
        boolean cachedHandler = mappedProtocols != null 
                && (protocolHandler = 
                        mappedProtocols.get(task.getSelectionKey())) != null;
        SelectorThread selectorThread = task.getSelectorThread();
        KeepAlivePipeline kap = selectorThread == null ? null : 
                                        selectorThread.getKeepAlivePipeline();
        if (protocolFinders.isEmpty() 
                || kap == null
                || protocolHandlers.isEmpty()
                || task.getType() != Task.READ_TASK 
                || (kap.isKeepAlive(task.getSelectionKey()) && !cachedHandler)){
            super.addTask(task);
            return;
        } 
        task.getSelectionKey().attach(null);
        
        if (!isSet) {
            isRequestedTransportSecure = 
                (task.getSelectorThread() instanceof SecureSelector) 
                    ? true: false;
            isSet = true;
            if (isRequestedTransportSecure){
                sslContext = ((SSLSelectorThread)task.getSelectorThread())
                                .getSSLContext();
            }
            
            if (!isRequestedTransportSecure || sslContext == null) {
                if ( sslContext == null ){
                    Enumeration<SelectorThread> selectors 
                        = SelectorThread.getSelectors();                          
                    SelectorThread sel;
                    while (selectors.hasMoreElements()){
                        sel = selectors.nextElement();
                        if (sel instanceof SSLSelectorThread){
                            sslContext = 
                                    ((SSLSelectorThread)sel).getSSLContext();
                            if (sslContext != null)
                                break;
                        }
                    }
                }
            }
            
            if (sslContext == null){
                try{
                    SSLImplementation sslHelper = SSLImplementation.getInstance();
                    ServerSocketFactory serverSF = 
                            sslHelper.getServerSocketFactory();
                    serverSF.setAttribute("keystoreType","JKS");
                    serverSF.setAttribute("keystore",
                            System.getProperty("javax.net.ssl.keyStore"));
                    serverSF.setAttribute("truststoreType","JKS");
                    serverSF.setAttribute("truststore",
                            System.getProperty("javax.net.ssl.trustStore"));                    
                    serverSF.init();
                    sslContext = serverSF.getSSLContext();                    
                } catch(Throwable ex){
                    logger.log(Level.FINE,
                               "SSL not supported.",ex);                    
                }
            }
        }      
        super.addTask(getPUTask((DefaultReadTask)task,protocolHandler));
    }
    
    
    private PUTask getPUTask(DefaultReadTask readTask,
            ProtocolHandler protocolHandler){
        PUTask task = puTasks.poll();
        if (task == null){
            task = new PUTask();
        }
        task.readTask = readTask;
        task.protocolHandler = protocolHandler;
        task.setSelectionKey(readTask.getSelectionKey());
        task.setSelectorThread(readTask.getSelectorThread());
        return task;
    }
    
    
    /**
     * Invoked when the SelectorThread is about to expire a SelectionKey.
     * @return true if the SelectorThread should expire the SelectionKey, false
     *              if not.
     */
    @Override
    public boolean expireKey(SelectionKey key){
        ProtocolHandler ph = mappedProtocols.get(key);
        if (ph != null){           
            try{
                /**
                 * Workaround issue 3615
                 * This code is an ugly workaround as we cannot change the current
                 * API to handle this scenario.
                 *
                 * Since we can't predict what the user attach to the SelectionKey
                 * a ClassCastException is required.
                 */
                Long maxKeepAliveRequestsToken = (Long) key.attachment();
                if (maxKeepAliveRequestsToken == -1
                        && ph.getClass().getName().equals("com.sun.xml.ws.transport.tcp.grizzly.WSTCPProtocolHandler")){
                    return false;
                } 
            } catch (ClassCastException ex){
                if (logger.isLoggable(Level.FINE)){
                    logger.log(Level.FINE,"expireKey",ex);
                }
            }
            return ph.expireKey(key);
        } else {
            return true;
        }
    }       
    
    /**
     * PortUnification Grizzly Task implementation.
     */
    private class PUTask extends TaskBase{
        
        final static int PU_TASK = 4;
        
        DefaultReadTask readTask;
        
        ProtocolInfo protocolInfo = new ProtocolInfo();
        
        private int maxTry = MAX_RETRY;
        
        private ProtocolHandler protocolHandler = null;
        
        public void doTask(){
            // prevent multiple task.terminate that enqueues
            // same take to readTasks in SelectorThread
            boolean isTaskTerminated = false;
            
            if (sslContext != null) {
                protocolInfo.sslContext = sslContext;
            }
        
            SelectionKey key = readTask.getSelectionKey();
            SSLEngine sslEngine = null;
            if ( isRequestedTransportSecure ) {
                sslEngine = ((SSLReadTask)readTask).getSSLEngine();
            }
            protocolInfo.sslEngine = sslEngine;
            protocolInfo.key = key;
            protocolInfo.isRequestedTransportSecure = isRequestedTransportSecure;
            protocolInfo.mappedProtocols = mappedProtocols;
            if (isRequestedTransportSecure){
                SSLReadTask sslReadTask = (SSLReadTask)readTask;
                sslReadTask.allocateBuffers();
                protocolInfo.inputBB = sslReadTask.getInputBB();
                protocolInfo.outputBB = sslReadTask.getOutputBB();
            }
            protocolInfo.byteBuffer = readTask.getByteBuffer();
                        
            boolean notFound = true;
            int readTry = 0;
            try{
                if (protocolHandler == null){           
                    while (notFound && readTry++ < maxTry){
                        String protocol = null;            
                        Iterator<ProtocolFinder> iterator = protocolFinders.iterator();
                        while (iterator.hasNext()) {
                            try{
                                iterator.next().find(protocolInfo);                                                           
                            } catch (IOException ex){
                                protocolInfo.bytesRead = -1;
                                return;                    
                            } finally {
                                if (protocolInfo.bytesRead == -1){
                                    isTaskTerminated = true;
                                    readTask.terminate(false);
                                    return;
                                }                                                                
                            }
                            protocol = protocolInfo.protocol;                     
                            if (protocol != null){
                                break;
                            }
                        }         

                        // Get the associated ProtocolHandler. If the current 
                        // SelectorThread can server the request, avoid redirection
                        if (protocol != null) {
                            notFound = false;
                            boolean isHttp = protocolInfo.protocol.startsWith("http");
                            protocolHandler = protocolHandlers.get(
                                    protocol.toLowerCase());

                            // Since Grizzly is listening by default for http, don't redirect
                            // if http is used and this pipeline can serve it.
                            boolean redirect = (isRequestedTransportSecure 
                                    == protocolInfo.isSecure && isHttp);               

                            // Tell the ReadTask we already have read bytes.
                            readTask.setBytesAvailable(true);           
                            readTask.setByteBuffer(protocolInfo.byteBuffer);

                            if (protocolHandler != null && !redirect){
                                protocolHandler.handle(protocolInfo);
                                if (!isHttp){
                                    if (mappedProtocols == null){
                                        mappedProtocols= new ConcurrentHashMap
                                                <SelectionKey,ProtocolHandler>();
                                    }
                                    mappedProtocols.put(key,protocolHandler);
                                }
                                if (protocolInfo.keepAlive){
                                    readTask.registerKey();
                                }
                                isTaskTerminated = true;
                                readTask.terminate(protocolInfo.keepAlive);                            
                            } else { 
                                if ( isRequestedTransportSecure ) {
                                    ((SSLReadTask)readTask).setHandshake(false);
                                }
                                readTask.doTask();
                                if (readTask.getSelectionKey() != key) {
                                   isTaskTerminated = true;
                                }
                            } 
                        } else {
                            // If the protocol wasn't found, it might be because 
                            // lack of missing bytes. Thus we must register the key for 
                            // extra bytes. The trick here is to re-use the ReadTask
                            // ByteBufferInpuStream.
                            final boolean isSslBuffer = !protocolInfo.handshake;

                            ByteBuffer tmpBB;
                            
                            if (isSslBuffer) {
                                tmpBB = protocolInfo.inputBB;
                            } else {
                                tmpBB = protocolInfo.byteBuffer;
                            }


                            if (!tmpBB.hasRemaining()) {
                                tmpBB.clear();
                            }
                            
                            int byteAvailables = tmpBB.position();
                            ByteBufferInputStream.readBlocking(
                                    (SocketChannel) key.channel(), tmpBB, 1000);
                            
                            if ((tmpBB.position() - byteAvailables) <= 0){
                                isTaskTerminated = true;
                                cancel(readTask,tmpBB);    
                                return;
                            }
                            
//                            protocolInfo.byteBuffer = tmpBB;
                        }
                    } 
                } else {
                    protocolHandler.handle(protocolInfo);
                    readTask.registerKey();
                    isTaskTerminated = true;
                    readTask.terminate(true);
                }
            } catch (Throwable ex){
                notFound = false;
                if (logger.isLoggable(Level.WARNING)){
                    logger.log(Level.WARNING,"PortUnification exception",ex);
                }   
                if (!isTaskTerminated) {
                    isTaskTerminated = true;
                    cancel(readTask, protocolInfo.byteBuffer);
                }
                
                cancel(readTask,protocolInfo.byteBuffer);         
            } finally {                                
                if (readTry >= maxTry && !isTaskTerminated){
                    cancel(readTask,protocolInfo.byteBuffer);
                } 
                protocolHandler = null;
                readTask = null;
                protocolInfo.recycle();
                puTasks.offer(this);                
            }
        }
        
        
        @Override
        public int getType(){
            return PU_TASK;
        }


        private void cancel(Task task,ByteBuffer bb){
            // Prevent calling multiple terminate.
            if (key == null || task == null
                    || task.getSelectionKey() != key) {
                return;
            }
            if (logger.isLoggable(Level.FINE) 
                    || task.getSelectorThread().isEnableNioLogging() ){                
                logger.log(Level.FINE,"Invalid request from: " +
                           task.getSelectionKey().channel() 
                           + " " + bb.remaining());
            }
            readTask.terminate(false);
        }
        
        
        /**
         * When the <code>Task</code> is completed, make sure it is removed from 
         * the cached list.
         */
        @Override
        public void taskEvent(TaskEvent event) {
        }
    }

    
    /**
     * Load <code>ProtocolHandler</code> defined as a System property
     * (-Dcom.sun.enterprise.web.connector.grizzly.protocolHandlers=... , ...)
     */
    private void loadHandlers(){      
        if ( System.getProperty(PROTOCOL_HANDLERS) != null){
            StringTokenizer st = new StringTokenizer(
                    System.getProperty(PROTOCOL_HANDLERS),",");
            
            while (st.hasMoreTokens()){
                ProtocolHandler protocolHandler = (ProtocolHandler)
                    loadInstance(st.nextToken());
                if ( protocolHandler != null) {
                    String[] protocols = protocolHandler.getProtocols();
                    for(String protocol: protocols) {
                        if (protocol != null) {
                            protocolHandlers.put(protocol.toLowerCase(), protocolHandler);
                        }
                    }
                }
            } 
        }   
    }
    
    
    /**
     * Load <code>ProtocolFinder</code> defined as a System property
     * (-Dcom.sun.enterprise.web.connector.grizzly.protocolFinders=... , ...)
     */    
    private void loadFinders(){      
        if ( System.getProperty(PROTOCOL_FINDERS) != null){
            StringTokenizer st = new StringTokenizer(
                    System.getProperty(PROTOCOL_FINDERS),",");
            
            while (st.hasMoreTokens()){
                ProtocolFinder protocolFinder = (ProtocolFinder)
                    loadInstance(st.nextToken());
                protocolFinders.offer(protocolFinder);
            } 
        }   
    }
    

    /**
     * Util to load classes using reflection.
     */
    private Object loadInstance(String property){        
        Class className = null;                               
        try{                              
            className = Class.forName(property,true,
                    Thread.currentThread().getContextClassLoader());
            return className.newInstance();
        } catch (Throwable t) {
            // Log me 
        }   
        return null;
    }  
    
    
    /**
     * Add an instance of <code>ProtocolFinder</code>
     */
    public void addProtocolFinder(ProtocolFinder protocolFinder){
        protocolFinders.offer(protocolFinder);
    }
    
    
    /**
     * Add an instance of <code>ProtocolHandler</code>
     */
    public void addProtocolHandler(ProtocolHandler protocolHandler){
        String[] protocols = protocolHandler.getProtocols();
        for(String protocol: protocols){
            protocolHandlers.put(protocol,protocolHandler);  
        }
    }   
    
    
    /**
     * Remove a <code>ProtocolFinder</code>
     */
    public void removeProtocolFinder(ProtocolFinder protocolFinder){
        protocolFinders.remove(protocolFinder);
    }
    
    
    /**
     * Remove a <code>ProtocolHandler</code>
     */
    public void removeProtocolHandler(ProtocolHandler protocolHandler){
        String[] protocols = protocolHandler.getProtocols();
        for(String protocol: protocols){
            protocolHandlers.remove(protocol);  
        }
    } 
}