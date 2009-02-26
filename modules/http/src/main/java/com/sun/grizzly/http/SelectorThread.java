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
package com.sun.grizzly.http;

import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.ControllerStateListenerAdapter;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.Pipeline;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.arp.AsyncProtocolFilter;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.http.algorithms.NoParsingAlgorithm;
import com.sun.grizzly.http.FileCache.FileCacheEntry;
import com.sun.grizzly.portunif.PUFilter;
import com.sun.grizzly.portunif.PUPreProcessor;
import com.sun.grizzly.portunif.ProtocolFinder;
import com.sun.grizzly.portunif.ProtocolHandler;
import com.sun.grizzly.rcm.ResourceAllocationFilter;
import com.sun.grizzly.util.SelectorFactory;
import com.sun.grizzly.util.WorkerThread;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;

import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.RequestGroupInfo;

import javax.management.ObjectName;
import javax.management.MBeanServer;
import javax.management.MBeanRegistration;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.CountDownLatch;



/**
 * This class implement an NIO socket HTTP Listener. This class 
 * supports three stagegy:
 *
 * Mode Blocking: This mode uses NIO blocking mode, and doesn't uses any of the 
 *                java.nio.* classies.
 *
 *
 * Mode Non-Blocking: This mode uses NIO non blocking mode and read the entire 
 *         request stream before processing the request. The stragegy used is 
 *         to find the content-lenght header and buffer bytes until the end of 
 *         the stream is read.
 *
 * @author Jean-Francois Arcand
 */
public class SelectorThread extends Thread implements MBeanRegistration{
            
    public final static String SERVER_NAME = 
            System.getProperty("product.name") != null 
                ? System.getProperty("product.name") : "grizzly";
    
        
    private Object[] lock = new Object[0];

    
    protected int serverTimeout = Constants.DEFAULT_SERVER_SOCKET_TIMEOUT;

    protected InetAddress inet;
    protected int port;
    
    protected boolean initialized = false;    
    protected volatile boolean running = false;    
    // ----------------------------------------------------- JMX Support ---/
    
    
    protected String domain;
    protected ObjectName oname;
    protected ObjectName globalRequestProcessorName;
    private ObjectName keepAliveMbeanName;
    private ObjectName pwcConnectionQueueMbeanName;
    private ObjectName pwcFileCacheMbeanName;
    protected MBeanServer mserver;
    protected ObjectName processorWorkerThreadName;


    // ------------------------------------------------------Socket setting --/

    protected boolean tcpNoDelay=false;
    
    
    protected int linger=100;
    
    
    protected int socketTimeout=-1;
    
    
    protected int maxKeepAliveRequests = Constants.DEFAULT_MAX_KEEP_ALIVE;
    
    // ------------------------------------------------------ Compression ---/


    /**
     * Compression value.
     */
    protected String compression = "off";
    protected String noCompressionUserAgents = null;
    protected String restrictedUserAgents = null;
    protected String compressableMimeTypes = "text/html,text/xml,text/plain";
    private volatile String[] parsedCompressableMimeTypes = null;
    private volatile int parsedComressableMimeTypesHash = -1;
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
     * Number of polled <code>Read*Task</code> instance.
     */
    protected int minReadQueueLength = 10;


    /**
     * Number of polled <code>ProcessorTask</code> instance.
     */
    protected int minProcessorQueueLength = 10;
    
    
    protected int maxPostSize = 2 * 1024 * 1024;


    /**
     * The <code>Selector</code> used by the connector.
     */
    protected Selector selector;

    /**
     * <code>SelectorHandler</code> current <code>SelectorThread</code> is
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
    protected LinkedListPipeline processorPipeline;
    
  
    /**
     * Placeholder for <code>Pipeline</code> statistic.
     */
    protected PipelineStatistic pipelineStat;
    
    
    /**
     * The default <code>Pipeline</code> used.
     */
    protected String pipelineClassName = 
        com.sun.grizzly.http.LinkedListPipeline.class.getName();
    
    /**
     * Maximum number of <code>WorkerThread</code>
     */
    protected int maxProcessorWorkerThreads = 20; // By default
    
    
    /**
     * Maximum number of <code>ReadWorkerThread</code>
     */
    protected int maxReadWorkerThreads = -1; // By default

    
    /**
     * Minimum numbers of <code>WorkerThread</code> created
     */
    protected int minWorkerThreads = 5;
    

    /**
     * Minimum numbers of <code>WorkerThread</code> 
     * before creating new thread.
     * <implementation-note>
     * Not used in 9.x
     * </implementation-note>
     */
    protected int minSpareThreads = 2;

    
    /**
     * The number used when increamenting the <code>Pipeline</code> 
     * thread pool.
     */
    protected int threadsIncrement = 1;
    
    
    /**
     * The timeout used by the thread when processing a request.
     */
    protected int threadsTimeout = Constants.DEFAULT_TIMEOUT;

    
    /**
     * Is the <code>ByteBuffer</code> used by the <code>ReadTask</code> use
     * direct <code>ByteBuffer</code> or not.
     */
    protected boolean useDirectByteBuffer = false;
    
  
    /**
     * Monitoring object used to store information.
     */
    protected RequestGroupInfo globalRequestProcessor= new RequestGroupInfo();
    
    
    /**
     * Keep-alive stats
     */
    private KeepAliveStats keepAliveStats = new KeepAliveStats();


    /**
     * If <code>true</code>, display the NIO configuration information.
     */
    protected boolean displayConfiguration = false;
    
    
    /**
     * Is monitoring already started.
     */
    protected boolean isMonitoringEnabled = false;
    

    /**
     * The current number of simulatenous connection.
     */
    protected int currentConnectionNumber;


    /**
     * Is this Selector currently in Wating mode?
     */
    protected volatile boolean isWaiting = false;
    

    /**
     * The input request buffer size.
     */
    protected int requestBufferSize = Constants.DEFAULT_REQUEST_BUFFER_SIZE;
    
    
    /**
     * Create view <code>ByteBuffer</code> from another <code>ByteBuffer</code>
     */
    protected boolean useByteBufferView = false;
    

