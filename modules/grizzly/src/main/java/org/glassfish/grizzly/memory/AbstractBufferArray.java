/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 *
 * @author oleksiys
 */
public abstract class AbstractBufferArray<E> {
    protected final Class<E> clazz;
    private E[] byteBufferArray;
    private PosLim[] initStateArray;
    
    private int size;

    protected abstract void setPositionLimit(E buffer, int position, int limit);
    protected abstract int getPosition(E buffer);
    protected abstract int getLimit(E buffer);

    @SuppressWarnings("unchecked")
    protected AbstractBufferArray(Class<E> clazz) {
        this.clazz = clazz;
        byteBufferArray = (E[]) Array.newInstance(clazz, 4);
        initStateArray = new PosLim[4];
    }

    public void add(final E byteBuffer) {
        add(byteBuffer, getPosition(byteBuffer), getLimit(byteBuffer));
    }

    public void add(final E byteBuffer, final int restorePosition,
            final int restoreLimit) {
        
        ensureCapacity(1);
        byteBufferArray[size] = byteBuffer;
        PosLim poslim = initStateArray[size];
        if (poslim == null) {
            poslim = new PosLim();
            initStateArray[size] = poslim;
        }

        poslim.initialPosition = getPosition(byteBuffer);
        poslim.initialLimit = getLimit(byteBuffer);
        poslim.restorePosition = restorePosition;
        poslim.restoreLimit = restoreLimit;

        size++;
    }

    public E[] getArray() {
        return byteBufferArray;
    }

    public void restore() {
        for (int i = 0; i < size; i++) {
            final PosLim poslim = initStateArray[i];
            setPositionLimit(byteBufferArray[i],
                    poslim.restorePosition, poslim.restoreLimit);
        }
    }

    public final int getInitialPosition(final int idx) {
        return initStateArray[idx].initialPosition;
    }
    
    public int getInitialLimit(final int idx) {
        return initStateArray[idx].initialLimit;
    }

    public final int getInitialBufferSize(final int idx) {
        return getInitialLimit(idx) - getInitialPosition(idx);
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

    public void reset() {
        Arrays.fill(byteBufferArray, 0, size, null);
        size = 0;
    }

    public void recycle() {
        reset();
    }

    private final static class PosLim {
        int initialPosition;
        int initialLimit;

        int restorePosition;
        int restoreLimit;
    }
}
