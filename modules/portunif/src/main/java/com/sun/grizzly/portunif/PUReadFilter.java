/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.portunif;

import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.util.ConcurrentWeakHashMap;
import com.sun.grizzly.util.DataStructures;
import com.sun.grizzly.util.Utils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Port unification ReadFilter.
 *
 * Could be used for usecases, where on one TCP/UDP/... port several higher level
 * protocols should be served (for example HTTP, HTTPS, IIOP).
 *
 * If input {@link ByteBuffer} is empty - <code>ReadFilter</code> logic will
 * be executed in order to read request data.
 *
 * @author Jean-Francois Arcand
 * @author Alexey Stashok
 */
public class PUReadFilter extends ReadFilter {

    private final static Logger logger = Controller.logger();

    public static final long DEFAULT_READ_TIMEOUT = 1000;

    public static final String PROTOCOL_FINDERS =
            "com.sun.grizzly.portunif.protocolFinders";

    public static final String PROTOCOL_HANDLERS =
            "com.sun.grizzly.portunif.protocolHandlers";

    public static final String PU_PRE_PROCESSORS =
            "com.sun.grizzly.portunif.PUPreProcessors";

    private static final int MAX_FIND_TRIES = 10;
    
    private static final int FIND_TIMEOUT_MILLIS = 3000;

    private static final ProtocolHandler filterChainProtocolHandler =
            new DefaultFilterChainProtocolHandler();

    private static final ProtocolHandler sslFilterChainProtocolHandler =
            new SSLFilterChainProtocolHandler();

    /**
     * List of available <code>ProtocolHandler</code>.
     */
    private final Map<String, ProtocolHandler> protocolHandlers =
            new ConcurrentHashMap<String, ProtocolHandler>();

    /**
     * List of available <code>ProtocolFinder</code>.
     */
    private final Queue<ProtocolFinder> protocolFinders =
        DataStructures.getCLQinstance(ProtocolFinder.class);

    /**
     * List of available <code>ProtocolFinder</code>.
     */
    private final Map<SelectionKey, ProtocolHandler> mappedProtocols =
            new ConcurrentWeakHashMap<SelectionKey, ProtocolHandler>();

    /**
     * List of registered <code>PUPreProcessor</code>.
     */
    private List<PUPreProcessor> preProcessors;

    /**
     * Time interval <code>PUReadFilter</code> will expect more data on channel.
     *
     * If no <code>ProtocolFinder</code> found - Filter tries to read more data
     * and rerun finders.
     */
    private long readTimeout = DEFAULT_READ_TIMEOUT;

