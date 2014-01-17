/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.io;

import java.util.Arrays;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.HeapBuffer;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * {@link HeapBuffer} implementation, which might be reset to reference another
 * byte[] at any moment.
 * 
 * @author Alexey Stashok
 */
final class TemporaryHeapBuffer extends HeapBuffer {

    boolean isDisposed;
    boolean hasClonedArray;
    
    /**
     * Reset the byte[] this Buffer wraps.
     */
    void reset(final byte[] heap, final int offset, final int len) {
        this.heap = heap;
        this.offset = offset;
        this.cap = len;
        this.lim = len;
        this.pos = 0;
        byteBuffer = null;
        isDisposed = false;
        hasClonedArray = false;
    }

    Buffer cloneContent(final MemoryManager memoryManager) {
        final Buffer buffer;
        
        final int length = remaining();
        
        if (!hasClonedArray) {
            buffer = memoryManager.allocate(length);
            buffer.put(heap, offset + pos, length);
            buffer.flip();
        } else {
            buffer = Buffers.wrap(memoryManager, heap, offset + pos, length);
        }

        buffer.allowBufferDispose(true);
        dispose();
        
        return buffer;
    }

    @Override
    protected void onShareHeap() {
        if (!hasClonedArray) {
            heap = Arrays.copyOfRange(heap, offset, offset + cap);
            offset = 0;
            hasClonedArray = true;
        }
        
        super.onShareHeap();
    }
    
    @Override
    public void dispose() {
        isDisposed = true;
        
        super.dispose();
    }
    
    boolean isDisposed() {
        return isDisposed;
    }

    public void recycle() {
        reset(null, 0, 0);
    }
}
