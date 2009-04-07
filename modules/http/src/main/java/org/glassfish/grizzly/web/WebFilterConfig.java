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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.threadpool.WorkerThread;
import org.glassfish.grizzly.web.container.Adapter;
import org.glassfish.grizzly.web.container.util.Interceptor;
import org.glassfish.grizzly.web.container.util.IntrospectionUtils;

public class WebFilterConfig {
    public final static String SERVER_NAME =
            System.getProperty("product.name") != null
                ? System.getProperty("product.name") : "grizzly";


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

    protected ExecutorService workerThreadPool;

    protected ScheduledExecutorService scheduledThreadPool;


    // ------------------------------------------- HTTP connection setting --/
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
     * Is monitoring already started.
     */
    protected boolean isMonitoringEnabled = false;


    /**
     * Buffer the response until the buffer is full.
     */
    protected boolean isBufferResponse = false;


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
     * The timeout used by the thread when processing a request.
     */
    protected int transactionTimeout = Constants.DEFAULT_TIMEOUT;


    /**
     * Use chunking.
     */
    private boolean useChunking = true;


    /**
     * Is NIO logging enabled
     */
    private boolean enableNioLogging = false;


    /**
     * If <tt>true</tt>, display the NIO configuration information.
     */
    protected boolean displayConfiguration = false;


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
     * The Classloader used to load instance of StreamAlgorithm.
     */
    private ClassLoader classLoader;

    /**
     * The root folder where application are deployed
     */
    protected String rootFolder = "";

    /**
     * HTTP server name
     */
    protected String serverName = SERVER_NAME;

    // -----------------------------------------  Multi-Selector supports --//

    /**
     * Flag to disable setting a different time-out on uploads.
     */
    protected boolean disableUploadTimeout = true;


    /**
     * Maximum timeout on uploads. 5 minutes as in Apache HTTPD server.
     */
    protected int uploadTimeout = 30000;

    public WebFilterConfig() {
        memoryManager =
                TransportFactory.getInstance().getDefaultMemoryManager();
        workerThreadPool =
                TransportFactory.getInstance().getDefaultWorkerThreadPool();
        scheduledThreadPool =
                TransportFactory.getInstance().getDefaultScheduledThreadPool();
        
        this.properties = new Properties();
    }

    // ---------------- Configure ProcessorTask -----------------------
    public ProcessorTask initializeProcessorTask(ProcessorTask task) {
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

        initializeCompression(task);

        return task;
    }

    public void initializeCompression(ProcessorTask processorTask){
        processorTask.addNoCompressionUserAgent(noCompressionUserAgents);
        parseComressableMimeTypes();
        processorTask.setCompressableMimeTypes(parsedCompressableMimeTypes);
        processorTask.setCompressionMinSize(compressionMinSize);
        processorTask.setCompression(compression);
        processorTask.addRestrictedUserAgent(restrictedUserAgents);
    }

    public Adapter getAdapter() {
        return adapter;
    }

    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }

    public FileCache getFileCache() {
        return fileCache;
    }

    public void setFileCache(FileCache fileCache) {
        this.fileCache = fileCache;
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

    public ScheduledExecutorService getScheduledThreadPool() {
        return scheduledThreadPool;
    }

    public void setScheduledThreadPool(ScheduledExecutorService scheduledThreadPool) {
        this.scheduledThreadPool = scheduledThreadPool;
    }

    public ExecutorService getWorkerThreadPool() {
        return workerThreadPool;
    }

    public void setWorkerThreadPool(ExecutorService workerThreadPool) {
        this.workerThreadPool = workerThreadPool;
    }


    // ------------- Server name ---------------------//

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    // ------------- Root folder ---------------------//

    public String getRootFolder() {
        return rootFolder;
    }

    public void setRootFolder(String rootFolder) {
        this.rootFolder = rootFolder;
    }


    private Properties properties;

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
     * Returns <tt>true</tt> if monitoring has been enabled,
     * <tt>false</tt> otherwise.
     */
    public boolean isMonitoringEnabled() {
        return isMonitoringEnabled;
    }

    public void setMonitoringEnabled(boolean isMonitoringEnabled) {
        this.isMonitoringEnabled = isMonitoringEnabled;
    }


    public Properties getProperties() {
        return properties;
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
    public void setRequestBufferSize(int requestBufferSize){
        this.requestBufferSize = requestBufferSize;
    }


    /**
     * Return the request input buffer size
     */
    public int getRequestBufferSize(){
        return requestBufferSize;
    }


    // ------------------------------------------------------------------- //


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


    /**
     * Return <tt>true</tt> if the reponse is buffered.
     */
    public boolean isBufferResponse() {
        return isBufferResponse;
    }


    /**
     * <tt>true</tt>if the reponse willk be buffered.
     */
    public void setBufferResponse(boolean isBufferResponse) {
        this.isBufferResponse = isBufferResponse;
    }


   // ------------------------------------------------------ Compression ---//

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

    public boolean isDisplayConfiguration() {
        return displayConfiguration;
    }

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
    public boolean isDisableUploadTimeout() {
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

    public boolean isEnableNioLogging() {
        return enableNioLogging;
    }

    public void setEnableNioLogging(boolean enableNioLogging) {
        this.enableNioLogging = enableNioLogging;
    }
    // --------------------------------------------------------------------- //


    /**
     * Use reflection to configure Grizzly setter.
     */
    private void configureProperties() {
        Iterator keys = properties.keySet().iterator();
        while( keys.hasNext() ) {
            String name = (String)keys.next();
            String value = properties.get(name).toString();
            IntrospectionUtils.setProperty(this, name, value);
        }
    }
}
