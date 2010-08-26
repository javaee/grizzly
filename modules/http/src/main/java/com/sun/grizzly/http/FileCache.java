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

import com.sun.grizzly.tcp.ActionCode;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.util.OutputWriter;
import com.sun.grizzly.util.WorkerThreadImpl;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.sun.grizzly.util.http.MimeHeaders;
import java.util.Queue;
import java.util.StringTokenizer;


/**
 * This class implements a file caching mechanism used to cache static resources. 
 *
 * @author Jeanfrancois Arcand
 * @author Scott Oaks
 */
public class FileCache{
    
    public final static String DEFAULT_SERVLET_NAME = "default";
   
    
    /**
     * A {@link ByteBuffer} cache of static pages.
     */   
    private final ConcurrentHashMap<String,FileCacheEntry> fileCache = 
            new ConcurrentHashMap<String,FileCacheEntry>();
    
    
    /**
     * A dummy instance of {@link ByteBuffer}
     */
    protected final static ByteBuffer nullByteBuffer = ByteBuffer.allocate(0);
  
    
    /**
     * A connection: close of {@link ByteBuffer}
     */
    protected final static ByteBuffer connectionCloseBB = 
            ByteBuffer.wrap("Connection: close\r\n\r\n".getBytes());

    /**
     * A connection: keep-alive of {@link ByteBuffer}
     */
    protected final static ByteBuffer connectionKaBB = 
            ByteBuffer.wrap("Connection: keep-alive\r\n\r\n".getBytes());
    
    
    /**
     * HTTP end line.
     */
    private final static String NEWLINE = "\r\n";


    /**
     * HTTP OK header
     */
    public final static String OK = "HTTP/1.1 200 OK" + NEWLINE;    

    /**
     * The port associated with this cache.
     */
    private int port = 8080;


    private InetAddress address;
    
    
    /**
     * Scheduled Thread that clean the cache every XX seconds.
     */
    private ScheduledThreadPoolExecutor cacheResourcesThread
        = new ScheduledThreadPoolExecutor(1,new ThreadFactory(){
                public Thread newThread(Runnable r) {
                    return new WorkerThreadImpl(new ThreadGroup("Grizzly"),r);
                }
            }); 
    
    
    /**
     * FileCacheEntry cache
     */
    private Queue<FileCacheEntry> cacheManager;

    
    /**
     * Timeout before remove the static resource from the cache.
     */
    private int secondsMaxAge = -1;
    
    
    /**
     * The maximum entries in the {@link FileCache}
     */
    private int maxCacheEntries = 1024;
    
 
    /**
     * The maximum size of a cached resources.
     */
    private long minEntrySize = Long.MIN_VALUE;
            
               
    /**
     * The maximum size of a cached resources.
     */
    private long maxEntrySize = Long.MAX_VALUE;
    
    
    /**
     * The maximum memory mapped bytes
     */
    private long maxLargeFileCacheSize = Long.MAX_VALUE;
            
    
    /**
     * The maximum cached bytes
     */
    private long maxSmallFileCacheSize = 1048576;
    
    
    /**
     * The current cache size in bytes
     */
    private long mappedMemorySize = 0;
    
    
    /**
     * The current cache size in bytes
     */
    private long heapSize = 0;  
    
            
    /**
     * Is the file cache enabled.
     */
    private boolean isEnabled = true;
        
    
    /**
     * Is the large FileCache enabled.
     */
    private boolean isLargeFileCacheEnabled = true;   
    
    
    /**
     * Is monitoring enabled.
     */
    private boolean isMonitoringEnabled = false;
    
    
    /**
     * The number of current open cache entries
     */
    private int openCacheEntries = 0;
   
       
    /**
     * The number of max current open cache entries
     */
    private int maxOpenCacheEntries = 0;
    
    
    /**
     * Max heap space used for cache
     */
    private long maxHeapCacheSize = 0;   
    
    
    /**
     * Max mapped memory used for cache
     */
    private long maxMappedMemory = 0;   
    
    
    /**
     * Number of cache lookup hits
     */
    private int countHits = 0;
    
    
    /**
     * Number of cache lookup misses
     */
    private int countMisses = 0;
    
    
    /**
     * Number of hits on cached file info
     */
    private int countCacheHits;
    
    
    /**
     * Number of misses on cached file info
     */
    private int countCacheMisses;
        
    
    /**
     * Number of hits on cached file info
     */
    private int countMappedHits;
    
    
    /**
     * Number of misses on cached file info
     */
    private int countMappedMisses;

    
    /**
     * The Header ByteBuffer default size.
     */
    private int headerBBSize = 4096;