    @Override
    public boolean execute(Context context) throws IOException {
        SelectionKey selectionKey = context.getSelectionKey();
        ProtocolHandler protocolHandler = mappedProtocols.get(selectionKey);

        //investigate how to lower the gc overhead:
        ProtocolRequestWorkerThreadAdapter protocolRequest =
                new ProtocolRequestWorkerThreadAdapter();
        protocolRequest.setContext(context);

        final boolean isloglevelfine = logger.isLoggable(Level.FINE);
        int readTry = 0;
        try {
            // Perform input buffer read
            // Peek correspondent ByteBuffer first
            ByteBuffer readBuffer = protocolHandler != null ?
                protocolHandler.getByteBuffer() :
                protocolRequest.getByteBuffer();

            if (!execute(context, readBuffer)) {
                // if ReadFilter didn't succeed - return
                if (isloglevelfine) {
                    logger.log(Level.FINE, "PUReadFilter. Read failed");
                }

                if (protocolHandler != null &&
                        context.getKeyRegistrationState() ==
                        Context.KeyRegistrationState.CANCEL) {
                    protocolHandler.expireKey(selectionKey);
                }

                return false;
            }

            if (protocolHandler == null) {
                if (isloglevelfine) {
                    logger.log(Level.FINE, "PUReadFilter. Finding protocol...");
                }

                final long startTime = System.currentTimeMillis();
                
                while (readTry++ < MAX_FIND_TRIES &&
                        System.currentTimeMillis() - startTime < FIND_TIMEOUT_MILLIS) {
                    String protocolName;

                    if (preProcessors != null) {
                        for (PUPreProcessor preProcessor : preProcessors) {
                            if (isloglevelfine) {
                                logger.log(Level.FINE, "PUReadFilter. Apply preprocessor process: {0}", preProcessor);
                            }

                            if (preProcessor.process(context, protocolRequest)) {
                                protocolRequest.addPassedPreProcessor(
                                        preProcessor.getId());
                            }
                        }
                    }

                    try {
                        protocolName = findProtocol(context, protocolRequest);
                        if (isloglevelfine) {
                            logger.log(Level.FINE, "PUReadFilter. Found protocol: {0}", protocolName);
                        }
                    } catch (IOException e) {
                        if (isloglevelfine) {
                            logger.log(Level.FINE, "PUReadFilter. IOException during protocol finding:", e);
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
                                    context, protocolRequest,
                                    protocolRequest.isMapSelectionKey());
                        } else {
                            // Protocol will be processed by next Filters in chain
                            if (protocolRequest.isMapSelectionKey()) {
                                mappedProtocols.put(selectionKey,
                                        getProtocolChainHandler(context, protocolRequest));
                            }
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
                                    if (isloglevelfine) {
                                        logger.log(Level.FINE,
                                                "PUReadFilter. Apply preprocessor POSTProcess: {0}", preProcessor);
                                    }

                                    preProcessor.postProcess(context, protocolRequest);
                                }
                            }
                        }

                        // Try to read more data
                        int bytesRead;
                        try {
                            bytesRead = Utils.readWithTemporarySelector(
                                selectionKey.channel(),
                                protocolRequest.getByteBuffer(), readTimeout).bytesRead;
                        } catch (Exception e) {
                            logger.log(Level.FINE, "PUReadFilter. Error when reading more bytes", e);
                            bytesRead = -1;
                        }

                        if (isloglevelfine) {
                            logger.log(Level.FINE, "PUReadFilter. Read more bytes: {0}", bytesRead);
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
            Level level = Level.WARNING;
            if (ex instanceof CancelledKeyException){
                level = Level.FINE;
            }

            if (logger.isLoggable(level)) {
                logger.log(level,
                           LogMessages.WARNING_GRIZZLY_PU_GENERAL_EXCEPTION(),
                           ex);
            }
            cancelKey(context);
            return false;
        } finally {
            if (readTry == MAX_FIND_TRIES) {
                cancelKey(context);
            }
        }

        return true;
    }

    /**
     * Returns filter chain <code>ProtocolHandler</code>, depending on current
     * {@link Context} and <code>PUProtocolRequest</code> state
     *
     * @param context
     * @param protocolRequest
     * @return <code>ProtocolHandler</code>
     */
    protected ProtocolHandler getProtocolChainHandler(Context context,
            PUProtocolRequest protocolRequest) {
        if (protocolRequest.getSSLEngine() != null) {
            return sslFilterChainProtocolHandler;
        }

        return filterChainProtocolHandler;
    }

    public boolean processProtocolHandler(ProtocolHandler protocolHandler,
            Context context, PUProtocolRequest protocolRequest,
            boolean mapSelectionKey) throws IOException {

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "PUReadFilter. Process request with handler: {0}", protocolHandler);
        }

        boolean isKeepAlive = protocolHandler.handle(context, protocolRequest);

        mapSelectionKey = protocolRequest.isMapSelectionKey();
        if (isKeepAlive && mapSelectionKey) {
            mappedProtocols.put(context.getSelectionKey(), protocolHandler);
        } else {
            mappedProtocols.remove(context.getSelectionKey());
        }
        
        if (protocolRequest.isExecuteFilterChain()) return true;

        if (isKeepAlive) {
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
    @SuppressWarnings({"unchecked"})
    public void configure(Properties props) {
        configure((List<ProtocolFinder>) loadPUArtifacts(props.getProperty(PROTOCOL_FINDERS)),
                (List<ProtocolHandler>) loadPUArtifacts(props.getProperty(PROTOCOL_HANDLERS)),
                (List<PUPreProcessor>) loadPUArtifacts(props.getProperty(PU_PRE_PROCESSORS)));
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
     * Time interval <code>PUReadFilter</code> will expect more data on channel.
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
     * Time interval <code>PUReadFilter</code> will expect more data on channel.
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

        for (ProtocolFinder protocolFinder : protocolFinders) {
            String protocolName = protocolFinder.find(context, protocolRequest);
            if (protocolName != null) {
                return protocolName;
            }
        }

        return null;
    }

}
