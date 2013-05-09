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
package org.glassfish.grizzly.memory;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.monitoring.DefaultMonitoringConfig;
import org.glassfish.grizzly.threadpool.DefaultWorkerThread;


/**
 * A {@link MemoryManager} abstraction to provide utilities that may be useful
 * across different {@link MemoryManager} implementations.
 *
 * @since 2.0
 */
public abstract class AbstractMemoryManager<E extends Buffer>
        implements MemoryManager<E>, ThreadLocalPoolProvider {


    /**
     * The maximum size of the memory pool that is to be maintained by
     * either the MemoryManager itself or any {@link ThreadLocalPool}s.
     */
    public static final int DEFAULT_MAX_BUFFER_SIZE = 1024 * 64;


    /**
     * Basic monitoring support.  Concrete implementations of this class need
     * only to implement the {@link #createJmxManagementObject()}  method
     * to plug into the Grizzly 2.0 JMX framework.
     */
    protected final DefaultMonitoringConfig<MemoryProbe> monitoringConfig =
            new DefaultMonitoringConfig<MemoryProbe>(MemoryProbe.class) {

        @Override
        public Object createManagementObject() {
            return createJmxManagementObject();
        }

    };

    protected final int maxBufferSize;


    // ------------------------------------------------------------ Constructors


    /**
     * Creates a new <code>AbstractMemoryManager</code> using a max buffer size
     * of {@value #DEFAULT_MAX_BUFFER_SIZE}.
     */
    public AbstractMemoryManager() {

        this(DEFAULT_MAX_BUFFER_SIZE);

    }

    /**
     * Creates a new <code>AbstractMemoryManager</code> using the specified
     * buffer size.
     *
     * @param maxBufferSize max size of the maintained buffer.
     */
    public AbstractMemoryManager(final int maxBufferSize) {

        this.maxBufferSize = maxBufferSize;

    }


    // ---------------------------------------------------------- Public Methods


    /**
     * Get the size of local thread memory pool.
     *
     * @return the size of local thread memory pool.
     */
    public int getReadyThreadBufferSize() {
       ThreadLocalPool threadLocalPool = getThreadLocalPool();
        if (threadLocalPool != null) {
            return threadLocalPool.remaining();
        }

        return 0;
    }


    /**
     * @return the max size of the buffer maintained by this
     * <code>AbstractMemoryManager</code>.
     */
    public int getMaxBufferSize() {
        return maxBufferSize;
    }


    // ------------------------------------------------------- Protected Methods


    /**
     * Allocate a {@link Buffer} using the provided {@link ThreadLocalPool}.
     *
     * @param threadLocalCache the {@link ThreadLocalPool} to allocate from.
     * @param size the amount to allocate.
     *
     * @return an memory buffer, or <code>null</code> if the requested size
     *  exceeds the remaining free memory of the {@link ThreadLocalPool}.
     */
    protected Object allocateFromPool(final ThreadLocalPool threadLocalCache,
                                      final int size) {
        if (threadLocalCache.remaining() >= size) {
            ProbeNotifier.notifyBufferAllocatedFromPool(monitoringConfig, size);

            return threadLocalCache.allocate(size);
        }

        return null;
    }


    /**
     * @return the JMX {@link Object} used to register/deregister with the
     *  JMX runtime.
     */
    protected abstract Object createJmxManagementObject();


    /**
     * Get thread associated buffer pool.
     *
     * @return thread associated buffer pool.  This method may return
     *  <code>null</code> if the current thread doesn't have a buffer pool
     *  associated with it.
     */
    protected static ThreadLocalPool getThreadLocalPool() {
        final Thread t = Thread.currentThread();
        if (t instanceof DefaultWorkerThread) {
            return ((DefaultWorkerThread) t).getMemoryPool();
        } else {
            return null;
        }
    }


    // ---------------------------------------------------------- Nested Classes

    /**
     * This is a marker interface indicating a particular {@link Buffer}
     * implementation can be trimmed.
     */
    protected static interface TrimAware extends Cacheable { }

}
