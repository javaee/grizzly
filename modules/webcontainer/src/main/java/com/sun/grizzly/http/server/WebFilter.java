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
package com.sun.grizzly.http.server;

import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.File;
import javax.management.ObjectName;
import javax.management.MBeanServer;
import javax.management.MBeanRegistration;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.filterchain.Filter;
import com.sun.grizzly.filterchain.FilterAdapter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.ssl.SSLFilter;
import com.sun.grizzly.ssl.SSLStreamReader;
import com.sun.grizzly.ssl.SSLStreamWriter;
import com.sun.grizzly.threadpool.ExtendedThreadPool;
import com.sun.grizzly.utils.LinkedTransferQueue;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.RequestGroupInfo;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.util.res.StringManager;



/**
 * The WebFilter is the extry point when writing Web application build on
 * Grizzly. You can create a WebFilter by simply doing:
 * <pre><code>
 *
 *      Transport transport = TransportFactory.getInstance().createTCPTransport();

        WebFilterConfig webConfig = new WebFilterConfig();
        webConfig.setAdapter(adapter);
        webConfig.setDisplayConfiguration(true);

        WebFilter webFilter = new WebFilter("MyHtttpServer", webConfig);
        webFilter.enableMonitoring();

        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(webFilter);
        try {
            webFilter.initialize();
            transport.bind(PORT);
            transport.start();
        } catch (Exception ex) {
 *          ...
 *      }
 *
 *
 * </code></pre>
 * 
 */
public class WebFilter<T extends WebFilterConfig> extends FilterAdapter
        implements MBeanRegistration {
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

    protected T config;
    
    protected WebFilterJMXManager jmxManager;
    
    // ------------------------------------------------- FileCache support --//
    /**
     * Keep-alive stats
     */
    private KeepAliveStats keepAliveStats = null;

    private static Attribute<Integer> keepAliveCounterAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            "connection-keepalive-counter", 0);

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
        this(name, (T) new WebFilterConfig());
    }

    /**
     * Create the {@link WebFilter} with the specific name.
     */
    public WebFilter(String name, T config) {
        this.name = name;
        this.config = config;
        jmxManager = new WebFilterJMXManager(this);
    }


    @Override
    public NextAction handleRead(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        boolean keepAlive = false;

        ProcessorTask processorTask = getProcessorTask(ctx);

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

        configureProcessorTask(processorTask, ctx);

        try {
            keepAlive = processorTask.process(ctx.getStreamReader(),
                    ctx.getStreamWriter());
        } catch (Throwable ex) {
            logger.log(Level.INFO, "ProcessorTask exception", ex);
            keepAlive = false;
        }

        boolean isSuspend = processorTask.getRequest().getResponse().isSuspended();
        if (isSuspend) {
            ctx.suspend();
            return ctx.getSuspendAction();
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
            FilterChainContext context) {
        processorTask.setConnection(context.getConnection());
        processorTask.setHandler(config.getInterceptor());
        processorTask.setAdapter(config.getAdapter());
        processorTask.setFilterChainContext(context);
        
        // If current StreamReader is SSL
        if (context.getStreamReader() instanceof SSLStreamReader) {
            // Find SSLFilter in executed filter list
            for (Filter filter : context.getExecutedFilters()) {
                if (filter instanceof SSLFilter) {
                    SSLFilter sslFilter = (SSLFilter) filter;
                    processorTask.setSSLSupport(sslFilter.createSSLSupport(
                            (SSLStreamReader) context.getStreamReader(),
                            (SSLStreamWriter) context.getStreamWriter()));
                    break;
                }
            }
        }
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public T getConfig() {
        return config;
    }

    public void setConfig(T config) {
        this.config = config;
    }

    public WebFilterJMXManager getJmxManager() {
        return jmxManager;
    }

    public void setJmxManager(WebFilterJMXManager jmxManager) {
        this.jmxManager = jmxManager;
    }


    public ThreadPoolStatistic getThreadPoolStatistic() {
        return threadPoolStat;
    }

    /**
     * Injects {@link ThreadPoolStatistic} into every
     * {@link ExecutorService}, for monitoring purposes.
     */
    protected void enableThreadPoolStats(){   
        threadPoolStat.setThreadPool(config.getWorkerThreadPool());
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
    protected void initProcessorTasks(int size){
        for (int i=0; i < size; i++){           
            processorTasks.offer(newProcessorTask(false));
        }
    }  


    /**
     * Initialize {@link ProcessorTask}
     */
    protected void rampUpProcessorTasks(){
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
        return initializeProcessorTask(task);
    }
    
    
    protected ProcessorTask initializeProcessorTask(ProcessorTask task) {
        task.setAdapter(config.getAdapter());
        task.setWebFilter(this);
        return config.initializeProcessorTask(task);
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
    public void initialize() throws IOException {
        rampUpProcessorTasks();
        registerComponents();

        displayConfiguration();

        initMonitoringLevel();
        
        int processorTasksToInit;

        ExecutorService workerThreadPool = config.getWorkerThreadPool();
        if (workerThreadPool instanceof ExtendedThreadPool) {
            processorTasksToInit =
                    ((ExtendedThreadPool) workerThreadPool).getMaximumPoolSize();
        } else {
            processorTasksToInit = 5;
        }
        
        initProcessorTasks(processorTasksToInit);

        Adapter adapter = config.getAdapter();
        if (adapter instanceof GrizzlyAdapter) {
            ((GrizzlyAdapter) adapter).start();
        }
    }
    
    
    public void release() {
        processorTasks.clear();

        Adapter adapter = config.getAdapter();
        
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
    @Override
    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        jmxManager.setOname(name);
        jmxManager.setMserver(server);
        jmxManager.setDomain(name.getDomain());
        return name;
    }

    
    @Override
    public void postRegister(Boolean registrationDone) {
        // Do nothing
    }

    @Override
    public void preDeregister() throws Exception {
        // Do nothing
    }

    @Override
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

        FileCache fileCache = config.getFileCache();
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
        
        FileCache fileCache = config.getFileCache();
        if (fileCache != null) {
            fileCache.setMonitoringEnabled(false);
        }
    }

    public KeepAliveStats getKeepAliveStats() {
        return keepAliveStats;
    }



    /**
     * Register JMX components supported by this {@link WebFilter}. This
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
                jmxManager.registerComponent(config.getFileCache(), fileCacheMbeanName, null);
            } catch (Exception ex) {
                logger.log(Level.WARNING,
                            sm.getString("WebFilter.mbeanRegistrationException"),
                            name);
                logger.log(Level.WARNING, "", ex);
            }
        }
    }

    /**
     * Unregister JMX components supported by this {@link WebFilter}. This
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
                           sm.getString("WebFilter.mbeanDeregistrationException"),
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
        threadPoolStat = new ThreadPoolStatistic(name,
                config.getScheduledThreadPool());
        threadPoolStat.setThreadPool(config.getWorkerThreadPool());
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
    
        
    /**
     * Display the Grizzly configuration parameters.
     */
    protected void displayConfiguration() {
        if (config.isDisplayConfiguration()) {
            FileCache fileCache = config.getFileCache();
            Adapter adapter = config.getAdapter();

            logger.log(Level.INFO,
                    "\n Grizzly WebFilter configuration"
                    + "\n\t name: "
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
