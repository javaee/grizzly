/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.utils.Holder;

/**
 * Result of write operation, returned by {@link Writeable}.
 *
 * @param <K> type of the message
 * @param <L> type of the address
 * 
 * @author Alexey Stashok
 */
public class WriteResult<K, L> implements Result, Cacheable {
    private static final ThreadCache.CachedTypeIndex<WriteResult> CACHE_IDX =
            ThreadCache.obtainIndex(WriteResult.class, 4);

    private boolean isRecycled = false;

    public static <K, L> WriteResult<K, L> create(Connection<L> connection) {
        final WriteResult<K, L> writeResult = takeFromCache();
        if (writeResult != null) {
            writeResult.connection = connection;
            writeResult.isRecycled = false;
            return writeResult;
        }

        return new WriteResult<K, L>(connection);
    }

    public static <K, L> WriteResult<K, L> create(final Connection<L> connection,
            final K message, final L dstAddress, final long writeSize) {
        final WriteResult<K, L> writeResult = takeFromCache();
        if (writeResult != null) {
            writeResult.set(connection, message, dstAddress, writeSize);
            writeResult.isRecycled = false;

            return writeResult;
        }

        return new WriteResult<K, L>(connection, message, dstAddress, writeSize);

    }

    @SuppressWarnings("unchecked")
    private static <K, L> WriteResult<K, L> takeFromCache() {
        return ThreadCache.takeFromCache(CACHE_IDX);
    }
    
    /**
     * Connection, from which data were read.
     */
    private Connection<L> connection;

    /**
     * message data
     */
    private K message;

    /**
     *  Destination address.
     */

    private Holder<L> dstAddressHolder;

    /**
     * Number of bytes written.
     */
    private long writtenSize;

    protected WriteResult() {
    }
    
    private WriteResult(final Connection<L> connection) {
        this(connection, null, null, 0);
    }

    private WriteResult(Connection<L> connection, K message, L dstAddress,
            long writeSize) {
        set(connection, message, dstAddress, writeSize);
    }

    /**
     * Get the {@link Connection} data were read from.
     *
     * @return the {@link Connection} data were read from.
     */
    @Override
    public final Connection<L> getConnection() {
        checkRecycled();
        return connection;
    }

    /**
     * Get the message, which was read.
     *
     * @return the message, which was read.
     */
    public final K getMessage() {
        checkRecycled();
        return message;
    }

    /**
     * Set the message, which was read.
     *
     * @param message the message, which was read.
     */
    public final void setMessage(K message) {
        checkRecycled();
        this.message = message;
    }

    /**
     * Get the destination address, the message was written to.
     *
     * @return the destination address, the message was written to.
     */
    public final L getDstAddress() {
        checkRecycled();
        return dstAddressHolder != null ? dstAddressHolder.get() : null;
    }

    /**
     * Get the destination address, the message was written to.
     *
     * @return the destination address, the message was written to.
     */
    public final Holder<L> getDstAddressHolder() {
        checkRecycled();
        return dstAddressHolder;
    }

    /**
     * Set the destination address, the message was written to.
     *
     * @param dstAddress the destination address, the message was written to.
     */
    public final void setDstAddress(final L dstAddress) {
        checkRecycled();
        this.dstAddressHolder = createAddrHolder(dstAddress);
    }

    /**
     * Set the destination address, the message was written to.
     *
     * @param dstAddressHolder the destination address, the message was written to.
     */
    public final void setDstAddressHolder(final Holder<L> dstAddressHolder) {
        checkRecycled();
        this.dstAddressHolder = dstAddressHolder;
    }

    /**
     * Get the number of bytes, which were written.
     *
     * @return the number of bytes, which were written.
     */
    public final long getWrittenSize() {
        checkRecycled();
        return writtenSize;
    }

    /**
     * Set the number of bytes, which were written.
     *
     * @param writeSize the number of bytes, which were written.
     */
    public final void setWrittenSize(long writeSize) {
        checkRecycled();
        this.writtenSize = writeSize;
    }

    private void checkRecycled() {
        if (Grizzly.isTrackingThreadCache() && isRecycled)
            throw new IllegalStateException("ReadResult has been recycled!");
    }

    /**
     * One method to set all the WriteResult properties.
     * 
     * @param connection
     * @param message
     * @param dstAddress
     * @param writtenSize 
     */
    protected void set(final Connection<L> connection, final K message,
            final L dstAddress, final long writtenSize) {
        this.connection = connection;
        this.message = message;
        this.dstAddressHolder = createAddrHolder(dstAddress);
        this.writtenSize = writtenSize;
    }
    
    /**
     * Create an address holder.
     * 
     * @param dstAddress
     * @return 
     */
    protected Holder<L> createAddrHolder(final L dstAddress) {
        return Holder.<L>staticHolder(dstAddress);
    }
    
    protected void reset() {
        connection = null;
        message = null;
        dstAddressHolder = null;
        writtenSize = 0;
    }
    
    @Override
    public void recycle() {
        reset();
        isRecycled = true;
        ThreadCache.putToCache(CACHE_IDX, this);
    }

    @Override
    public Object copy() {
        return WriteResult.<K, L>create(getConnection(), getMessage(),
                getDstAddress(), getWrittenSize());
    }
}