    /*
     * Number of seconds before idle keep-alive connections expire
     */
    protected int keepAliveTimeoutInSeconds = Constants.DEFAULT_TIMEOUT;

    
    /**
     * Number of seconds before idle keep-alive connections expire
     */
    private int kaTimeout = Constants.DEFAULT_TIMEOUT * 1000;
    
    
    /**
     * Recycle the <code>Task</code> after running them
     */
    protected boolean recycleTasks = Constants.DEFAULT_RECYCLE;
    
    
    /**
     * The <code>Selector</code> timeout value. By default, it is set to 60000
     * miliseconds (as in the j2se 1.5 ORB).
     */
    protected static int selectorTimeout = 1000;


    /**
     * Maximum pending connection before refusing requests.
     */
    protected int maxQueueSizeInBytes = Constants.DEFAULT_QUEUE_SIZE;


    /**
     * The <code>Algorithm</code> used to predict the end of the NIO stream
     */
    protected Class algorithmClass;
    
    
    /**
     * The <code>Algorithm</code> used to parse the NIO stream.
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
     * Next time the exprireKeys() will delete keys.
     */    
    private long nextKeysExpiration = 0;
    
    
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
    protected static String rootFolder = "";
    
    
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
    protected PUFilter portUnificationFilter;
    
    
    protected boolean oOBInline = false;
    // ---------------------------------------------------- Object pools --//


    /**
     * <code>ConcurrentLinkedQueue</code> used as an object pool.
     * If the list becomes empty, new <code>ProcessorTask</code> will be
     * automatically added to the list.
     */
    protected ConcurrentLinkedQueue<ProcessorTask> processorTasks =
        new ConcurrentLinkedQueue<ProcessorTask>();
                                                                             
    
    /**
     * List of active <code>ProcessorTask</code>.
     */
    protected ConcurrentLinkedQueue<ProcessorTask> activeProcessorTasks =
        new ConcurrentLinkedQueue<ProcessorTask>();
    
    // -----------------------------------------  Multi-Selector supports --//

    /**
     * The number of <code>SelectorReadThread</code>
     */
    protected int readThreadsCount = 0;

    
    /**
     * The logger used by the grizzly classes.
     */
    protected static Logger logger = Logger.getLogger("GRIZZLY");
    
    
    /**
     * Flag to disable setting a different time-out on uploads.
     */
    protected boolean disableUploadTimeout = true;    
    
    
    /**
     * Maximum timeout on uploads. 5 minutes as in Apache HTTPD server.
     */
    protected int uploadTimeout = 30000;    
    
    
    // -----------------------------------------  Keep-Alive subsystems --//
    
     
    /**
     * Keep-Alive subsystem. If a client opens a socket but never close it,
     * the <code>SelectionKey</code> will stay forever in the 
     * <code>Selector</code> keys, and this will eventualy produce a 
     * memory leak.
     */
    protected KeepAliveCountManager keepAliveCounter;
    
    
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
     * The maximum entries in the <code>fileCache</code>
     */
    protected int maxCacheEntries = 1024;
    
 
    /**
     * The maximum size of a cached resources.
     */
    protected long minEntrySize = 2048;
            
               
    /**
     * The maximum size of a cached resources.
     */
    protected long maxEntrySize = 537600;
    
    
    /**
     * The maximum cached bytes
     */
    protected long maxLargeFileCacheSize = 10485760;
 
    
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
     * will be delegated to the <code>AsyncHandler</code>
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
    protected boolean enableNioLogging = false;
    
    
    /**
     * Static list of current instance of this class.
     */
    private final static ConcurrentHashMap<Integer,SelectorThread> 
            selectorThreads = new ConcurrentHashMap<Integer,SelectorThread>();
    
    /**
     * The current SelectionKeyHandler.
     */
    private SelectorThreadKeyHandler keyHandler;
    
    
    // ---------------------------------------------------- Constructor --//
    
    
    /**
     * Create the <code>Selector</code> object. Each instance of this class
     * will listen to a specific port.
     */
    public SelectorThread(){
    }
    
    // ------------------------------------------------------ Selector hook --/
    
    
    /**
     * Return the <code>SelectorThread</code> which listen on port, or null
     * if there is no <code>SelectorThread</code>.
     */
    public final static SelectorThread getSelector(int port){
        return selectorThreads.get(port);
    }
    
    
    /**
     * Return an <code>Enumeration</code> of the active 
     * <code>SelectorThread</code>s
     */
    public final static Enumeration<SelectorThread> getSelectors(){
        return selectorThreads.elements();
    }
    
    
    // ----------------------------------------------------------------------/
    
    
    /**
     * Register a <code>SelectionKey</code> to this <code>Selector</code>
     * running of this thread.
     */
    public void registerKey(SelectionKey key){
        selectorHandler.register(key, SelectionKey.OP_READ);
    } 

   // -------------------------------------------------------------- Init // 


    /**
     * Initialize the Grizzly Framework classes.
     */
    protected void initController(){
        if (controller == null){
            controller = new Controller();
        }
        selectorHandler = createSelectorHandler();
        configureSelectorHandler(selectorHandler);
        controller.setSelectorHandler(selectorHandler);
        
        keyHandler = new SelectorThreadKeyHandler(this);

        keyHandler.setLogger(logger);
        keyHandler.setTimeout(keepAliveTimeoutInSeconds * 1000);
        selectorHandler.setSelectionKeyHandler(keyHandler);

        final DefaultProtocolChain protocolChain = new DefaultProtocolChain(){
            @Override
            public void execute(Context ctx) throws Exception {
                if (rcmSupport) {
                    ByteBuffer byteBuffer =
                            (ByteBuffer)ctx.getAttribute
                            (ResourceAllocationFilter.BYTE_BUFFER);
                    // Switch ByteBuffer
                    if (byteBuffer != null){
                        ((WorkerThread)Thread.currentThread())
                            .setByteBuffer(byteBuffer);
                    }

                    if (protocolFilters.size() != 0){
                        int currentPosition = super.executeProtocolFilter(ctx);
                        super.postExecuteProtocolFilter(currentPosition, ctx);
                    }
                } else {
                    super.execute(ctx);
                }
            }              
        };
        protocolChain.setContinuousExecution(true);
        
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
        controller.setPipeline(processorPipeline);
        controller.setReadThreadsCount(readThreadsCount);
    }
    
    /**
     * Create <code>TCPSelectorHandler</code>
     */
    protected TCPSelectorHandler createSelectorHandler() {
        return new SelectorThreadHandler(this);
    }
    
