/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2012 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.Controller;
import com.sun.grizzly.ControllerStateListenerAdapter;
import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.arp.AsyncInterceptor;
import com.sun.grizzly.arp.AsyncProtocolFilter;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.http.FileCache.FileCacheEntry;
import com.sun.grizzly.http.algorithms.NoParsingAlgorithm;
import com.sun.grizzly.portunif.PUPreProcessor;
import com.sun.grizzly.portunif.PUReadFilter;
import com.sun.grizzly.portunif.ProtocolFinder;
import com.sun.grizzly.portunif.ProtocolHandler;
import com.sun.grizzly.rcm.ResourceAllocationFilter;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.RequestGroupInfo;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyListener;
import com.sun.grizzly.util.DataStructures;
import com.sun.grizzly.util.ExtendedThreadPool;
import com.sun.grizzly.util.FutureImpl;
import com.sun.grizzly.util.IntrospectionUtils;
import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.util.SelectionKeyAttachment;
import com.sun.grizzly.util.SelectorFactory;
import com.sun.grizzly.util.StreamAlgorithm;
import com.sun.grizzly.util.WorkerThread;
import com.sun.grizzly.util.WorkerThreadImpl;
import com.sun.grizzly.util.http.MimeHeaders;
import com.sun.grizzly.util.res.StringManager;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;



/**
 * The SelectorThread class is the entry point when embedding the Grizzly Web
 * Server. All Web Server configuration must be set on this object before invoking
 * the {@link #listen} method. As an example:
 * <pre><code>
        final SelectorThread selectorThread = new SelectorThread(){
                public void listen() throws IOException, InstantiationException{
                    super.listen();
                    System.out.println("Server started in " + (System.currentTimeMillis() - t1)
                            + " milliseconds.");
                }
        };
        selectorThread.setAlgorithmClassName(StaticStreamAlgorithm.class.getName());       
        selectorThread.setPort(port);
        selectorThread.setWebAppRootPath(folder);
        Adapter adapter = new StaticResourcesAdapter(folder);
        ((StaticResourcesAdapter)adapter).setRootFolder(folder);
        selectorThread.setAdapter(adapter);
        selectorThread.setDisplayConfiguration(true);
        selectorThread.listen(); 
 * </code></pre>
 * 
 * @author Jean-Francois Arcand
 */
public class SelectorThread implements Runnable, MBeanRegistration, GrizzlyListener{
            
    public final static String SERVER_NAME = 
            System.getProperty("product.name") != null 
                ? System.getProperty("product.name") : "grizzly";
    
        
    private final Object lock = new Object();

    
    protected int serverTimeout = Constants.DEFAULT_SERVER_SOCKET_TIMEOUT;

    protected InetAddress inet;
    protected int port;
    
    protected boolean initialized = false;    
    protected final AtomicBoolean running = new AtomicBoolean();
    // ----------------------------------------------------- JMX Support ---/
    
    
    protected String domain;
    protected ObjectName oname;
    protected ObjectName globalRequestProcessorName;
    private ObjectName keepAliveMbeanName;
    private ObjectName connectionQueueMbeanName;
    private ObjectName fileCacheMbeanName;
    protected MBeanServer mserver;
    protected ObjectName processorWorkerThreadName;


    // ------------------------------------------------------Socket setting --/

    protected boolean tcpNoDelay=false;
    
    
    protected int linger=-1;
    
    protected boolean socketKeepAlive = false;
    
    protected int socketTimeout=-1;
    
    
    // Number of keep-alive threads
    protected int keepAliveThreadCount = 1;

    // ------------------------------------------------------ Compression ---/


    /**
     * Compression value.
     */
    protected String compression = "off";
    private final CommaSeparatedList noCompressionUserAgents = new CommaSeparatedList();

    private final CommaSeparatedList restrictedUserAgents = new CommaSeparatedList();

    private final CommaSeparatedList compressableMimeTypes = new CommaSeparatedList("text/html,text/xml,text/plain");
    
    protected int compressionMinSize    = 2048;
       
    // ------------------------------------------------------ Properties----/
    
    
    /**
     * Is the socket reuse socket enabled.
     */
    private boolean reuseAddress = true;
    
    
    /**
     * Buffer the response until the buffer is full.
     */
    protected boolean bufferResponse = false;
    
    
    /**
     * Default HTTP header buffer size.
     */
    protected int maxHttpHeaderSize = Constants.DEFAULT_HEADER_SIZE;
    
    
    /**
     * Is Async HTTP write enabled.
     */
    protected boolean isAsyncHttpWriteEnabled;


    protected int maxPostSize = 2 * 1024 * 1024;

    /**
     * The maximum number of headers allowed for a request.
     */
    protected int maxRequestHeaders = MimeHeaders.MAX_NUM_HEADERS_DEFAULT;

    /**
     * The maximum number of headers allowed for a response.
     */
    protected int maxResponseHeaders = MimeHeaders.MAX_NUM_HEADERS_DEFAULT;

    /**
     * Max number of bytes Grizzly will try to swallow in order to read off
     * the current request payload and prepare input to process next request.
     */
    protected long maxSwallowingInputBytes = -1;

    /**
     * Size of the ByteBuffer used to store the bytes before flushing.
     */
    private int sendBufferSize = Constants.SEND_BUFFER_SIZE;

    /**
     * The {@link Selector} used by the connector.
     */
    protected Selector selector;

    /**
     * {@link SelectorHandler} current {@link SelectorThread} is
     * based on
     */
    protected TCPSelectorHandler selectorHandler;
        
    /**
     * Associated adapter.
     */
    protected Adapter adapter = null;

    
    /**
     * The queue shared by this thread and the code>ProcessorTask</code>.
     */ 
    protected ExecutorService threadPool;
    
  
    /**
     * Placeholder for {@link ExecutorService} statistic.
     */
    protected ThreadPoolStatistic threadPoolStat;
    
    
    /**
     * The timeout used by the thread when processing a request.
     */
    protected int transactionTimeout = Constants.DEFAULT_TIMEOUT;
    
        
    /**
     * Use chunking.
     */
    private boolean useChunking = true;

    /**
     * The auxiliary configuration, which might be used, when Grizzly HttpServer
     * is running behind some HTTP gateway like reverse proxy or load balancer.
     */
    private BackendConfiguration backendConfiguration;
    
    /**
     * Is the {@link ByteBuffer} used by the <code>ReadTask</code> use
     * direct {@link ByteBuffer} or not.
     */
    protected boolean useDirectByteBuffer = false;
    
  
    /**
     * Monitoring object used to store information.
     */
    protected RequestGroupInfo globalRequestProcessor= new RequestGroupInfo();
    
    
    /**
     * Keep-alive stats
     */
    protected KeepAliveStats keepAliveStats = createKeepAliveStats();


    /**
     * If <tt>true</tt>, display the NIO configuration information.
     */
    protected boolean displayConfiguration = false;
    
    
    /**
     * Is monitoring already started.
     */
    protected boolean isMonitoringEnabled = false;
    

    /**
     * The input request buffer size.
     */
    protected int requestBufferSize = Constants.DEFAULT_REQUEST_BUFFER_SIZE;
    
    
    /**
     * Create view {@link ByteBuffer} from another {@link ByteBuffer}
     */
    protected boolean useByteBufferView = false;
    

    /**
     * The {@link Selector} timeout value. By default, it is set to 60000
     * miliseconds (as in the j2se 1.5 ORB).
     */
    protected int selectorTimeout = 1000;


    /**
     * The{@link StreamAlgorithm} used to predict the end of the NIO stream
     */
    protected Class algorithmClass;
    
    
    /**
     * The{@link StreamAlgorithm} used to parse the NIO stream.
     */
    protected String algorithmClassName = DEFAULT_ALGORITHM;
    
    
    /**
     * The default NIO stream algorithm.
     */
    public final static String DEFAULT_ALGORITHM =
        com.sun.grizzly.http.algorithms.
            NoParsingAlgorithm.class.getName();

    
    /**
     * Server socket backlog.
     */
    protected int ssBackLog = 4096;
    
    
    /**
     * The default response-type
     */
    protected String defaultResponseType = Constants.DEFAULT_RESPONSE_TYPE;


    /**
     * The forced request-type
     */
    protected String forcedRequestType = Constants.FORCED_REQUEST_TYPE;
    
    
    /**
     * The root folder where application are deployed
     */
    protected String rootFolder = "";
    
    /**
     * The Grizzly's Controller.
     */
    protected Controller controller;
    
    
    /**
     * RCM support
     */
    protected boolean rcmSupport = false;
    
    
    /**
     * Port unification filter
     */
    protected PUReadFilter portUnificationFilter;
    
    
    protected boolean oOBInline = false;
    
    
    /**
     * Holder for our configured properties.
     */
    protected HashMap<String,Object> properties = new HashMap<String,Object>();
    
    /**
     * SelectorThread {@link ErrorHandler}.
     */
    protected ErrorHandler errorHandler;

    /**
     * Flag indicating whether or not {@link ProcessorTask} instances
     * should be pre-allocated when the SelectorThread starts.
     */
    protected boolean preallocateProcessorTasks;
    