    /**
     * Status code (304) indicating that a conditional GET operation
     * found that the resource was available and not modified.
     */
    public static final int SC_NOT_MODIFIED = 304;

    /**
     * Status code (412) indicating that the precondition given in one
     * or more of the request-header fields evaluated to false when it
     * was tested on the server.
     */
    public static final int SC_PRECONDITION_FAILED = 412;

    // ---------------------------------------------------- Methods ----------//
             
            
    /**
     * Add a resource to the cache. Currently, only static resources served
     * by the DefaultServlet can be cached.
     */
    public synchronized void add(String mappedServlet,
                                 String baseDir,
                                 String requestURI,
                                 String host,
                                 MimeHeaders headers,
                                 boolean xPoweredBy){
        
        if (requestURI == null || fileCache.get(requestURI) != null) return;
        
        // cache is full.
        if ( fileCache.size() > maxCacheEntries) {
            return;
        }
        
        if ( mappedServlet.equals(DEFAULT_SERVLET_NAME) ){                                     
            File file = new File(baseDir + requestURI);
            ByteBuffer bb = mapFile(file);

            SelectorThread selectorThread =
                    SelectorThread.getSelector(address, port);
            String root = selectorThread.getWebAppRootPath();
            if (bb == null && !root.equals(baseDir)){
                Adapter a = selectorThread.getAdapter();
                Queue<String> rootFolders;
                if (a instanceof StaticResourcesAdapter){
                    rootFolders = ((StaticResourcesAdapter)a).getRootFolders();
                    for (String s:rootFolders){
                        file = new File(s + requestURI);
                        if (file.exists()){
                            break;
                        }
                    }
                } else {
                    // Backward Compatibility with < 1.9.18
                    file = new File(root + requestURI);
                }
                bb = mapFile(file);
            }

            // Always put the answer into the map. If it's null, then
            // we know that it doesn't fit into the cache, so there's no
            // reason to go through this code again.
            if (bb == null)
                bb = nullByteBuffer;
            
            FileCacheEntry entry = cacheManager.poll();
            if (entry == null){
                entry = new FileCacheEntry();
            }
            entry.bb = bb;
            entry.requestURI = requestURI;
            
            if (bb != nullByteBuffer){
                String ifModif = headers.getHeader("Last-Modified") ;
                entry.lastModified = (ifModif == null ?
                    String.valueOf(file.lastModified()):ifModif);
                entry.contentType = headers.getHeader("Content-type");
                entry.xPoweredBy = xPoweredBy;
                entry.isInHeap = (file.length() < minEntrySize);
                entry.date = headers.getHeader("Date");
                entry.Etag = headers.getHeader("Etag");
                entry.contentLength = headers.getHeader("Content-Length");
                entry.host = host;

                incOpenCacheEntries();
                if ( isMonitoringEnabled ) {

                    if ( openCacheEntries > maxOpenCacheEntries){
                        maxOpenCacheEntries = openCacheEntries;
                    }

                    if ( heapSize > maxHeapCacheSize){
                        maxHeapCacheSize = heapSize;
                    }

                    if ( mappedMemorySize > maxMappedMemory){
                        maxMappedMemory = mappedMemorySize;
                    }
                }

                if ( secondsMaxAge > 0 ) {
                    entry.future = cacheResourcesThread.schedule(entry, 
                                                secondsMaxAge, TimeUnit.SECONDS);
                }
            }
            fileCache.put(requestURI,entry);
        }            
    }
       
    
    /**
     * Map the file to a {@link ByteBuffer}
     * @return the {@link ByteBuffer}
     */
    private ByteBuffer mapFile(File file){
        FileChannel fileChannel = null;
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(file);
            fileChannel = stream.getChannel();
             
            long size = fileChannel.size();
            
            if ( size > maxEntrySize){
                return null;
            }

            if ( size > minEntrySize )
                addMappedMemorySize(size);
            else
                addHeapSize(size);
 
            // Cache full
            if ( mappedMemorySize > maxLargeFileCacheSize ) {
                subMappedMemorySize(size);
                return null;
            } else  if ( heapSize > maxSmallFileCacheSize ) {
                subHeapSize(size);
                return null;
            }        
            
            ByteBuffer bb = 
                    fileChannel.map(FileChannel.MapMode.READ_ONLY,0,size);
                                 
            if ( size < minEntrySize) {
                ((MappedByteBuffer)bb).load();
            }
            return bb;
        } catch (IOException ioe) {
            return null;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
            if (fileChannel != null) {
                try {
                    fileChannel.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
        
    
    /**
     * Return <tt>true</tt> if the file is cached.
     */
    protected final FileCacheEntry map(Request request){
        FileCacheEntry entry = null;
        
        if ( !fileCache.isEmpty() ){
            String uri = request.requestURI().toString();
            entry = fileCache.get(uri);

            if (entry != null) {
                String host = request.serverName().toString();
                if (!host.equals(entry.host)) {
                    entry = null;
                }
            }
            
            recalcCacheStatsIfMonitoring(entry);
        } else {
            recalcCacheStatsIfMonitoring(null);
        }
        
        return entry;
    }

    protected void recalcCacheStatsIfMonitoring(final FileCacheEntry entry) {
        if (isMonitoringEnabled) {
            recalcCacheStats(entry);
        }
    }

    protected final void recalcCacheStats(final FileCacheEntry entry) {
        if (entry != null && entry.bb != null && entry.bb != nullByteBuffer) {
            if (entry.isInHeap) {
                countInfoHit();
            } else {
                countContentHit();
            }

            countHit();

        } else {
            countMiss();
        }
    }
      
    
    /**
     * Send the cache.
     */
    public boolean sendCache(Request req){

        if (req.method().toString().equalsIgnoreCase("HEAD")) return false;

        try{
            FileCacheEntry entry = map(req);
            if (entry != null && entry.bb != nullByteBuffer){
                sendCache(req,entry);
                return true;
            }
        } catch (Throwable t){
            t.printStackTrace();    
            // If an unexpected exception occurs, try to serve the page
            // as if it wasn't in a cache.
            SelectorThread.logger()
                .fine("File Cache exception:" + t.getMessage());
        }
        return false;
    }    
     
    
    /**
     * Set the cache manager used by this instance.
     */
    public void setCacheManager(Queue<FileCacheEntry> cacheManager){
        this.cacheManager = cacheManager;
    }   
    
    
    // -------------------------------------------------- Static cache -------/
    
    
    /**
     * Send the cached resource.
     */
    protected void sendCache(Request request, FileCacheEntry entry) throws IOException{
        boolean flushBody = checkIfHeaders(request, entry);
        request.getResponse().setContentType(entry.contentType);
        request.getResponse().setContentLength(Integer.valueOf(entry.contentLength));

        if (flushBody) {
            ByteBuffer sliced = entry.bb.slice();
            ByteBuffer ob = ((SocketChannelOutputBuffer)request.getResponse()
                    .getOutputBuffer()).getOutputByteBuffer();
            int left = ob.remaining();

            // It's better to execute a byte copy than two network operation.
            if (left > sliced.limit()){
                request.getResponse().action(ActionCode.ACTION_COMMIT, null);
                ob.put(sliced);
                ((SocketChannelOutputBuffer)request.getResponse()
                    .getOutputBuffer()).flushBuffer();
            } else {
                request.getResponse().flush();
                OutputWriter.flushChannel(request.getResponse().getChannel(),sliced);
            }
        } else {
            request.getResponse().flush();
        }
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return the network address associated with this cache instance.
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * <p>
     * Associates the specified network address with this cache instance.
     * </p>
     *
     * @param address the network address
     */
    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public final class FileCacheEntry implements Runnable{
        public String host;
        public String requestURI;
        public String lastModified = "";
        public String contentType;
        public ByteBuffer bb;
        public boolean xPoweredBy;
        public boolean isInHeap = false;
        public String date;
        public String Etag;
        public Future future;
        public String contentLength;
        public String keepAlive;
             
        public void run(){                          
            fileCache.remove(requestURI);
            
            if (requestURI == null) return;
            
            if (bb != null) {

                /**
                 * If the position !=0, it means the ByteBuffer has a view
                 * that is still used. If that's the case, wait another 10 seconds
                 * before marking the ByteBuffer for garbage collection
                 */
                if ( bb.position() != 0 ){
                    future = cacheResourcesThread
                                .schedule(this, 10, TimeUnit.SECONDS);
                    return;
                } 

                if ( !isInHeap )
                    subMappedMemorySize(bb.limit());
                else
                    subHeapSize(bb.limit());

                bb = null;
                decOpenCacheEntries();
            }
            
            if ( future != null ) {
                future.cancel(false);
                future = null;
            }
            requestURI = null;
            cacheManager.offer(this);
        }
    }
    
    
    // ---------------------------------------------------- Monitoring --------//
    
    
    /** 
     * Returns flag indicating whether file cache has been enabled
     * @return 1 if file cache has been enabled, 0 otherwise
     */
    public int getFlagEnabled() {
        return (isEnabled ? 1 : 0);
    }
    
    
    /** 
     * Return the maximum age of a valid cache entry
     * @return cache entry maximum age
     */
    public int getSecondsMaxAge() {
        return secondsMaxAge;
    }
    
    
    /** 
     * Return the number of current cache entries.  
     * @return current cache entries
     */
    public long getCountEntries() {
        return fileCache.size();
    }
    
    
    /** 
     * Return the maximum number of cache entries
     * @return maximum cache entries
     */
    public long getMaxEntries() {
        return maxCacheEntries;
    }


    protected void incOpenCacheEntries() {
        openCacheEntries++;
    }

    protected void decOpenCacheEntries() {
        openCacheEntries--;
    }
    
    /** 
     * The number of current open cache entries
     * @return open cache entries
     */
    public long getCountOpenEntries() {
        return openCacheEntries;
    }
    
    
    /** 
     * Return the maximum number of open cache entries
     * @return maximum open cache entries
     */
    public long getMaxOpenEntries() {
       return maxOpenCacheEntries;        
    }
    

    protected void addHeapSize(long size) {
        heapSize += size;
    }

    protected void subHeapSize(long size) {
        heapSize -= size;
    }

    /** 
     * Return the heap space used for cache
     * @return heap size
     */
    public long getSizeHeapCache() {
        return heapSize;
    }
    
    
    /** 
     * Return the maximum heap space used for cache
     * @return maximum heap size
     */
    public long getMaxHeapCacheSize() {
        return maxHeapCacheSize;
    }
    

    protected void addMappedMemorySize(long size) {
        mappedMemorySize += size;
    }

    protected void subMappedMemorySize(long size) {
        mappedMemorySize -= size;
    }
    
    /** 
     * Return the size of Mapped memory used for caching
     * @return Mapped memory size
     */
    public long getSizeMmapCache() {
        return mappedMemorySize;
    }
    
    
    /** 
     * Return the Maximum Memory Map size to be used for caching
     * @return maximum Memory Map size
     */
    public long getMaxMmapCacheSize() {
        return maxMappedMemory;
    }
    
    
    protected void countHit() {
        countHits++;
    }

    /**
     * Return the Number of cache lookup hits
     * @return cache hits
     */
    public long getCountHits() {
        return countHits;
    }
    
    
    protected void countMiss() {
        countMisses++;
    }

    /**
     * Return the Number of cache lookup misses
     * @return cache misses
     */
    public long getCountMisses() {
        return countMisses;
    }
    
    
    protected void countInfoHit() {
        countCacheHits++;
    }

    /**
     * The Number of hits on cached file info
     * @return hits on cached file info
     */
    public long getCountInfoHits() {
        return countCacheHits;
    }
    
    
    protected void countInfoMiss() {
        countCacheMisses++;
    }

    /**
     * Return the number of misses on cached file info
     * @return misses on cache file info
     */
    public long getCountInfoMisses() {
        return countCacheMisses;
    }
    
    
    protected void countContentHit() {
        countMappedHits++;
    }

    /**
     * Return the Number of hits on cached file content
     * @return hits on cache file content
     */
    public long getCountContentHits() {
        return countMappedHits;
    }
    
    
    protected void countContentMiss() {
        countMappedMisses++;
    }

    /**
     * Return the Number of misses on cached file content
     * @return missed on cached file content
     */
    public int getCountContentMisses() {
        return countMappedMisses;
    }
    
    // ---------------------------------------------------- Properties ----- //
    
    
    /**
     * Turn monitoring on/off
     */
    public void setIsMonitoringEnabled(boolean isMe){
        isMonitoringEnabled = isMe;
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
    public boolean isEnabled(){
        return isEnabled;
    }

    
    /**
     * Is the file caching mechanism enabled.
     */
    public void setIsEnabled(boolean isEnabled){
        this.isEnabled = isEnabled;
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
    
    
    /**
     * Return the FileCache
     */
    public ConcurrentHashMap<String,FileCacheEntry> getCache(){
        return fileCache;
    }

    
    /**
     * Retunr the header size buffer.
     */ 
    public int getHeaderBBSize() {
        return headerBBSize;
    }

    /**
     * Set the size of the header ByteBuffer.
     */
    public void setHeaderBBSize(int headerBBSize) {
        this.headerBBSize = headerBBSize;
    }


    /**
     * Check if the if-modified-since condition is satisfied.
     * 
     * @return boolean true if the resource meets the specified condition,
     * and false if the condition is not satisfied, in which case request
     * processing is stopped
     */
    private boolean checkIfModifiedSince(Request request, FileCacheEntry entry)
        throws IOException {
        try {
            Response response = request.getResponse();
            String h = request.getHeader("If-Modified-Since");
            long headerValue = (h == null ? -1: Long.parseLong(h));
            if (headerValue != -1) {
                long lastModified = Long.parseLong(entry.lastModified);
                // If an If-None-Match header has been specified,
                // If-Modified-Since is ignored.
                if ((request.getHeader("If-None-Match") == null)
                    && (lastModified < headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.setStatus(SC_NOT_MODIFIED);
                    response.setHeader("ETag", getETag(entry));
                    return false;
                }
            }
        } catch(IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;

    }


    /**
     * Check if the if-none-match condition is satisfied.

     * @return boolean true if the resource meets the specified condition,
     * and false if the condition is not satisfied, in which case request
     * processing is stopped
     */
    private boolean checkIfNoneMatch(Request request, FileCacheEntry entry)
        throws IOException {

        Response response = request.getResponse();
        String eTag = getETag(entry);
        String headerValue = request.getHeader("If-None-Match");
        if (headerValue != null) {

            boolean conditionSatisfied = false;

            if (!headerValue.equals("*")) {

                StringTokenizer commaTokenizer =
                    new StringTokenizer(headerValue, ",");

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

            } else {
                conditionSatisfied = true;
            }

            if (conditionSatisfied) {

                // For GET and HEAD, we should respond with
                // 304 Not Modified.
                // For every other method, 412 Precondition Failed is sent
                // back.
                if ( ("GET".equals(request.method().getString()))
                     || ("HEAD".equals(request.method().getString())) ) {
                    response.setStatus(SC_NOT_MODIFIED);
                    response.setHeader("ETag", eTag);
                    return false;
                } else {
                    response.setStatus
                        (SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        }
        return true;

    }

    /**
     * Check if the if-unmodified-since condition is satisfied.
     *
     * @return boolean true if the resource meets the specified condition,
     * and false if the condition is not satisfied, in which case request
     * processing is stopped
     */
    protected boolean checkIfUnmodifiedSince(Request request, FileCacheEntry entry)
        throws IOException {
        try {
            Response response = request.getResponse();
            long lastModified = Long.parseLong(entry.lastModified);
            String h = request.getHeader("If-Unmodified-Since");
            long headerValue = (h == null? -1: Long.parseLong(h));
            if (headerValue != -1) {
                if ( lastModified >= (headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.setStatus(SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        } catch(IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;

    }

    /**
     * Get ETag.
     *
     * @return strong ETag if available, else weak ETag
     */
    private String getETag(FileCacheEntry entry) {
        String result = entry.Etag;
        if (result == null) {
            long contentLength = Long.parseLong(entry.contentLength);
            long lastModified = Long.parseLong(entry.lastModified);
            if ((contentLength >= 0) || (lastModified >= 0)) {
                result = "W/\"" + contentLength + "-" +
                           lastModified + "\"";
                entry.Etag = result;
            }
        }
        return result;
    }

    /**
     * Check if the conditions specified in the optional If headers are
     * satisfied.
     *
     * @return boolean true if the resource meets all the specified conditions,
     * and false if any of the conditions is not satisfied, in which case
     * request processing is stopped
     */
    protected boolean checkIfHeaders(Request request, FileCacheEntry entry)
        throws IOException {

        return checkIfMatch(request,entry)
            && checkIfModifiedSince(request,entry)
            && checkIfNoneMatch(request,entry)
            && checkIfUnmodifiedSince(request,entry);

    }

        /**
     * Check if the if-match condition is satisfied.
     *
     * @param request The servlet request we are processing
     * @param entry the FileCacheEntry to validate
     * @return <code>true</code> if the resource meets the specified condition,
     *  and false if the condition is not satisfied, in which case request
     *  processing is stopped
     */
    protected boolean checkIfMatch(Request request, FileCacheEntry entry)
        throws IOException {

        Response response = request.getResponse();
        String eTag = getETag(entry);
        String headerValue = request.getHeader("If-Match");
        if (headerValue != null) {
            if (headerValue.indexOf('*') == -1) {

                StringTokenizer commaTokenizer = new StringTokenizer
                    (headerValue, ",");
                boolean conditionSatisfied = false;

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

                // If none of the given ETags match, 412 Precodition failed is
                // sent back
                if (!conditionSatisfied) {
                    response.setStatus
                        (SC_PRECONDITION_FAILED);
                    return false;
                }

            }
        }
        return true;

    }
}
