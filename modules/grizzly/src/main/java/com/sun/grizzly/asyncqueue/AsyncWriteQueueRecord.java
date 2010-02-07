/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
 *
 */
package com.sun.grizzly.asyncqueue;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Cacheable;
import com.sun.grizzly.Interceptor;
import com.sun.grizzly.Transformer;
import java.util.concurrent.Future;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.ThreadCache;
import com.sun.grizzly.WriteResult;

/**
 * {@link AsyncQueue} write element unit
 * 
 * @author Alexey Stashok
 */
public final class AsyncWriteQueueRecord extends AsyncQueueRecord<WriteResult>
        implements Cacheable {
    private static final ThreadCache.CachedTypeIndex<AsyncWriteQueueRecord> CACHE_IDX =
            ThreadCache.obtainIndex(AsyncWriteQueueRecord.class);

    public static final AsyncWriteQueueRecord create(Object message,
            Future future,
            WriteResult currentResult, CompletionHandler completionHandler,
            Transformer transformer,
            Interceptor<WriteResult> interceptor, Object dstAddress,
            Buffer outputBuffer,
            boolean isCloned) {

        final AsyncWriteQueueRecord asyncWriteQueueRecord =
                ThreadCache.takeFromCache(CACHE_IDX);
        
        if (asyncWriteQueueRecord != null) {
            asyncWriteQueueRecord.set(message, future, currentResult,
                    completionHandler, transformer, interceptor, dstAddress,
                    outputBuffer, isCloned);
            return asyncWriteQueueRecord;
        }

        return new AsyncWriteQueueRecord(message, future, currentResult,
                completionHandler, transformer, interceptor, dstAddress,
                outputBuffer, isCloned);
    }
    
    private Object dstAddress;
    private boolean isCloned;
    private Buffer outputBuffer;

    private AsyncWriteQueueRecord(Object message, Future future,
            WriteResult currentResult, CompletionHandler completionHandler,
            Transformer transformer,
            Interceptor<WriteResult> interceptor, Object dstAddress,
            Buffer outputBuffer,
            boolean isCloned) {

        super(message, future, currentResult, completionHandler, transformer,
                interceptor);
        this.dstAddress = dstAddress;
        this.outputBuffer = outputBuffer;
        this.isCloned = isCloned;
    }

    protected void set(Object message, Future future,
            WriteResult currentResult, CompletionHandler completionHandler,
            Transformer transformer,
            Interceptor<WriteResult> interceptor, Object dstAddress,
            Buffer outputBuffer,
            boolean isCloned) {
        super.set(message, future, currentResult, completionHandler, transformer,
                interceptor);

        this.dstAddress = dstAddress;
        this.outputBuffer = outputBuffer;
        this.isCloned = isCloned;
    }

    public final boolean isCloned() {
        return isCloned;
    }

    public final void setCloned(boolean isCloned) {
        this.isCloned = isCloned;
    }

    public final Object getDstAddress() {
        return dstAddress;
    }

    public Buffer getOutputBuffer() {
        return outputBuffer;
    }

    public void setOutputBuffer(Buffer outputBuffer) {
        this.outputBuffer = outputBuffer;
    }

    protected final void reset() {
        set(null, null, null, null, null, null, null, null, false);
    }

    @Override
    public void recycle() {
        reset();
        ThreadCache.putToCache(CACHE_IDX, this);
    }
}
