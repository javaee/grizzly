/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.server.util.SimpleDateFormats;
import org.glassfish.grizzly.http.util.FastHttpDateFormat;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.utils.DelayedExecutor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import org.glassfish.grizzly.http.CompressionConfig;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.util.ContentType;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.monitoring.DefaultMonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringAware;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringUtils;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * This class implements a file caching mechanism used to cache static resources.
 *
 * @author Jeanfrancois Arcand
 * @author Scott Oaks
 */
public class FileCache implements MonitoringAware<FileCacheProbe> {
    private static final File TMP_DIR =
            new File(System.getProperty("java.io.tmpdir"));
    
    final static String[] COMPRESSION_ALIASES = {"gzip"};

    public enum CacheType {
        HEAP, MAPPED, FILE, TIMESTAMP
    }

    public enum CacheResult {
        OK_CACHED,
        OK_CACHED_TIMESTAMP,
        FAILED_CACHE_FULL,
        FAILED_ENTRY_EXISTS,
        FAILED
    }

    private static final Logger LOGGER = Grizzly.logger(FileCache.class);
    
    /**
     * Cache size.
     */
    private final AtomicInteger cacheSize = new AtomicInteger();
    
    /**
     * A {@link ByteBuffer} cache of static pages.
     */
    private final ConcurrentMap<FileCacheKey, FileCacheEntry> fileCacheMap =
            DataStructures.getConcurrentMap();
    
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
    private final AtomicLong mappedMemorySize = new AtomicLong();

    /**
     * The current cache size in bytes
     */
    private final AtomicLong heapSize = new AtomicLong();

    /**
     * Is the file cache enabled.
     */
    private boolean enabled = true;
    
    private DelayedExecutor.DelayQueue<FileCacheEntry> delayQueue;

    /**
     * Folder to store compressed cached files
     */
    private volatile File compressedFilesFolder = TMP_DIR;
    /**
     * Compression configuration, used to decide if cached resource
     * has to be compressed or not
     */
    private final CompressionConfig compressionConfig = new CompressionConfig();
    
    /**
     * <tt>true</tt>, if zero-copy file-send feature could be used, or
     * <tt>false</tt> otherwise.
     */
    private boolean fileSendEnabled;
    
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

