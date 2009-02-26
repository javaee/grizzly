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

package com.sun.grizzly.portunif;

import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.util.Utils;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Port unification Filter.
 * 
 * Could be used for usecases, where on one TCP/UDP/... port several higher level
 * protocols should be served (for example HTTP, HTTPS, IIOP).
 * 
 * If input <code>ByteBuffer</code> is empty - <code>ReadFilter</code> logic will 
 * be executed in order to read request data.
 * 
 * @author Jean-Francois Arcand
 * @author Alexey Stashok
 */
public class PUFilter extends ReadFilter {
    public static final long DEFAULT_READ_TIMEOUT = 1000;
    
    public static final String PROTOCOL_FINDERS = 
            "com.sun.grizzly.portunif.protocolFinders";
    
    public static final String PROTOCOL_HANDLERS = 
            "com.sun.grizzly.portunif.protocolHandlers";
    
    public static final String PU_PRE_PROCESSORS = 
            "com.sun.grizzly.portunif.PUPreProcessors";

    private static final int MAX_FIND_TRIES = 2;
    
    private static final ProtocolHandler filterChainProtocolHandler = 
            new FilterChainProtocolHandler();
    
    /**
     * List of available <code>ProtocolHandler</code>.
     */
    private ConcurrentHashMap<String, ProtocolHandler> protocolHandlers = 
            new ConcurrentHashMap<String, ProtocolHandler>();
    
    /**
     * List of available <code>ProtocolFinder</code>.
     */
    private ConcurrentLinkedQueue<ProtocolFinder> protocolFinders = 
            new ConcurrentLinkedQueue<ProtocolFinder>();
    
    /**
     * List of available <code>ProtocolFinder</code>.
     */
    private volatile Map<SelectionKey, ProtocolHandler> mappedProtocols = 
            createMappedProtocolsMap();
    
    /**
     * List of registered <code>PUPreProcessor</code>.
     */
    private List<PUPreProcessor> preProcessors;
    
    /**
     * Time interval <code>PUFilter</code> will expect more data on channel.
     * 
     * If no <code>ProtocolFinder</code> found - Filter tries to read more data
     * and rerun finders.
     */
    private long readTimeout = DEFAULT_READ_TIMEOUT;

    /**
     * Logger
     */
    private static Logger logger = Controller.logger();

