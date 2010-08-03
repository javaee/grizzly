/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.http.server.filecache;

/**
 * Configuration/tuning parameters for {@link com.sun.grizzly.http.server.filecache.FileCache}.
 *
 * @since 2.0
 */
public class FileCacheConfiguration {

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


    private volatile boolean enabled;


    // ---------------------------------------------------------- Public Methods


    /**
     * Enables/disables the {@link FileCache} for this.  By default, the
     * {@link FileCache} is disabled.
     *
     * @param enabled <code>true</code> to enable the {@link FileCache}.
     */
    public void setFileCacheEnabled(boolean enabled) {

        this.enabled = enabled;

    }


    /**
     * @return <code>true</code> if the {@link FileCache} is enabled,
     *  otherwise <code>false</code>
     */
    public boolean isFileCacheEnabled() {

        return enabled;

    }

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
     * @return the maximum size, in bytes, a file must be in order to be cached
     *  in the heap cache.
     *
     * @see FileCacheConfiguration#setMaxSmallFileCacheSize(long)
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
     *  longer be considered cachable.
     */
    public long getMaxEntrySize() {
        return maxEntrySize;
    }

    /**
     * The maximum size, in bytes, a resource may be before it can no
     * longer be considered cachable.
     *
     * @param maxEntrySize the maximum size, in bytes, a resource may be before it can no
     *  longer be considered cachable.
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
     *
     * @see FileCacheConfiguration#setMaxSmallFileCacheSize(long)
     */
    public void setMaxSmallFileCacheSize(long maxSmallFileCacheSize) {
        this.maxSmallFileCacheSize = maxSmallFileCacheSize;
    }
    
}