    /**
     * Configure <code>TCPSelectorHandler</code>
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
        selectorHandler.setSocketTimeout(keepAliveTimeoutInSeconds * 1000);
        selectorHandler.setSsBackLog(ssBackLog);
        selectorHandler.setTcpNoDelay(tcpNoDelay);
    }
    
    /**
     * Create and configure resource allocation <code>ProtocolFilter</code>
     * @return resource allocation <code>ProtocolFilter</code>
     */
    protected ProtocolFilter createRaFilter() {
        return new ResourceAllocationFilter() {
            /**
             * Creates a new <code>Pipeline</code>
             */
            @Override
            protected Pipeline newPipeline(int threadCount, Pipeline p) {
                if (threadCount == 0) {
                    return null;
                }
                Pipeline pipeline = new LinkedListPipeline();
                pipeline.setMinThreads(1);
                pipeline.setMaxThreads(threadCount);
                pipeline.setName("RCM_" + threadCount);
                pipeline.initPipeline();
                pipeline.startPipeline();
                return pipeline;
            }
        };
    }

    /**
     * Create HTTP parser <code>ProtocolFilter</code>
     * @return HTTP parser <code>ProtocolFilter</code>
     */
    protected ProtocolFilter createHttpParserFilter() {
        if (asyncExecution){
            return new AsyncProtocolFilter(algorithmClass,port);
        } else {
            return new DefaultProtocolFilter(algorithmClass, port);
        }
    }

    /**
     * Adds and configures <code>ProtocolChain</code>'s filters
     * @param <code>ProtocolChain</code> to configure
     */
    protected void configureFilters(ProtocolChain protocolChain) {
        if (portUnificationFilter != null) {
            protocolChain.addFilter(portUnificationFilter);
        } else {
            ReadFilter readFilter = new ReadFilter();
            readFilter.setContinuousExecution(true);
            protocolChain.addFilter(readFilter);
        }
        
        if (rcmSupport){
            protocolChain.addFilter(createRaFilter());
        }
        
        protocolChain.addFilter(createHttpParserFilter());
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

        portUnificationFilter = new PUFilter();
        portUnificationFilter.configure(props);
    }

    /**
     * Configures port unification depending on passed <code>List</code>s
     * @param protocolFinders <code>ProtocolFinder</code>s
     * @param protocolHandlers <code>ProtocolHandler</code>s
     * @param preProcessors <code>PUPreProcessor</code>s
     */
    public void configurePortUnification(List<ProtocolFinder> protocolFinders,
            List<ProtocolHandler> protocolHandlers, 
            List<PUPreProcessor> preProcessors) {
        
        portUnificationFilter = new PUFilter();
        portUnificationFilter.configure(protocolFinders, protocolHandlers, 
                preProcessors);
    }
      