    // ---------------------------------------------------- Object pools --//


    /**
     * {@link Queue} used as an object pool.
     * If the list becomes empty, new {@link ProcessorTask} will be
     * automatically added to the list.
     */
    protected volatile Queue<ProcessorTask> processorTasks;
                                                           
    /**
     * Maximum number of {@link ProcessorTask}s to be cached.
     */
    private int maxCachedProcessorTasks =
            Integer.getInteger("com.sun.grizzly.http.maxCachedProcessorTasks", -1);
    
    /**
     * List of active {@link ProcessorTask}.
     */
    protected final Queue<ProcessorTask> activeProcessorTasks =
        DataStructures.getCLQinstance(ProcessorTask.class);
    
    // -----------------------------------------  Multi-Selector supports --//

    /**
     * The number of read threads.
     */
    protected int readThreadsCount = -1;

    
    /**
     * The logger used by the grizzly classes.
     */
    protected static Logger logger = LoggerUtils.getLogger();
    
    
    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package,
                                 Constants.class.getClassLoader());
    
    
    /**
     * Flag to disable setting a different time-out on uploads.
     */
    protected boolean disableUploadTimeout = true;    
    
    
    /**
     * Maximum timeout on uploads. 5 minutes as in Apache HTTPD server.
     */
    protected int uploadTimeout = 5 * 60 * 1000;
    
    
    /**
     * Number of core threads in a thread pool
     */
    private int coreThreads = StatsThreadPool.DEFAULT_MIN_THREAD_COUNT;


    /**
     * Number of max threads in a thread pool
     */
    private int maxThreads = StatsThreadPool.DEFAULT_MAX_THREAD_COUNT;

    /**
     * Class name of thread pool implementation
     */
    private String threadPoolClassname;
    
    
    // ------------------------------------------------- FileCache support --//
   
    
    /**
     * The FileCacheFactory associated with this Selector
     */ 
    protected FileCacheFactory fileCacheFactory;
    
        /**
     * Timeout before remove the static resource from the cache.
     */
    protected int secondsMaxAge = -1;
    
    
    /**
     * The maximum entries in the {@link FileCache}
     */
    protected int maxCacheEntries = 1024;
    
 
    /**
     * The maximum size of a cached resources.
     */
    protected long minEntrySize = Long.MIN_VALUE;
            
               
    /**
     * The maximum size of a cached resources.
     */
    protected long maxEntrySize = Long.MAX_VALUE;
    
    
    /**
     * The maximum cached bytes
     */
    protected long maxLargeFileCacheSize = Long.MAX_VALUE;
 
    
    /**
     * The maximum cached bytes
     */
    protected long maxSmallFileCacheSize = 1048576;
    
    
    /**
     * Is the FileCache enabled.
     */
    protected boolean isFileCacheEnabled = true;
    
    
    /**
     * Is the large FileCache enabled.
     */
    protected boolean isLargeFileCacheEnabled = true;    
    
    // --------------------------------------------- Asynch supports -----//
    
    /**
     * Is asynchronous mode enabled?
     */
    protected boolean asyncExecution = false;
    
    
    /**
     * When the asynchronous mode is enabled, the execution of this object
     * will be delegated to the {@link AsyncHandler}
     */
    protected AsyncHandler asyncHandler;
    
    
    /**
     * Is the DEFAULT_ALGORITHM used.
     */
    protected static boolean defaultAlgorithmInstalled = true;
    
    
    /**
     * The JMX Management class.
     */
    private Management jmxManagement = null;
    
    
    /**
     * The Classloader used to load instance of StreamAlgorithm.
     */
    private ClassLoader classLoader;
    
    
    /**
     * Grizzly own debug flag.
     */
    protected static boolean enableNioLogging = false;
    
    
    /**
     * Static list of current instance of this class.
     */
    protected final static ConcurrentHashMap<Integer,SelectorThread> 
            selectorThreads = new ConcurrentHashMap<Integer,SelectorThread>();
    
    /**
     * The current SelectionKeyHandler.
     */
    private SelectorThreadKeyHandler keyHandler;
    
    // Force keep-alive
    protected boolean forceKeepAlive = false;
    
    // AsyncInterceptor implemenation.
    private AsyncInterceptor asyncInterceptor;

    // ProcessorTaskFactory, which is used to create ProcessorTasks
    protected volatile ProcessorTaskFactory processorTaskFactory;

    // ---------------------------------------------------- Constructor --//
    
    
    /**
     * Create the {@link Selector} object. Each instance of this class
     * will listen to a specific port.
     */
    public SelectorThread() {
        setMaxCachedProcessorTasks(maxCachedProcessorTasks);
    }
    
    // ------------------------------------------------------ Selector hook --/
    
    
    /**
     * Return the {@link SelectorThread} which listen on port, or null
     * if there is no {@link SelectorThread}.
     *
     * @deprecated This method is not safe when a machine with multiple
     *  listeners are bound to different addresses using the same port.
     *  Use {@link SelectorThread#getSelector(java.net.InetAddress, int)}.
     */
    @Deprecated
    public static SelectorThread getSelector(int port){
        return getSelector(null, port);
    }


    public static SelectorThread getSelector(InetAddress address, int port) {

        SelectorThread thread = selectorThreads.get(getSelectorThreadLookupKey(address, port));
        if (thread == null) {
            // try to look up the SelectorThread by the port in the case the
            // SelectorThread was bound to 0.0.0.0.
            thread = selectorThreads.get(port);
        }
        return thread;
    }
    
    
    /**
     * Return an <code>Enumeration</code> of the active 
     * {@link SelectorThread}s
     */
    public static Enumeration<SelectorThread> getSelectors(){
        return selectorThreads.elements();
    }
    
    
    // ----------------------------------------------------------------------/
    
    
    /**
     * Register a {@link SelectionKey} to this {@link Selector}
     * running of this thread.
     */
    public void registerKey(SelectionKey key){
        selectorHandler.register(key.channel(), SelectionKey.OP_READ);
    } 

   // -------------------------------------------------------------- Init // 


    /**
     * Initialize the Grizzly Framework classes.
     */
    protected void initController(){
        if (controller == null){
            controller = new Controller();
        }
        // Set the logger of the utils package to the same as this class.       
        LoggerUtils.setLogger(logger);
        // Set the controller logger
        Controller.setLogger(logger);
        
        selectorHandler = createSelectorHandler();
        configureSelectorHandler(selectorHandler);
        controller.setSelectorHandler(selectorHandler);
        
        keyHandler = createSelectionKeyHandler();

        keyHandler.setLogger(logger);
        long timeout = keepAliveStats.getKeepAliveTimeoutInSeconds();
        keyHandler.setTimeout(timeout == -1
                    ? SelectionKeyAttachment.UNLIMITED_TIMEOUT: timeout * 1000L);
        selectorHandler.setSelectionKeyHandler(keyHandler);

        configureProtocolChain();

        controller.setThreadPool(threadPool);
        if (readThreadsCount > controller.getReadThreadsCount()){
            controller.setReadThreadsCount(readThreadsCount);
        } else {
            readThreadsCount = controller.getReadThreadsCount();
        }
    }
    
    /**
     * Create {@link TCPSelectorHandler}
     */
    protected TCPSelectorHandler createSelectorHandler() {
        return new SelectorThreadHandler(this);
    }

    /**
     * Create {@link SelectorThreadKeyHandler}
     */
    protected SelectorThreadKeyHandler createSelectionKeyHandler() {
        return new SelectorThreadKeyHandler(this);
    }

    /**
     * Configure <tt>SelectorThread</tt> {@link ProtocolChain}
     */
    protected void configureProtocolChain() {
        final HttpProtocolChain protocolChain = new HttpProtocolChain();
        protocolChain.enableRCM(rcmSupport);

        configureFilters(protocolChain);

        DefaultProtocolChainInstanceHandler instanceHandler
                = new DefaultProtocolChainInstanceHandler(){
            /**
             * Always return instance of ProtocolChain.
             */
            @Override
            public ProtocolChain poll(){
                return protocolChain;
            }

            /**
             * Pool an instance of ProtocolChain.
             */
            @Override
            public boolean offer(ProtocolChain instance){
                return true;
            }
        };

        controller.setProtocolChainInstanceHandler(instanceHandler);
    }

    /**
     * Configure {@link TCPSelectorHandler}
     */
    protected void configureSelectorHandler(TCPSelectorHandler selectorHandler) {
        selectorHandler.setSelector(selector);
        selectorHandler.setPort(port);
        selectorHandler.setInet(inet);
        selectorHandler.setLinger(linger);
        selectorHandler.setLogger(logger);
        selectorHandler.setReuseAddress(reuseAddress);
        selectorHandler.setSelectTimeout(selectorTimeout);
        selectorHandler.setServerTimeout(serverTimeout);
        selectorHandler.setSocketTimeout(keepAliveStats.getKeepAliveTimeoutInSeconds() * 1000);
        selectorHandler.setSsBackLog(ssBackLog);
        selectorHandler.setTcpNoDelay(tcpNoDelay);
        selectorHandler.setKeepAlive(socketKeepAlive);
    }
    
    /**
     * Create and configure resource allocation {@link ProtocolFilter}
     * @return resource allocation {@link ProtocolFilter}
     */
    protected ProtocolFilter createRaFilter() {
        return new ResourceAllocationFilter() {
            /**
             * Creates a new {@link ExecutorService}
             */
            @Override
            protected ExecutorService newThreadPool(int threadCount,
                    ExecutorService p) {
                if (threadCount == 0) {
                    return null;
                }
                StatsThreadPool threadPool = new StatsThreadPool();
                threadPool.setCorePoolSize(1);
                threadPool.setMaximumPoolSize(threadCount);
                threadPool.setName("RCM_" + threadCount);
                threadPool.start();
                return threadPool;
            }
        };
    }

    /**
     * Create HTTP parser {@link ProtocolFilter}
     * @return HTTP parser {@link ProtocolFilter}
     */
    protected ProtocolFilter createHttpParserFilter() {
        if (asyncExecution){
           AsyncProtocolFilter f =
                   new AsyncProtocolFilter(algorithmClass, inet, port);
           if (asyncInterceptor != null) {
               f.setInterceptor(asyncInterceptor);
           }
           return f;
        } else {
            return new DefaultProtocolFilter(algorithmClass, inet, port);
        }
    }

    /**
     * Adds and configures {@link ProtocolChain}'s filters
     * @param {@link ProtocolChain} to configure
     */
    protected void configureFilters(ProtocolChain protocolChain) {
        if (portUnificationFilter != null) {
            protocolChain.addFilter(portUnificationFilter);
        } else if (rcmSupport) {
            protocolChain.addFilter(createRaFilter());
        } else {
            ReadFilter readFilter = new ReadFilter();
            protocolChain.addFilter(readFilter);
        }

        final ProtocolFilter filter = createHttpParserFilter();
        protocolChain.addFilter(filter);
    }
    
    /**
     * Configures port unification depending on passed <code>Properties</code>
     * @param props <code>Properties</code>. If props is null - port unification
     *        will be configured from System properties
     */
    public void configurePortUnification(Properties props) {
        if (props == null) {
            props = System.getProperties();
        }

        portUnificationFilter = new PUReadFilter();
        portUnificationFilter.configure(props);
    }

    /**
     * Configures port unification depending on passed <code>List</code>s
     * @param protocolFinders {@link ProtocolFinder}s
     * @param protocolHandlers {@link ProtocolHandler}s
     * @param preProcessors <code>PUPreProcessor</code>s
     */
    public void configurePortUnification(List<ProtocolFinder> protocolFinders,
            List<ProtocolHandler> protocolHandlers, 
            List<PUPreProcessor> preProcessors) {
        
        portUnificationFilter = new PUReadFilter();
        portUnificationFilter.configure(protocolFinders, protocolHandlers, 
                preProcessors);
    }

    /**
     * Return thr {@link ProtocolChain} used by this instance.
     * @return {@link ProtocolChain}
     */
    public ProtocolChain getProtocolChain(){
        if (controller == null) throw new 
                IllegalStateException("SelectorThread not initialized (initEndpoint not called)");

        return getController().getProtocolChainInstanceHandler().poll();
    }

    
    /**
     * Create a new {@link StatsThreadPool} instance.
     */
    protected ExecutorService newThreadPool(int maxQueueSize,
            String name, int port,
            int priority) {
        ExecutorService newThreadPool = null;
        
        if (threadPoolClassname == null) {
             newThreadPool = new StatsThreadPool("Grizzly-" + port, coreThreads,
                    maxThreads, maxQueueSize,
                    StatsThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT,
                    TimeUnit.MILLISECONDS);
            ((StatsThreadPool) newThreadPool).setPort(port);
            ((StatsThreadPool) newThreadPool).setPriority(priority);
        } else {
            try {
                Class threadPoolClass = Class.forName(threadPoolClassname);
                newThreadPool = (ExecutorService) threadPoolClass.newInstance();
            } catch (Exception e) {
                newThreadPool = new StatsThreadPool("Grizzly-" + port, coreThreads,
                        maxThreads, maxQueueSize,
                        StatsThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT,
                        TimeUnit.MILLISECONDS);
                ((StatsThreadPool) newThreadPool).setPort(port);
                ((StatsThreadPool) newThreadPool).setPriority(priority);
            }
        }
        
        if (threadPool instanceof ExtendedThreadPool) {
            ExtendedThreadPool extThreadPool = (ExtendedThreadPool) threadPool;
            extThreadPool.setCorePoolSize(coreThreads);
            extThreadPool.setMaximumPoolSize(maxThreads);
            extThreadPool.setName(name);
        }

        return newThreadPool;
    }
    
    
    /**
     * Initialize the {@link FileCacheFactory} associated with this instance
     */
    protected void initFileCacheFactory() {
        if (fileCacheFactory != null) return;
        
        fileCacheFactory = createFileCacheFactory();
        //FileCacheFactory.cache.put(port, fileCacheFactory);

        configureFileCacheFactory();
    }
       
    protected FileCacheFactory createFileCacheFactory() {
        return FileCacheFactory.getFactory(inet, port);
    }

    protected void configureFileCacheFactory() {
        fileCacheFactory = FileCacheFactory.getFactory(inet, port);
        fileCacheFactory.setIsEnabled(isFileCacheEnabled);
        fileCacheFactory.setLargeFileCacheEnabled(isLargeFileCacheEnabled);
        fileCacheFactory.setSecondsMaxAge(secondsMaxAge);
        fileCacheFactory.setMaxCacheEntries(maxCacheEntries);
        fileCacheFactory.setMinEntrySize(minEntrySize);
        fileCacheFactory.setMaxEntrySize(maxEntrySize);
        fileCacheFactory.setMaxLargeCacheSize(maxLargeFileCacheSize);
        fileCacheFactory.setMaxSmallCacheSize(maxSmallFileCacheSize);
        fileCacheFactory.setIsMonitoringEnabled(isMonitoringEnabled);
        fileCacheFactory.setHeaderBBSize(requestBufferSize);
    }
    
    /**
     * Injects {@link ThreadPoolStatistic} into every
     * {@link ExecutorService}, for monitoring purposes.
     */
    protected void enableThreadPoolStats() {
        if (threadPool instanceof StatsThreadPool) {
            threadPoolStat.start();

            keepAliveStats.enable();
            StatsThreadPool statsThreadPool = (StatsThreadPool) threadPool;
            statsThreadPool.setStatistic(threadPoolStat);
            threadPoolStat.setThreadPool(threadPool);
        } else {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning(LogMessages.WARNING_GRIZZLY_HTTP_SELECTOR_THREAD_STATISTICS());
            }
        }
    }
    

    /**
     * Removes {@link ThreadPoolStatistic} from every
     * {@link ExecutorService}, when monitoring has been turned off.
     */
    protected void disableThreadPoolStats(){
        if (threadPool instanceof StatsThreadPool) {

            threadPoolStat.stop();

            keepAliveStats.disable();
            StatsThreadPool statsThreadPool = (StatsThreadPool) threadPool;
            statsThreadPool.setStatistic(null);
            threadPoolStat.setThreadPool(null);
        }
    }

    
    /**
     * Load using reflection the{@link StreamAlgorithm} class.
     */
    protected void initAlgorithm(){
        try{    
            if (classLoader == null){
                algorithmClass = Class.forName(algorithmClassName);
            } else {
                algorithmClass = classLoader.loadClass(algorithmClassName);
            }
            logger.log(Level.FINE,
                       "Using Algorithm: " + algorithmClassName);   
        } catch (ClassNotFoundException ex){
            logger.log(Level.FINE,
                       "Unable to load Algorithm: " + algorithmClassName);        
        }  finally {
            if ( algorithmClass == null ){
                algorithmClass = NoParsingAlgorithm.class;
            }
        }

        defaultAlgorithmInstalled = 
                algorithmClassName.equals(DEFAULT_ALGORITHM) ? true:false;
    }
    

    
    /**
     * Init the {@link StatsThreadPool}s used by the {@link WorkerThread}s.
     */
    public void initThreadPool(){
        if (threadPool == null) {
            threadPool = newThreadPool(
                    Constants.DEFAULT_QUEUE_SIZE,
                    "http", port, Thread.MAX_PRIORITY);
        } else {
            if (threadPool instanceof StatsThreadPool) {
                StatsThreadPool statsThreadPool = (StatsThreadPool) threadPool;
                statsThreadPool.setPort(port);
                statsThreadPool.setPriority(Thread.MAX_PRIORITY);
            }

            if (threadPool instanceof ExtendedThreadPool) {
                ExtendedThreadPool extThreadPool = (ExtendedThreadPool) threadPool;
                extThreadPool.setCorePoolSize(coreThreads);
                extThreadPool.setMaximumPoolSize(maxThreads);
            }
        }
        
        if (isMonitoringEnabled){
            enableThreadPoolStats(); 
        }
    }
    
 
    /**
     * Create a pool of {@link ProcessorTask}
     */
    protected void initProcessorTask(int size){
        for (int i=0; i < size; i++){           
            processorTasks.offer(newProcessorTask(false));
        }
    }  


    /**
     * Initialize {@link ProcessorTask}
     */
    protected void rampUpProcessorTask(){
        for (ProcessorTask processorTask : processorTasks) {
            processorTask.initialize();
        }
    }  
    

    /**
     * Create {@link ProcessorTask} objects and configure it to be ready
     * to proceed request.
     */
    protected ProcessorTask newProcessorTask(boolean initialize) {
        final ProcessorTaskFactory factoryLocal = processorTaskFactory;
        final ProcessorTask task;
        if (factoryLocal != null) {
            task = factoryLocal.createProcessorTask(this, initialize);
        } else {
            task = new ProcessorTask(initialize, bufferResponse);
        }

        return configureProcessorTask(task);       
    }
    
    
    protected ProcessorTask configureProcessorTask(ProcessorTask task) {
        task.setAdapter(adapter);
        task.setMaxHttpHeaderSize(maxHttpHeaderSize);
        task.setBufferSize(requestBufferSize);
        task.setSelectorThread(this);               
        task.setDefaultResponseType(defaultResponseType);
        task.setForcedRequestType(forcedRequestType);
        task.setMaxPostSize(maxPostSize);
        task.setTimeout(uploadTimeout);
        task.setDisableUploadTimeout(disableUploadTimeout);
        task.setAsyncHttpWriteEnabled(isAsyncHttpWriteEnabled);
        task.setTransactionTimeout(transactionTimeout);
        task.setUseChunking(useChunking);
        task.setSendBufferSize(sendBufferSize);
        task.setMaxRequestHeaders(maxRequestHeaders);
        task.setMaxResponseHeaders(maxResponseHeaders);

        // Asynch extentions
        if ( asyncExecution ) {
            task.setEnableAsyncExecution(asyncExecution);
            task.setAsyncHandler(asyncHandler);          
        }
                
        task.setThreadPool(threadPool);
        configureCompression(task);
        
        return task;
    }
 
    
    /**
     * Reconfigure Grizzly Asynchronous Request Processing(ARP) internal 
     * objects.
     */
    protected void reconfigureAsyncExecution(){
        for(ProcessorTask task :processorTasks){
            task.setEnableAsyncExecution(asyncExecution);
            task.setAsyncHandler(asyncHandler);
        }
    }
   
    /*
     * Reconfigure {@link Adapter} on already configured {@link ProcessorTask} 
     */
    protected void reconfigureAdapter(){
        for(ProcessorTask task :processorTasks){
            task.setAdapter(adapter);
        }
    }
 
    // --------------------------------------------------------- Thread run --/
    
    /**
     * Start using the Controller's internal Thread Pool.
     */
    public void start() {
        if (port == 0){
            selectorThreads.put(getSelectorThreadLookupKey(inet, port), this);
        }
        new WorkerThreadImpl("SelectorThread-" + port,this).start();
    }

    /**
     * Start the endpoint (this)
     */
    public void run(){
        try{
            running.set(true);

            if (preallocateProcessorTasks) {
                rampUpProcessorTask();
            }
            registerComponents();

            displayConfiguration();
            startListener();  
        } catch (Exception ex){
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE,
                           LogMessages.SEVERE_GRIZZLY_HTTP_SELECTOR_THREAD_HTTP_PROCESSING_ERROR(),
                           ex);
            }
        }
    }

    // ---------------------------------------------------Endpoint Lifecycle --/
   
    
    /**
     * initialized the endpoint by creating the <code>ServerScoketChannel</code>
     * and by initializing the server socket.
     */
    public void initEndpoint() throws IOException, InstantiationException {
        SelectorThreadConfig.configure(this);

        configureProperties();
        initAlgorithm();
        initThreadPool();
        initController();
        initFileCacheFactory();
        initMonitoringLevel();
        
        int maxPoolSize = StatsThreadPool.DEFAULT_MAX_THREAD_COUNT;
        if (threadPool instanceof ExtendedThreadPool) {
            maxPoolSize = ((ExtendedThreadPool) threadPool).getMaximumPoolSize();
        }

        if (preallocateProcessorTasks) {
            initProcessorTask(maxPoolSize);
        }
        
        if (SelectorFactory.getMaxSelectors() < maxPoolSize){
            SelectorFactory.setMaxSelectors(maxPoolSize);
        }
        if (port != 0){
            selectorThreads.put(getSelectorThreadLookupKey(inet, port), this);
        }

        initialized = true;     
        if (adapter instanceof GrizzlyAdapter){
            ((GrizzlyAdapter)adapter).start();
        }
    }
    
    
    public void stopEndpoint() {
        if (!running.getAndSet(false)) return;
        
        try{
            controller.stop();
        } catch (IOException ex){
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING,
                           LogMessages.WARNING_GRIZZLY_HTTP_SELECTOR_THREAD_STOP(),
                           ex);
            }
        }
        synchronized(lock){
            // Wait for the main thread to stop.
            clearTasks();
        }

        if (adapter instanceof GrizzlyAdapter){
            ((GrizzlyAdapter)adapter).destroy();
        }
    }
    
    
    /**
     * Use reflection to configure Grizzly setter.
     */
    private void configureProperties(){
        for (String name : properties.keySet()) {
            String value = properties.get(name).toString();
            IntrospectionUtils.setProperty(this, name, value);
        }       
    }
    
    
    /**
     * Start the Acceptor Thread and wait for incoming connection, in a non
     * blocking mode.
     */
    public void startEndpoint() throws IOException, InstantiationException {
        run(); // For compatibility with grizzly 1.0        
    }
    
    
    /**
     * Start the SelectorThread using its own thread and don't block the Thread.
     *  This method should be used when Grizzly is embedded.
     */
    public void listen() throws IOException, InstantiationException{
        initEndpoint();
        final FutureImpl<Boolean> future = new FutureImpl<Boolean>();
        controller.addStateListener(new ControllerStateListenerAdapter() {
            @Override
            public void onReady() {
                future.setResult(Boolean.TRUE);
            }

            @Override
            public void onException(Throwable e) {
                if (!future.isDone()) {
                     logger().log(Level.SEVERE, "Exception during " +
                            "starting the controller", e);
                     future.setException(e);
                } else {
                     logger().log(Level.SEVERE, "Exception during " +
                            "controller processing", e);
                }
            }
        });

        start();

        try {
            future.get();
        } catch (InterruptedException ignored) {
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }

            throw new IOException(cause.getClass().getName() + ": " + cause.getMessage());
        }
        
        if (!controller.isStarted()) {
            throw new IllegalStateException("Controller is not started!");
        }                       
    }
    
    
    /**
     * Starts the {@link ExecutorService} used by this {@link Selector}
     */
    protected void startThreadPool(){
    }

    
    /**
     * Stop the {@link ExecutorService} used by this {@link Selector}
     */
    protected void stopThreadPool(){
        if (threadPool != null){
            threadPool.shutdown();
        }
    }
    
    
    /**
     * Start a non blocking {@link Selector} object.
     */
    protected void startListener(){
        synchronized(lock){          
            try{
                controller.start();
                stopThreadPool();
                unregisterComponents();                
            } catch (Throwable t) {
                 if (logger.isLoggable(Level.SEVERE)) {
                     logger.log(Level.SEVERE,
                                LogMessages.SEVERE_GRIZZLY_HTTP_SELECTOR_THREAD_CONTROLLER_START_ERROR(),
                                t);
                 }
            }
        }
    }
   
    
    /**
     * Cancel the current {@link SelectionKey}
     */
    public void cancelKey(SelectionKey key){
        selectorHandler.getSelectionKeyHandler().cancel(key);
    }
        
    
    /**
     * Return a {@link ProcessorTask} from the pool. If the pool is empty,
     * create a new instance.
     */
    public ProcessorTask getProcessorTask(){
        ProcessorTask processorTask;
        processorTask = processorTasks.poll();
        
        if (processorTask == null){
            processorTask = newProcessorTask(false);
        } 
        
        if ( isMonitoringEnabled() ){
           activeProcessorTasks.offer(processorTask); 
        }        
        return processorTask;
    }
          
    /**
     * Returns the {@link Task} object to the pool.
     */
    public void returnTask(Task task){
        // Returns the object to the pool.
        if (task != null) {
            if (task.getType() == Task.PROCESSOR_TASK){
                                
                if ( isMonitoringEnabled() ){
                   activeProcessorTasks.remove(task);
                }  
                
                processorTasks.offer((ProcessorTask)task);
            } 
        }
    }

    
    /**
     * Clear all cached <code>Tasks</code> 
     */
    protected void clearTasks(){
        processorTasks.clear();
    }

    
    // ------------------------------------------------------Public methods--/
 
    /**
     * Similar to <code>getPort()</code>, but getting port number directly from 
     * connection ({@link ServerSocket}, {@link DatagramSocket}).
     * So if default port number 0 was set during initialization, then <code>getPort()</code>
     * will return 0, but getPortLowLevel() will return port number assigned by OS.
     * 
     * @return port number, or -1 if {@link SelectorThread} was not started
     * @deprecated - uses {@link #getPort} instead
     */
    public int getPortLowLevel() {
        if (selectorHandler != null){
            return selectorHandler.getPort();
        } else {
            return port;
        }
    }
    
    public int getPort() {
        return getPortLowLevel();
    }

    public void setPort(int port ) {
        this.port=port;
    }
    
    public InetAddress getAddress() {
        return inet;
    }

    public void setAddress(InetAddress inet) {
        this.inet=inet;
    }


    public boolean isRunning() {
        return running.get();
    }


    /**
     * Sets the timeout in ms of the server sockets created by this
     * server. This method allows the developer to make servers
     * more or less responsive to having their server sockets
     * shut down.
     *
     * <p>By default this value is 1000ms.
     */
    public void setServerTimeout(int timeout) {
        this.serverTimeout = timeout;
    }

    public boolean getTcpNoDelay() {
        return tcpNoDelay;
    }
    
    public void setTcpNoDelay( boolean b ) {
        tcpNoDelay=b;
    }

    public int getLinger() {
        return linger;
    }
    
    public void setLinger( int i ) {
        linger=i;
    }

    public boolean getSocketKeepAlive() {
        return socketKeepAlive;
    }
    
    public void setSocketKeepAlive( boolean b ) {
        socketKeepAlive=b;
    }

    public int getServerTimeout() {
        return serverTimeout;
    }  

    public InetAddress getInet() {
        return inet;
    }

    public void setInet(InetAddress inet) {
        this.inet = inet;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    } 
    // ------------------------------------------------------ Connector Methods


    public int getMaxKeepAliveRequests() {
        return keepAliveStats.getMaxKeepAliveRequests();
    }
    
    
    /** 
     * Set the maximum number of Keep-Alive requests that we will honor.
     */
    public void setMaxKeepAliveRequests(int maxKeepAliveRequests) {
        keepAliveStats.setMaxKeepAliveRequests(maxKeepAliveRequests);
    }
    

    /** 
     * Sets the number of seconds before a keep-alive connection that has
     * been idle times out and is closed.
     *
     * @param timeout Keep-alive timeout in number of seconds
     */    
    public void setKeepAliveTimeoutInSeconds(int timeout) {
        keepAliveStats.setKeepAliveTimeoutInSeconds(timeout);
        if (keyHandler != null) {
            keyHandler.setTimeout(timeout == -1
                    ? SelectionKeyAttachment.UNLIMITED_TIMEOUT: timeout * 1000L);
        }
    }


    /** 
     * Gets the number of seconds before a keep-alive connection that has
     * been idle times out and is closed.
     *
     * @return Keep-alive timeout in number of seconds
     */    
    public int getKeepAliveTimeoutInSeconds() {
        return keepAliveStats.getKeepAliveTimeoutInSeconds();
    }

    
    /**
     * Set the associated adapter.
     * 
     * @param adapter the new adapter
     */
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
        reconfigureAdapter();
    }


    /**
     * Get the associated adapter.
     * 
     * @return the associated adapter
     */
    public Adapter getAdapter() {
        return adapter;
    }
    
    
    protected void setSocketOptions(Socket socket){
        try{
            if(linger >= 0 ) {
                socket.setSoLinger( true, linger);
            }
        } catch (SocketException ex){
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING,
                           LogMessages.WARNING_GRIZZLY_HTTP_SELECTOR_THREAD_SOCKET_OPTION_ERROR("setSoLinger"),
                           ex);
            }
        }
        
        try{
            if( tcpNoDelay )
                socket.setTcpNoDelay(tcpNoDelay);
        } catch (SocketException ex){
             if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING,
                           LogMessages.WARNING_GRIZZLY_HTTP_SELECTOR_THREAD_SOCKET_OPTION_ERROR("setTcpNoDelay"),
                           ex);
            }
        }
        
        try{
            socket.setReuseAddress(reuseAddress);
        } catch (SocketException ex){
             if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING,
                           LogMessages.WARNING_GRIZZLY_HTTP_SELECTOR_THREAD_SOCKET_OPTION_ERROR("setReuseAddress"),
                           ex);
            }
        }    
        
        try{
            if(oOBInline){
                socket.setOOBInline(oOBInline);
            }
        } catch (SocketException ex){
             if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING,
                           LogMessages.WARNING_GRIZZLY_HTTP_SELECTOR_THREAD_SOCKET_OPTION_ERROR("setOOBInline"),
                           ex);
            }
        }   
    }
    
    // ------------------------------- JMX and Monnitoring support --------//
    
    public ObjectName getObjectName() {
        return oname;
    }

    
    public String getDomain() {
        return domain;
    }

    
    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        oname=name;
        mserver=server;
        domain=name.getDomain();
        return name;
    }

    
    public void postRegister(Boolean registrationDone) {
        // Do nothing
    }

    public void preDeregister() throws Exception {
        // Do nothing
    }

    public void postDeregister() {
        // Do nothing
    }  


    /**
     * Register JMX components supported by this {@link SelectorThread}. This 
     * include {@link FileCache}, {@link RequestInfo}, {@link KeepAliveStats}
     * and {@link StatsThreadPool}. The {@link Management#registerComponent}
     * will be invoked during the registration process. 
     */
    public void registerComponents(){
        if( this.domain != null  && jmxManagement != null) {
            try {
                globalRequestProcessorName = new ObjectName(
                    domain + ":type=GlobalRequestProcessor,name=GrizzlyHttpEngine-" + port);
                jmxManagement.registerComponent(globalRequestProcessor,
                                                globalRequestProcessorName,
                                                null);
 
                keepAliveMbeanName = new ObjectName(
                    domain + ":type=KeepAlive,name=GrizzlyHttpEngine-" + port);
                jmxManagement.registerComponent(keepAliveStats,
                                                keepAliveMbeanName,
                                                null);

                connectionQueueMbeanName = new ObjectName(
                    domain + ":type=ConnectionQueue,name=GrizzlyHttpEngine-" + port);
                jmxManagement.registerComponent(threadPoolStat,
                                                connectionQueueMbeanName,
                                                null);
                
                fileCacheMbeanName = new ObjectName(
                    domain + ":type=FileCache,name=GrizzlyHttpEngine-" + port);
                jmxManagement.registerComponent(fileCacheFactory,
                                                fileCacheMbeanName,
                                                null);                
            } catch (Exception ex) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                               LogMessages.WARNING_GRIZZLY_HTTP_SELECTOR_THREAD_MBEAN_REGISTRATION_ERROR(port),
                               ex);
                }
            }
        }
    }
    
    
    /**
     * Unregister JMX components supported by this {@link SelectorThread}. This 
     * include {@link FileCache}, {@link RequestInfo}, {@link KeepAliveStats}
     * , {@link StatsThreadPool} and {@link ProcessorTask}.
     * The {@link Management#unregisterComponent} will be invoked during the
     * registration process.
     */
    protected void unregisterComponents(){
        if (this.domain != null && jmxManagement != null) {
            try {
                if (globalRequestProcessorName != null) {
                    jmxManagement.unregisterComponent(globalRequestProcessorName);
                }
                if (keepAliveMbeanName != null) {
                    jmxManagement.unregisterComponent(keepAliveMbeanName);
                }
                if (connectionQueueMbeanName != null) {
                    jmxManagement.unregisterComponent(connectionQueueMbeanName);
                }
                if (fileCacheMbeanName != null) {
                    jmxManagement.unregisterComponent(fileCacheMbeanName);
                }                    
            } catch (Exception ex) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                               LogMessages.WARNING_GRIZZLY_HTTP_SELECTOR_THREAD_MBEAN_DEREGISTRATION_ERROR(port),
                               ex);
                }
            }
            
            Iterator<ProcessorTask> iterator = activeProcessorTasks.iterator();
            ProcessorTask pt;
            while (iterator.hasNext()) {
                pt = iterator.next();
                if (pt instanceof ProcessorTask) {
                    pt.unregisterMonitoring();
                }
            }
            
            iterator = processorTasks.iterator();
            pt = null;
            while (iterator.hasNext()) {
                pt = iterator.next();
                if (pt instanceof ProcessorTask) {
                    pt.unregisterMonitoring();
                }
            }
        }
    }

    
    /**
     * Return the {@link Management} interface, or null if JMX management is 
     * no enabled.
     * @return the {@link Management}
     */
    public Management getManagement() {
        return jmxManagement;
    }

    
    /**
     * Set the {@link Management} interface. Setting this interface automatically
     * expose Grizzly HTTP Engine mbeans.
     * @param jmxManagement
     */
    public void setManagement(Management jmxManagement) {
        this.jmxManagement = jmxManagement;
    }
    
    
    /**
     * Enable gathering of monitoring data.
     */
    public void enableMonitoring(){
        if (threadPoolStat == null){
            initThreadPool();
            initMonitoringLevel();
            initFileCacheFactory();
        }
        isMonitoringEnabled = true;
        enableThreadPoolStats();
        fileCacheFactory.setIsMonitoringEnabled(isMonitoringEnabled);
    }
    
    
    /**
     * Disable gathering of monitoring data. 
     */
    public void disableMonitoring(){
        disableThreadPoolStats();
        // If monitoring was enabled before isMonitoringEnabled == true
        isMonitoringEnabled = false;
        fileCacheFactory.setIsMonitoringEnabled(isMonitoringEnabled);        
    }

    
    /**
     * Returns <tt>true</tt> if monitoring has been enabled, 
     * <tt>false</tt> otherwise.
     */
    public boolean isMonitoringEnabled() {
        return isMonitoringEnabled;
    }

    
    public RequestGroupInfo getRequestGroupInfo() {
        return globalRequestProcessor;
    }


    public KeepAliveStats getKeepAliveStats() {
        return keepAliveStats;
    }


    /**
     * Initialize the {@link ThreadPoolStatistic} instance.
     */
    protected void initMonitoringLevel() {
        if (threadPoolStat != null) return;
        threadPoolStat = new ThreadPoolStatistic(port);
        int maxQueuedTasks = -1;
        if (threadPool instanceof ExtendedThreadPool) {
            maxQueuedTasks =
                    ((ExtendedThreadPool) threadPool).getMaxQueuedTasksCount();
        }

        threadPoolStat.setQueueSizeInBytes(maxQueuedTasks);
    }
 
    // ------------------------------------------- Config ------------------//
    
    public int getMaxHttpHeaderSize() {
        return maxHttpHeaderSize;
    }
    
    
    public void setMaxHttpHeaderSize(int maxHttpHeaderSize) {
        this.maxHttpHeaderSize = maxHttpHeaderSize;
    }

    /**
     * Is async HTTP write enabled
     * @return <tt>true</tt>, if async HTTP write enabled,
     * or <tt>false</tt> otherwise.
     */
    public boolean isAsyncHttpWriteEnabled() {
        return isAsyncHttpWriteEnabled;
    }

    /**
     * Set if async HTTP write enabled
     * @param isAsyncHttpWriteEnabled <tt>true</tt>, if async HTTP
     * write enabled, or <tt>false</tt> otherwise.
     */
    public void setAsyncHttpWriteEnabled(boolean isAsyncHttpWriteEnabled) {
        this.isAsyncHttpWriteEnabled = isAsyncHttpWriteEnabled;
    }
            
    /**
     * Set the request input buffer size
     */
    public void setBufferSize(int requestBufferSize){
        this.requestBufferSize = requestBufferSize;
    }
    

    /**
     * Return the request input buffer size
     */
    public int getBufferSize(){
        return requestBufferSize;
    }
    
    // --------------------------------------------------- Low level NIO ----//
    

    /**
     * Get main {@link Selector}
     * @deprecated To get the right {@link Selector}, please request context associated <tt>SelectorHandler</tt>: {@link SelectorHandler#getSelector()}
     */
    public Selector getSelector(){
        if (selector != null) {
            return selector;
        } else {
            return selectorHandler.getSelector();
        }
    }

    
    /**
     * Get main {@link SelectorHandler}
     * @deprecated use {@link SelectorHandler}, which is usually provided by
     *  the current {@link com.sun.grizzly.Context}.
     */
    public TCPSelectorHandler getSelectorHandler() {
        return selectorHandler;
    }
    
    public Controller getController() {
        return controller;
    }

    
    public void setController(Controller controller) {
        this.controller = controller;
    }
    //------------------------------------------------- FileCache config -----/

    
    /**
     * Remove a context path from the {@link FileCache}.
     */
    public void removeCacheEntry(String contextPath){  
        Map<String,FileCacheEntry> 
                cachedEntries = fileCacheFactory.getCache();
        
        if ( cachedEntries == null){
            return;
        }
        
        Iterator<String> iterator = cachedEntries.keySet().iterator();
        String cachedPath;
        while (iterator.hasNext()){
            cachedPath = iterator.next();
            if ( cachedPath.startsWith(contextPath) ){
                cachedEntries.remove(cachedPath).run();
            }            
        }
    }
   
    
    /**
     * The timeout in seconds before remove a {@link FileCacheEntry}
     * from the {@link FileCache}
     */
    public void setSecondsMaxAge(int sMaxAges){
        secondsMaxAge = sMaxAges;
    }
    
    
    /**
     * Set the maximum entries this cache can contains.
     */
    public void setMaxCacheEntries(int mEntries){
        maxCacheEntries = mEntries;
    }

    
    /**
     * Return the maximum entries this cache can contains.
     */    
    public int getMaxCacheEntries(){
        return maxCacheEntries;
    }
    
    
    /**
     * Set the maximum size a {@link FileCacheEntry} can have.
     */
    public void setMinEntrySize(long mSize){
        minEntrySize = mSize;
    }
    
    
    /**
     * Get the maximum size a {@link FileCacheEntry} can have.
     */
    public long getMinEntrySize(){
        return minEntrySize;
    }
     
    
    /**
     * Set the maximum size a {@link FileCacheEntry} can have.
     */
    public void setMaxEntrySize(long mEntrySize){
        maxEntrySize = mEntrySize;
    }
    
    
    /**
     * Get the maximum size a {@link FileCacheEntry} can have.
     */
    public long getMaxEntrySize(){
        return maxEntrySize;
    }
    
    
    /**
     * Set the maximum cache size
     */ 
    public void setMaxLargeCacheSize(long mCacheSize){
        maxLargeFileCacheSize = mCacheSize;
    }

    
    /**
     * Get the maximum cache size
     */ 
    public long getMaxLargeCacheSize(){
        return maxLargeFileCacheSize;
    }
    
    
    /**
     * Set the maximum cache size
     */ 
    public void setMaxSmallCacheSize(long mCacheSize){
        maxSmallFileCacheSize = mCacheSize;
    }
    
    
    /**
     * Get the maximum cache size
     */ 
    public long getMaxSmallCacheSize(){
        return maxSmallFileCacheSize;
    }    

    
    /**
     * Is the fileCache enabled.
     */
    public boolean isFileCacheEnabled(){
        return isFileCacheEnabled;
    }

    
    /**
     * Is the file caching mechanism enabled.
     */
    public void setFileCacheIsEnabled(boolean isFileCacheEnabled){
        this.isFileCacheEnabled = isFileCacheEnabled;
    }
   
    
    /**
     * Is the large file cache support enabled.
     */
    public void setLargeFileCacheEnabled(boolean isLargeEnabled){
        this.isLargeFileCacheEnabled = isLargeEnabled;
    }
   
    
    /**
     * Is the large file cache support enabled.
     */
    public boolean getLargeFileCacheEnabled(){
        return isLargeFileCacheEnabled;
    }    

    // ---------------------------- Async-------------------------------//
    
    /**
     * Enable the {@link AsyncHandler} used when asynchronous
     */
    public void setEnableAsyncExecution(boolean asyncExecution) {
        this.asyncExecution = asyncExecution;     
        if (running.get()) {
            reconfigureAsyncExecution();
        }
    }
    
       
    /**
     * Return true when asynchronous execution is 
     * enabled.
     */    
    public boolean getEnableAsyncExecution(){
        return asyncExecution;
    }
    
    
    /**
     * Set the {@link AsyncHandler} used when asynchronous execution is 
     * enabled.
     */
    public void setAsyncHandler(AsyncHandler asyncHandler){
        this.asyncHandler = asyncHandler;     
    }
    
       
    /**
     * Return the {@link AsyncHandler} used when asynchronous execution is 
     * enabled.
     */    
    public AsyncHandler getAsyncHandler(){
        return asyncHandler;
    }
    
    // ------------------------------------------------------------------- //
    
    /**
     * Set the logger used by this instance.
     */
    public static void setLogger(Logger l){
        if ( l != null )
            logger = l;
    }

    
    /**
     * Return the logger used by the Grizzly classes.
     */
    public static Logger logger(){
        return logger;
    }
    
    
    /**
     * Set the document root folder
     */
    public void setWebAppRootPath(String rf){
        rootFolder = rf;
    }
    
    
    /**
     * Return the folder's root where application are deployed.
     */
    public String getWebAppRootPath(){
        return rootFolder;
    }
    

    // ------------------------------------------------------ Debug ---------//
    
        
    /**
     * Display the Grizzly configuration parameters.
     */
    private void displayConfiguration(){
       if (displayConfiguration){
           logger.log(Level.INFO,
                      LogMessages.INFO_GRIZZLY_HTTP_SELECTOR_THREAD_CONFIG(
                           System.getProperty("os.name"),
                           System.getProperty("os.version"),
                           System.getProperty("java.version"),
                           System.getProperty("java.vendor"),
                           Integer.toString(getPort()),
                           threadPool,
                           Integer.toString(readThreadsCount),
                           Integer.toString(requestBufferSize),
                           Integer.toString(requestBufferSize),
                           Integer.toString(sendBufferSize),
                           keepAliveStats.getMaxKeepAliveRequests(),
                           keepAliveStats.getKeepAliveTimeoutInSeconds(),
                           Boolean.toString(isFileCacheEnabled),
                           new File(rootFolder).getAbsolutePath(),
                           ((adapter == null) ? null : adapter.getClass().getName()),
                           Boolean.toString(asyncExecution)
                   ));
       }
    }

    
    /**
     * Return <tt>true</tt> if the reponse is buffered.
     */
    public boolean getBufferResponse() {
        return bufferResponse;
    }

    
    /**
     * <tt>true</tt>if the reponse willk be buffered.
     */
    public void setBufferResponse(boolean bufferResponse) {
        this.bufferResponse = bufferResponse;
    }
    
 
    /**
     * Enable Application Resource Allocation Grizzly Extension.
     */
    public void enableRcmSupport(boolean rcmSupport){
        this.rcmSupport = rcmSupport;
    }
    
    /**
     * Returns whether 
     * Application Resource Allocation Grizzly Extension is supported
     * @return is RCM supported
     */
    public boolean isRcmSupported() {
        return rcmSupport;
    }
    
        
   // ------------------------------------------------------ Compression ---//


    protected void configureCompression(ProcessorTask processorTask) {
        processorTask.setCompressableMimeTypes(compressableMimeTypes.split());
        processorTask.setCompressionMinSize(compressionMinSize);
        processorTask.setCompression(compression);
        processorTask.setRestrictedUserAgents(restrictedUserAgents.split());
        processorTask.setNoCompressionUserAgents(noCompressionUserAgents.split());
    }

    
    /**
     * Returns the compression level. The possible values are:
     * 1) <b>off</b>: never compress HTTP response content
     * 2) <b>force</b>: force HTTP response content compression regardless
     *      {@link #getCompressionMinSize()}, {@link #getCompressableMimeTypes()},
     *      {@link #getNoCompressionUserAgents()} limitations. This mode might be
     *      particularly useful for testing purposes.
     * 3) <b>on</b>: enable compression for HTTP response content, if the HTTP
     *      request/response headers conform to the
     *      {@link #getCompressionMinSize()}, {@link #getCompressableMimeTypes()},
     *      {@link #getNoCompressionUserAgents()} requirements.
     * 
     * Pls note: for "on" and "force" compression levels, the
     * compression will be applied to HTTP response content only
     * in case if corresponding HTTP request contains appropriate Accept-Encoding
     * header.
     */
    public String getCompression() {
        return compression;
    }


    /**
     * Sets the compression level. The acceptable <code>compression</code>
     * parameter values are:
     * 1) <b>off</b>: never compress HTTP response content
     * 2) <b>force</b>: force HTTP response content compression regardless
     *      {@link #getCompressionMinSize()}, {@link #getCompressableMimeTypes()},
     *      {@link #getNoCompressionUserAgents()} limitations. This mode might be
     *      particularly useful for testing purposes.
     * 3) <b>on</b>: enable compression for HTTP response content, if the HTTP
     *      request/response headers conform to the
     *      {@link #getCompressionMinSize()}, {@link #getCompressableMimeTypes()},
     *      {@link #getNoCompressionUserAgents()} requirements.
     * 
     * Pls note: for "on" and "force" compression levels, the
     * compression will be applied to HTTP response content only
     * in case if corresponding HTTP request contains appropriate Accept-Encoding
     * header.
     */
    public void setCompression(String compression) {
        this.compression = compression;
    }

    /**
     * Returns the min size (in bytes) of HTTP response payload, the compression might be applied on.
     */
    public int getCompressionMinSize() {
        return compressionMinSize;
    }

    
    /**
     * Sets the min size (in bytes) of HTTP response payload, the compression might be applied on.
     */
    public void setCompressionMinSize(int compressionMinSize) {
        this.compressionMinSize = compressionMinSize;
    }
    
    
    /**
     * Returns the comma-separated list of client user-agents which we never
     * apply compression to.
     */
    public String getNoCompressionUserAgents() {
        return noCompressionUserAgents.getComaSeparatedList();
    }

    
    /**
     * Sets the comma-separated list of client user-agents which we never
     * apply compression to.
     */
    public void setNoCompressionUserAgents(String noCompressionUserAgents) {
        this.noCompressionUserAgents.setComaSeparatedList(noCompressionUserAgents);
    }
    
    /**
     * Returns the comma-separated list of restricted user-agents, which will be
     * responded using HTTP/1.0 protocol only.
     */    
    public String getRestrictedUserAgents() {
        return restrictedUserAgents.getComaSeparatedList();
    }

    /**
     * Sets the comma-separated list of restricted user-agents, which will be
     * responded using HTTP/1.0 protocol only.
     */    
    public void setRestrictedUserAgents(String restrictedUserAgents) {
        this.restrictedUserAgents.setComaSeparatedList(restrictedUserAgents);
    }

    /**
     * Returns the comma-separated compressable mime-type list.
     */
    public String getCompressableMimeTypes() {
        return compressableMimeTypes.getComaSeparatedList();
    }
    
    /**
     * Sets the comma-separated compressable mime-type list.
     */
    public void setCompressableMimeTypes(String compressableMimeTypes) {
        this.compressableMimeTypes.setComaSeparatedList(compressableMimeTypes);
    }

    private static int getSelectorThreadLookupKey(InetAddress address, int port) {
        int addressHash = ((address != null) ? address.hashCode() : 0);
        return (addressHash + port);
    }
    
    // ------------------------------------------------------------------- //
    
    
    public int getSelectorReadThreadsCount() {
        return readThreadsCount;
    }

    
    public void setSelectorReadThreadsCount(int readThreadsCount) {
        this.readThreadsCount = readThreadsCount;
    }

    
    public ExecutorService getThreadPool() {
        return threadPool;
    }

    
    /**
     * Set the {@link ExecutorService} this class should use. A {@link ExecutorService}
     * must ensure its {@link ThreadFactory} return Thread which are instance
     * of {@link HttpWorkerThread}.
     * @param threadPool - the thread pool used by this instance.
     */
    public void setThreadPool(ExecutorService threadPool) {
        if (threadPool == null){
            throw new IllegalStateException("Cannot be null");
        }
        
        ThreadFactory threadFactory = getThreadFactory(threadPool);

        if (threadFactory != null){
            if ( !(threadFactory.newThread(new Runnable() {
                public void run() {
                }
            }) instanceof HttpWorkerThread)){
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.severe(LogMessages.SEVERE_GRIZZLY_HTTP_SELECTOR_THREAD_INVALID_THREAD_FACTORY_ERROR(HttpWorkerThread.class.getName()));
                }
                throw new RuntimeException("Invalid ThreadFactory. Threads must be instance of "
                        + HttpWorkerThread.class.getName());
            }    
        } else {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning(LogMessages.WARNING_GRIZZLY_HTTP_SELECTOR_THREAD_UNKNOWN_THREAD_FACTORY_ERROR(HttpWorkerThread.class.getName()));
            }
        }
        this.threadPool = threadPool;
    }

    private ThreadFactory getThreadFactory(ExecutorService threadPool) {
        if (threadPool instanceof ExtendedThreadPool) {
            return ((ExtendedThreadPool) threadPool).getThreadFactory();
        } else if (threadPool instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) threadPool).getThreadFactory();
        }

        return null;
    }

    public boolean isUseDirectByteBuffer() {
        return useDirectByteBuffer;
    }

    public void setUseDirectByteBuffer(boolean useDirectByteBuffer) {
        this.useDirectByteBuffer = useDirectByteBuffer;
    }

    public void setDisplayConfiguration(boolean displayConfiguration) {
        this.displayConfiguration = displayConfiguration;
    }

    public boolean isUseByteBufferView() {
        return useByteBufferView;
    }

    public void setUseByteBufferView(boolean useByteBufferView) {
        this.useByteBufferView = useByteBufferView;
    }

    public int getSelectorTimeout() {
        return selectorTimeout;
    }

    public void setSelectorTimeout(int aSelectorTimeout) {
        selectorTimeout = aSelectorTimeout;
    }

    public String getAlgorithmClassName() {
        return algorithmClassName;
    }

    public void setAlgorithmClassName(String algorithmClassName) {
        this.algorithmClassName = algorithmClassName;
    }

    public int getSsBackLog() {
        return ssBackLog;
    }

    public void setSsBackLog(int ssBackLog) {
        this.ssBackLog = ssBackLog;
    }

    public String getDefaultResponseType() {
        return defaultResponseType;
    }

    public void setDefaultResponseType(String defaultResponseType) {
        this.defaultResponseType = defaultResponseType;
    }

    public String getForcedRequestType() {
        return forcedRequestType;
    }

    public void setForcedRequestType(String forcedRequestType) {
        this.forcedRequestType = forcedRequestType;
    }

    public Queue<ProcessorTask> getActiveProcessorTasks() {
        return activeProcessorTasks;
    }

    
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    
    /**
     * Set the <code>ClassLoader</code> used to load configurable
     * classes (ExecutorService, StreamAlgorithm).
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    
    public static boolean isEnableNioLogging() {
        return enableNioLogging;
    }

    
    public static void setEnableNioLogging(boolean enl) {
        enableNioLogging = enl;
    }

    
    public int getMaxPostSize() {
        return maxPostSize;
    }

    
    public void setMaxPostSize(int maxPostSize) {
        this.maxPostSize = maxPostSize;
    }

    public int getMaxRequestHeaders() {
        return maxRequestHeaders;
    }

    public void setMaxRequestHeaders(int maxRequestHeaders) {
        this.maxRequestHeaders = maxRequestHeaders;
    }

    public int getMaxResponseHeaders() {
        return maxResponseHeaders;
    }

    public void setMaxResponseHeaders(int maxResponseHeaders) {
        this.maxResponseHeaders = maxResponseHeaders;
    }

    /**
     * Get the max number of bytes Grizzly will try to swallow in order
     * to read off from the current request payload and prepare input to
     * process next request.
     * 
     * @return the max number of bytes Grizzly will try to swallow in order
     * to read off from the current request payload and prepare input to
     * process next request.
     */
    public long getMaxSwallowingInputBytes() {
        return maxSwallowingInputBytes;
    }

    /**
     * Set the max number of bytes Grizzly will try to swallow in order
     * to read off from the current request payload and prepare input to
     * process next request.
     * 
     * @param maxSwallowingInputBytes  the max number of bytes Grizzly will try
     * to swallow in order to read off from the current request payload and
     * prepare input to process next request.
     */
    public void setMaxSwallowingInputBytes(long maxSwallowingInputBytes) {
        this.maxSwallowingInputBytes = maxSwallowingInputBytes;
    }
    
    public void setReuseAddress(boolean reuseAddress){
        this.reuseAddress = reuseAddress;
    }

    
    public boolean getReuseAddress(){
        return reuseAddress;
    }
    
    
    public SelectorThreadKeyHandler getSelectorThreadKeyHandler(){
        return keyHandler;
    }
    
       /**
     * Set the flag to control upload time-outs.
     */
    public void setDisableUploadTimeout(boolean isDisabled) {
        disableUploadTimeout = isDisabled;
    }

    
    /**
     * Get the flag that controls upload time-outs.
     */
    public boolean getDisableUploadTimeout() {
        return disableUploadTimeout;
    }
    
    
    /**
     * Set the upload timeout.
     */
    public void setUploadTimeout(int uploadTimeout) {
        this.uploadTimeout = uploadTimeout ;
    }

    
    /**
     * Get the upload timeout.
     */
    public int getUploadTimeout() {
        return uploadTimeout;
    } 
    
    /** 
     * Set the maximum time, in milliseconds, a {@link WorkerThread} executing
     * an instance of this class can execute.
     * 
     * @return  the maximum time, in milliseconds
     */
    public int getTransactionTimeout() {
        return transactionTimeout;
    }

    /**
     * Set the maximum time, in milliseconds, a {@link WorkerThread} processing
     * an instance of this class.
     * 
     * @param transactionTimeout  the maximum time, in milliseconds.
     */
    public void setTransactionTimeout(int transactionTimeout) {
        this.transactionTimeout = transactionTimeout;
    }
    /**
     * Is chunking encoding used. Default is true;
     * @return Is chunking encoding used.
     */
    public boolean isUseChunking() {
        return useChunking;
    }

    
    /**
     * Enable chunking the http response. Default is true.
     * @param useChunking
     */
    public void setUseChunking(boolean useChunking) {
        this.useChunking = useChunking;
    }

    /**
     * Returns the auxiliary configuration, which might be used, when Grizzly
     * HttpServer is running behind HTTP gateway like reverse proxy or load balancer.
     * 
     * @since 1.9.50
     */    
    public BackendConfiguration getBackendConfiguration() {
        return backendConfiguration;
    }

    /**
     * Sets the auxiliary configuration, which might be used, when Grizzly HttpServer
     * is running behind HTTP gateway like reverse proxy or load balancer.
     *
     * @since 1.9.50
     */
    public void setBackendConfiguration(BackendConfiguration backendConfiguration) {
        this.backendConfiguration = backendConfiguration;
    }
    
    /**
     * Return <code>true</code> if this <code>SelectorThread</code> will
     * pre-allocate {@link ProcessorTask} instances.
     * 
     * @return <code>true</code> if this <code>SelectorThread</code> will
     * pre-allocate {@link ProcessorTask} instances.
     * 
     * @since 1.9.43
     */
    public boolean isPreallocateProcessorTasks() {
        return preallocateProcessorTasks;
    }


    /**
     * Set the {@link ProcessorTask} pre-allocation flag.
     * @param preallocateProcessorTasks <code>true</code> if {@link ProcessorTask}s
     *  should be pre-allocated when starting the <code>SelectorThread</code>.
     *
     * @since 1.9.43
     */
    public void setPreallocateProcessorTasks(boolean preallocateProcessorTasks) {
        this.preallocateProcessorTasks = preallocateProcessorTasks;
    }

    // --------------------------------------------------------------------- //
    
    /**
     * Return a configured property.
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    
    /**
     * Set a configured property.
     */
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    
    /** 
     * remove a configured property.
     */
    public void removeProperty(String name) {
        properties.remove(name);
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public int getCoreThreads() {
        return coreThreads;
    }

    public void setCoreThreads(int coreThreads) {
        this.coreThreads = coreThreads;
    }

    public String getThreadPoolClassname() {
        return threadPoolClassname;
    }

    public void setThreadPoolClassname(String threadPoolClassname) {
        this.threadPoolClassname = threadPoolClassname;
    }

    /**
     * Return the protocol supported by this {@link GrizzlyListener}
     */
    public String protocol(){
        return "HTTP";
    }
    
    /**
     * Force keep-alive no matter what the client support.
     */
    public void setForceKeepAlive(boolean forceKeepAlive){
        this.forceKeepAlive = forceKeepAlive;
    }
    
    /**
     * Is keep-alive forced?
     */
    public boolean getForceKeepAlive(){
        return forceKeepAlive;
    }
    
    /**
     * Add a context-path that will be allowed to execute using the 
     * {@link AsyncHandler}.
     * 
     * @param s A context-path that will be allowed to execute using the
     */
    public void addAsyncEnabledContextPath(String s){
        if (asyncInterceptor == null){
            asyncInterceptor = new AsyncInterceptor();
        }
        asyncInterceptor.addContextPath(s);
    }

    protected KeepAliveStats createKeepAliveStats() {
        return new KeepAliveStats();
    }

    /**
     * @return the sendBufferSize
     */
    public int getSendBufferSize() {
        return sendBufferSize;
    }

    /**
     * @param sendBufferSize the sendBufferSize to set
     */
    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    /**
     * Returns the {@link ErrorHandler}.
     * @return the {@link ErrorHandler}
     */
    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    /**
     * Sets the {@link ErrorHandler}.
     * @param errorHandler {@link ErrorHandler}.
     */
    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }
    
    /**
     * Get {@link ProcessorTaskFactory}.
     *
     * @return {@link ProcessorTaskFactory}
     */
    public ProcessorTaskFactory getProcessorTaskFactory() {
        return processorTaskFactory;
    }

    /**
     * Sets {@link ProcessorTaskFactory}.
     *
     * @param processorTaskFactory
     */
    public void setProcessorTaskFactory(ProcessorTaskFactory processorTaskFactory) {
        this.processorTaskFactory = processorTaskFactory;
        clearTasks();
    }

    /**
     * Maximum number of {@link ProcessorTask}s to be cached.
     * @return maximum number of {@link ProcessorTask}s to be cached.
     */
    public int getMaxCachedProcessorTasks() {
        return maxCachedProcessorTasks;
    }

    /**
     * Maximum number of {@link ProcessorTask}s to be cached.
     * @param maximum number of {@link ProcessorTask}s to be cached.
     */
    public void setMaxCachedProcessorTasks(final int maxCachedProcessorTasks) {
        if (maxCachedProcessorTasks != this.maxCachedProcessorTasks ||
                processorTasks == null) {
            processorTasks = maxCachedProcessorTasks >= 0 ?
                    new LinkedBlockingQueue<ProcessorTask>(maxCachedProcessorTasks) :
                    DataStructures.getCLQinstance(ProcessorTask.class);
        }
        
        this.maxCachedProcessorTasks = maxCachedProcessorTasks;
    }
    
    /**
     * Class represents a wrapper over comma-separated list represented as {@link String} value.
     */
    protected static class CommaSeparatedList {
        private String comaSeparatedList;
        private String[] splitArray;

        public CommaSeparatedList() {
        }

        public CommaSeparatedList(String comaSeparatedList) {
            this.comaSeparatedList = comaSeparatedList;
        }
                
        public String getComaSeparatedList() {
            return comaSeparatedList;
        }

        public void setComaSeparatedList(String comaSeparatedList) {
            this.comaSeparatedList = comaSeparatedList;
            splitArray = null;
        }

        /**
         * Split the comma-separated list and return the list as {@link String}[].
         */
        public String[] split() {
            if (splitArray != null) {
                return splitArray;
            }
            
            if (comaSeparatedList == null) {
                splitArray = new String[0];
                return splitArray;
            }
            
            List<String> tmpSplitList = new ArrayList<String>(4);
            StringTokenizer st = new StringTokenizer(comaSeparatedList, ",");

            while(st.hasMoreTokens()) {
                tmpSplitList.add(st.nextToken().trim());
            }

            String[] tmpSplitArray = new String[tmpSplitList.size()];
            tmpSplitList.toArray(tmpSplitArray);
            splitArray = tmpSplitArray;
            return splitArray;
        }
    }
}
