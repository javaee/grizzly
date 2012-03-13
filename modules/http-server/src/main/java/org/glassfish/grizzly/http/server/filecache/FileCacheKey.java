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

/**
 * The entry key in the file cache map.
 * 
 * @author Alexey Stashok
 */
public class FileCacheKey implements Cacheable {

    private static final ThreadCache.CachedTypeIndex<FileCacheKey> CACHE_IDX =
                    ThreadCache.obtainIndex(FileCacheKey.class, 16);

    protected String host;
    protected String uri;


    // ------------------------------------------------------------ Constructors


    protected FileCacheKey() { }

    protected FileCacheKey(final String host, final String uri) {
        this.host = host;
        this.uri = uri;
    }


    // -------------------------------------------------- Methods from Cacheable

    @Override
    public void recycle() {
        host = null;
        uri = null;
        ThreadCache.putToCache(CACHE_IDX, this);
    }


    // ---------------------------------------------------------- Public Methods


    public static FileCacheKey create(final String host, final String uri) {
        final FileCacheKey key =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (key != null) {
            key.host = host;
            key.uri = uri;
            return key;
        }

        return new FileCacheKey(host, uri);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }        
        final FileCacheKey other = (FileCacheKey) obj;
        
        final String otherHost = other.host;
        if ((this.host == null) ? (otherHost != null) : !this.host.equals(otherHost)) {
            return false;
        }

        final String otherUri = other.uri;
        if ((this.uri == null) ? (otherUri != null) : !this.uri.equals(otherUri)) {
            return false;
        }
        
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 23 * hash + (this.host != null ? this.host.hashCode() : 0);
        hash = 23 * hash + (this.uri != null ? this.uri.hashCode() : 0);
        return hash;
    }


    // ------------------------------------------------------- Protected Methods


    protected String getHost() {
        return host;
    }

    protected String getUri() {
        return uri;
    }

}
