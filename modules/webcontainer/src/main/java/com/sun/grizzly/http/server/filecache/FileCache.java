/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.http.server.filecache;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpPacket;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.HttpResponsePacket;
import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.http.util.MimeHeaders;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.utils.ArraySet;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements a file caching mechanism used to cache static resources.
 *
 * @author Jeanfrancois Arcand
 * @author Scott Oaks
 */
public class FileCache {
    public enum CacheType {
        HEAP, MAPPED
    }

    private static final Logger logger = Grizzly.logger(FileCache.class);
    
    /**
     * A {@link ByteBuffer} cache of static pages.
     */
    private final ConcurrentHashMap<FileCacheKey, FileCacheEntry> fileCacheMap =
            new ConcurrentHashMap<FileCacheKey, FileCacheEntry>();
    
    private final FileCacheEntry NULL_CACHE_ENTRY = new FileCacheEntry(this);
    /**
     * Timeout before remove the static resource from the cache.
     */
    private int secondsMaxAge = -1;
    /**
     * The maximum entries in the {@link FileCache}
     */
    private volatile int maxCacheEntries = 1024;
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
    private boolean isEnabled = true;
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

    private final MemoryManager memoryManager;
    private final ScheduledExecutorService scheduledExecutorService;

    /**
     * Connection probes
     */
    protected final ArraySet<FileCacheMonitoringProbe> monitoringProbes =
            new ArraySet<FileCacheMonitoringProbe>();

    public FileCache(MemoryManager memoryManager,
            ScheduledExecutorService scheduledExecutorService) {

        this.memoryManager = memoryManager;
        this.scheduledExecutorService = scheduledExecutorService;
    }


    // ---------------------------------------------------- Methods ----------//
    /**
     * Add a resource to the cache. Currently, only static resources served
     * by the DefaultServlet can be cached.
     */
    public void add(final HttpRequestPacket request, File cacheFile) {

        final String requestURI = request.getRequestURI();
        final String host = request.getHeader("Host");
        final HttpResponsePacket response = request.getResponse();
        final MimeHeaders headers = response.getHeaders();

        final FileCacheKey key = new FileCacheKey(host, requestURI);
        if (requestURI == null || fileCacheMap.putIfAbsent(key, NULL_CACHE_ENTRY) != null) {
            //@TODO return key and entry to the thread cache object pool
            return;
        }

        // cache is full.
        if (fileCacheMap.size() > maxCacheEntries) {
            fileCacheMap.remove(key);
            //@TODO return key and entry to the thread cache object pool
            return;
        }

        final FileCacheEntry entry = mapFile(cacheFile);

        // Always put the answer into the map. If it's null, then
        // we know that it doesn't fit into the cache, so there's no
        // reason to go through this code again.
        if (entry == null) {
            return;
        }

        entry.key = key;
        entry.requestURI = requestURI;

        String lastModified = headers.getHeader("Last-Modified");
        entry.lastModified = (lastModified == null
                ? String.valueOf(cacheFile.lastModified()) : lastModified);
        entry.contentType = headers.getHeader("Content-type");
        entry.xPoweredBy = headers.getHeader("X-Powered-By");
        entry.date = headers.getHeader("Date");
        entry.Etag = headers.getHeader("Etag");
        entry.contentLength = response.getContentLength();
        entry.host = host;

        fileCacheMap.put(key, entry);
        
        notifyProbesEntryAdded(this, entry);

        if (secondsMaxAge > 0) {
            scheduledExecutorService.schedule(entry, secondsMaxAge, TimeUnit.SECONDS);
        }
    }


    /**
     * Send the cache.
     */
    public HttpPacket get(final HttpRequestPacket request) {

        final String requestURI = request.getRequestURI();
        final String host = request.getHeader("Host");

        final FileCacheKey key = new FileCacheKey(host, requestURI);
        final FileCacheEntry entry = fileCacheMap.get(key);

        try {
            if (entry != null && entry != NULL_CACHE_ENTRY) {
                notifyProbesEntryHit(this, entry);
                return makeResponse(entry, request);
            } else {
                notifyProbesEntryMissed(this, host, requestURI);
            }
        } catch (Exception e) {
            notifyProbesError(this, e);
            // If an unexpected exception occurs, try to serve the page
            // as if it wasn't in a cache.
            logger.log(Level.WARNING, "File Cache exception", e);
        }
        
        return null;
    }

    final ConcurrentHashMap<FileCacheKey, FileCacheEntry> getFileCacheMap() {
        return fileCacheMap;
    }

    protected void remove(FileCacheEntry entry) {
        fileCacheMap.remove(entry.key);

        if (entry.type == FileCache.CacheType.MAPPED) {
            subMappedMemorySize(entry.bb.remaining());
        } else {
            subHeapSize(entry.bb.remaining());
        }

        notifyProbesEntryRemoved(this, entry);
    }

