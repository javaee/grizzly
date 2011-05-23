/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.util.DataChunk;

/**
 * Lazy {@link FileCacheKey} object.
 * 
 * @author Alexey Stashok
 */
public class LazyFileCacheKey extends FileCacheKey {
    private final HttpRequestPacket request;
    private boolean isInitialized;
    
    public LazyFileCacheKey(final HttpRequestPacket request) {
        this.request = request;
    }

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
        int hash = 3;
        final DataChunk hostDC = getHostLazy();
        final DataChunk uriDC = getUriLazy();
        
        hash = 23 * hash + (hostDC != null ? hostDC.hashCode() : 0);
        hash = 23 * hash + (uriDC != null ? uriDC.hashCode() : 0);
        return hash;
    }
    
    private void initialize() {
        host = request.getHeader("host");
        uri = request.getRequestURI();
    }
    
    private DataChunk getHostLazy() {
        return request.getHeaders().getValue("host");
    }
    
    private DataChunk getUriLazy() {
        return request.getRequestURIRef().getRequestURIBC();
    }
}
