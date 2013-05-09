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

package org.glassfish.grizzly.http.server.filecache.jmx;

import org.glassfish.grizzly.http.server.filecache.FileCacheEntry;
import org.glassfish.grizzly.http.server.filecache.FileCacheProbe;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.GmbalMBean;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedObject;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.glassfish.grizzly.jmxbase.GrizzlyJmxManager;

/**
 * This class provides a JMX view of the current operating state of the
 * FileCache.
 *
 * @since 2.0
 */
@ManagedObject
@Description("Static file caching implementation.  There will be one FileCache instance per NetworkListener.")
public class FileCache extends JmxObject {

    /**
     * The {@link org.glassfish.grizzly.http.server.filecache.FileCache} being managed.
     */
    private final org.glassfish.grizzly.http.server.filecache.FileCache fileCache;

    /**
     * The current {@link org.glassfish.grizzly.http.server.filecache.FileCache} entry count.
     */
    private final AtomicInteger cachedEntryCount = new AtomicInteger();

    /**
     * The number of cache hits.
     */
    private final AtomicLong cacheHitCount = new AtomicLong();

    /**
     * The number of cache misses.
     */
    private final AtomicLong cacheMissCount = new AtomicLong();

    /**
     * The number of cache errors.
     */
    private final AtomicInteger cacheErrorCount = new AtomicInteger();

    /**
     * The {@link FileCacheProbe} used to track cache statistics.
     */
    private final JMXFileCacheProbe fileCacheProbe = new JMXFileCacheProbe();



    // ------------------------------------------------------------ Constructors


    /**
     * Constructs a new JMX managed FileCache for the specified
     * {@link org.glassfish.grizzly.http.server.filecache.FileCache} instance.
     *
     * @param fileCache the {@link org.glassfish.grizzly.http.server.filecache.FileCache}
     *  to manage.
     */
    public FileCache(org.glassfish.grizzly.http.server.filecache.FileCache fileCache) {
        this.fileCache = fileCache;
    }


    // -------------------------------------------------- Methods from JmxObject


    /**
     * {@inheritDoc}
     */
    @Override
    public String getJmxName() {
        return "FileCache";
    }

    /**
     * <p>
     * {@inheritDoc}
     * </p>
     *
     * <p>
     * When invoked, this method will add a {@link FileCacheProbe} to track
     * statistics.
     * </p>
     */
    @Override
    protected void onRegister(GrizzlyJmxManager mom, GmbalMBean bean) {
        fileCache.getMonitoringConfig().addProbes(fileCacheProbe);
    }

    /**
     * <p>
     * {@inheritDoc}
     * </p>
     *
     * <p>
     * When invoked, this method will remove the {@link FileCacheProbe} added
     * by the {@link #onRegister(org.glassfish.grizzly.monitoring.jmx.GrizzlyJmxManager, org.glassfish.gmbal.GmbalMBean)}
     * call.
     * </p>
     */
    @Override
    protected void onDeregister(GrizzlyJmxManager mom) {
        fileCache.getMonitoringConfig().removeProbes(fileCacheProbe);
    }


    // --------------------------------------------------- File Cache Properties


    /**
     * @see org.glassfish.grizzly.http.server.filecache.FileCache#isEnabled()
     */
    @ManagedAttribute(id="file-cache-enabled")
    @Description("Indicates whether or not the file cache is enabled.")
    public boolean isFileCacheEnabled() {
        return fileCache.isEnabled();
    }

    /**
     * @see org.glassfish.grizzly.http.server.filecache.FileCache#getSecondsMaxAge()
     */
    @ManagedAttribute(id="max-age-seconds")
    @Description("The maximum age, in seconds, a resource may be cached.")
    public int getSecondsMaxAge() {
        return fileCache.getSecondsMaxAge();
    }

    /**
     * @see org.glassfish.grizzly.http.server.filecache.FileCache#getMaxCacheEntries()
     */
    @ManagedAttribute(id="max-number-of-cache-entries")
    @Description("The maxumim number of entries that may exist in the cache.")
    public int getMaxCacheEntries() {
        return fileCache.getMaxCacheEntries();
    }