    /**
     * initialized the endpoint by creating the <code>ServerScoketChannel</code>
     * and by initializing the server socket.
     */
    public void initEndpoint() throws IOException, InstantiationException {
        SelectorThreadConfig.configure(this);
 
        initAlgorithm();
        initKeepAliveCounter();
        initPipeline();        
        initController();
        initFileCacheFactory();
        initMonitoringLevel();
        
        setName("SelectorThread-" + port);
        initProcessorTask(maxProcessorWorkerThreads);               
        SelectorFactory.setMaxSelectors(maxProcessorWorkerThreads);

        initialized = true;           
        logger.log(Level.FINE,"Initializing Grizzly Non-Blocking Mode");                     
    }
     
    
    /**
     * Create a new <code>Pipeline</code> instance using the 
     * <code>pipelineClassName</code> value.
     */
    protected LinkedListPipeline newPipeline(int maxThreads,
                                   int minThreads,
                                   String name, 
                                   int port,
                                   int priority){
        
        Class className = null;                               
        LinkedListPipeline pipeline = null;                               
        try{           
            if ( classLoader == null ){
                className = Class.forName(pipelineClassName);
            } else {
                className = classLoader.loadClass(pipelineClassName);
            }
            pipeline = (LinkedListPipeline)className.newInstance();
        } catch (ClassNotFoundException ex){
            logger.log(Level.WARNING,
                       "Unable to load Pipeline: " + pipelineClassName);
            pipeline = new LinkedListPipeline();
        } catch (InstantiationException ex){
            logger.log(Level.WARNING,
                       "Unable to instantiate Pipeline: "
                       + pipelineClassName);
            pipeline = new LinkedListPipeline();
        } catch (IllegalAccessException ex){
            logger.log(Level.WARNING,
                       "Unable to instantiate Pipeline: "
                       + pipelineClassName);
            pipeline = new LinkedListPipeline();
        }
        
        if (logger.isLoggable(Level.FINE)){
            logger.log(Level.FINE,
                       "http-listener " + port + " uses pipeline: "
                       + pipeline.getClass().getName());
        }
        
        pipeline.setMaxThreads(maxThreads);
        pipeline.setMinThreads(minThreads);    
        pipeline.setName(name);
        pipeline.setPort(port);
        pipeline.setPriority(priority);
        pipeline.setQueueSizeInBytes(maxQueueSizeInBytes);
        pipeline.setThreadsIncrement(threadsIncrement);
        
        return pipeline;
    }
    
    
    /**
     * Initialize the fileCacheFactory associated with this instance
     */
    protected void initFileCacheFactory(){        
        if (asyncExecution){
            isFileCacheEnabled = false;
            isLargeFileCacheEnabled = false;
        }
        
        fileCacheFactory = FileCacheFactory.getFactory(port);
        FileCacheFactory.setIsEnabled(isFileCacheEnabled);
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
     * Injects <code>PipelineStatistic</code> into every
     * <code>Pipeline</code>, for monitoring purposes.
     */
    protected void enablePipelineStats(){
        pipelineStat.start();

        processorPipeline.setPipelineStatistic(pipelineStat);       
        pipelineStat.setProcessorPipeline(processorPipeline);

        if (keepAliveCounter != null){
            keepAliveCounter.setKeepAliveStats(keepAliveStats);
        }
    }
    

    /**
     * Removes <code>PipelineStatistic</code> from every
     * <code>Pipeline</code>, when monitoring has been turned off.
     */
    protected void disablePipelineStats(){
        pipelineStat.stop();
        
        processorPipeline.setPipelineStatistic(null);
        pipelineStat.setProcessorPipeline(null);

        if (keepAliveCounter != null){
            keepAliveCounter.setKeepAliveStats(null);
        }

    }

    
    /**
     * Load using reflection the <code>Algorithm</code> class.
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
     * Initialize the keep-alive mechanism.
     */
    protected void initKeepAliveCounter(){
        keepAliveCounter = new KeepAliveCountManager();
        keepAliveCounter.setMaxKeepAliveRequests(maxKeepAliveRequests);
        keepAliveCounter
            .setKeepAliveTimeoutInSeconds(keepAliveTimeoutInSeconds);
        keepAliveCounter.setPort(port);
        keepAliveCounter.setThreadsTimeout(threadsTimeout);

        keepAliveStats.setMaxConnections(maxKeepAliveRequests);
        keepAliveStats.setSecondsTimeouts(keepAliveTimeoutInSeconds);        
    }
    
    
    /**
     * Init the <code>Pipeline</code>s used by the <code>WorkerThread</code>s.
     */
    protected void initPipeline(){     
        selectorThreads.put(port,this);
        processorPipeline = newPipeline(maxProcessorWorkerThreads, 
                                        minWorkerThreads, "http",
                                        port,Thread.MAX_PRIORITY);  

        if ( maxReadWorkerThreads == 0){
            maxReadWorkerThreads = -1;
            logger.log(Level.WARNING,
                       "http-listener " + port + 
                       " is security-enabled and needs at least 2 threads");
        }
    }
    
 
    /**
     * Create a pool of <code>ProcessorTask</code>
     */
    protected void initProcessorTask(int size){
        for (int i=0; i < size; i++){           
            processorTasks.offer(newProcessorTask(false));
        }
    }  


    /**
     * Initialize <code>ProcessorTask</code>
     */
    protected void rampUpProcessorTask(){
        Iterator<ProcessorTask> iterator = processorTasks.iterator();
        while (iterator.hasNext()) {
            iterator.next().initialize();
        }
    }  
    

    /**
     * Create <code>ProcessorTask</code> objects and configure it to be ready
     * to proceed request.
     */
    protected ProcessorTask newProcessorTask(boolean initialize){                                                      
        DefaultProcessorTask task = 
                new DefaultProcessorTask(initialize, bufferResponse);
        return configureProcessorTask(task);       
    }
    
    
    protected ProcessorTask configureProcessorTask(DefaultProcessorTask task){
        task.setAdapter(adapter);
        task.setMaxHttpHeaderSize(maxHttpHeaderSize);
        task.setBufferSize(requestBufferSize);
        task.setSelectorThread(this);               
        task.setRecycle(recycleTasks);
        task.setDefaultResponseType(defaultResponseType);
        task.setForcedRequestType(forcedRequestType);
        task.setMaxPostSize(maxPostSize);
        task.setTimeout(uploadTimeout);
        task.setDisableUploadTimeout(disableUploadTimeout);
        
        if ( keepAliveCounter.dropConnection() ) {
            task.setDropConnection(true);
        }
        
        // Asynch extentions
        if ( asyncExecution ) {
            task.setEnableAsyncExecution(asyncExecution);
            task.setAsyncHandler(asyncHandler);          
        }
                
        task.setPipeline(processorPipeline);         
        configureCompression(task);
        
        return (ProcessorTask)task;        
    }
 
    
    /**
     * Reconfigure Grizzly Asynchronous Request Processing(ARP) internal 
     * objects.
     */
    protected void reconfigureAsyncExecution(){
        for(ProcessorTask task :processorTasks){
            if (task instanceof DefaultProcessorTask) {
                ((DefaultProcessorTask)task)
                    .setEnableAsyncExecution(asyncExecution);
                ((DefaultProcessorTask)task).setAsyncHandler(asyncHandler);  
            }
        }
    }
    
 
    /**
     * Return a <code>ProcessorTask</code> from the pool. If the pool is empty,
     * create a new instance.
     */
    public ProcessorTask getProcessorTask(){
        ProcessorTask processorTask = null;
        if (recycleTasks) {
            processorTask = processorTasks.poll();
        }
        
        if (processorTask == null){
            processorTask = newProcessorTask(false);
        } 
        
        if ( isMonitoringEnabled() ){
           activeProcessorTasks.offer(processorTask); 
        }        
        return processorTask;
    }
           
    // --------------------------------------------------------- Thread run --/
    
    
    /**
     * Start the endpoint (this)
     */
    @Override
    public void run(){
        try{
            running = true;

            kaTimeout = keepAliveTimeoutInSeconds * 1000;
            rampUpProcessorTask();
            registerComponents();

            displayConfiguration();
            startListener();  
        } catch (Exception ex){
            logger.log(Level.SEVERE,"selectorThread.errorOnRequest", ex);
        }
    }

    
    // ------------------------------------------------------------Start ----/
    
    
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
        final CountDownLatch latch = new CountDownLatch(1);
        controller.addStateListener(new ControllerStateListenerAdapter() {
            @Override
            public void onReady() {
                latch.countDown();
            }

            @Override
            public void onException(Throwable e) {
                if (latch.getCount() > 0) {
                    logger().log(Level.SEVERE, "Exception during " +
                            "starting the controller", e);
                    latch.countDown();
                } else {
                    logger().log(Level.SEVERE, "Exception during " +
                            "controller processing", e);
                }
            }
        });

        super.start();

        try {
            latch.await();
        } catch (InterruptedException ex) {
        }
        
        if (!controller.isStarted()) {
            throw new IllegalStateException("Controller is not started!");
        }                       
    }
    
    
    /**
     * Starts the <code>Pipeline</code> used by this <code>Selector</code>
     */
    protected void startPipelines(){
        processorPipeline.startPipeline();        
    }

    
    /**
     * Stop the <code>Pipeline</code> used by this <code>Selector</code>
     */
    protected void stopPipelines(){
        if ( keepAliveCounter != null )
            keepAliveCounter.stopPipeline();        

        processorPipeline.stopPipeline();
    }
    