    /**
     * Map the file to a {@link ByteBuffer}
     * @return the preinitialized {@link FileCacheEntry}
     */
    private FileCacheEntry mapFile(File file) {
        final CacheType type;
        final long size;
        final ByteBuffer bb;
        
        FileChannel fileChannel = null;
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(file);
            fileChannel = stream.getChannel();

            size = fileChannel.size();

            if (size > maxEntrySize) {
                return null;
            }

            if (size > minEntrySize) {
                if (addMappedMemorySize(size) > maxLargeFileCacheSize) {
                    // Cache full
                    subMappedMemorySize(size);
                    return null;
                }
                
                type = CacheType.MAPPED;
            } else {
                if (addHeapSize(size) > maxSmallFileCacheSize) {
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
    protected HttpPacket makeResponse(FileCacheEntry entry,
            HttpRequestPacket request) throws IOException {

        final HttpResponsePacket response = request.getResponse();
        response.setStatus(200);
        response.setReasonPhrase("OK");

        boolean flushBody = checkIfHeaders(entry, request);
        response.setContentType(entry.contentType);

        if (flushBody) {
            response.setContentLength(entry.contentLength);
            final ByteBuffer sliced = entry.bb.slice();
            final Buffer buffer = MemoryUtils.wrap(memoryManager, sliced);

            final HttpContent content =
                    HttpContent.builder(response)
                    .content(buffer)
                    .last(true)
                    .build();

            return content;
        } else {
            response.setChunked(false);
        }

        return response;
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
        return fileCacheMap.size();
    }

    /**
     * Return the maximum number of cache entries
     * @return maximum cache entries
     */
    public long getMaxEntries() {
        return maxCacheEntries;
    }

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

    // ---------------------------------------------------- Properties ----- //
    /**
     * The timeout in seconds before remove a {@link FileCacheEntry}
     * from the {@link FileCache}
     */
    public void setSecondsMaxAge(int sMaxAges) {
        secondsMaxAge = sMaxAges;
    }

    /**
     * Set the maximum entries this cache can contains.
     */
    public void setMaxCacheEntries(int mEntries) {
        maxCacheEntries = mEntries;
    }

    /**
     * Return the maximum entries this cache can contains.
     */
    public int getMaxCacheEntries() {
        return maxCacheEntries;
    }

    /**
     * Set the maximum size a {@link FileCacheEntry} can have.
     */
    public void setMinEntrySize(long mSize) {
        minEntrySize = mSize;
    }

    /**
     * Get the maximum size a {@link FileCacheEntry} can have.
     */
    public long getMinEntrySize() {
        return minEntrySize;
    }

    /**
     * Set the maximum size a {@link FileCacheEntry} can have.
     */
    public void setMaxEntrySize(long mEntrySize) {
        maxEntrySize = mEntrySize;
    }

    /**
     * Get the maximum size a {@link FileCacheEntry} can have.
     */
    public long getMaxEntrySize() {
        return maxEntrySize;
    }

    /**
     * Set the maximum cache size
     */
    public void setMaxLargeCacheSize(long mCacheSize) {
        maxLargeFileCacheSize = mCacheSize;
    }

    /**
     * Get the maximum cache size
     */
    public long getMaxLargeCacheSize() {
        return maxLargeFileCacheSize;
    }

    /**
     * Set the maximum cache size
     */
    public void setMaxSmallCacheSize(long mCacheSize) {
        maxSmallFileCacheSize = mCacheSize;
    }

    /**
     * Get the maximum cache size
     */
    public long getMaxSmallCacheSize() {
        return maxSmallFileCacheSize;
    }

    /**
     * Is the fileCache enabled.
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Is the file caching mechanism enabled.
     */
    public void setIsEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
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
            String h = request.getHeader("If-Modified-Since");
            long headerValue = (h == null ? -1 : Long.parseLong(h));
            if (headerValue != -1) {
                long lastModified = Long.parseLong(entry.lastModified);
                // If an If-None-Match header has been specified,
                // If-Modified-Since is ignored.
                if ((request.getHeader("If-None-Match") == null)
                        && (lastModified < headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.setStatus(SC_NOT_MODIFIED);
                    response.setReasonPhrase("Not Modified");
                    response.setHeader("ETag", getETag(entry));
                    return false;
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
        String headerValue = request.getHeader("If-None-Match");
        if (headerValue != null) {
            String eTag = getETag(entry);

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
                final BufferChunk methodBC = request.getMethodBC();
                if (methodBC.equals("GET") || methodBC.equals("HEAD")) {
                    response.setStatus(SC_NOT_MODIFIED);
                    response.setReasonPhrase("Not Modified");
                    response.setHeader("ETag", eTag);
                    return false;
                } else {
                    response.setStatus(SC_PRECONDITION_FAILED);
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

            long lastModified = Long.parseLong(entry.lastModified);
            String h = request.getHeader("If-Unmodified-Since");
            long headerValue = (h == null ? -1 : Long.parseLong(h));
            if (headerValue != -1) {
                if (lastModified >= (headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.setStatus(SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
            notifyProbesError(this, illegalArgument);
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
            final StringBuilder sb = new StringBuilder();
            
            long contentLength = entry.fileSize;
            long lastModified = Long.parseLong(entry.lastModified);
            if ((contentLength >= 0) || (lastModified >= 0)) {
                sb.append("W/\"").append(contentLength).append('-').
                        append(lastModified).append('"');
                result = sb.toString();
                entry.Etag = result;
            }
        }
        return result;
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
        String headerValue = request.getHeader("If-Match");
        if (headerValue != null) {
            if (headerValue.indexOf('*') == -1) {
                String eTag = getETag(entry);

                StringTokenizer commaTokenizer = new StringTokenizer(headerValue, ",");
                boolean conditionSatisfied = false;

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag)) {
                        conditionSatisfied = true;
                    }
                }

                // If none of the given ETags match, 412 Precodition failed is
                // sent back
                if (!conditionSatisfied) {
                    response.setStatus(SC_PRECONDITION_FAILED);
                    return false;
                }

            }
        }
        return true;
    }

    /**
     * Add the {@link FileCacheMonitoringProbe}, which will be notified about
     * <tt>FileCache</tt> lifecycle events.
     *
     * @param probe the {@link FileCacheMonitoringProbe}.
     */
    public void addMonitoringProbe(FileCacheMonitoringProbe probe) {
        monitoringProbes.add(probe);
    }

    /**
     * Remove the {@link FileCacheMonitoringProbe}.
     *
     * @param probe the {@link FileCacheMonitoringProbe}.
     */
    public boolean removeMonitoringProbe(FileCacheMonitoringProbe probe) {
        return monitoringProbes.remove(probe);
    }

    /**
     * Get the {@link FileCacheMonitoringProbe}, which are registered on the <tt>FileCache</tt>.
     * Please note, it's not appropriate to modify the returned array's content.
     * Please use {@link #addMonitoringProbe(com.sun.grizzly.http.server.filecache.FileCacheMonitoringProbe)}and
     * {@link #removeMonitoringProbe(com.sun.grizzly.http.server.filecache.FileCacheMonitoringProbe)} instead.
     *
     * @return the {@link FileCacheMonitoringProbe}, which are registered on the <tt>FileCache</tt>.
     */
    public FileCacheMonitoringProbe[] getMonitoringProbes() {
        return monitoringProbes.obtainArrayCopy(FileCacheMonitoringProbe.class);
    }

    /**
     * Notify registered {@link FileCacheMonitoringProbe}s about the "entry added" event.
     *
     * @param fileCache the <tt>FileCache</tt> event occurred on.
     * @param entry entry been added
     */
    protected static void notifyProbesEntryAdded(final FileCache fileCache,
            final FileCacheEntry entry) {
        final FileCacheMonitoringProbe[] probes =
                fileCache.monitoringProbes.getArray();
        if (probes != null) {
            for (FileCacheMonitoringProbe probe : probes) {
                probe.onEntryAddedEvent(fileCache, entry);
            }
        }
    }

    /**
     * Notify registered {@link FileCacheMonitoringProbe}s about the "entry removed" event.
     *
     * @param fileCache the <tt>FileCache</tt> event occurred on.
     * @param entry entry been removed
     */
    protected static void notifyProbesEntryRemoved(final FileCache fileCache,
            final FileCacheEntry entry) {
        final FileCacheMonitoringProbe[] probes =
                fileCache.monitoringProbes.getArray();
        if (probes != null) {
            for (FileCacheMonitoringProbe probe : probes) {
                probe.onEntryRemovedEvent(fileCache, entry);
            }
        }
    }

    /**
     * Notify registered {@link FileCacheMonitoringProbe}s about the "entry hitted" event.
     *
     * @param fileCache the <tt>FileCache</tt> event occurred on.
     * @param entry entry been hitted.
     */
    protected static void notifyProbesEntryHit(final FileCache fileCache,
            final FileCacheEntry entry) {
        final FileCacheMonitoringProbe[] probes =
                fileCache.monitoringProbes.getArray();
        if (probes != null) {
            for (FileCacheMonitoringProbe probe : probes) {
                probe.onEntryHitEvent(fileCache, entry);
            }
        }
    }

    /**
     * Notify registered {@link FileCacheMonitoringProbe}s about the "entry missed" event.
     *
     * @param fileCache the <tt>FileCache</tt> event occurred on.
     * @param host requested HTTP "Host" parameter.
     * @param requestURI requested HTTP request URL.
     */
    protected static void notifyProbesEntryMissed(final FileCache fileCache,
            final String host, final String requestURI) {
        
        final FileCacheMonitoringProbe[] probes =
                fileCache.monitoringProbes.getArray();
        if (probes != null) {
            for (FileCacheMonitoringProbe probe : probes) {
                probe.onEntryMissedEvent(fileCache, host, requestURI);
            }
        }
    }

    /**
     * Notify registered {@link FileCacheMonitoringProbe}s about the error.
     *
     * @param fileCache the <tt>FileCache</tt> event occurred on.
     */
    protected static void notifyProbesError(final FileCache fileCache,
            final Throwable error) {
        final FileCacheMonitoringProbe[] probes =
                fileCache.monitoringProbes.getArray();
        if (probes != null) {
            for (FileCacheMonitoringProbe probe : probes) {
                probe.onErrorEvent(fileCache, error);
            }
        }
    }
}
