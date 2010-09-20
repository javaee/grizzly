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

package org.glassfish.grizzly.memory;

import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import java.nio.ByteBuffer;

/**
 * The {@link ByteBufferManager} implementation, which doesn't allocate
 * {@link ByteBuffer}s each time allocate is called. Instead of this,
 * implementation preallocates large {@link ByteBuffer} pool, and returns
 * {@link ByteBuffer} view of required size for each allocate method call.
 *
 * @see MemoryManager
 * @see ByteBufferManager
 * @see ByteBuffer
 * 
 * @author Jean-Francois Arcand
 * @author Alexey Stashok
 */
public class ByteBufferViewManager extends ByteBufferManager {
    /**
     * The default capacity of the <code>ByteBuffer</code> from which views
     * will be created.
     */
    public static final int DEFAULT_CAPACITY = 512 * 1024;

    /**
     * Large {@link ByteBuffer} pool.
     */
    protected ByteBuffer largeByteBuffer;
    
    /**
     * Capacity of the large {@link ByteBuffer} pool.
     */
    protected int capacity;
    
    public ByteBufferViewManager() {
        this(false);
    }

    public ByteBufferViewManager(boolean isDirect) {
        this(isDirect, DEFAULT_CAPACITY);
    }

    public ByteBufferViewManager(boolean isDirect, int capacity) {
        super(isDirect);
        this.capacity = capacity;
    }

    /**
     * Allocates {@link org.glassfish.grizzly.Buffer} of required size, which is actually sliced from
     * large preallocated {@link ByteBuffer} pool.
     *
     * @param size size of the {@link ByteBuffer} to be allocated.
     *
     * @return {@link ByteBuffer} of required size, which is actualled sliced from
     * large preallocated {@link ByteBuffer} pool.
     */
    @Override
    public synchronized ByteBuffer allocateByteBuffer(int size) {
        if (largeByteBuffer == null || largeByteBuffer.remaining() < size) {
            largeByteBuffer = super.allocateByteBuffer(capacity);
        }

        ProbeNotifier.notifyBufferAllocatedFromPool(monitoringConfig, size);
        
        return BufferUtils.slice(largeByteBuffer, size);
    }

    /**
     * Get the initial view size.
     * @return the initial view size.
     */
    public int getViewSize() {
        return capacity;
    }

    /**
     * Get the number of bytes remaining in the view.
     * @return the number of bytes remaining in the view.
     */
    public synchronized int getViewRemaining() {
        final ByteBuffer view = largeByteBuffer;
        return view != null ? view.remaining() : 0;
    }

    /**
     * Create the Memory Manager JMX managment object.
     *
     * @return the Memory Manager JMX managment object.
     */
    @Override
    protected JmxObject createJmxManagementObject() {
        return new org.glassfish.grizzly.memory.jmx.ByteBufferViewManager(this);
    }
}
