/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server.filecache;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.server.util.SimpleDateFormats;
import org.glassfish.grizzly.http.util.FastHttpDateFormat;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.DelayedExecutor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.monitoring.DefaultMonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringAware;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringUtils;

/**
 * This class implements a file caching mechanism used to cache static resources.
 *
 * @author Jeanfrancois Arcand
 * @author Scott Oaks
 */
public class FileCache implements MonitoringAware<FileCacheProbe> {
    public enum CacheType {
        HEAP, MAPPED, TIMESTAMP
    }

    public enum CacheResult {
        OK_CACHED,
        OK_CACHED_TIMESTAMP,
        FAILED_CACHE_FULL,
        FAILED_ENTRY_EXISTS,
        FAILED
    }

    private static final Logger logger = Grizzly.logger(FileCache.class);
    
    /**
     * Cache size.
     */
    private final AtomicInteger cacheSize = new AtomicInteger();
    
    /**
     * A {@link ByteBuffer} cache of static pages.
     */
    private final ConcurrentHashMap<FileCacheKey, FileCacheEntry> fileCacheMap =
            new ConcurrentHashMap<FileCacheKey, FileCacheEntry>();
    
    private final FileCacheEntry NULL_CACHE_ENTRY = new FileCacheEntry(this);

    /**
     * Specifies the maximum time in seconds a resource may be cached.
     */
    private int secondsMaxAge = -1;

    /**
     * The maximum entries in the {@link FileCache}
     */
    private volatile int maxCacheEntries = 1024;

    /**
     * The maximum size of a cached resource.
     */
    private long minEntrySize = Long.MIN_VALUE;

    /**
     * The maximum size of a cached resource.
     */
    private long maxEntrySize = Long.MAX_VALUE;

    /**
     * The maximum memory mapped bytes.
     */
    private volatile long maxLargeFileCacheSize = Long.MAX_VALUE;

    /**
     * The maximum cached bytes
     */
    private volatile long maxSmallFileCacheSize = 1048576;

    /**
     * The current cache size in bytes
     */
    private AtomicLong mappedMemorySize = new AtomicLong();

    /**
     * The current cache size in bytes
     */
    private AtomicLong heapSize = new AtomicLong();

    /**
     * Is the file cache enabled.
     */
    private boolean enabled = true;

    private MemoryManager memoryManager;
    
    private DelayedExecutor.DelayQueue<FileCacheEntry> delayQueue;

    /**
     * File cache probes
     */
    protected final DefaultMonitoringConfig<FileCacheProbe> monitoringConfig =
            new DefaultMonitoringConfig<FileCacheProbe>(FileCacheProbe.class) {

        @Override
        public Object createManagementObject() {
            return createJmxManagementObject();
        }

    };


    // ---------------------------------------------------- Methods ----------//

    public void initialize(MemoryManager memoryManager,
            DelayedExecutor delayedExecutor) {
        this.memoryManager = memoryManager;
        delayQueue = delayedExecutor.createDelayQueue(new EntryWorker(),
                new EntryResolver());
    }

    /**
     * Add a resource to the cache.
     * Unlike the {@link #add(org.glassfish.grizzly.http.HttpRequestPacket, java.io.File)}
     * this method adds a resource to a cache but is not able to send the
     * resource content to a client if client doesn't have the latest version
     * of this resource.
     */
    public CacheResult add(final HttpRequestPacket request,
            final long lastModified) {
        return add(request, null, lastModified);
    }
    
    /**
     * Add a {@link File} resource to the cache.
     * If a client comes with not the latest version of this resource - the
     * {@link FileCache} will return it the latest resource version.
     */
    public CacheResult add(final HttpRequestPacket request,
            final File cacheFile) {
        return add(request, cacheFile, cacheFile.lastModified());
    }
    
