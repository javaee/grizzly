/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 *
 */
package org.glassfish.grizzly.web;

import org.glassfish.grizzly.web.arp.AsyncHandler;
import org.glassfish.grizzly.web.arp.AsyncProtocolFilter;
import org.glassfish.grizzly.web.FileCache.FileCacheEntry;

import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

import java.io.File;
import javax.management.ObjectName;
import javax.management.MBeanServer;
import javax.management.MBeanRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterAdapter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.util.LinkedTransferQueue;
import org.glassfish.grizzly.web.container.Adapter;
import org.glassfish.grizzly.web.container.RequestGroupInfo;
import org.glassfish.grizzly.web.container.util.res.StringManager;



/**
 * The SelectorThread class is the entry point when embedding the Grizzly Web
 * Server. All Web Server configuration must be set on this object before invoking
 * the {@link listen()} method. As an example:
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
        SelectorThread.setWebAppRootPath(folder);
        Adapter adapter = new StaticResourcesAdapter(folder);
        ((StaticResourcesAdapter)adapter).setRootFolder(folder);
        selectorThread.setAdapter(adapter);
        selectorThread.setDisplayConfiguration(true);
        selectorThread.listen(); 
 * </code></pre>
 * 
 * @author Jean-Francois Arcand
 */
public class WebFilter extends FilterAdapter implements MBeanRegistration{
            
    public final static String SERVER_NAME = 
            System.getProperty("product.name") != null 
                ? System.getProperty("product.name") : "grizzly";
    
        
    // ----------------------------------------------------- JMX Support ---/
    
    /**
     * The logger used by the grizzly classes.
     */
    protected static Logger logger = Grizzly.logger;
    
    protected String name;
    protected String domain;
    protected ObjectName oname;
    protected ObjectName globalRequestProcessorName;
    private ObjectName keepAliveMbeanName;
    private ObjectName connectionQueueMbeanName;
    private ObjectName fileCacheMbeanName;
    protected MBeanServer mserver;
    protected ObjectName processorWorkerThreadName;


    // ------------------------------------------------------Socket setting --/
    protected int maxKeepAliveRequests = Constants.DEFAULT_MAX_KEEP_ALIVE;
    
    // Number of keep-alive threads
    protected int keepAliveThreadCount = 1;
    
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
     * Associated adapter.
     */
    protected Adapter adapter = null;

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
     * Monitoring object used to store information.
     */
    protected RequestGroupInfo globalRequestProcessor= new RequestGroupInfo();
    
    
    /**
     * Keep-alive stats
     */
    private KeepAliveStats keepAliveStats = null;


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
    

    /*
     * Number of seconds before idle keep-alive connections expire
     */
    protected int keepAliveTimeoutInSeconds = 30;


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
    
    
    // ---------------------------------------------------- Object pools --//


    /**
     * {@link ConcurrentLinkedQueue} used as an object pool.
     * If the list becomes empty, new {@link ProcessorTask} will be
     * automatically added to the list.
     */
    protected LinkedTransferQueue<ProcessorTask> processorTasks =
        new LinkedTransferQueue<ProcessorTask>();
                                                                             
    
    /**
     * List of active {@link ProcessorTask}.
     */
    protected LinkedTransferQueue<ProcessorTask> activeProcessorTasks =
        new LinkedTransferQueue<ProcessorTask>();
    
    // -----------------------------------------  Multi-Selector supports --//

