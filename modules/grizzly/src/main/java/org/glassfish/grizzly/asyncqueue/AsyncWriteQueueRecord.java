/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.asyncqueue;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Interceptor;
import java.util.concurrent.Future;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.utils.DebugPoint;

/**
 * {@link AsyncQueue} write element unit
 * 
 * @author Alexey Stashok
 */
public class AsyncWriteQueueRecord extends AsyncQueueRecord<WriteResult> {
    private static final ThreadCache.CachedTypeIndex<AsyncWriteQueueRecord> CACHE_IDX =
            ThreadCache.obtainIndex(AsyncWriteQueueRecord.class, 2);

    public static AsyncWriteQueueRecord create(
            final Connection connection,
            final Object message,
            final Future future,
            final WriteResult currentResult,
            final CompletionHandler completionHandler,
            final Interceptor interceptor,
            final Object dstAddress,
            final Buffer outputBuffer,
            final boolean isEmptyRecord) {

        final AsyncWriteQueueRecord asyncWriteQueueRecord =
                ThreadCache.takeFromCache(CACHE_IDX);
        
        if (asyncWriteQueueRecord != null) {
            asyncWriteQueueRecord.isRecycled = false;
            asyncWriteQueueRecord.set(connection, message, future, currentResult,
                    completionHandler, interceptor, dstAddress,
                    outputBuffer, isEmptyRecord);
            
            return asyncWriteQueueRecord;
        }

        return new AsyncWriteQueueRecord(connection, message, future,
                currentResult, completionHandler, interceptor, dstAddress,
                outputBuffer, isEmptyRecord);
    }
    
    private boolean isEmptyRecord;
    private Object dstAddress;
    private Buffer outputBuffer;

    protected AsyncWriteQueueRecord(final Connection connection,
            final Object message, final Future future,
            final WriteResult currentResult,
            final CompletionHandler completionHandler,
            final Interceptor interceptor, final Object dstAddress,
            final Buffer outputBuffer,
            final boolean isEmptyRecord) {

        super(connection, message, future, currentResult, completionHandler,
                interceptor);
        this.dstAddress = dstAddress;
        this.outputBuffer = outputBuffer;
        this.isEmptyRecord = isEmptyRecord;
    }

    protected void set(final Connection connection, final Object message,
            final Future future, final WriteResult currentResult,
            final CompletionHandler completionHandler,
            final Interceptor interceptor, final Object dstAddress,
            final Buffer outputBuffer,
            final boolean isEmptyRecord) {
        super.set(connection, message, future, currentResult,
                completionHandler, interceptor);

        this.dstAddress = dstAddress;
        this.outputBuffer = outputBuffer;
        this.isEmptyRecord = isEmptyRecord;
    }

    public final Object getDstAddress() {
        checkRecycled();
        return dstAddress;
    }

    public Buffer getOutputBuffer() {
        checkRecycled();
        return outputBuffer;
    }

    public void setOutputBuffer(Buffer outputBuffer) {
        checkRecycled();
        this.outputBuffer = outputBuffer;
    }

    public boolean isEmptyRecord() {
        return isEmptyRecord;
    }

    public void setEmptyRecord(boolean isEmptyRecord) {
        this.isEmptyRecord = isEmptyRecord;
    }

    protected final void reset() {
        set(null, null, null, null, null, null, null, null, false);
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