    /**
     * @see org.glassfish.grizzly.http.server.filecache.FileCache#getMinEntrySize()
     */
    @ManagedAttribute(id="min-entry-size")
    @Description("The maximum size, in bytes, a file must be in order to be cached in the heap cache.")
    public long getMinEntrySize() {
        return fileCache.getMinEntrySize();
    }

    /**
     * @see org.glassfish.grizzly.http.server.filecache.FileCache#getMaxEntrySize()
     */
    @ManagedAttribute(id="max-entry-size")
    @Description("The maximum size, in bytes, a resource may be before it can no longer be considered cachable.")
    public long getMaxEntrySize() {
        return fileCache.getMaxEntrySize();
    }

    /**
     * @see org.glassfish.grizzly.http.server.filecache.FileCache#getMaxLargeFileCacheSize()
     */
    @ManagedAttribute(id="memory-mapped-file-cache-size")
    @Description("The maximum size, in bytes, of the memory mapped cache for large files.")
    public long getMaxLargeFileCacheSize() {
        return fileCache.getMaxLargeFileCacheSize();
    }

    /**
     * @see org.glassfish.grizzly.http.server.filecache.FileCache#getMaxSmallFileCacheSize()
     */
    @ManagedAttribute(id="heap-file-cache-size")
    @Description("The maximum size, in bytes, of the heap cache for files below the water mark set by min-entry-size.")
    public long getMaxSmallFileCacheSize() {
        return fileCache.getMaxSmallFileCacheSize();
    }


    /**
     * @return the total number of cached entries.
     */
    @ManagedAttribute(id="cached-entries-count")
    @Description("The current cached entry count.")
    public int getCachedEntryCount() {
        return cachedEntryCount.get();
    }

    /**
     * @return the total number of cache hits.
     */
    @ManagedAttribute(id="cache-hit-count")
    @Description("The total number of cache hits.")
    public long getCacheHitCount() {
        return cacheHitCount.get();
    }

    /**
     * @return the total number of cache misses.
     */
    @ManagedAttribute(id="cache-miss-count")
    @Description("The total number of cache misses.")
    public long getCacheMissCount() {
        return cacheMissCount.get();
    }

    /**
     * @return the total number of cache errors.
     */
    @ManagedAttribute(id="cache-error-count")
    @Description("The total number of cache errors.")
    public int getCacheErrorCount() {
        return cacheErrorCount.get();
    }

    /**
     * @return the total size, in bytes, of the heap memory cache.
     */
    @ManagedAttribute(id="heap-cache-size-in-bytes")
    @Description("The current size, in bytes, of the heap memory cache.")
    public long getHeapMemoryInBytes() {
        return fileCache.getHeapCacheSize();        
    }

    /**
     * @return the total size, in bytes, of the mapped memory cache.
     */
    @ManagedAttribute(id="mapped-memory-cache-size-in-bytes")
    @Description("The current size, in bytes, of the mapped memory cache.")
    public long getMappedMemorytInBytes() {
        return fileCache.getMappedCacheSize();
    }


    // ---------------------------------------------------------- Nested Classes


    /**
     * JMX statistic gathering {@link FileCacheProbe}.
     */
    private final class JMXFileCacheProbe implements FileCacheProbe {


        // ----------------------------------------- Methods from FileCacheProbe


        @Override
        public void onEntryAddedEvent(org.glassfish.grizzly.http.server.filecache.FileCache fileCache, FileCacheEntry entry) {
            cachedEntryCount.incrementAndGet();
        }

        @Override
        public void onEntryRemovedEvent(org.glassfish.grizzly.http.server.filecache.FileCache fileCache, FileCacheEntry entry) {
            if (cachedEntryCount.get() > 0) {
                cachedEntryCount.decrementAndGet();
            }
        }

        @Override
        public void onEntryHitEvent(org.glassfish.grizzly.http.server.filecache.FileCache fileCache, FileCacheEntry entry) {
            cacheHitCount.incrementAndGet();
        }

        @Override
        public void onEntryMissedEvent(org.glassfish.grizzly.http.server.filecache.FileCache fileCache, String host, String requestURI) {
            cacheMissCount.incrementAndGet();
        }

        @Override
        public void onErrorEvent(org.glassfish.grizzly.http.server.filecache.FileCache fileCache, Throwable error) {
            cacheErrorCount.incrementAndGet();
        }

    } // END JMXFileCacheProbe

}
