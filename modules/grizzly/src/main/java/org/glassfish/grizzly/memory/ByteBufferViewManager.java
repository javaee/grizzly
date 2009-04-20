/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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

package org.glassfish.grizzly.memory;

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
     * Allocates {@link Buffer} of required size, which is actually sliced from
     * large preallocated {@link ByteBuffer} pool.
     * 
     * @param size size of the {@link Buffer} to be allocated.
     * 
     * @return {@link Buffer} of required size, which is actualled sliced from
     * large preallocated {@link ByteBuffer} pool.
     */
    @Override
    public synchronized ByteBufferWrapper allocate(int size) {
        if (largeByteBuffer == null || largeByteBuffer.remaining() < size) {
            largeByteBuffer = allocate0(capacity);
        }

        return wrap(slice(largeByteBuffer, size));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized ByteBufferWrapper reallocate(
            ByteBufferWrapper oldBuffer, int newSize) {
        return super.reallocate(oldBuffer, newSize);
    }

    /**
     * Slice {@link ByteBuffer} of required size from big chunk.
     *
     * @param chunk big {@link ByteBuffer} pool.
     * @param size required slice size.
     *
     * @return sliced {@link ByteBuffer} of required size.
     */
    protected static ByteBuffer slice(ByteBuffer chunk, int size) {
        chunk.limit(chunk.position() + size);
        ByteBuffer view = chunk.slice();
        chunk.position(chunk.limit());
        chunk.limit(chunk.capacity());
        
        return view;
    }
}
