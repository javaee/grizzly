/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.connectionpool;

import org.glassfish.grizzly.Connection;

/**
 * Pooled {@link Connection} information, that might be used for monitoring reasons.
 * 
 * @param <E>
 * @author Alexey Stashok
 */
public final class ConnectionInfo<E> {
    final Connection connection;
    final Link<ConnectionInfo<E>> readyStateLink;
    final SingleEndpointPool<E> endpointPool;
    
    long ttlTimeout; // the place holder for TTL time stamp
    
    private final long pooledTimeStamp;

    ConnectionInfo(final Connection connection, final SingleEndpointPool<E> endpointPool) {
        this.connection = connection;
        this.endpointPool = endpointPool;
        this.readyStateLink = new Link<ConnectionInfo<E>>(this);
        pooledTimeStamp = System.currentTimeMillis();
    }

    /**
     * @return <tt>true</tt> if the {@link Connection} is in ready state,
     * waiting for a user to pull it out from the pool. Returns <tt>false</tt>
     * if the {@link Connection} is currently busy.
     */
    public boolean isReady() {
        synchronized(endpointPool.poolSync) {
            return readyStateLink.isAttached();
        }
    }
    
    /**
     * @return the timestamp (in milliseconds) when this {@link Connection} was
     * returned to the pool and its state switched to ready, or <tt>-1</tt> if
     * the {@link Connection} is currently in busy state.
     */
    public long getReadyTimeStamp() {
        synchronized(endpointPool.poolSync) {
            return readyStateLink.getAttachmentTimeStamp();
        }
    }
    
    /**
     * @return the timestamp (in milliseconds) when this {@link Connection} was
     * added to the pool: either created directly by pool or attached.
     */
    public long getPooledTimeStamp() {
        return pooledTimeStamp;
    }

    @Override
    public String toString() {
        return "ConnectionInfo{"
                    + "connection=" + connection
                    + ", readyStateLink=" + readyStateLink
                    + ", endpointPool=" + endpointPool
                    + ", pooledTimeStamp=" + pooledTimeStamp
                    + "} " + super.toString();
    }
}
