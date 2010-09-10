/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.utils;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * The thread safe set implementation, which uses array to hold its elements.
 * This set could be used, for cases, which require minimum set modifications.
 *
 * @author Alexey Stashok
 */
public class ArraySet<T> {
    private volatile T[] array;
    private final Object sync = new Object();
    
    /**
     * Add the element(s) to the set.
     *
     * @param elements the elements to add.
     * @return <tt>true</tt>, if at least one element was added to the set and,
     * as result, the size of the set was increased, or <tt>false</tt>, all
     * element(s) was/were present in the set and, as the result, the set values
     * were just reset.
     */
    public final boolean add(final T... elements) {
        if (elements.length == 0) return false;
        
        synchronized(sync) {
            int startIdx = 0;
            if (array == null) {
                array = (T[]) Array.newInstance(
                        elements.getClass().getComponentType(), 1);
                array[0] = elements[0];
                startIdx = 1;
            }

            boolean result = false;

            for (int i = startIdx; i < elements.length; i++) {
                final T element = elements[i];
                
                final T[] oldArray = array;
                array = ArrayUtils.addUnique(array, element);

                result |= (oldArray != array);
            }

            return result;
        }
    }

    /**
     * Add all the elements from the source <tt>ArraySet</tt>.
     *
     * @param source the elements to add.
     * @return <tt>true</tt>, if at least one element was added to the set and,
     * as result, the size of the set was increased, or <tt>false</tt>, all
     * element(s) was/were present in the set and, as the result, the set values
     * were just reset.
     */
    public final boolean add(ArraySet<T> source) {
        final T[] sourceArraySet = source.getArray();
        
        if (sourceArraySet == null) return false;
        
        synchronized(sync) {
            if (array == null) {
                array = Arrays.copyOf(sourceArraySet, sourceArraySet.length);
                return true;
            }

            boolean result = false;

            for (int i = 0; i < sourceArraySet.length; i++) {
                final T element = sourceArraySet[i];
                
                final T[] oldArray = array;
                array = ArrayUtils.addUnique(array, element);

                result |= (oldArray != array);
            }

            return result;
        }
    }
    
    /**
     * Remove element(s) from the set.
     *
     * @param elements the element(s) to remove.
     * @return <tt>true</tt>, if at least one element was found and removed,
     * or <tt>false</tt> otherwise.
     */
    public final boolean remove(T... elements) {
        if (elements.length == 0) return false;

        synchronized(sync) {
            if (array == null) {
                return false;
            }

            boolean result = false;
            for (T element : elements) {
                final T[] oldArray = array;


                array = ArrayUtils.remove(array, element);

                result |= (oldArray != array);
            }

            return result;
        }
    }

    /**
     * Get the underlying array.
     * Please note, it's not appropriate to modify the returned array's content.
     * Please use {@link #add(Object[])} and {@link #remove(Object[])} instead.
     *
     * @return the array.
     */
    public final T[] getArray() {
        return array;
    }

    /**
     * Get the copy of the underlying array. If the underlying array is 
     * <tt>null</tt> - then <tt>null</tt> will be returned.
     * 
     * @return the copy of the underlying array. If the underlying array is
     * <tt>null</tt> - then <tt>null</tt> will be returned.
     */
    public final T[] getArrayCopy() {
        final T[] localArray = array;
        if (localArray == null) return null;

        return Arrays.copyOf(localArray, localArray.length);
    }

    /**
     * Get the copy of the underlying array. If the underlying array is
     * <tt>null</tt> - then empty array will be returned.
     * @param clazz type of the array elements.
     *
     * @return the copy of the underlying array. If the underlying array is
     * <tt>null</tt> - then empty array will be returned.
     */
    public final T[] obtainArrayCopy(Class<T> clazz) {
        final T[] localArray = array;
        if (localArray == null) return (T[]) Array.newInstance(clazz, 0);

        return Arrays.copyOf(localArray, localArray.length);
    }

    /**
     * Remove all the set elements.
     */
    public void clear() {
        array = null;
    }
}
