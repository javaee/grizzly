/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 * @author Alexey Stashok
 */
public abstract class CompositeBuffer implements Buffer {
    /**
     * The order in which internal {@link Buffer}s will be disposed.
     */
    public enum DisposeOrder {
        LAST_TO_FIRST, FIRST_TO_LAST
    }
    
    protected DisposeOrder disposeOrder = DisposeOrder.LAST_TO_FIRST;
    
    /**
     * Construct <tt>CompositeBuffer</tt>.
     * @return new <tt>CompositeBuffer</tt>
     */
    public static CompositeBuffer newBuffer() {
        return BuffersBuffer.create();
    }

    public static CompositeBuffer newBuffer(final MemoryManager memoryManager) {
        return BuffersBuffer.create(memoryManager);
    }

    public static CompositeBuffer newBuffer(final MemoryManager memoryManager,
            final Buffer... buffers) {
        return BuffersBuffer.create(memoryManager, buffers);
    }

    public static CompositeBuffer newBuffer(final MemoryManager memoryManager,
            final Buffer[] buffers, final boolean isReadOnly) {
        return BuffersBuffer.create(memoryManager, buffers, isReadOnly);
    }

    /**
     * Returns the order in which internal {@link Buffer}s will be disposed.
     * @return {@link DisposeOrder}
     */
    public DisposeOrder disposeOrder() {
        return disposeOrder;
    }

    /**
     * Sets the order in which internal {@link Buffer}s will be disposed.
     * @param disposeOrder
     * @return this buffer
     */
    public CompositeBuffer disposeOrder(final DisposeOrder disposeOrder) {
        this.disposeOrder = disposeOrder;
        return this;
    }
    
    public abstract CompositeBuffer append(Buffer buffer);
    
    @Override
    public abstract CompositeBuffer prepend(Buffer buffer);

    @Override
    public abstract Object[] underlying();

    /**
     * Removes underlying {@link Buffer}s, without disposing
     */
    public abstract void removeAll();

    public abstract void allowInternalBuffersDispose(boolean allow);

    public abstract boolean allowInternalBuffersDispose();
    
    /**
     * Iterates over {@link Buffer} bytes from {@link #position()} to {@link #limit()}
     * and lets {@link BulkOperation} examine/change the buffer content;
     * 
     * @param operation {@link BulkOperation}
     * @return <tt>Buffer</tt> position the processing was stopped on, or <tt>-1</tt>,
     * if processing reached the limit.
     */
    public abstract int bulk(BulkOperation operation);
    
    /**
     * Iterates over {@link Buffer} bytes from position to limit
     * and lets {@link BulkOperation} examine/change the buffer content;
     * 
     * @param operation {@link BulkOperation}
     * @return <tt>Buffer</tt> position the processing was stopped on, or <tt>-1</tt>,
     * if processing reached the limit.
     */
    public abstract int bulk(BulkOperation operation, int position, int limit);

    /**
     * Replace one internal {@link Buffer} with another one.
     * @param oldBuffer the {@link Buffer} to replace.
     * @param newBuffer the new {@link Buffer}.
     * @return <tt>true</tt>, if the oldBuffer was found and replaced, or
     * <tt>false</tt> otherwise.
     */
    public abstract boolean replace(Buffer oldBuffer, Buffer newBuffer);
    
    /**
     * Bulk Buffer operation, responsible for byte-by-byte Buffer processing.
     */
    public interface BulkOperation {
        /**
         * Method is responsible to examine/change one single Buffer's byte.
         * @param setter {@link Setter}
         * @return <tt>true</tt>, if we want bulk processing stop, or <tt>false</tt>
         *          to continue processing.
         */
        boolean processByte(byte value, Setter setter);
    }
    
    /**
     * Setter.
     */
    public interface Setter {
        /**
         * Set the current byte value.
         * @param value value
         */
        void set(byte value);
    }    
}
