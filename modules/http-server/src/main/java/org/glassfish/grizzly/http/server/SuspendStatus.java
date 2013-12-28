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

package org.glassfish.grizzly.http.server;

import org.glassfish.grizzly.ThreadCache;

/**
 * The request/response suspend status bound to a specific thread.
 * 
 * @author Alexey Stashok
 */
public final class SuspendStatus {
    private static final ThreadCache.CachedTypeIndex<SuspendStatus> CACHE_IDX =
            ThreadCache.obtainIndex(SuspendStatus.class, 4);

    public static SuspendStatus create() {
        SuspendStatus status = ThreadCache.takeFromCache(CACHE_IDX);
        if (status == null) {
            status = new SuspendStatus();
        }
        
        assert status.initThread == Thread.currentThread();
        
        status.state = State.NOT_SUSPENDED;

        return status;
    }
    
    private static enum State {
        NOT_SUSPENDED, SUSPENDED, INVALIDATED;
    }
    
    private State state;
    
    private final Thread initThread;

    private SuspendStatus() {
        initThread = Thread.currentThread();
    }
    
    public void suspend() {
        assert Thread.currentThread() == initThread;

        if (state != State.NOT_SUSPENDED) {
            throw new IllegalStateException("Can not suspend. Expected suspend state='" + State.NOT_SUSPENDED + "' but was '" + state +"'");
        }
        
        state = State.SUSPENDED;
    }
    
    boolean getAndInvalidate() {
        assert Thread.currentThread() == initThread;
        
        final boolean wasSuspended = (state == State.SUSPENDED);
        state = State.INVALIDATED;
        
        ThreadCache.putToCache(initThread, CACHE_IDX, this);
        
        return wasSuspended;
    }
    
    public void reset() {
        assert Thread.currentThread() == initThread;
        
        state = State.NOT_SUSPENDED;
    }
}
