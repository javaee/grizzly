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

package com.sun.grizzly.filterchain;

import com.sun.grizzly.IOEvent;
import com.sun.grizzly.Context;
import com.sun.grizzly.utils.IOEventMask;
import com.sun.grizzly.ProcessorResult;
import com.sun.grizzly.utils.ArrayIOEventMask;
import com.sun.grizzly.utils.ConcurrentQueuePool;
import com.sun.grizzly.utils.ObjectPool;
import java.io.IOException;
import com.sun.grizzly.threadpool.DefaultWorkerThread;

/**
 * Abstract {@link FilterChain} implementation,
 * which redirects {@link com.sun.grizzly.Processor#process(com.sun.grizzly.Context)}
 * call to the {@link AbstractFilterChain#execute(com.sun.grizzly.filterchain.FilterChainContext)}
 *
 * @see FilterChain
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractFilterChain implements FilterChain {
    // By default interested in all client connection related events
    protected final IOEventMask interestedIoEventsMask = new ArrayIOEventMask(
            IOEventMask.CLIENT_EVENTS_MASK).xor(new ArrayIOEventMask(IOEvent.WRITE));

    /**
     * {@link FilterChainContext} object pool.
     */
    protected final ObjectPool<FilterChainContext> filterChainContextPool =
            new ConcurrentQueuePool<FilterChainContext>() {
        @Override
        public FilterChainContext newInstance() {
            return new FilterChainContext(filterChainContextPool);
        }

        @Override
        public void offer(FilterChainContext context) {
            Thread thread = Thread.currentThread();

            if (thread instanceof DefaultWorkerThread) {
                context.release();
                ((DefaultWorkerThread) thread).setCachedFilterChainContext(context);
                return;
            }

            super.offer(context);
        }

        @Override
        public FilterChainContext poll() {
            final Thread thread = Thread.currentThread();

            if (thread instanceof DefaultWorkerThread) {
                final DefaultWorkerThread workerThread = (DefaultWorkerThread) thread;
                final FilterChainContext context =
                        workerThread.removeCachedFilterChainContext();
                
                if (context != null) {
                    context.prepare();
                    return context;
                }
            }

            return super.poll();
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInterested(IOEvent ioEvent) {
        return interestedIoEventsMask.isInterested(ioEvent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInterested(IOEvent ioEvent, boolean isInterested) {
        interestedIoEventsMask.setInterested(ioEvent, isInterested);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeProcess(Context context) throws IOException {
    }

    /**
     * Delegates processing to {@link AbstractFilterChain#execute(com.sun.grizzly.filterchain.FilterChainContext)}
     *
     * @param context processing {@link Context}
     * @return {@link ProcessorResult}
     * @throws java.io.IOException
     */
    @Override
    public final ProcessorResult process(Context context)
            throws IOException {
        return execute((FilterChainContext) context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterProcess(Context context) throws IOException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FilterChainContext context() {
        return filterChainContextPool.poll();
    }
    
    /**
     * Method processes occured {@link IOEvent} on this {@link FilterChain}.
     * 
     * @param context processing context
     * @return {@link ProcessorResult}
     * 
     * @throws java.io.IOException
     */
    public abstract ProcessorResult execute(FilterChainContext context)
            throws IOException;
}
