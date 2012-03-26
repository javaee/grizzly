/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.asyncqueue.WritableMessage;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.NullaryFunction;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.nio.AbstractNIOAsyncQueueWriter;
import org.glassfish.grizzly.threadpool.WorkerThread;
import org.glassfish.grizzly.utils.Futures;

/**
 * Abstract class, which provides transitive dependencies for overloaded
 * {@link Writer} methods.
 *
 * @author Alexey Stashok
 */
public abstract class AbstractWriter<L> implements Writer<L> {
    private final ThreadLocal<AsyncQueueWriter.Reentrant> REENTRANTS_COUNTER =
            new ThreadLocal<AsyncQueueWriter.Reentrant>() {

        @Override
        protected Reentrant initialValue() {
            return new AsyncQueueWriter.Reentrant();
        }
    };

    private final Attribute<AsyncQueueWriter.Reentrant> reentrantsAttribute =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            AbstractNIOAsyncQueueWriter.class.getName() + hashCode() + ".reentrant",
            new NullaryFunction<AsyncQueueWriter.Reentrant>() {

                @Override
                public AsyncQueueWriter.Reentrant evaluate() {
                    return new AsyncQueueWriter.Reentrant();
                }
            });


    protected volatile int maxWriteReentrants = 10;    

    /**
     * {@inheritDoc}
     */
    @Override
    public final GrizzlyFuture<WriteResult<WritableMessage, L>> write(
            final Connection connection,
            final WritableMessage message) {
        return write(connection, null, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void write(
            final Connection connection,
            final WritableMessage message,
            final CompletionHandler<WriteResult<WritableMessage, L>> completionHandler) {
        write(connection, null, message, completionHandler, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final GrizzlyFuture<WriteResult<WritableMessage, L>> write(
            final Connection connection,
            final L dstAddress, final WritableMessage message) {
        final FutureImpl<WriteResult<WritableMessage, L>> future =
                Futures.<WriteResult<WritableMessage, L>>createSafeFuture();
        
        write(connection, dstAddress, message,
                Futures.toCompletionHandler(future), null);
        
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void write(
            final Connection connection,
            final L dstAddress, final WritableMessage message,
            final CompletionHandler<WriteResult<WritableMessage, L>> completionHandler) {
        write(connection, dstAddress, message, completionHandler, null);
    }
    

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncQueueWriter.Reentrant getWriteReentrant() {
        final Thread t = Thread.currentThread();
        // If it's a Grizzly WorkerThread - use GrizzlyAttribute
        if (WorkerThread.class.isAssignableFrom(t.getClass())) {
            return reentrantsAttribute.get((WorkerThread) t);
        }

        // ThreadLocal otherwise
        return REENTRANTS_COUNTER.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMaxReentrantsReached(final AsyncQueueWriter.Reentrant reentrant) {
        return reentrant.get() >= getMaxWriteReentrants();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxWriteReentrants() {
        return maxWriteReentrants;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMaxWriteReentrants(int maxWriteReentrants) {
        this.maxWriteReentrants = maxWriteReentrants;
    }    
}
