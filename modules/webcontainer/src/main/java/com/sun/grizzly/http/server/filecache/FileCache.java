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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements a file caching mechanism used to cache static resources.
 *
 * @author Jeanfrancois Arcand
 * @author Scott Oaks
 */
public class FileCache {

    private static final Logger logger = Grizzly.logger(FileCache.class);
    
    /**
     * A {@link ByteBuffer} cache of static pages.
     */
    private final ConcurrentHashMap<FileCacheKey, FileCacheEntry> fileCache =
            new ConcurrentHashMap<FileCacheKey, FileCacheEntry>();
    
    private final FileCacheEntry NULL_CACHE_ENTRY = new FileCacheEntry();
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
//    private int port = 8080;
    /**
     * FileCacheEntry cache
     */
//    private Queue<FileCacheEntry> cacheManager;
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

    private final MemoryManager memoryManager;
    private final ScheduledExecutorService scheduledExecutorService;

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
        final MimeHeaders headers = request.getHeaders();
        
        final FileCacheKey key = new FileCacheKey(host, requestURI);
        if (requestURI == null || fileCache.putIfAbsent(key, NULL_CACHE_ENTRY) != null) {
            //@TODO return key and entry to the thread cache object pool
            return;
        }

        // cache is full.
        if (fileCache.size() > maxCacheEntries) {
            fileCache.remove(key);
            //@TODO return key and entry to the thread cache object pool
            return;
        }

        ByteBuffer bb = mapFile(cacheFile);

        // Always put the answer into the map. If it's null, then
        // we know that it doesn't fit into the cache, so there's no
        // reason to go through this code again.
        if (bb == null) {
            return;
        }

        final FileCacheEntry entry = new FileCacheEntry();

        entry.key = key;
        entry.bb = bb;
        entry.requestURI = requestURI;

        String lastModified = headers.getHeader("Last-Modified");
        entry.lastModified = (lastModified == null
                ? String.valueOf(cacheFile.lastModified()) : lastModified);
        entry.contentType = headers.getHeader("Content-type");
        entry.xPoweredBy = headers.getHeader("X-Powered-By");
        entry.isInHeap = (cacheFile.length() < minEntrySize);
        entry.date = headers.getHeader("Date");
        entry.Etag = headers.getHeader("Etag");
        entry.contentLength = parseLong(headers.getHeader("Content-Length"), -1);
        entry.host = host;

        incOpenCacheEntries();
        if (isMonitoringEnabled) {

            if (openCacheEntries > maxOpenCacheEntries) {
                maxOpenCacheEntries = openCacheEntries;
            }

            if (heapSize > maxHeapCacheSize) {
                maxHeapCacheSize = heapSize;
            }

            if (mappedMemorySize > maxMappedMemory) {
                maxMappedMemory = mappedMemorySize;
            }
        }

        if (secondsMaxAge > 0) {
            entry.future = scheduledExecutorService.schedule(entry,
                    secondsMaxAge, TimeUnit.SECONDS);
        }

