/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.asyncqueue;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.Interceptor;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.ThreadCache;
import com.sun.grizzly.utils.DebugPoint;
import java.util.concurrent.Future;

/**
 * {@link AsyncQueue} read element unit
 * 
 * @author Alexey Stashok
 */
public final class AsyncReadQueueRecord extends AsyncQueueRecord<ReadResult> {
    private static final ThreadCache.CachedTypeIndex<AsyncReadQueueRecord> CACHE_IDX =
            ThreadCache.obtainIndex(AsyncReadQueueRecord.class, 2);
    
    public static AsyncReadQueueRecord create(Object message,
            Future future,
            ReadResult currentResult, CompletionHandler completionHandler,
            Interceptor<ReadResult> interceptor) {

        final AsyncReadQueueRecord asyncReadQueueRecord =
                ThreadCache.takeFromCache(CACHE_IDX);
        
        if (asyncReadQueueRecord != null) {
            asyncReadQueueRecord.isRecycled = false;
            asyncReadQueueRecord.set(message, future, currentResult,
                    completionHandler, interceptor);
            return asyncReadQueueRecord;
        }

        return new AsyncReadQueueRecord(message, future, currentResult,
                completionHandler, interceptor);
    }

    private Buffer remainderBuffer;
    
    private AsyncReadQueueRecord(Object message, Future future,
            ReadResult currentResult, CompletionHandler completionHandler,
            Interceptor<ReadResult> interceptor) {
        
        super(message, future, currentResult, completionHandler, interceptor);
    }

    public Buffer getRemainderBuffer() {
        checkRecycled();
        return remainderBuffer;
    }

    public void setRemainderBuffer(Buffer remainderBuffer) {
        checkRecycled();
        this.remainderBuffer = remainderBuffer;
    }

    protected final void reset() {
        set(null, null, null, null, null);
        remainderBuffer = null;
    }

    @Override
    public void recycle() {
        checkRecycled();
        
        reset();
        isRecycled = true;
        if (Grizzly.isTrackingThreadCache()) {
            recycleTrack = new DebugPoint(new Exception(),
                    Thread.currentThread().getName());
        }

        ThreadCache.putToCache(CACHE_IDX, this);
    }
}