    /**
     * Start a non blocking <code>Selector</code> object.
     */
    protected void startListener(){
        synchronized(lock){          
            try{
                controller.start();
                stopPipelines();
                unregisterComponents();                
            } catch (Throwable t){
                logger.log(Level.SEVERE,"selectorThread.stopException",t);
            } 
        }
    }

   
    
    
    /**
     * Cancel the current <code>SelectionKey</code>
     */
    public void cancelKey(SelectionKey key){
        selectorHandler.getSelectionKeyHandler().cancel(key);
    }
    
    
    /**
     * Returns the <code>Task</code> object to the pool.
     */
    public void returnTask(Task task){
        // Returns the object to the pool.
        if (task != null) {
            if (task.getType() == Task.PROCESSOR_TASK){
                                
                if ( isMonitoringEnabled() ){
                   activeProcessorTasks.remove(((DefaultProcessorTask)task));
                }  
                
                processorTasks.offer((DefaultProcessorTask)task);
            } 
        }
    }

    
    /**
     * Clear all cached <code>Tasks</code> 
     */
    protected void clearTasks(){
        processorTasks.clear();
    }

    
    /**
     * Cancel the <code>threadID</code> execution. Return <code>true</code>
     * if it is successful.
     *
     * @param cancelThreadID the thread name to cancel
     */
    public boolean cancelThreadExecution(long cancelThreadID){              
        if (activeProcessorTasks.size() == 0) return false;
        
        /*Iterator<ProcessorTask> iterator = activeProcessorTasks.iterator();
        ProcessorTask processorTask;
        long threadID;
        while( iterator.hasNext() ){
            processorTask = iterator.next();
            threadID = processorTask.getWorkerThreadID();
            if (threadID == cancelThreadID){
                processorTask.cancelTask("Request cancelled.","500");
                logger.log(Level.WARNING,
                        "Thread Request Cancelled: " + threadID);     
                return processorTask.getPipeline().interruptThread(threadID);
            }
        }*/
        return false;
    }

        
    // ---------------------------------------------------Endpoint Lifecycle --/
 
    public void stopEndpoint() {
        if (!running) return;
        running = false;
        try{
            controller.stop();
        } catch (IOException ex){
            logger.log(Level.WARNING,"stopException",ex);
        }
        synchronized(lock){
            // Wait for the main thread to stop.
            clearTasks();
        }
    }
    
    // ------------------------------------------------------Public methods--/
 

    public void setMaxThreads(int maxThreads) {
        if ( maxThreads == 1 ) {
            maxProcessorWorkerThreads = 5;
        } else {
            maxProcessorWorkerThreads = maxThreads;
        }
    }

    public int getMaxThreads() {
        return maxProcessorWorkerThreads;
    }

    public void setMaxSpareThreads(int maxThreads) {
    }

    public int getMaxSpareThreads() {
        return maxProcessorWorkerThreads;
    }

    public void setMinSpareThreads(int minSpareThreads) {
        this.minSpareThreads = minSpareThreads;
    }

    public int getMinSpareThreads() {
        return  minSpareThreads;
    }


    /**
     * Similar to <code>getPort()</code>, but getting port number directly from 
     * connection (<code>ServerSocket</code>, <code>DatagramSocket</code>).
     * So if default port number 0 was set during initialization, then <code>getPort()</code>
     * will return 0, but getPortLowLevel() will return port number assigned by OS.
     * 
     * @return port number, or -1 if <code>SelectorThread</code> was not started
     */
    public int getPortLowLevel() {
        return selectorHandler.getPortLowLevel();
    }
    
    public int getPort() {
        return port;
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
        return running;
    }
 
    
    /**
     * Provides the count of request threads that are currently
     * being processed by the container
     *
     * @return The count of busy threads 
     */
    public int getCurrentBusyProcessorThreads() {
        int busy = processorPipeline.getCurrentThreadsBusy();
 
        return busy;  
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

    public int getSoLinger() {
        return linger;
    }
    
    public void setSoLinger( int i ) {
        linger=i;
    }

    public int getSoTimeout() {
        return socketTimeout;
    }
    
    public void setSoTimeout( int i ) {
        socketTimeout=i;
    }
    
    public int getServerSoTimeout() {
        return serverTimeout;
    }  
    
    public void setServerSoTimeout( int i ) {
        serverTimeout=i;
    }
 
    // ------------------------------------------------------ Connector Methods


    /**
     * Get the maximum pending connection this <code>Pipeline</code>
     * can handle.
     */
    public int getQueueSizeInBytes(){
        return maxQueueSizeInBytes;
    }
    
    
    
    public int getMaxKeepAliveRequests() {
        return maxKeepAliveRequests;
    }
    
    
    /** 
     * Set the maximum number of Keep-Alive requests that we will honor.
     */
    public void setMaxKeepAliveRequests(int mkar) {
        maxKeepAliveRequests = mkar;
    }
    

    /** 
     * Sets the number of seconds before a keep-alive connection that has
     * been idle times out and is closed.
     *
     * @param timeout Keep-alive timeout in number of seconds
     */    
    public void setKeepAliveTimeoutInSeconds(int timeout) {
        keepAliveTimeoutInSeconds = timeout;
        keepAliveStats.setSecondsTimeouts(timeout);
    }


    /** 
     * Gets the number of seconds before a keep-alive connection that has
     * been idle times out and is closed.
     *
     * @return Keep-alive timeout in number of seconds
     */    
    public int getKeepAliveTimeoutInSeconds() {
        return keepAliveTimeoutInSeconds;
    }


    /** 
     * Sets the number of keep-alive threads.
     *
     * @param threadCount Number of keep-alive threads
     */    
    public void setKeepAliveThreadCount(int threadCount) {
        keepAliveCounter.setMaxThreads(threadCount);
    }


    /**
     * Set the associated adapter.
     * 
     * @param adapter the new adapter
     */
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
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
            logger.log(Level.WARNING,
                        "setSoLinger exception ",ex);
        }
        
        try{
            if( tcpNoDelay )
                socket.setTcpNoDelay(tcpNoDelay);
        } catch (SocketException ex){
            logger.log(Level.WARNING,
                        "setTcpNoDelay exception ",ex);
        }
        
        try{
            if ( maxReadWorkerThreads != 0)
                socket.setReuseAddress(reuseAddress);
        } catch (SocketException ex){
            logger.log(Level.WARNING,
                        "setReuseAddress exception ",ex);
        }    
        