    @Override
    public boolean execute(Context context) throws IOException {
        // Perform input buffer read
        if (!super.execute(context)) {
            // if ReadFilter didn't succeed - return
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "PUFilter. Read failed");
            }
            return false;
        }

        SelectionKey selectionKey = context.getSelectionKey();
        ProtocolHandler protocolHandler = null;
        
        if (mappedProtocols != null) {
            protocolHandler = mappedProtocols.get(selectionKey);
        }
        
        ProtocolRequestWorkerThreadAdapter protocolRequest = 
                new ProtocolRequestWorkerThreadAdapter();
        protocolRequest.setContext(context);
        
        int readTry = 0;
        try {
            if (protocolHandler == null) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "PUFilter. Finding protocol...");
                }
                
                while (readTry++ < MAX_FIND_TRIES) {
                    String protocolName = null;
                    
                    if (preProcessors != null) {
                        for (PUPreProcessor preProcessor : preProcessors) {
                            if (logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE, "PUFilter. Apply preprocessor process: " + preProcessor);
                            }
                            
                            if (preProcessor.
                                    process(context, protocolRequest)) {
                                protocolRequest.addPassedPreProcessor(
                                        preProcessor.getId());
                            }
                        }
                    }
                    
                    try {
                        protocolName = findProtocol(context, protocolRequest);
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "PUFilter. Found protocol: " + protocolName);
                        }
                    } catch (IOException e) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "PUFilter. IOException during protocol finding:", e);
                        }
                        
                        exceptionKey(context, e);
                        return false;
                    }
                    
                    // Get the associated ProtocolHandler. If the current
                    // SelectorThread can server the request, avoid redirection
                    if (protocolName != null) {
                        protocolRequest.setProtocolName(protocolName);
                        protocolHandler = protocolHandlers.get(protocolName);

                        if (protocolHandler != null) {
                            return processProtocolHandler(protocolHandler, 
                                    context, protocolRequest, true);
                        } else { 
                            // Protocol will be processed by next Filters in chain
                            mapSelectionKey(selectionKey, 
                                    filterChainProtocolHandler);
                            return true;
                        }
                    } else {
                        // If the protocol wasn't found, it might be because
                        // lack of missing bytes. Thus we must register the key for
                        // extra bytes. The trick here is to re-use the ReadTask
                        // ByteBufferInpuStream.

                        // Call portProcess on each passed <code>PUPreProcessor</code>
                        if (preProcessors != null &&
                                protocolRequest.getPassedPreProcessors() != null) {
                            Collection<String> passedPreProcessors = 
                                    protocolRequest.getPassedPreProcessors();
                            for (int i = preProcessors.size() - 1; i >= 0; i--) {
                                PUPreProcessor preProcessor = preProcessors.get(i);
                                if (passedPreProcessors.contains(preProcessor.getId())) {
                                    if (logger.isLoggable(Level.FINE)) {
                                        logger.log(Level.FINE, 
                                                "PUFilter. Apply preprocessor POSTProcess: " + 
                                                preProcessor);
                                    }
                                    
                                    preProcessor.postProcess(context, protocolRequest);
                                }
                            }
                        }
                        
                        // Try to read more data
                        int bytesRead = Utils.readWithTemporarySelector(
                                selectionKey.channel(), 
                                protocolRequest.getByteBuffer(), readTimeout);
                        
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "PUFilter. Read more bytes: " + bytesRead);
                        }
                        
                        if (bytesRead <= 0) {
                            return true;
                        }
                    }
                }
            } else {
                return processProtocolHandler(protocolHandler, context, 
                        protocolRequest, false);
            }
        } catch (Throwable ex) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "PortUnification exception", ex);
            }
            cancelKey(context);
            return false;
        } finally {
            if (readTry == MAX_FIND_TRIES) {
                cancelKey(context);
            }
            protocolHandler = null;
        }
        
        return true;
    }

    @Override
    public boolean postExecute(Context context) throws IOException {
        return super.postExecute(context);
    }

    public boolean processProtocolHandler(ProtocolHandler protocolHandler, 
            Context context, PUProtocolRequest protocolRequest, 
            boolean mapSelectionKey) throws IOException {
        
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "PUFilter. Process request with handler: " + protocolHandler);
        }
        
        boolean isKeepAlive = protocolHandler.handle(context, protocolRequest);

        if (protocolRequest.isExecuteFilterChain()) return true;
        
        if (isKeepAlive) {
            if (mapSelectionKey) {
                mapSelectionKey(context.getSelectionKey(), protocolHandler);
            }
            
            reRegisterKey(context);
        } else {
            cancelKey(context);
        }

        return false;
    }
    
    private void exceptionKey(Context context, Exception e) {
        context.setAttribute(ProtocolFilter.SUCCESSFUL_READ, Boolean.FALSE);
        context.setAttribute(Context.THROWABLE, e);
        cancelKey(context);
    }

    private void cancelKey(Context context) {
        context.setKeyRegistrationState(Context.KeyRegistrationState.CANCEL);
    }
    
    private void reRegisterKey(Context context) {
        context.setKeyRegistrationState(Context.KeyRegistrationState.REGISTER);
    }

    /**
     * Load <code>ProtocolHandler</code> defined as a System property
     * (-Dcom.sun.enterprise.web.connector.grizzly.protocolHandlers=... , ...)
     */
    private static List loadPUArtifacts(String enumeration) {
        if (enumeration != null) {
            StringTokenizer st =
                    new StringTokenizer(System.getProperty(enumeration), ",");

            List result = new ArrayList(4);
            while (st.hasMoreTokens()) {
                Object artifact = loadInstance(st.nextToken());
                if (artifact != null) {
                    result.add(artifact);
                }
            }
            
            return result;
        }
        
        return null;
    }

    /**
     * Util to load classes using reflection.
     */
    private static Object loadInstance(String className) {
        try {
            Class clazz = Class.forName(className, true, 
                    Thread.currentThread().getContextClassLoader());
            
            return clazz.newInstance();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error initializing class: " + className, e);
        }
        return null;
    }

    /**
     * Configures port unification depending on passed <code>Properties</code>
     * @param props <code>Properties</code>
     */
    public void configure(Properties props) {
        List<ProtocolFinder> protocolFinders = 
                loadPUArtifacts(props.getProperty(PROTOCOL_FINDERS));
        
        List<ProtocolHandler> protocolHandlers = 
                loadPUArtifacts(props.getProperty(PROTOCOL_HANDLERS));
        
        List<PUPreProcessor> puPreProcessors = 
                loadPUArtifacts(props.getProperty(PU_PRE_PROCESSORS));
     
        configure(protocolFinders, protocolHandlers, puPreProcessors);
    }
    
    
    /**
     * Configures port unification depending on passed <code>List</code>s
     * @param protocolFinders <code>ProtocolFinder</code>s
     * @param protocolHandlers <code>ProtocolHandler</code>s
     * @param preProcessors <code>PUPreProcessor</code>s
     */
    public void configure(List<ProtocolFinder> protocolFinders, 
            List<ProtocolHandler> protocolHandlers, 
            List<PUPreProcessor> preProcessors) {
        
        this.protocolFinders.clear();
        this.protocolHandlers.clear();
        
        if (protocolFinders != null) {
            for (ProtocolFinder protocolFinder : protocolFinders) {
                addProtocolFinder(protocolFinder);
            }
        }

        if (protocolHandlers != null) {
            for (ProtocolHandler protocolHandler : protocolHandlers) {
                addProtocolHandler(protocolHandler);
            }
        }

        if (preProcessors != null) {
            if (this.preProcessors != null) {
                this.preProcessors.clear();
            }
            
            for (PUPreProcessor preProcessor : preProcessors) {
                addPreProcessor(preProcessor);
            }
        }

    }
    
    /**
     * Add an instance of <code>ProtocolFinder</code>
     */
    public void addProtocolFinder(ProtocolFinder protocolFinder) {
        protocolFinders.offer(protocolFinder);
    }

    /**
     * Remove a <code>ProtocolFinder</code>
     */
    public void removeProtocolFinder(ProtocolFinder protocolFinder) {
        protocolFinders.remove(protocolFinder);
    }

    /**
     * Add an instance of <code>ProtocolHandler</code>
     */
    public void addProtocolHandler(ProtocolHandler protocolHandler) {
        String[] protocols = protocolHandler.getProtocols();
        for (String protocol : protocols) {
            protocolHandlers.put(protocol, protocolHandler);
        }
    }

    /**
     * Remove a <code>ProtocolHandler</code>
     */
    public void removeProtocolHandler(ProtocolHandler protocolHandler) {
        String[] protocols = protocolHandler.getProtocols();
        for (String protocol : protocols) {
            protocolHandlers.remove(protocol);
        }
    }

    /**
     * Add <code>PUPreProcessor</code> to preprocess income request
     */
    public void addPreProcessor(PUPreProcessor preProcessor) {
        if (preProcessors == null) {
            preProcessors = new ArrayList<PUPreProcessor>(2);
        }
        
        preProcessors.add(preProcessor);
    }
    
    /**
     * Remove <code>PUPreProcessor</code> from preprocess queue
     */
    public void removePreProcessor(PUPreProcessor preProcessor) {
        if (preProcessors != null) {
            preProcessors.remove(preProcessor);
        }
    }
    
    /**
     * Set readTimeout.
     * Time interval <code>PUFilter</code> will expect more data on channel.
     * 
     * If no <code>ProtocolFinder</code> found - Filter tries to read more data
     * and rerun finders.
     * @param readTimeout new timeout value
     */
    public void setReadTimeout(long readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * Get readTimeout.
     * Time interval <code>PUFilter</code> will expect more data on channel.
     * 
     * If no <code>ProtocolFinder</code> found - Filter tries to read more data
     * and rerun finders.
     * @return read timeout value
     */
    public long getReadTimeout() {
        return readTimeout;
    }
            
    private String findProtocol(Context context,
            PUProtocolRequest protocolRequest) throws IOException {
        
        Iterator<ProtocolFinder> iterator = protocolFinders.iterator();
        for (ProtocolFinder protocolFinder : protocolFinders) {
            String protocolName = protocolFinder.find(context, protocolRequest);
            if (protocolName != null) {
                return protocolName;
            }
        }
        
        return null;
    }

    private void mapSelectionKey(SelectionKey selectionKey, ProtocolHandler protocolHandler) {
        if (mappedProtocols == null) {
            synchronized(this) {
                if (mappedProtocols == null) {
                    mappedProtocols = createMappedProtocolsMap();
                }
            }
        }
        
        mappedProtocols.put(selectionKey, protocolHandler);
    }
    
    private static Map<SelectionKey, ProtocolHandler> createMappedProtocolsMap() {
        return Collections.synchronizedMap(new WeakHashMap<SelectionKey, ProtocolHandler>());
    }
}