    public void initialize(final DelayedExecutor delayedExecutor) {
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
        if (fileCacheMap.putIfAbsent(key, NULL_CACHE_ENTRY) != null) {
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

        final HttpResponsePacket response = request.getResponse();
        final MimeHeaders headers = response.getHeaders();
        
        final String contentType = response.getContentType();
        
        final FileCacheEntry entry;
        if (cacheFile != null) { // If we have a file - try to create File-aware cache resource
            entry = createEntry(cacheFile);
            entry.setCanBeCompressed(canBeCompressed(cacheFile, contentType));
        } else {
            entry = new FileCacheEntry(this);
            entry.type = CacheType.TIMESTAMP;
        }

        entry.key = key;
        entry.requestURI = requestURI;

        entry.lastModified = lastModified;
        entry.contentType = ContentType.newContentType(contentType);
        entry.xPoweredBy = headers.getHeader(Header.XPoweredBy);
        entry.date = headers.getHeader(Header.Date);
        entry.lastModifiedHeader = headers.getHeader(Header.LastModified);
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
     * Returns {@link FileCacheEntry}.
     * If {@link FileCacheEntry} has been found - this method also sets
     * correspondent {@link HttpResponsePacket} status code and reason phrase.
     */
    public FileCacheEntry get(final HttpRequestPacket request) {
        // It should be faster than calculating the key hash code
        if (cacheSize.get() == 0) return null;

        final LazyFileCacheKey key = LazyFileCacheKey.create(request);
        final FileCacheEntry entry = fileCacheMap.get(key);
        key.recycle();
        try {
            if (entry != null && entry != NULL_CACHE_ENTRY) {
                // determine if we need to send the cache entry bytes
                // to the user-agent
                final HttpStatus httpStatus = checkIfHeaders(entry, request);

                final boolean flushBody = (httpStatus == null);
                if (flushBody && entry.type == CacheType.TIMESTAMP) {
                    return null; // this will cause control to be passed to the static handler
                }
                
                request.getResponse().setStatus(httpStatus != null?
                        httpStatus :
                        HttpStatus.OK_200);
                
                notifyProbesEntryHit(this, entry);
                return entry;
            }
            
            notifyProbesEntryMissed(this, request);
        } catch (Exception e) {
            notifyProbesError(this, e);
            // If an unexpected exception occurs, try to serve the page
            // as if it wasn't in a cache.
            LOGGER.log(Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_HTTP_SERVER_FILECACHE_GENERAL_ERROR(), e);
        }
        
        return null;
    }

    protected void remove(final FileCacheEntry entry) {
        if (fileCacheMap.remove(entry.key) != null) {
            cacheSize.decrementAndGet();
        }

        if (entry.type == FileCache.CacheType.MAPPED) {
            subMappedMemorySize(entry.bb.remaining());
        } else if (entry.type == FileCache.CacheType.HEAP) {
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
     * Creates {@link FileCacheEntry}.
     */
    private FileCacheEntry createEntry(final File file) {
        FileCacheEntry entry = tryMapFileToBuffer(file);
        if (entry == null) {
            entry = new FileCacheEntry(this);
            entry.type = CacheType.FILE;
        }
        
        entry.plainFile = file;
        entry.plainFileSize = file.length();

        return entry;
    }
    
    /**
     * Map the file to a {@link ByteBuffer}
     * @return the preinitialized {@link FileCacheEntry}
     */
    private FileCacheEntry tryMapFileToBuffer(final File file) {
        
        final long size = file.length();
        if (size > getMaxEntrySize()) {
            return null;
        }
        
        final CacheType type;
        final ByteBuffer bb;
        FileChannel fileChannel = null;
        FileInputStream stream = null;
        try {
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

            stream = new FileInputStream(file);
            fileChannel = stream.getChannel();

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
        entry.plainFileSize = size;
        entry.bb = bb;

        return entry;
    }

    /**
     * Checks if the {@link File} with the given content-type could be compressed.
     */
    private boolean canBeCompressed(final File cacheFile,
            final String contentType) {
        switch (compressionConfig.getCompressionMode()) {
            case FORCE: return true;
            case OFF: return false;
            case ON: {
                if (cacheFile.length() <
                        compressionConfig.getCompressionMinSize()) {
                    return false;
                }
                
                return compressionConfig.checkMimeType(contentType);
            }
                
            default: throw new IllegalStateException("Unknown mode");
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

    /**
     * Returns the <tt>FileCache</tt> compression configuration settings.
     */
    public CompressionConfig getCompressionConfig() {
        return compressionConfig;
    }

    /**
     * Returns the folder to be used to store temporary compressed files.
     */
    public File getCompressedFilesFolder() {
        return compressedFilesFolder;
    }

    /**
     * Sets the folder to be used to store temporary compressed files.
     */
    public void setCompressedFilesFolder(final File compressedFilesFolder) {
        this.compressedFilesFolder = compressedFilesFolder != null ?
                compressedFilesFolder :
                TMP_DIR;
    }

    /**
     * <p>
     * Returns <code>true</code> if File resources may be be sent using
     * {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}.
     * </p>
     * <p/>
     * <p>
     * By default, this property will be true, except in the following cases:
     * </p>
     * <p/>
     * <ul>
     * <li>JVM OS is HP-UX</li>
     * <li>JVM OS is Linux, and the Oracle JVM in use is 1.6.0_17 or older</li>
     * </ul>
     * <p/>
     * <p/>
     * <p>
     * Finally, if the connection between endpoints is secure, send file functionality
     * will be disabled regardless of configuration.
     * </p>
     *
     * @return <code>true</code> if resources will be sent using
     *         {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}.
     * @since 2.3.5
     */
    public boolean isFileSendEnabled() {
        return fileSendEnabled;
    }

    /**
     * Configure whether or send-file support will enabled which allows sending
     * {@link java.io.File} resources via {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}.
     * If disabled, the more traditional byte[] copy will be used to send content.
     *
     * @param fileSendEnabled <code>true</code> to enable {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}
     *                        support.
     * @since 2.3.5
     */
    public void setFileSendEnabled(boolean fileSendEnabled) {
        this.fileSendEnabled = fileSendEnabled;
    }
    
    /**
     * Creates a temporary compressed representation of the given cache entry.
     */
    protected void compressFile(final FileCacheEntry entry) {
        try {
            final File tmpCompressedFile = File.createTempFile(
                    String.valueOf(entry.plainFile.hashCode()),
                    ".tmpzip", compressedFilesFolder);
            tmpCompressedFile.deleteOnExit();

            InputStream in = null;
            OutputStream out = null;
            try {
                in = new FileInputStream(entry.plainFile);
                out = new GZIPOutputStream(
                        new FileOutputStream(tmpCompressedFile));
                
                final byte[] tmp = new byte[1024];
                
                do {
                    final int readNow = in.read(tmp);
                    if (readNow == -1) {
                        break;
                    }
                    
                    out.write(tmp, 0, readNow);
                } while (true);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
            }
            
            final long size = tmpCompressedFile.length();
            
            switch (entry.type) {
                case HEAP:
                case MAPPED: {
                    final FileInputStream cFis =
                            new FileInputStream(tmpCompressedFile);
                    
                    try {
                        final FileChannel cFileChannel = cFis.getChannel();

                        final MappedByteBuffer compressedBb = cFileChannel.map(
                                FileChannel.MapMode.READ_ONLY, 0, size);

                        if (entry.type == CacheType.HEAP) {
                            compressedBb.load();
                        }
                        
                        entry.compressedBb = compressedBb;
                    } finally {
                        cFis.close();
                    }
                    
                    break;
                }
                case FILE: {
                    break;
                }

                default: throw new IllegalStateException("The type is not supported: " + entry.type);
            }
            
            entry.compressedFileSize = size;
            entry.compressedFile = tmpCompressedFile;
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Can not compress file: " + entry.plainFile, e);
        }
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
     * @return {@link HttpStatus} if the decision has been made and the response
     *         status has been defined, or <tt>null</tt> otherwise
     */
    private HttpStatus checkIfHeaders(final FileCacheEntry entry,
            final HttpRequestPacket request) throws IOException {

        HttpStatus httpStatus = checkIfMatch(entry, request);
        if (httpStatus == null) {
            httpStatus = checkIfModifiedSince(entry, request);
            if (httpStatus == null) {
                httpStatus = checkIfNoneMatch(entry, request);
                if (httpStatus == null) {
                    httpStatus = checkIfUnmodifiedSince(entry, request);
                }
            }
        }

        return httpStatus;
    }

    /**
     * Check if the if-modified-since condition is satisfied.
     *
     * @return {@link HttpStatus} if the decision has been made and the response
     *         status has been defined, or <tt>null</tt> otherwise
     */
    private HttpStatus checkIfModifiedSince(final FileCacheEntry entry,
            final HttpRequestPacket request) throws IOException {
        try {
            final String reqModified = request.getHeader(Header.IfModifiedSince);
            if (reqModified != null) {
                // optimization - assume the String value sent in the
                // client's If-Modified-Since header is the same as what
                // was originally sent
                if (reqModified.equals(entry.lastModifiedHeader)) {
                    return HttpStatus.NOT_MODIFIED_304;
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
                        return HttpStatus.NOT_MODIFIED_304;
                    }
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
            notifyProbesError(this, illegalArgument);
        }
        
        return null;
    }

    /**
     * Check if the if-none-match condition is satisfied.

     * @return {@link HttpStatus} if the decision has been made and the response
     *         status has been defined, or <tt>null</tt> otherwise
     */
    private HttpStatus checkIfNoneMatch(final FileCacheEntry entry,
            final HttpRequestPacket request) throws IOException {

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
                    return HttpStatus.NOT_MODIFIED_304;
                } else {
                    return HttpStatus.PRECONDITION_FAILED_412;
                }
            }
        }
        
        return null;
    }

    /**
     * Check if the if-unmodified-since condition is satisfied.
     *
     * @return {@link HttpStatus} if the decision has been made and the response
     *         status has been defined, or <tt>null</tt> otherwise
     */
    private HttpStatus checkIfUnmodifiedSince(final FileCacheEntry entry,
            final HttpRequestPacket request) throws IOException {

        try {
            long lastModified = entry.lastModified;
            String h = request.getHeader(Header.IfUnmodifiedSince);
            if (h != null) {
                // optimization - assume the String value sent in the
                // client's If-Unmodified-Since header is the same as what
                // was originally sent
                if (h.equals(entry.lastModifiedHeader)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    return HttpStatus.PRECONDITION_FAILED_412;
                }
                long headerValue = convertToLong(h);
                if (headerValue != -1) {
                    if (headerValue - lastModified <= 1000) {
                        // The entity has not been modified since the date
                        // specified by the client. This is not an error case.
                        return HttpStatus.PRECONDITION_FAILED_412;
                    }
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
            notifyProbesError(this, illegalArgument);
        }
        
        return null;
    }

    /**
     * Check if the if-match condition is satisfied.
     *
     * @param request The servlet request we are processing
     * @param entry the FileCacheEntry to validate
     * @return {@link HttpStatus} if the decision has been made and the response
     *         status has been defined, or <tt>null</tt> otherwise
     */
    private HttpStatus checkIfMatch(final FileCacheEntry entry,
            final HttpRequestPacket request) throws IOException {
        
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
                    return HttpStatus.PRECONDITION_FAILED_412;
                }

            }
        }
        
        return null;
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
