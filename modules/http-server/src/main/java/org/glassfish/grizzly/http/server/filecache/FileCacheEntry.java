/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.ThreadCache;

import java.nio.ByteBuffer;

/**
 * The entry value in the file cache map.
 *
 * @author Alexey Stashok
 */
public final class FileCacheEntry implements Runnable, Cacheable {

    private static final ThreadCache.CachedTypeIndex<FileCacheEntry> CACHE_IDX =
                    ThreadCache.obtainIndex(FileCacheEntry.class, 16);

    public FileCacheKey key;
    public String host;
    public String requestURI;
    public String lastModified = "";
    public String contentType;
    public ByteBuffer bb;
    public String xPoweredBy;
    public FileCache.CacheType type;
    public String date;
    public String Etag;
    public long contentLength = -1;
    public long fileSize = -1;
    public String keepAlive;
    
    public volatile long timeoutMillis;

    protected FileCache fileCache;


    // ------------------------------------------------------------ Constructors


    protected FileCacheEntry(final FileCache fileCache) {
        this.fileCache = fileCache;
    }


    // ---------------------------------------------------------- Public Methods


    public static FileCacheEntry create(final FileCache fileCache) {
        final FileCacheEntry entry =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (entry != null) {
            entry.fileCache = fileCache;
            return entry;
        }

        return new FileCacheEntry(fileCache);
    }


    // --------------------------------------------------- Methods from Runnable


    @Override
    public void run() {
        fileCache.remove(this);
    }


    // -------------------------------------------------- Methods from Cacheable


    @SuppressWarnings("UnusedDeclaration")
    @Override
    public void recycle() {
        if (key != null) {
            key.recycle();
        }
        FileCacheKey key = null;
        String host = null;
        String requestURI = null;
        String lastModified = "";
        String contentType = null;
        ByteBuffer bb = null;
        String xPoweredBy = null;
        FileCache.CacheType type = null;
        String date = null;
        String Etag = null;
        long contentLength = -1;
        long fileSize = -1;
        String keepAlive = null;
        long timeoutMillis = 0;
        FileCache fileCache = null;
    }

}