        try{
            if(oOBInline){
                socket.setOOBInline(oOBInline);
            }
        } catch (SocketException ex){
            logger.log(Level.WARNING,
                        "setOOBInline exception ",ex);
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
     * Register JMX components.
     */
    protected void registerComponents(){

        if( this.domain != null  && jmxManagement != null) {

            try {
                globalRequestProcessorName = new ObjectName(
                    domain + ":type=GlobalRequestProcessor,name=http" + port);
                jmxManagement.registerComponent(globalRequestProcessor,
                                                globalRequestProcessorName,
                                                null);
 
                keepAliveMbeanName = new ObjectName(
                    domain + ":type=PWCKeepAlive,name=http" + port);
                jmxManagement.registerComponent(keepAliveStats,
                                                keepAliveMbeanName,
                                                null);

                pwcConnectionQueueMbeanName = new ObjectName(
                    domain + ":type=PWCConnectionQueue,name=http" + port);
                jmxManagement.registerComponent(pipelineStat,
                                                pwcConnectionQueueMbeanName,
                                                null);
                
                pwcFileCacheMbeanName = new ObjectName(
                    domain + ":type=PWCFileCache,name=http" + port);
                jmxManagement.registerComponent(fileCacheFactory,
                                                pwcFileCacheMbeanName,
                                                null);                
            } catch (Exception ex) {
                logger.log(Level.WARNING,
                           "selectorThread.mbeanRegistrationException",
                           new Object[]{new Integer(port),ex});
            }
        }

    }
    
    
    /**
     * Unregister components.
     **/
    protected void unregisterComponents(){

        if (this.domain != null && jmxManagement != null) {
            try {
                if (globalRequestProcessorName != null) {
                    jmxManagement.unregisterComponent(globalRequestProcessorName);
                }
                if (keepAliveMbeanName != null) {
                    jmxManagement.unregisterComponent(keepAliveMbeanName);
                }
                if (pwcConnectionQueueMbeanName != null) {
                    jmxManagement.unregisterComponent(pwcConnectionQueueMbeanName);
                }
                if (pwcFileCacheMbeanName != null) {
                    jmxManagement.unregisterComponent(pwcFileCacheMbeanName);
                }                    
            } catch (Exception ex) {
                logger.log(Level.WARNING,
                           "mbeanDeregistrationException",
                           new Object[]{new Integer(port),ex});
            }
        }
    }

    
    /**
     * Enable gathering of monitoring datas.
     */
    public void enableMonitoring(){
        isMonitoringEnabled = true;
        enablePipelineStats();      
        fileCacheFactory.setIsMonitoringEnabled(isMonitoringEnabled);
    }
    
    
    /**
     * Disable gathering of monitoring datas. 
     */
    public void disableMonitoring(){
        disablePipelineStats();  
        fileCacheFactory.setIsMonitoringEnabled(isMonitoringEnabled);        
    }

    
    /**
     * Returns <code>true</code> if monitoring has been enabled, 
     * <code>false</code> otherwise.
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


    /*
     * Initializes the web container monitoring level from the domain.xml.
     */
    protected void initMonitoringLevel() {
        pipelineStat = new PipelineStatistic(port);
        pipelineStat.setQueueSizeInBytes(maxQueueSizeInBytes);
    }
 
    
    public int getMaxHttpHeaderSize() {
        return maxHttpHeaderSize;
    }
    
    public void setMaxHttpHeaderSize(int maxHttpHeaderSize) {
        this.maxHttpHeaderSize = maxHttpHeaderSize;
    }
    
        
    /**
     * The minimun threads created at startup.
     */ 
    public void setMinThreads(int minWorkerThreads){
        this.minWorkerThreads = minWorkerThreads;
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
    
    
    public Selector getSelector(){
        if (selector != null) {
            return selector;
        } else {
            selectorHandler.getSelector();
        }
        
        return null;
    }

    /************************* PWCThreadPool Stats *************************/

    public int getCountThreadsStats() {
        int ret = processorPipeline.getCurrentThreadCount();

        return ret;
    }


    public int getCountThreadsIdleStats() {
        int ret = processorPipeline.getWaitingThread();

        return ret;
    }


    /************************* HTTPListener Stats *************************/

    public int getCurrentThreadCountStats() {
        int ret = processorPipeline.getCurrentThreadCount();

        return ret;
    }


    public int getCurrentThreadsBusyStats() {
        int ret = processorPipeline.getCurrentThreadsBusy();

        return ret;
    }

    public int getMaxSpareThreadsStats() {
        int ret = processorPipeline.getMaxSpareThreads();

        return ret;
    }


    public int getMinSpareThreadsStats() {
        int ret = processorPipeline.getMinSpareThreads();

        return ret;
    }


    public int getMaxThreadsStats() {
        int ret = processorPipeline.getMaxThreads();
        
        return ret;
    }


    //------------------------------------------------- FileCache config -----/

    
    /**
     * Remove a context path from the <code>FileCache</code>.
     */
    public void removeCacheEntry(String contextPath){  
        ConcurrentHashMap<String,FileCacheEntry> 
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
     * The timeout in seconds before remove a <code>FileCacheEntry</code>
     * from the <code>fileCache</code>
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
     * Set the maximum size a <code>FileCacheEntry</code> can have.
     */
    public void setMinEntrySize(long mSize){
        minEntrySize = mSize;
    }
    
    
    /**
     * Get the maximum size a <code>FileCacheEntry</code> can have.
     */
    public long getMinEntrySize(){
        return minEntrySize;
    }
     
    
    /**
     * Set the maximum size a <code>FileCacheEntry</code> can have.
     */
    public void setMaxEntrySize(long mEntrySize){
        maxEntrySize = mEntrySize;
    }
    
    
    /**
     * Get the maximum size a <code>FileCacheEntry</code> can have.
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

    // --------------------------------------------------------------------//
    
    /**
     * Enable the <code>AsyncHandler</code> used when asynchronous
     */
    public void setEnableAsyncExecution(boolean asyncExecution){
        this.asyncExecution = asyncExecution;     
        if (running){
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
     * Set the <code>AsyncHandler</code> used when asynchronous execution is 
     * enabled.
     */
    public void setAsyncHandler(AsyncHandler asyncHandler){
        this.asyncHandler = asyncHandler;     
    }
    
       
    /**
     * Return the <code>AsyncHandler</code> used when asynchronous execution is 
     * enabled.
     */    
    public AsyncHandler getAsyncHandler(){
        return asyncHandler;
    }
    
    
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
    public static void setWebAppRootPath(String rf){
        rootFolder = rf;
    }
    
    
    /**
     * Return the folder's root where application are deployed.
     */
    public static String getWebAppRootPath(){
        return rootFolder;
    }
    
    
    public int getMaxReadWorkerThreads(){
        return maxReadWorkerThreads;
    }
    
        
    // ------------------------------------------------------ Compression ---//


    protected void configureCompression(DefaultProcessorTask processorTask){
        processorTask.setCompression(compression);
        processorTask.addNoCompressionUserAgent(noCompressionUserAgents);
        parseComressableMimeTypes();
        processorTask.addCompressableMimeType(compressableMimeTypes);
        processorTask.setCompressionMinSize(compressionMinSize);
        processorTask.addRestrictedUserAgent(restrictedUserAgents);
    }


    // ------------------------------------------------------ Debug ---------//
    
        
    /**
     * Display the Grizzly configuration parameters.
     */
    private void displayConfiguration(){
       if (displayConfiguration){
            logger.log(Level.INFO,
                    "\n Grizzly configuration for port " 
                    + port 
                    + "\n\t maxThreads: " 
                    + maxProcessorWorkerThreads 
                    + "\n\t minThreads: " 
                    + minWorkerThreads 
                    + "\n\t ByteBuffer size: " 
                    + requestBufferSize 
                    + "\n\t useDirectByteBuffer: "
                    + useDirectByteBuffer                       
                    + "\n\t useByteBufferView: "                        
                    + useByteBufferView                   
                    + "\n\t maxHttpHeaderSize: " 
                    + maxHttpHeaderSize
                    + "\n\t maxKeepAliveRequests: "
                    + maxKeepAliveRequests
                    + "\n\t keepAliveTimeoutInSeconds: "
                    + keepAliveTimeoutInSeconds
                    + "\n\t Static File Cache enabled: "                        
                    + isFileCacheEnabled                    
                    + "\n\t Stream Algorithm : "                        
                    + algorithmClassName        
                    + "\n\t Pipeline : "                        
                    + pipelineClassName                    
                    + "\n\t Round Robin Selector Algorithm enabled: "                        
                    + (readThreadsCount > 0)
                    + "\n\t Round Robin Selector pool size: "
                    + readThreadsCount
                    + "\n\t recycleTasks: "                        
                    + recycleTasks
                    + "\n\t Asynchronous Request Processing enabled: " 
                    + asyncExecution); 
        }
    }

    
    /**
     * Return <code>true</code> if the reponse is buffered.
     */
    public boolean getBufferResponse() {
        return bufferResponse;
    }

    
    /**
     * <code>true</code>if the reponse willk be buffered.
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
    // ----------------------------------------------Setter/Getter-----------//
    
    public KeepAliveCountManager getKeepAliveCounter() {
        return keepAliveCounter;
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

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public ObjectName getOname() {
        return oname;
    }

    public void setOname(ObjectName oname) {
        this.oname = oname;
    }

    public ObjectName getGlobalRequestProcessorName() {
        return globalRequestProcessorName;
    }

    public void setGlobalRequestProcessorName
            (ObjectName globalRequestProcessorName) {
        this.globalRequestProcessorName = globalRequestProcessorName;
    }

    public ObjectName getKeepAliveMbeanName() {
        return keepAliveMbeanName;
    }

    public void setKeepAliveMbeanName(ObjectName keepAliveMbeanName) {
        this.keepAliveMbeanName = keepAliveMbeanName;
    }

    public ObjectName getPwcConnectionQueueMbeanName() {
        return pwcConnectionQueueMbeanName;
    }

    public void setPwcConnectionQueueMbeanName
            (ObjectName pwcConnectionQueueMbeanName) {
        this.pwcConnectionQueueMbeanName = pwcConnectionQueueMbeanName;
    }

    public ObjectName getPwcFileCacheMbeanName() {
        return pwcFileCacheMbeanName;
    }

    public void setPwcFileCacheMbeanName(ObjectName pwcFileCacheMbeanName) {
        this.pwcFileCacheMbeanName = pwcFileCacheMbeanName;
    }

    public MBeanServer getMserver() {
        return mserver;
    }

    public void setMserver(MBeanServer mserver) {
        this.mserver = mserver;
    }

    public ObjectName getProcessorWorkerThreadName() {
        return processorWorkerThreadName;
    }

    public void setProcessorWorkerThreadName
            (ObjectName processorWorkerThreadName) {
        this.processorWorkerThreadName = processorWorkerThreadName;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public int getLinger() {
        return linger;
    }

    public void setLinger(int linger) {
        this.linger = linger;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public String getCompression() {
        return compression;
    }

    public void setCompression(String compression) {
        this.compression = compression;
    }

    public String getNoCompressionUserAgents() {
        return noCompressionUserAgents;
    }

    public void setNoCompressionUserAgents(String noCompressionUserAgents) {
        this.noCompressionUserAgents = noCompressionUserAgents;
    }

    public String getRestrictedUserAgents() {
        return restrictedUserAgents;
    }

    public void setRestrictedUserAgents(String restrictedUserAgents) {
        this.restrictedUserAgents = restrictedUserAgents;
    }

    public String getCompressableMimeTypes() {
        return compressableMimeTypes;
    }

    public void setCompressableMimeTypes(String compressableMimeTypes) {
        this.compressableMimeTypes = compressableMimeTypes;
    }
    
    private void parseComressableMimeTypes() {
        if (compressableMimeTypes == null) {
            parsedCompressableMimeTypes = new String[0];
            return;
        }
        
        int hash = -1;
        if ((hash = compressableMimeTypes.hashCode()) == parsedComressableMimeTypesHash)
            return;
        
        List<String> compressableMimeTypeList = new ArrayList<String>(4);
        StringTokenizer st = new StringTokenizer(compressableMimeTypes, ",");

        while(st.hasMoreTokens()) {
            compressableMimeTypeList.add(st.nextToken().trim());
        }
        
        String[] tmpParsedCompressableMimeTypes = new String[compressableMimeTypeList.size()];
        compressableMimeTypeList.toArray(tmpParsedCompressableMimeTypes);
        parsedCompressableMimeTypes = tmpParsedCompressableMimeTypes;
        parsedComressableMimeTypesHash = hash;
    }
    
    
    public int getCompressionMinSize() {
        return compressionMinSize;
    }

    public void setCompressionMinSize(int compressionMinSize) {
        this.compressionMinSize = compressionMinSize;
    }

    public boolean isBufferResponse() {
        return bufferResponse;
    }

    public int getMinReadQueueLength() {
        return minReadQueueLength;
    }

    public void setMinReadQueueLength(int minReadQueueLength) {
        this.minReadQueueLength = minReadQueueLength;
    }

    public int getMinProcessorQueueLength() {
        return minProcessorQueueLength;
    }

    public void setMinProcessorQueueLength(int minProcessorQueueLength) {
        this.minProcessorQueueLength = minProcessorQueueLength;
    }

    public void setSelector(Selector selector) {
        this.selector = selector;
    }

    public int getSelectorReadThreadsCount() {
        return readThreadsCount;
    }

    public void setSelectorReadThreadsCount(int readThreadsCount) {
        this.readThreadsCount = readThreadsCount;
    }

    public Pipeline getProcessorPipeline() {
        return processorPipeline;
    }

    public void setProcessorPipeline(LinkedListPipeline processorPipeline) {
        this.processorPipeline = processorPipeline;
    }

    public PipelineStatistic getPipelineStat() {
        return pipelineStat;
    }

    public void setPipelineStat(PipelineStatistic pipelineStat) {
        this.pipelineStat = pipelineStat;
    }

    public String getPipelineClassName() {
        return pipelineClassName;
    }

    public void setPipelineClassName(String pipelineClassName) {
        this.pipelineClassName = pipelineClassName;
    }

    public int getMaxProcessorWorkerThreads() {
        return maxProcessorWorkerThreads;
    }

    public void setMaxProcessorWorkerThreads(int maxProcessorWorkerThreads) {
        this.maxProcessorWorkerThreads = maxProcessorWorkerThreads;
    }

    public void setMaxReadWorkerThreads(int maxReadWorkerThreads) {
        this.maxReadWorkerThreads = maxReadWorkerThreads;
    }

    public int getMinWorkerThreads() {
        return minWorkerThreads;
    }

    public void setMinWorkerThreads(int minWorkerThreads) {
        this.minWorkerThreads = minWorkerThreads;
    }

    public int getThreadsIncrement() {
        return threadsIncrement;
    }

    public void setThreadsIncrement(int threadsIncrement) {
        this.threadsIncrement = threadsIncrement;
    }

    public int getThreadsTimeout() {
        return threadsTimeout;
    }

    public void setThreadsTimeout(int threadsTimeout) {
        this.threadsTimeout = threadsTimeout;
    }

    public boolean isUseDirectByteBuffer() {
        return useDirectByteBuffer;
    }

    public void setUseDirectByteBuffer(boolean useDirectByteBuffer) {
        this.useDirectByteBuffer = useDirectByteBuffer;
    }

    public RequestGroupInfo getGlobalRequestProcessor() {
        return globalRequestProcessor;
    }

    public void setGlobalRequestProcessor(RequestGroupInfo globalRequestProcessor) {
        this.globalRequestProcessor = globalRequestProcessor;
    }

    public void setKeepAliveStats(KeepAliveStats keepAliveStats) {
        this.keepAliveStats = keepAliveStats;
    }

    public boolean isDisplayConfiguration() {
        return displayConfiguration;
    }

    public void setDisplayConfiguration(boolean displayConfiguration) {
        this.displayConfiguration = displayConfiguration;
    }

    public boolean isIsMonitoringEnabled() {
        return isMonitoringEnabled;
    }

    public void setIsMonitoringEnabled(boolean isMonitoringEnabled) {
        this.isMonitoringEnabled = isMonitoringEnabled;
    }

    public int getCurrentConnectionNumber() {
        return currentConnectionNumber;
    }

    public void setCurrentConnectionNumber(int currentConnectionNumber) {
        this.currentConnectionNumber = currentConnectionNumber;
    }

    public void setIsWaiting(boolean isWaiting) {
        this.isWaiting = isWaiting;
    }

    public boolean isUseByteBufferView() {
        return useByteBufferView;
    }

    public void setUseByteBufferView(boolean useByteBufferView) {
        this.useByteBufferView = useByteBufferView;
    }

    public int getKaTimeout() {
        return kaTimeout;
    }

    public void setKaTimeout(int kaTimeout) {
        this.kaTimeout = kaTimeout;
    }

    public boolean isRecycleTasks() {
        return recycleTasks;
    }

    public void setRecycleTasks(boolean recycleTasks) {
        this.recycleTasks = recycleTasks;
    }

    public static int getSelectorTimeout() {
        return selectorTimeout;
    }

    public static void setSelectorTimeout(int aSelectorTimeout) {
        selectorTimeout = aSelectorTimeout;
    }

    public int getMaxQueueSizeInBytes() {
        return maxQueueSizeInBytes;
    }

    public void setMaxQueueSizeInBytes(int maxQueueSizeInBytes) {
        this.maxQueueSizeInBytes = maxQueueSizeInBytes;
    }

    public Class getAlgorithmClass() {
        return algorithmClass;
    }

    public void setAlgorithmClass(Class algorithmClass) {
        this.algorithmClass = algorithmClass;
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

    public long getNextKeysExpiration() {
        return nextKeysExpiration;
    }

    public void setNextKeysExpiration(long nextKeysExpiration) {
        this.nextKeysExpiration = nextKeysExpiration;
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

    public static String getRootFolder() {
        return rootFolder;
    }

    public static void setRootFolder(String aRootFolder) {
        rootFolder = aRootFolder;
    }

    public ConcurrentLinkedQueue<ProcessorTask> getProcessorTasks() {
        return processorTasks;
    }

    public void setProcessorTasks(ConcurrentLinkedQueue<ProcessorTask> 
            processorTasks) {
        this.processorTasks = processorTasks;
    }

    public ConcurrentLinkedQueue<ProcessorTask> getActiveProcessorTasks() {
        return activeProcessorTasks;
    }

    public void setActiveProcessorTasks(ConcurrentLinkedQueue<ProcessorTask> 
            activeProcessorTasks) {
        this.activeProcessorTasks = activeProcessorTasks;
    }

    public static Logger getLogger() {
        return logger;
    }

    public Management getManagement() {
        return jmxManagement;
    }

    public void setManagement(Management jmxManagement) {
        this.jmxManagement = jmxManagement;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    
    /**
     * Set the <code>ClassLoader</code> used to load configurable
     * classes (Pipeline, StreamAlgorithm).
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public boolean isEnableNioLogging() {
        return enableNioLogging;
    }

    public void setEnableNioLogging(boolean enableNioLogging) {
        this.enableNioLogging = enableNioLogging;
    }

    public int getMaxPostSize() {
        return maxPostSize;
    }

    public void setMaxPostSize(int maxPostSize) {
        this.maxPostSize = maxPostSize;
    }

    public Controller getController() {
        return controller;
    }

    public void setController(Controller controller) {
        this.controller = controller;
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
    public int getTimeout() {
        return uploadTimeout;
    } 
}
