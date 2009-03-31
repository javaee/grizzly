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

import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.File;
import java.io.InputStream;
import javax.management.ObjectName;
import javax.management.MBeanServer;
import javax.management.MBeanRegistration;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.FilterAdapter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.threadpool.ExtendedThreadPool;
import org.glassfish.grizzly.util.LinkedTransferQueue;
import org.glassfish.grizzly.web.container.Adapter;
import org.glassfish.grizzly.web.container.RequestGroupInfo;
import org.glassfish.grizzly.web.container.http11.GrizzlyAdapter;
import org.glassfish.grizzly.web.container.util.Interceptor;
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
public class WebFilter extends FilterAdapter implements MBeanRegistration {
    /**
     * The logger used by the grizzly classes.
     */
    protected static Logger logger = Grizzly.logger;
    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

            
    protected String name;

    protected WebFilterConfig config;
    
    protected WebFilterJMXManager jmxManager;
    
    // ------------------------------------------------- FileCache support --//

    /**
     * The FileCache associated with this Selector
     */
    protected FileCache fileCache;

    /**
     * Associated adapter.
     */
    protected Adapter adapter = null;

    protected Interceptor interceptor;

    protected MemoryManager memoryManager;

    protected ExecutorService threadPool;

    /**
     * Keep-alive stats
     */
    private KeepAliveStats keepAliveStats = null;