    /**
     * Add a resource to the cache.
     */
    protected CacheResult add(final HttpRequestPacket request,
            final File cacheFile, final long lastModified) {

        final String requestURI = request.getRequestURI();

        if (requestURI == null) {
            return CacheResult.FAILED;
        }

        final String host = request.getHeader(Header.Host);
        final FileCacheKey key = new FileCacheKey(host, requestURI);
        if(fileCacheMap.putIfAbsent(key, NULL_CACHE_ENTRY) != null) {
            key.recycle();
            return CacheResult.FAILED_ENTRY_EXISTS;
        }

        final int size = cacheSize.incrementAndGet();
        // cache is full.
        if (size > getMaxCacheEntries()) {
            cacheSize.decrementAndGet();
            fileCacheMap.remove(key);
            key.recycle();
            return CacheResult.FAILED_CACHE_FULL;
        }

        FileCacheEntry entry = null;
        if (cacheFile != null) { // If we have a file - try to create File-aware cache resource
            entry = mapFile(cacheFile);
        }

        if (entry == null) {
            entry = new FileCacheEntry(this);
            entry.type = CacheType.TIMESTAMP;
        }

        final HttpResponsePacket response = request.getResponse();
        final MimeHeaders headers = response.getHeaders();

        entry.key = key;
        entry.requestURI = requestURI;

        entry.lastModified = lastModified;
        entry.contentType = response.getContentType();
        entry.xPoweredBy = headers.getHeader(Header.XPoweredBy);
        entry.date = headers.getHeader(Header.Date);
        entry.lastModifiedHeader = headers.getHeader(Header.LastModified);
        entry.contentLength = response.getContentLength();
        entry.host = host;
        entry.Etag = headers.getHeader(Header.ETag);
        entry.server = headers.getHeader(Header.Server);

        fileCacheMap.put(key, entry);
        
        notifyProbesEntryAdded(this, entry);
        
        final int secondsMaxAgeLocal = getSecondsMaxAge();
        if (secondsMaxAgeLocal > 0) {
            delayQueue.add(entry, secondsMaxAgeLocal, TimeUnit.SECONDS);
        }

        return ((entry.type == CacheType.TIMESTAMP)
                    ? CacheResult.OK_CACHED_TIMESTAMP
                    : CacheResult.OK_CACHED);
    }

    /**
     * Send the cache.
     */
    public HttpPacket get(final HttpRequestPacket request) {
        // It should be faster than calculating the key hash code
        if (cacheSize.get() == 0) return null;

        final LazyFileCacheKey key = LazyFileCacheKey.create(request);
        final FileCacheEntry entry = fileCacheMap.get(key);
        key.recycle();
        try {
            if (entry != null && entry != NULL_CACHE_ENTRY) {
                final HttpPacket response = makeResponse(entry, request);
                if (response != null) {
                    notifyProbesEntryHit(this, entry);
                    return response;
                }
            }
            
            notifyProbesEntryMissed(this, request);
        } catch (Exception e) {
            notifyProbesError(this, e);
            // If an unexpected exception occurs, try to serve the page
            // as if it wasn't in a cache.
            logger.log(Level.WARNING, "File Cache exception", e);
        }
        
        return null;
    }

    protected void remove(final FileCacheEntry entry) {
        if (fileCacheMap.remove(entry.key) != null) {
            cacheSize.decrementAndGet();
        }

        if (entry.type == FileCache.CacheType.MAPPED) {
            subMappedMemorySize(entry.bb.remaining());
        } else {
            subHeapSize(entry.bb.remaining());
        }

        notifyProbesEntryRemoved(this, entry);
    }

    protected Object createJmxManagementObject() {
        return MonitoringUtils.loadJmxObject(
                "org.glassfish.grizzly.http.server.filecache.jmx.FileCache",
                this, FileCache.class);
    }

