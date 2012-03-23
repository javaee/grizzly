/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;

/**
 * Lazy {@link FileCacheKey} object.
 * 
 * @author Alexey Stashok
 */
public class LazyFileCacheKey extends FileCacheKey {

    private static final ThreadCache.CachedTypeIndex<LazyFileCacheKey> CACHE_IDX =
                ThreadCache.obtainIndex(LazyFileCacheKey.class, 16);

    private HttpRequestPacket request;
    private boolean isInitialized;
    private int hashCode;


    // ------------------------------------------------------------ Constructors

    
    private LazyFileCacheKey(final HttpRequestPacket request) {
        this.request = request;
    }


    // ----------------------------------------------- Methods from FileCacheKey


    @Override
    protected String getHost() {
        if (!isInitialized) {
            initialize();
        }
        
        return super.getHost();
    }

    @Override
    protected String getUri() {
        if (!isInitialized) {
            initialize();
        }
        
        return super.getUri();
    }


    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        host = null;
        uri = null;
        isInitialized = false;
        request = null;
        hashCode = 0;
        ThreadCache.putToCache(CACHE_IDX, this);
    }


    // ---------------------------------------------------------- Public Methods


    public static LazyFileCacheKey create(final HttpRequestPacket request) {
        final LazyFileCacheKey key =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (key != null) {
            key.request = request;
            return key;
        }

        return new LazyFileCacheKey(request);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass().isAssignableFrom(obj.getClass())) {
            return false;
        }        
        final FileCacheKey other = (FileCacheKey) obj;
        
        final String otherHost = other.host;
        final DataChunk hostDC = getHostLazy();
        if ((hostDC == null || hostDC.isNull()) ? (otherHost != null) : !hostDC.equals(otherHost)) {
            return false;
        }

        final String otherUri = other.uri;
        final DataChunk uriDC = getUriLazy();
        if ((uriDC == null || uriDC.isNull()) ? (otherUri != null) : !uriDC.equals(otherUri)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            int hash = 3;
            final DataChunk hostDC = getHostLazy();
            final DataChunk uriDC = getUriLazy();
        
            hash = 23 * hash + (hostDC != null ? hostDC.hashCode() : 0);
            hash = 23 * hash + (uriDC != null ? uriDC.hashCode() : 0);
            hashCode = hash;
        }
        return hashCode;
    }


    // --------------------------------------------------------- Private Methods

    
    private void initialize() {
        isInitialized = true;
        host = request.getHeader(Header.Host);
        uri = request.getRequestURI();
    }
    
    private DataChunk getHostLazy() {
        return request.getHeaders().getValue(Header.Host);
    }
    
    private DataChunk getUriLazy() {
        return request.getRequestURIRef().getRequestURIBC();
    }
}
