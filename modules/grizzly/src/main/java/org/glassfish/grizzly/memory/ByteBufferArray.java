/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.grizzly.memory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.glassfish.grizzly.ThreadCache;

/**
 *
 * @author oleksiys
 */
public class ByteBufferArray {

    private static final ThreadCache.CachedTypeIndex<ByteBufferArray> CACHE_IDX =
            ThreadCache.obtainIndex(ByteBufferArray.class, 4);

    public static ByteBufferArray create() {
        final ByteBufferArray array = ThreadCache.takeFromCache(CACHE_IDX);
        if (array != null) {
            return array;
        }

        return new ByteBufferArray();
    }
    
    private ByteBuffer[] byteBufferArray = new ByteBuffer[4];
    private PosLim[] initStateArray = new PosLim[4];
    private int size;

    private ByteBufferArray() {
    }

    public void add(final ByteBuffer byteBuffer) {
        ensureCapacity(1);
        byteBufferArray[size] = byteBuffer;
        PosLim poslim = initStateArray[size];
        if (poslim == null) {
            poslim = new PosLim();
            initStateArray[size] = poslim;
        }

        poslim.position = byteBuffer.position();
        poslim.limit = byteBuffer.limit();

        size++;
    }

    public ByteBuffer[] getArray() {
        return byteBufferArray;
    }

    public void restore() {
        for (int i = 0; i < size; i++) {
            final PosLim poslim = initStateArray[i];
            Buffers.setPositionLimit(byteBufferArray[i],
                    poslim.position, poslim.limit);
        }
    }

    public int size() {
        return size;
    }

    private void ensureCapacity(final int grow) {
        final int diff = byteBufferArray.length - size;
        if (diff >= grow) {
            return;
        }

        final int newSize = Math.max(diff + size, (byteBufferArray.length * 3) / 2 + 1);
        byteBufferArray = Arrays.copyOf(byteBufferArray, newSize);
        initStateArray = Arrays.copyOf(initStateArray, newSize);
    }

    protected void reset() {
        Arrays.fill(byteBufferArray, 0, size, null);
        size = 0;
    }

    public void recycle() {
        reset();

        ThreadCache.putToCache(CACHE_IDX, this);
    }

    private final static class PosLim {
        int position;
        int limit;
    }
}