    /**
     * Map the file to a {@link ByteBuffer}
     * @return the preinitialized {@link FileCacheEntry}
     */
    private FileCacheEntry mapFile(final File file) {
        final CacheType type;
        final long size;
        final ByteBuffer bb;
        
        FileChannel fileChannel = null;
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(file);
            fileChannel = stream.getChannel();

            size = fileChannel.size();

            if (size > getMaxEntrySize()) {
                return null;
            }

            if (size > getMinEntrySize()) {
                if (addMappedMemorySize(size) > getMaxLargeFileCacheSize()) {
                    // Cache full
                    subMappedMemorySize(size);
                    return null;
                }
                
                type = CacheType.MAPPED;
            } else {
                if (addHeapSize(size) > getMaxSmallFileCacheSize()) {
                    // Cache full
                    subHeapSize(size);
                    return null;
                }

                type = CacheType.HEAP;
            }

            bb = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, size);

            if (type == CacheType.HEAP) {
                ((MappedByteBuffer) bb).load();
            }            
        } catch (Exception e) {
            notifyProbesError(this, e);
            return null;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    notifyProbesError(this, ignored);
                }
            }
            if (fileChannel != null) {
                try {
                    fileChannel.close();
                } catch (IOException ignored) {
                    notifyProbesError(this, ignored);
                }
            }
        }

        final FileCacheEntry entry = new FileCacheEntry(this);
        entry.type = type;
        entry.fileSize = size;
        entry.bb = bb;

        return entry;
    }

    // -------------------------------------------------- Static cache -------/
    /**
     * Send the cached resource.
     */
    @SuppressWarnings({"unchecked"})
    protected HttpPacket makeResponse(FileCacheEntry entry,
            HttpRequestPacket request) throws IOException {

        final HttpResponsePacket response = request.getResponse();
        HttpStatus.OK_200.setValues(request.getResponse());

        // determine if we need to send the cache entry bytes
        // to the user-agent
        boolean flushBody = checkIfHeaders(entry, request);

        response.setContentType(entry.contentType);

        if (entry.server != null) {
            response.addHeader(Header.Server, entry.server);
        }

        if (flushBody) {
            if (entry.type == CacheType.TIMESTAMP) {
                return null; // this will cause control to be passed to the static handler
            }
            addCachingHeaders(entry, response);
            response.setContentLengthLong(entry.contentLength);

            final ByteBuffer sliced = entry.bb.slice();
            final Buffer buffer = Buffers.wrap(memoryManager, sliced);

            return HttpContent.builder(response)
                    .content(buffer)
                    .last(true)
                    .build();
        } else {
            return HttpContent.builder(response)
                    .content(Buffers.EMPTY_BUFFER)
                    .last(true)
                    .build();
        }
    }

    // ------------------------------------------------ Configuration Properties

    /**
     * @return the maximum time, in seconds, a file may be cached.
     */
    public int getSecondsMaxAge() {
        return secondsMaxAge;
    }

    /**
     * Sets the maximum time, in seconds, a file may be cached.
     *
     * @param secondsMaxAge max age of a cached file, in seconds.
     */
    public void setSecondsMaxAge(int secondsMaxAge) {
        this.secondsMaxAge = secondsMaxAge;
    }

    /**
     * @return the maximum number of files that may be cached.
     */
    public int getMaxCacheEntries() {
        return maxCacheEntries;
    }

    /**
     * Sets the maximum number of files that may be cached.
     *
     * @param maxCacheEntries the maximum number of files that may be cached.
     */
    public void setMaxCacheEntries(int maxCacheEntries) {
        this.maxCacheEntries = maxCacheEntries;
    }


    /**
     * @return the minimum size, in bytes, a file must be in order to be cached
     *  in the heap cache.
     */
    public long getMinEntrySize() {
        return minEntrySize;
    }

    /**
     * The maximum size, in bytes, a file must be in order to be cached
     * in the heap cache.
     *
     * @param minEntrySize the maximum size, in bytes, a file must be in order
     *  to be cached in the heap cache.
     */
    public void setMinEntrySize(long minEntrySize) {
        this.minEntrySize = minEntrySize;
    }

    /**
     * @return the maximum size, in bytes, a resource may be before it can no
     *  longer be considered cacheable.
     */
    public long getMaxEntrySize() {
        return maxEntrySize;
    }

    /**
     * The maximum size, in bytes, a resource may be before it can no
     * longer be considered cacheable.
     *
     * @param maxEntrySize the maximum size, in bytes, a resource may be before it can no
     *  longer be considered cacheable.
     */
    public void setMaxEntrySize(long maxEntrySize) {
        this.maxEntrySize = maxEntrySize;
    }

    /**
     * @return the maximum size of the memory mapped cache for large files.
     */
    public long getMaxLargeFileCacheSize() {
        return maxLargeFileCacheSize;
    }


    /**
     * Sets the maximum size, in bytes, of the memory mapped cache for large
     * files.
     *
     * @param maxLargeFileCacheSize the maximum size, in bytes, of the memory
     *  mapped cache for large files.
     */
    public void setMaxLargeFileCacheSize(long maxLargeFileCacheSize) {
        this.maxLargeFileCacheSize = maxLargeFileCacheSize;
    }


    /**
     * @return the maximum size, in bytes, of the heap cache for files below the
     *  water mark set by {@link #getMinEntrySize()}.
     */
    public long getMaxSmallFileCacheSize() {
        return maxSmallFileCacheSize;
    }

    /**
     * The maximum size, in bytes, of the heap cache for files below the
     * water mark set by {@link #getMinEntrySize()}.
     *
     * @param maxSmallFileCacheSize the maximum size, in bytes, of the heap
     *  cache for files below the water mark set by {@link #getMinEntrySize()}.
     */
    public void setMaxSmallFileCacheSize(long maxSmallFileCacheSize) {
        this.maxSmallFileCacheSize = maxSmallFileCacheSize;
    }

    /**
     * @return <code>true</code> if the {@link FileCache} is enabled,
     *  otherwise <code>false</code>
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables/disables the {@link FileCache}.  By default, the
     * {@link FileCache} is disabled.
     *
     * @param enabled <code>true</code> to enable the {@link FileCache}.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }


    // ---------------------------------------------------- Monitoring --------//


    protected final long addHeapSize(long size) {
        return heapSize.addAndGet(size);
    }

    protected final long subHeapSize(long size) {
        return heapSize.addAndGet(-size);
    }

    /**
     * Return the heap space used for cache
     * @return heap size
     */
    public long getHeapCacheSize() {
        return heapSize.get();
    }

    protected final long addMappedMemorySize(long size) {
        return mappedMemorySize.addAndGet(size);
    }

    protected final long subMappedMemorySize(long size) {
        return mappedMemorySize.addAndGet(-size);
    }

    /**
     * Return the size of Mapped memory used for caching
     * @return Mapped memory size
     */
    public long getMappedCacheSize() {
        return mappedMemorySize.get();
    }


    /**
     * Check if the conditions specified in the optional If headers are
     * satisfied.
     *
     * @return boolean true if the resource meets all the specified conditions,
     * and false if any of the conditions is not satisfied, in which case
     * request processing is stopped
     */
    protected boolean checkIfHeaders(final FileCacheEntry entry,
            final HttpRequestPacket request) throws IOException {

        return checkIfMatch(entry, request)
                && checkIfModifiedSince(entry, request)
                && checkIfNoneMatch(entry, request)
                && checkIfUnmodifiedSince(entry, request);

    }

    /**
     * Check if the if-modified-since condition is satisfied.
     *
     * @return boolean true if the resource meets the specified condition,
     * and false if the condition is not satisfied, in which case request
     * processing is stopped
     */
    private boolean checkIfModifiedSince(final FileCacheEntry entry,
            final HttpRequestPacket request) throws IOException {
        try {
            HttpResponsePacket response = request.getResponse();
            final String reqModified = request.getHeader(Header.IfModifiedSince);
            if (reqModified != null) {
                // optimization - assume the String value sent in the
                // client's If-Modified-Since header is the same as what
                // was originally sent
                if (reqModified.equals(entry.lastModifiedHeader)) {
                    HttpStatus.NOT_MODIFIED_304.setValues(response);
                    return false;
                }
                long headerValue = convertToLong(reqModified);
                if (headerValue != -1) {
                    long lastModified = entry.lastModified;
                    // If an If-None-Match header has been specified,
                    // If-Modified-Since is ignored.
                    if ((request.getHeader(Header.IfNoneMatch) == null)
                            && (headerValue - lastModified <= 1000)) {
                        // The entity has not been modified since the date
                        // specified by the client. This is not an error case.
                        HttpStatus.NOT_MODIFIED_304.setValues(response);
                        return false;
                    }
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
            notifyProbesError(this, illegalArgument);
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
    private boolean checkIfNoneMatch(final FileCacheEntry entry,
            final HttpRequestPacket request) throws IOException {

        final HttpResponsePacket response = request.getResponse();
        String headerValue = request.getHeader(Header.IfNoneMatch);
        if (headerValue != null) {
            String eTag = entry.Etag;

            boolean conditionSatisfied = false;

            if (!headerValue.equals("*")) {

                StringTokenizer commaTokenizer =
                        new StringTokenizer(headerValue, ",");

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag)) {
                        conditionSatisfied = true;
                    }
                }

            } else {
                conditionSatisfied = true;
            }
            if (conditionSatisfied) {

                // For GET and HEAD, we should respond with
                // 304 Not Modified.
                // For every other method, 412 Precondition Failed is sent
                // back.
                final Method method = request.getMethod();
                if (Method.GET.equals(method) || Method.HEAD.equals(method)) {
                    HttpStatus.NOT_MODIFIED_304.setValues(response);
                    return false;
                } else {
                    HttpStatus.PRECONDITION_FAILED_412.setValues(response);
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
    protected boolean checkIfUnmodifiedSince(final FileCacheEntry entry,
            final HttpRequestPacket request) throws IOException {

        try {
            final HttpResponsePacket response = request.getResponse();

            long lastModified = entry.lastModified;
            String h = request.getHeader(Header.IfUnmodifiedSince);
            if (h != null) {
                // optimization - assume the String value sent in the
                // client's If-Unmodified-Since header is the same as what
                // was originally sent
                if (h.equals(entry.lastModifiedHeader)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    HttpStatus.PRECONDITION_FAILED_412.setValues(response);
                    return false;
                }
                long headerValue = convertToLong(h);
                if (headerValue != -1) {
                    if (headerValue - lastModified <= 1000) {
                        // The entity has not been modified since the date
                        // specified by the client. This is not an error case.
                        HttpStatus.PRECONDITION_FAILED_412.setValues(response);
                        return false;
                    }
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
            notifyProbesError(this, illegalArgument);
            return true;
        }
        return true;

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
    protected boolean checkIfMatch(final FileCacheEntry entry,
            final HttpRequestPacket request) throws IOException {
        
        HttpResponsePacket response = request.getResponse();
        String headerValue = request.getHeader(Header.IfMatch);
        if (headerValue != null) {
            if (headerValue.indexOf('*') == -1) {
                String eTag = entry.Etag;

                StringTokenizer commaTokenizer = new StringTokenizer(headerValue, ",");
                boolean conditionSatisfied = false;

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag)) {
                        conditionSatisfied = true;
                    }
                }

                // If none of the given ETags match, 412 Precondition failed is
                // sent back
                if (!conditionSatisfied) {
                    HttpStatus.PRECONDITION_FAILED_412.setValues(response);
                    return false;
                }

            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MonitoringConfig<FileCacheProbe> getMonitoringConfig() {
        return monitoringConfig;
    }

    /**
     * Notify registered {@link FileCacheProbe}s about the "entry added" event.
     *
     * @param fileCache the <tt>FileCache</tt> event occurred on.
     * @param entry entry been added
     */
    protected static void notifyProbesEntryAdded(final FileCache fileCache,
            final FileCacheEntry entry) {
        final FileCacheProbe[] probes =
                fileCache.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (FileCacheProbe probe : probes) {
                probe.onEntryAddedEvent(fileCache, entry);
            }
        }
    }

    /**
     * Notify registered {@link FileCacheProbe}s about the "entry removed" event.
     *
     * @param fileCache the <tt>FileCache</tt> event occurred on.
     * @param entry entry been removed
     */
    protected static void notifyProbesEntryRemoved(final FileCache fileCache,
            final FileCacheEntry entry) {
        final FileCacheProbe[] probes =
                fileCache.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (FileCacheProbe probe : probes) {
                probe.onEntryRemovedEvent(fileCache, entry);
            }
        }
    }

    /**
     * Notify registered {@link FileCacheProbe}s about the "entry hit event.
     *
     * @param fileCache the <tt>FileCache</tt> event occurred on.
     * @param entry entry been hit.
     */
    protected static void notifyProbesEntryHit(final FileCache fileCache,
            final FileCacheEntry entry) {
        final FileCacheProbe[] probes =
                fileCache.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (FileCacheProbe probe : probes) {
                probe.onEntryHitEvent(fileCache, entry);
            }
        }
    }

    /**
     * Notify registered {@link FileCacheProbe}s about the "entry missed" event.
     *
     * @param fileCache the <tt>FileCache</tt> event occurred on.
     * @param request HTTP request.
     */
    protected static void notifyProbesEntryMissed(final FileCache fileCache,
            final HttpRequestPacket request) {
        
        final FileCacheProbe[] probes =
                fileCache.monitoringConfig.getProbesUnsafe();
        if (probes != null && probes.length > 0) {
            for (FileCacheProbe probe : probes) {
                probe.onEntryMissedEvent(fileCache, request.getHeader(Header.Host),
                        request.getRequestURI());
            }
        }
    }

    /**
     * Notify registered {@link FileCacheProbe}s about the error.
     *
     * @param fileCache the <tt>FileCache</tt> event occurred on.
     */
    protected static void notifyProbesError(final FileCache fileCache,
            final Throwable error) {
        final FileCacheProbe[] probes =
                fileCache.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (FileCacheProbe probe : probes) {
                probe.onErrorEvent(fileCache, error);
            }
        }
    }

    protected static long convertToLong(final String dateHeader) {

        if (dateHeader == null)
            return (-1L);

        final SimpleDateFormats formats = SimpleDateFormats.create();

        try {
            // Attempt to convert the date header in a variety of formats
            long result = FastHttpDateFormat.parseDate(dateHeader, formats.getFormats());
            if (result != (-1L)) {
                return result;
            }
            throw new IllegalArgumentException(dateHeader);
        } finally {
            formats.recycle();
        }

    }

    protected void addCachingHeaders(FileCacheEntry entry, HttpResponsePacket response) {
        response.addHeader(Header.ETag, entry.Etag);
        response.addHeader(Header.LastModified, entry.lastModifiedHeader);
    }


    private static class EntryWorker implements DelayedExecutor.Worker<FileCacheEntry> {
        @Override
        public boolean doWork(final FileCacheEntry element) {
            element.run();
            return true;
        }        
    }

    private static class EntryResolver implements DelayedExecutor.Resolver<FileCacheEntry> {

        @Override
        public boolean removeTimeout(FileCacheEntry element) {
            if (element.timeoutMillis != -1) {
                element.timeoutMillis = -1;
                return true;
            }

            return false;
        }

        @Override
        public long getTimeoutMillis(FileCacheEntry element) {
            return element.timeoutMillis;
        }

        @Override
        public void setTimeoutMillis(FileCacheEntry element, long timeoutMillis) {
            element.timeoutMillis = timeoutMillis;
        }
    }
}