        fileCache.put(key, entry);
    }


    /**
     * Send the cache.
     */
    public HttpPacket get(final HttpRequestPacket request) {

        final String requestURI = request.getRequestURI();
        final String host = request.getHeader("Host");

        final FileCacheKey key = new FileCacheKey(host, requestURI);
        final FileCacheEntry entry = fileCache.get(key);

        try {
            recalcCacheStatsIfMonitoring(entry);

            if (entry != null && entry != NULL_CACHE_ENTRY) {
                return makeResponse(entry, request);
            }
        } catch (Exception e) {
            // If an unexpected exception occurs, try to serve the page
            // as if it wasn't in a cache.
            logger.log(Level.WARNING, "File Cache exception", e);
        }
        
        return null;
    }

    /**
     * Map the file to a {@link ByteBuffer}
     * @return the {@link ByteBuffer}
     */
    private ByteBuffer mapFile(File file) {
        FileChannel fileChannel = null;
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(file);
            fileChannel = stream.getChannel();

            long size = fileChannel.size();

            if (size > maxEntrySize) {
                return null;
            }

            if (size > minEntrySize) {
                addMappedMemorySize(size);
            } else {
                addHeapSize(size);
            }

            // Cache full
            if (mappedMemorySize > maxLargeFileCacheSize) {
                subMappedMemorySize(size);
                return null;
            } else if (heapSize > maxSmallFileCacheSize) {
                subHeapSize(size);
                return null;
            }

            ByteBuffer bb =
                    fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, size);

            if (size < minEntrySize) {
                ((MappedByteBuffer) bb).load();
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

    protected void recalcCacheStatsIfMonitoring(final FileCacheEntry entry) {
        if (isMonitoringEnabled) {
            recalcCacheStats(entry);
        }
    }

    protected final void recalcCacheStats(final FileCacheEntry entry) {
//        if (entry != null && entry.bb != null && entry.bb != nullByteBuffer) {
        if (entry != null) {
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
        response.setContentLength(entry.contentLength);

        if (flushBody) {
            final ByteBuffer sliced = entry.bb.slice();
            final Buffer buffer = MemoryUtils.wrap(memoryManager, sliced);

            final HttpContent content =
                    HttpContent.builder(response)
                    .content(buffer)
                    .last(true)
                    .build();

            return content;
        }

        return response;
    }

    /**
     * @return the port
     */
//    public int getPort() {
//        return port;
//    }

    /**
     * @param port the port to set
     */
//    public void setPort(int port) {
//        this.port = port;
//    }

    /**
     * @return the network address associated with this cache instance.
     */
//    public InetAddress getAddress() {
//        return address;
//    }
    /**
     * <p>
     * Associates the specified network address with this cache instance.
     * </p>
     *
     * @param address the network address
     */
//    public void setAddress(InetAddress address) {
//        this.address = address;
//    }


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
    public void setIsMonitoringEnabled(boolean isMe) {
        isMonitoringEnabled = isMe;
    }

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
     * Is the large file cache support enabled.
     */
    public void setLargeFileCacheEnabled(boolean isLargeEnabled) {
        this.isLargeFileCacheEnabled = isLargeEnabled;
    }

    /**
     * Is the large file cache support enabled.
     */
    public boolean getLargeFileCacheEnabled() {
        return isLargeFileCacheEnabled;
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
                    response.setHeader("ETag", getETag(entry));
                    return false;
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
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
            long contentLength = entry.contentLength;
            long lastModified = Long.parseLong(entry.lastModified);
            if ((contentLength >= 0) || (lastModified >= 0)) {
                result = "W/\"" + contentLength + "-"
                        + lastModified + "\"";
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
    protected boolean checkIfHeaders(final FileCacheEntry entry,
            final HttpRequestPacket request) throws IOException {

        return checkIfMatch(entry, request)
                && checkIfModifiedSince(entry, request)
                && checkIfNoneMatch(entry, request)
                && checkIfUnmodifiedSince(entry, request);

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

    private long parseLong(final String value, final int defaultValue) {
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignore) {
            }
        }

        return defaultValue;
    }

    public final class FileCacheEntry implements Runnable {
        public FileCacheKey key;

        public String host;
        public String requestURI;
        public String lastModified = "";
        public String contentType;
        public ByteBuffer bb;
        public String xPoweredBy;
        public boolean isInHeap = false;
        public String date;
        public String Etag;
        public Future future;
        public long contentLength = -1;
        public String keepAlive;

        @Override
        public void run() {
            fileCache.remove(key);

            if (!isInHeap) {
                subMappedMemorySize(bb.limit());
            } else {
                subHeapSize(bb.limit());
            }

            decOpenCacheEntries();

            if (future != null) {
                future.cancel(false);
            }
        }
    }
}
