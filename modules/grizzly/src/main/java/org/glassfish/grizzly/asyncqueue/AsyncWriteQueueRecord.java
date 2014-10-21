/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.*;
import org.glassfish.grizzly.utils.DebugPoint;

/**
 * {@link AsyncQueue} write element unit
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("deprecation")
public class AsyncWriteQueueRecord extends AsyncQueueRecord<RecordWriteResult> {
    public final static int UNCOUNTABLE_RECORD_SPACE_VALUE = 1;

    private static final ThreadCache.CachedTypeIndex<AsyncWriteQueueRecord> CACHE_IDX =
            ThreadCache.obtainIndex(AsyncWriteQueueRecord.class, Writer.Reentrant.getMaxReentrants());

    public static AsyncWriteQueueRecord create(
            final Connection connection,
            final WritableMessage message,
            final CompletionHandler completionHandler,
            final Object dstAddress,
            final PushBackHandler pushbackHandler,
            final boolean isUncountable) {

        final AsyncWriteQueueRecord asyncWriteQueueRecord =
                ThreadCache.takeFromCache(CACHE_IDX);
        
        if (asyncWriteQueueRecord != null) {
            asyncWriteQueueRecord.isRecycled = false;
            asyncWriteQueueRecord.set(connection, message,
                    completionHandler, dstAddress, pushbackHandler, isUncountable);
            
            return asyncWriteQueueRecord;
        }

        return new AsyncWriteQueueRecord(connection, message,
                completionHandler, dstAddress, pushbackHandler,
                isUncountable);
    }
    
    private long initialMessageSize;
    private boolean isUncountable;
    private Object dstAddress;
    private PushBackHandler pushBackHandler;

    private final RecordWriteResult writeResult = new RecordWriteResult();
    
    protected AsyncWriteQueueRecord(final Connection connection,
            final WritableMessage message,
            final CompletionHandler completionHandler,
            final Object dstAddress,
            final PushBackHandler pushBackHandler,
            final boolean isUncountable) {

        set(connection, message, completionHandler, dstAddress,
                pushBackHandler, isUncountable);
    }

    @SuppressWarnings("unchecked")
    protected void set(final Connection connection, final WritableMessage message,
            final CompletionHandler completionHandler,
            final Object dstAddress,
            final PushBackHandler pushBackHandler,
            final boolean isUncountable) {
        super.set(connection, message, completionHandler);
        
        this.dstAddress = dstAddress;
        this.isUncountable = isUncountable;
        this.initialMessageSize = message != null ? message.remaining() : 0;
        this.pushBackHandler = pushBackHandler;
        
        writeResult.set(connection, message, dstAddress, 0);
    }

    public final Object getDstAddress() {
        checkRecycled();
        return dstAddress;
    }
    
    public final WritableMessage getWritableMessage() {
        return (WritableMessage) message;
    }

    /**
     * @return <tt>true</tt> if record reserves in async write queue space, that
     * is not related to message size {@link #remaining()}, but is constant
     * {@link AsyncWriteQueueRecord#UNCOUNTABLE_RECORD_SPACE_VALUE}.
     */
    public boolean isUncountable() {
        return isUncountable;
    }

    public void setUncountable(final boolean isUncountable) {
        this.isUncountable = isUncountable;
    }

    public long getBytesToReserve() {
        return isUncountable ? UNCOUNTABLE_RECORD_SPACE_VALUE : initialMessageSize;
    }
    
    public long getInitialMessageSize() {
        return initialMessageSize;
    }

    public long remaining() {
        return getWritableMessage().remaining();
    }

    @Override
    public RecordWriteResult getCurrentResult() {
        return writeResult;
    }
    
    @Deprecated
    public PushBackHandler getPushBackHandler() {
        return pushBackHandler;
    }
    
    public boolean canBeAggregated() {
        return !getWritableMessage().isExternal();
    }
    
    @SuppressWarnings("unchecked")
    public void notifyCompleteAndRecycle() {

        final CompletionHandler<WriteResult> completionHandlerLocal =
                completionHandler;

        final WritableMessage messageLocal = getWritableMessage();

        if (completionHandlerLocal != null) {
            completionHandlerLocal.completed(writeResult);
        }
        
        recycle();

        // try to dispose originalBuffer (if allowed)
        messageLocal.release();

    }
    
    public boolean isFinished() {
        return !getWritableMessage().hasRemaining();
    }

    protected final void reset() {
        set(null, null, null, null, null, false);
        writeResult.recycle(); // reset the write result
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