    /**
     * The number of {@link SelectorReadThread}
     */
    protected int readThreadsCount = 0;

    
    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);
    
    
    /**
     * Flag to disable setting a different time-out on uploads.
     */
    protected boolean disableUploadTimeout = true;    
    
    
    /**
     * Maximum timeout on uploads. 5 minutes as in Apache HTTPD server.
     */
    protected int uploadTimeout = 30000;    

    // ------------------------------------------------- FileCache support --//
   
    
    /**
     * The FileCacheFactory associated with this Selector
     */ 
    protected FileCacheFactory fileCacheFactory;

    /**
     * Is the FileCache enabled.
     */
    protected boolean isFileCacheEnabled;
    
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

    
    // ---------------------------------------------------- Constructor --//
    
    
    /**
     * Create the {@link WebFilter}.
     */
    public WebFilter() {
        this("webfilter");
    }

    /**
     * Create the {@link WebFilter} with the specific name.
     */
    public WebFilter(String name) {
        rampUpProcessorTask();
        registerComponents();

        displayConfiguration();
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        HttpWorkerThread workerThread =
                ((HttpWorkerThread)Thread.currentThread());

        boolean keepAlive = false;

        ProcessorTask processorTask = workerThread.getProcessorTask();
        if (processorTask == null) {
            processorTask = getProcessorTask();
            workerThread.setProcessorTask(processorTask);
        }

        KeepAliveThreadAttachment k = (KeepAliveThreadAttachment) workerThread.getAttachment();
        k.setTimeout(System.currentTimeMillis());
        KeepAliveStats ks = selectorThread.getKeepAliveStats();
        k.setKeepAliveStats(ks);

        // Bind the Attachment to the SelectionKey
        ctx.getSelectionKey().attach(k);

        int count = k.increaseKeepAliveCount();
        if (count > getMaxKeepAliveRequests() && ks != null) {
            ks.incrementCountRefusals();
            processorTask.setDropConnection(true);
        } else {
            processorTask.setDropConnection(false);
        }

        configureProcessorTask(processorTask, ctx, workerThread,
                streamAlgorithm.getHandler());

        try {
            keepAlive = processorTask.process(inputStream, null);
        } catch (Throwable ex) {
            logger.log(Level.INFO, "ProcessorTask exception", ex);
            keepAlive = false;
        }

        Object ra = workerThread.getAttachment().getAttribute("suspend");
        if (ra != null){
            // Detatch anything associated with the Thread.
            workerThread.setInputStream(new InputReader());
            workerThread.setByteBuffer(null);
            workerThread.setProcessorTask(null);

            ctx.setKeyRegistrationState(
                    Context.KeyRegistrationState.REGISTER);
            return true;
        }

        if (processorTask != null){
            processorTask.recycle();
        }

        if (keepAlive){
            ctx.setKeyRegistrationState(
                    Context.KeyRegistrationState.REGISTER);
        } else {
            ctx.setKeyRegistrationState(
                    Context.KeyRegistrationState.CANCEL);
        }

        // Last filter.
        return nextAction;
    }

    @Override
    public NextAction postRead(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        return nextAction;
    }

    /**
     * Configure {@link ProcessorTask}.
     */
    protected void configureProcessorTask(ProcessorTask processorTask,
            FilterChainContext context, HttpWorkerThread workerThread,
            Interceptor handler) {
        processorTask.setConnection(context.getConnection());

        if (processorTask.getHandler() == null){
            processorTask.setHandler(handler);
        }
    }

    /**
     * Is {@link ProtocolFilter} secured
     * @return is {@link ProtocolFilter} secured
     */
    protected boolean isSecure() {
        return false;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Create HTTP parser {@link ProtocolFilter}
     * @return HTTP parser {@link ProtocolFilter}
     */
    protected Filter createHttpParserFilter() {
        if (asyncExecution){
            return new AsyncProtocolFilter(algorithmClass,port);
        } else {
            return new DefaultProtocolFilter(algorithmClass, port);
        }
    }
    
    /**
     * Injects {@link ThreadPoolStatistic} into every
     * {@link ExecutorService}, for monitoring purposes.
     */
    protected void enableThreadPoolStats(){   
        threadPoolStat.start();

        keepAliveStats = new KeepAliveStats();
        StatsThreadPool statsThreadPool = (StatsThreadPool) threadPool;
        statsThreadPool.setStatistic(threadPoolStat);
        threadPoolStat.setThreadPool(threadPool);
    }
    

    /**
     * Removes {@link ThreadPoolStatistic} from every
     * {@link ExecutorService}, when monitoring has been turned off.
     */
    protected void disableThreadPoolStats(){
        threadPoolStat.stop();
        
        keepAliveStats = null;
        StatsThreadPool statsThreadPool = (StatsThreadPool) threadPool;
        statsThreadPool.setStatistic(null);
        threadPoolStat.setThreadPool(null);
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
        Iterator<ProcessorTask> iterator = processorTasks.iterator();
        while (iterator.hasNext()) {
            iterator.next().initialize();
        }
    }  
    

    /**
     * Create {@link ProcessorTask} objects and configure it to be ready
     * to proceed request.
     */
    protected ProcessorTask newProcessorTask(boolean initialize){                                                      
        ProcessorTask task = 
                new ProcessorTask(initialize, bufferResponse);
        return configureProcessorTask(task);       
    }
    
    
    protected ProcessorTask configureProcessorTask(ProcessorTask task) {
        task.setAdapter(adapter);
        task.setMaxHttpHeaderSize(maxHttpHeaderSize);
        task.setBufferSize(requestBufferSize);
        task.setDefaultResponseType(defaultResponseType);
        task.setForcedRequestType(forcedRequestType);
        task.setMaxPostSize(maxPostSize);
        task.setTimeout(uploadTimeout);
        task.setDisableUploadTimeout(disableUploadTimeout);
        task.setAsyncHttpWriteEnabled(isAsyncHttpWriteEnabled);
        task.setTransactionTimeout(transactionTimeout);
        task.setUseChunking(useChunking);
        
        // Asynch extentions
        if ( asyncExecution ) {
            task.setEnableAsyncExecution(asyncExecution);
            task.setAsyncHandler(asyncHandler);          
        }
                
        task.setThreadPool(threadPool);
        configureCompression(task);
        
        return (ProcessorTask)task;        
    }
 
    
    /**
     * Reconfigure Grizzly Asynchronous Request Processing(ARP) internal 
     * objects.
     */
    protected void reconfigureAsyncExecution(){
        for(ProcessorTask task : processorTasks) {
            if (task instanceof ProcessorTask) {
                ((ProcessorTask)task)
                    .setEnableAsyncExecution(asyncExecution);
                ((ProcessorTask)task).setAsyncHandler(asyncHandler);  
            }
        }
    }
    
 
    /**
     * Return a {@link ProcessorTask} from the pool. If the pool is empty,
     * create a new instance.
     */
    public ProcessorTask getProcessorTask(){
        ProcessorTask processorTask = null;
        processorTask = processorTasks.poll();
        
        if (processorTask == null){
            processorTask = newProcessorTask(false);
        } 
        
        if ( isMonitoringEnabled() ){
           activeProcessorTasks.offer(processorTask); 
        }        
        return processorTask;
    }
           
    // ---------------------------------------------------Endpoint Lifecycle --/
   
    
    /**
     * initialized the endpoint by creating the <code>ServerScoketChannel</code>
     * and by initializing the server socket.
     */
    public void init() throws IOException, InstantiationException {
        initMonitoringLevel();
        
        initProcessorTask(maxPoolSize);

        initialized = true;     
        if (adapter instanceof GrizzlyAdapter){
            ((GrizzlyAdapter)adapter).start();
        }
    }
    
    
    public void release() {
        synchronized(lock){
            // Wait for the main thread to stop.
            clearTasks();
        }

        if (adapter instanceof GrizzlyAdapter){
            ((GrizzlyAdapter)adapter).destroy();
        }
    }

    /**
     * Returns the {@link Task} object to the pool.
     */
    public void returnTask(Task task){
        // Returns the object to the pool.
        if (task != null) {
            if (task.getType() == Task.PROCESSOR_TASK){
                                
                if ( isMonitoringEnabled() ){
                   activeProcessorTasks.remove(((ProcessorTask)task));
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

    
    // ------------------------------------------------------ Connector Methods


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
     * include {@link FileCache}, {@link RequestInfo}, {@link KeepAliveCountManager}
     * and {@link StatsThreadPool}. The {@link Management#registerComponent}
     * will be invoked during the registration process. 
     */
    public void registerComponents(){
        if( this.domain != null  && jmxManagement != null) {
            try {
                globalRequestProcessorName = new ObjectName(
                    domain + ":type=GlobalRequestProcessor,name=GrizzlyHttpEngine-" + name);
                jmxManagement.registerComponent(globalRequestProcessor,
                                                globalRequestProcessorName,
                                                null);
 
                keepAliveMbeanName = new ObjectName(
                    domain + ":type=KeepAlive,name=GrizzlyHttpEngine-" + name);
                jmxManagement.registerComponent(keepAliveStats,
                                                keepAliveMbeanName,
                                                null);

                connectionQueueMbeanName = new ObjectName(
                    domain + ":type=ConnectionQueue,name=GrizzlyHttpEngine-" + name);
                jmxManagement.registerComponent(threadPoolStat,
                                                connectionQueueMbeanName,
                                                null);
                
                fileCacheMbeanName = new ObjectName(
                    domain + ":type=FileCache,name=GrizzlyHttpEngine-" + name);
                jmxManagement.registerComponent(fileCacheFactory,
                                                fileCacheMbeanName,
                                                null);                
            } catch (Exception ex) {
                logger.log(Level.WARNING,
                            sm.getString("selectorThread.mbeanRegistrationException"),
                            name);
                logger.log(Level.WARNING, "", ex);
            }
        }
    }
    
    
    /**
     * Unregister JMX components supported by this {@link SelectorThread}. This 
     * include {@link FileCache}, {@link RequestInfo}, {@link KeepAliveCountManager}
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
                logger.log(Level.WARNING,
                           sm.getString("selectorThread.mbeanDeregistrationException"),
                           name);
                logger.log(Level.WARNING, "", ex);
            }
            
            Iterator<ProcessorTask> iterator = activeProcessorTasks.iterator();
            ProcessorTask pt = null;
            while (iterator.hasNext()) {
                pt = iterator.next();
                if (pt instanceof ProcessorTask) {
                    ((ProcessorTask)pt).unregisterMonitoring();
                }
            }
            
            iterator = processorTasks.iterator();
            pt = null;
            while (iterator.hasNext()) {
                pt = iterator.next();
                if (pt instanceof ProcessorTask) {
                    ((ProcessorTask)pt).unregisterMonitoring();
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
     * expose Grizzl Http Engine mbeans.
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
            initMonitoringLevel();
        }
        isMonitoringEnabled = true;
        enableThreadPoolStats();

        if (fileCacheFactory != null) {
            fileCacheFactory.setIsMonitoringEnabled(isMonitoringEnabled);
        }
    }
    
    
    /**
     * Disable gathering of monitoring data. 
     */
    public void disableMonitoring(){
        disableThreadPoolStats();
        if (fileCacheFactory != null) {
            fileCacheFactory.setIsMonitoringEnabled(isMonitoringEnabled);
        }
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
     * Initialize the {@link ThreadPoolStat} instance.
     */
    protected void initMonitoringLevel() {
        if (threadPoolStat != null) return;
        threadPoolStat = new ThreadPoolStatistic(name);
        threadPoolStat.setThreadPool(threadPool);
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
    
    //------------------------------------------------- FileCache config -----/

    
    /**
     * Is the fileCache enabled.
     */
    public boolean isFileCacheEnabled(){
        return isFileCacheEnabled;
    }

    /**
     * Is the file caching mechanism enabled.
     */
    public void setFileCacheEnabled(boolean isFileCacheEnabled){
        this.isFileCacheEnabled = isFileCacheEnabled;
    }

    public FileCacheFactory getFileCacheFactory() {
        return fileCacheFactory;
    }

    public void setFileCacheFactory(FileCacheFactory fileCacheFactory) {
        this.fileCacheFactory = fileCacheFactory;
    }

    // ---------------------------- Async-------------------------------//
    
    /**
     * Enable the {@link AsyncHandler} used when asynchronous
     */
    public void setEnableAsyncExecution(boolean asyncExecution){
        this.asyncExecution = asyncExecution;     
        reconfigureAsyncExecution();
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
    public static void setWebAppRootPath(String rf){
        rootFolder = rf;
    }
    
    
    /**
     * Return the folder's root where application are deployed.
     */
    public static String getWebAppRootPath(){
        return rootFolder;
    }
    

    // ------------------------------------------------------ Debug ---------//
    
        
    /**
     * Display the Grizzly configuration parameters.
     */
    private void displayConfiguration(){
       if (displayConfiguration){
            logger.log(Level.INFO,
                    "\n Grizzly configuration"
                    + "\n\t name"
                    + name
                    + "\n\t maxHttpHeaderSize: " 
                    + maxHttpHeaderSize
                    + "\n\t maxKeepAliveRequests: "
                    + maxKeepAliveRequests
                    + "\n\t keepAliveTimeoutInSeconds: "
                    + keepAliveTimeoutInSeconds
                    + "\n\t Static File Cache enabled: "                        
                    + isFileCacheEnabled    
                    + "\n\t Static resources directory: "                        
                    + new File(rootFolder).getAbsolutePath()                        
                    + "\n\t Adapter : "                        
                    + (adapter == null ? null : adapter.getClass().getName() )      
                    + "\n\t Asynchronous Request Processing enabled: " 
                    + asyncExecution); 
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
    
 
   // ------------------------------------------------------ Compression ---//


    protected void configureCompression(ProcessorTask processorTask){
        processorTask.addNoCompressionUserAgent(noCompressionUserAgents);
        parseComressableMimeTypes();
        processorTask.setCompressableMimeTypes(parsedCompressableMimeTypes);
        processorTask.setCompressionMinSize(compressionMinSize);
        processorTask.setCompression(compression);
        processorTask.addRestrictedUserAgent(restrictedUserAgents);
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

    // ------------------------------------------------------------------- //
    public void setDisplayConfiguration(boolean displayConfiguration) {
        this.displayConfiguration = displayConfiguration;
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

    public LinkedTransferQueue<ProcessorTask> getActiveProcessorTasks() {
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

    public int getMaxPostSize() {
        return maxPostSize;
    }

    
    public void setMaxPostSize(int maxPostSize) {
        this.maxPostSize = maxPostSize;
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
     * Set the maximum time, in milliseconds, a {@link WrokerThread} processing 
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
    
    // --------------------------------------------------------------------- //
}