    private static Attribute<Integer> keepAliveCounterAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            "connection-keepalive-counter", 0);

    private static Attribute<Boolean> isSuspendAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            "is-suspend-connection", false);

    /**
     * Placeholder for {@link ExecutorService} statistic.
     */
    protected ThreadPoolStatistic threadPoolStat;

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
    

    //--------------- JMX monitorable objects -----------------------------
    /**
     * Monitoring object used to store information.
     */
    protected RequestGroupInfo globalRequestProcessor= new RequestGroupInfo();

    protected ObjectName globalRequestProcessorName;
    protected ObjectName processorWorkerThreadName;

    private ObjectName keepAliveMbeanName;
    private ObjectName connectionQueueMbeanName;
    private ObjectName fileCacheMbeanName;

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
        this(name, new WebFilterConfig());
    }

    /**
     * Create the {@link WebFilter} with the specific name.
     */
    public WebFilter(String name, WebFilterConfig config) {
        this(name, config,
                TransportFactory.getInstance().getDefaultThreadPool());
    }

    /**
     * Create the {@link WebFilter} with the specific name.
     */
    public WebFilter(String name, WebFilterConfig config,
            ExecutorService threadPool) {
        this(name, config, threadPool,
                TransportFactory.getInstance().getDefaultMemoryManager());
    }
    /**
     * Create the {@link WebFilter} with the specific name.
     */
    public WebFilter(String name, WebFilterConfig config,
            ExecutorService threadPool, MemoryManager memoryManager) {
        this.name = name;
        this.config = config;
        this.threadPool = threadPool;
        this.memoryManager = memoryManager;
        
        jmxManager = new WebFilterJMXManager(this);

        rampUpProcessorTask();
        registerComponents();

        displayConfiguration();
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        HttpWorkerThread workerThread =
                ((HttpWorkerThread) Thread.currentThread());

        boolean keepAlive = false;

        ProcessorTask processorTask = workerThread.getProcessorTask();
        if (processorTask == null) {
            processorTask = getProcessorTask(ctx);
            workerThread.setProcessorTask(processorTask);
        }

        Connection connection = ctx.getConnection();
        Integer keepAliveCounter = keepAliveCounterAttr.get(connection);
        keepAliveCounterAttr.set(connection, ++keepAliveCounter);

        if (keepAliveCounter > config.getMaxKeepAliveRequests()) {
            if (keepAliveStats != null) {
                keepAliveStats.incrementCountRefusals();
            }

            processorTask.setDropConnection(true);
        } else {
            processorTask.setDropConnection(false);
        }

        if (keepAliveStats != null) {
            keepAliveStats.incrementCountHits();
        }

        configureProcessorTask(processorTask, ctx, workerThread,
                interceptor);

        InputStream inputStream = (InputStream) ctx.getStreamReader();
        try {
            keepAlive = processorTask.process(inputStream, null);
        } catch (Throwable ex) {
            logger.log(Level.INFO, "ProcessorTask exception", ex);
            keepAlive = false;
        }

        Boolean isSuspend = isSuspendAttr.get(connection);
        if (isSuspend){
            // Detatch anything associated with the Thread.
            workerThread.setStreamReader(null);
            workerThread.setProcessorTask(null);

            return nextAction;
        }

        if (processorTask != null){
            processorTask.recycle();
        }

        if (!keepAlive) {
            ctx.getConnection().close();
        }

        // Last filter.
        return nextAction;
    }

    @Override
    public NextAction handleAccept(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        if (keepAliveStats != null) {
            keepAliveStats.incrementCountConnections();
        }

        getRequestGroupInfo().increaseCountOpenConnections();
        if (threadPoolStat != null) {
            threadPoolStat.incrementTotalAcceptCount();
            threadPoolStat.incrementOpenConnectionsCount(ctx.getConnection());
        }

        return nextAction;
    }

    @Override
    public NextAction handleClose(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        if (keepAliveStats != null) {
            keepAliveStats.decrementCountConnections();
        }
        
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

    public WebFilterConfig getConfig() {
        return config;
    }

    public void setConfig(WebFilterConfig config) {
        this.config = config;
    }

    public WebFilterJMXManager getJmxManager() {
        return jmxManager;
    }

    public void setJmxManager(WebFilterJMXManager jmxManager) {
        this.jmxManager = jmxManager;
    }


    //------------------------------------------------- FileCache config -----/

    public FileCache getFileCache() {
        return fileCache;
    }

    public void setFileCache(FileCache fileCache) {
        this.fileCache = fileCache;
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

    public Interceptor getInterceptor() {
        return interceptor;
    }

    public void setInterceptor(Interceptor interceptor) {
        this.interceptor = interceptor;
    }

    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    public void setMemoryManager(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    /**
     * Injects {@link ThreadPoolStatistic} into every
     * {@link ExecutorService}, for monitoring purposes.
     */
    protected void enableThreadPoolStats(){   
        threadPoolStat.setThreadPool(threadPool);
        threadPoolStat.start();

        keepAliveStats = new KeepAliveStats();
    }
    

    /**
     * Removes {@link ThreadPoolStatistic} from every
     * {@link ExecutorService}, when monitoring has been turned off.
     */
    protected void disableThreadPoolStats(){
        threadPoolStat.stop();
        threadPoolStat.setThreadPool(null);
        
        keepAliveStats = null;
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
                new ProcessorTask(initialize, config.isBufferResponse());
        return configureProcessorTask(task);       
    }
    
    
    protected ProcessorTask configureProcessorTask(ProcessorTask task) {
        task.setAdapter(adapter);
        task.setWebFilter(this);
        return config.configureProcessorTask(task);
    }

 
    /**
     * Return a {@link ProcessorTask} from the pool. If the pool is empty,
     * create a new instance.
     */
    public ProcessorTask getProcessorTask(FilterChainContext ctx) {
        ProcessorTask processorTask = null;
        processorTask = processorTasks.poll();
        
        if (processorTask == null){
            processorTask = newProcessorTask(false);
        } 

        processorTask.setThreadPool(
                ctx.getConnection().getTransport().getWorkerThreadPool());
        if (config.isMonitoringEnabled()){
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
        
        int processorTasksToInit;
        if (threadPool instanceof ExtendedThreadPool) {
            processorTasksToInit =
                    ((ExtendedThreadPool) threadPool).getMaximumPoolSize();
        } else {
            processorTasksToInit = 5;
        }
        
        initProcessorTask(processorTasksToInit);

        if (adapter instanceof GrizzlyAdapter){
            ((GrizzlyAdapter)adapter).start();
        }
    }
    
    
    public void release() {
        processorTasks.clear();

        if (adapter instanceof GrizzlyAdapter) {
            ((GrizzlyAdapter) adapter).destroy();
        }

        unregisterComponents();
    }

    /**
     * Returns the {@link Task} object to the pool.
     */
    public void returnTask(Task task){
        // Returns the object to the pool.
        if (task != null) {
            if (config.isMonitoringEnabled()) {
                activeProcessorTasks.remove(((ProcessorTask) task));
            }

            processorTasks.offer((ProcessorTask) task);
        }
    }
    
    // ------------------------------- JMX and Monnitoring support --------//
    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        jmxManager.setOname(name);
        jmxManager.setMserver(server);
        jmxManager.setDomain(name.getDomain());
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
     * Enable gathering of monitoring data.
     */
    public void enableMonitoring(){
        if (threadPoolStat == null){
            initMonitoringLevel();
        }
        config.setMonitoringEnabled(true);
        enableThreadPoolStats();

        if (fileCache != null) {
            fileCache.setMonitoringEnabled(true);
        }
    }
        
    /**
     * Disable gathering of monitoring data. 
     */
    public void disableMonitoring(){
        disableThreadPoolStats();
        config.setMonitoringEnabled(false);
        if (fileCache != null) {
            fileCache.setMonitoringEnabled(false);
        }
    }

    public KeepAliveStats getKeepAliveStats() {
        return keepAliveStats;
    }



    /**
     * Register JMX components supported by this {@link SelectorThread}. This
     * include {@link FileCache}, {@link RequestInfo}, {@link KeepAliveCountManager}
     * and {@link StatsThreadPool}. The {@link Management#registerComponent}
     * will be invoked during the registration process.
     */
    public void registerComponents(){
        if (jmxManager != null) {
            String domain = jmxManager.getDomain();
            try {
                globalRequestProcessorName = new ObjectName(domain +
                        ":type=GlobalRequestProcessor,name=GrizzlyHttpEngine-" +
                        name);
                jmxManager.registerComponent(globalRequestProcessor, globalRequestProcessorName, null);

                keepAliveMbeanName = new ObjectName(
                    domain + ":type=KeepAlive,name=GrizzlyHttpEngine-" + name);
                jmxManager.registerComponent(keepAliveStats,
                        keepAliveMbeanName, null);

                connectionQueueMbeanName = new ObjectName(domain +
                        ":type=ConnectionQueue,name=GrizzlyHttpEngine-" + name);
                jmxManager.registerComponent(threadPoolStat,
                        connectionQueueMbeanName, null);

                fileCacheMbeanName = new ObjectName(
                    domain + ":type=FileCache,name=GrizzlyHttpEngine-" + name);
                jmxManager.registerComponent(fileCache, fileCacheMbeanName, null);
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
        if (jmxManager != null) {
            try {
                if (globalRequestProcessorName != null) {
                    jmxManager.unregisterComponent(globalRequestProcessorName);
                }
                if (keepAliveMbeanName != null) {
                    jmxManager.unregisterComponent(keepAliveMbeanName);
                }
                if (connectionQueueMbeanName != null) {
                    jmxManager.unregisterComponent(connectionQueueMbeanName);
                }
                if (fileCacheMbeanName != null) {
                    jmxManager.unregisterComponent(fileCacheMbeanName);
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


    public RequestGroupInfo getRequestGroupInfo() {
        return globalRequestProcessor;
    }

    public void setRequestProcessor(RequestGroupInfo requestProcessor) {
        this.globalRequestProcessor = requestProcessor;
    }

    /**
     * Initialize the {@link ThreadPoolStat} instance.
     */
    protected void initMonitoringLevel() {
        if (threadPoolStat != null) return;
        threadPoolStat = new ThreadPoolStatistic(name);
        threadPoolStat.setThreadPool(threadPool);
    }
     
    /**
     * Set the logger used by this instance.
     */
    public static void setLogger(Logger l) {
        if (l != null)
            logger = l;
    }

    
    /**
     * Return the logger used by the Grizzly classes.
     */
    public static Logger logger() {
        return logger;
    }
    
    // ------------------------------------------------------ Debug ---------//
    
        
    /**
     * Display the Grizzly configuration parameters.
     */
    protected void displayConfiguration(){
       if (config.isDisplayConfiguration()){
            logger.log(Level.INFO,
                    "\n Grizzly configuration"
                    + "\n\t name"
                    + name
                    + "\n\t maxHttpHeaderSize: " 
                    + config.getMaxHttpHeaderSize()
                    + "\n\t maxKeepAliveRequests: "
                    + config.getMaxKeepAliveRequests()
                    + "\n\t keepAliveTimeoutInSeconds: "
                    + config.getKeepAliveTimeoutInSeconds()
                    + "\n\t Static File Cache enabled: "                        
                    + (fileCache != null && fileCache.isEnabled())
                    + "\n\t Static resources directory: "                        
                    + new File(config.getRootFolder()).getAbsolutePath()
                    + "\n\t Adapter : "                        
                    + (adapter == null ? null : adapter.getClass().getName())
                    + "\n\t Processing mode: synchronous");
        }
    }

    
    public LinkedTransferQueue<ProcessorTask> getActiveProcessorTasks() {
        return activeProcessorTasks;
    }
}
