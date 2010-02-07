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
package com.sun.grizzly;

import com.sun.grizzly.threadpool.DefaultWorkerThread;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author oleksiys
 */
public final class ThreadCache {
    private static final Object[] INITIAL_OBJECT_ARRAY = new Object[16];
    
    private static final Map<Class, CachedTypeIndex> typeIndexMap =
            new HashMap<Class, CachedTypeIndex>();
    
    private static int indexCounter;

    private static ThreadLocal<Object[]> genericCacheAttr =
            new ThreadLocal<Object[]>();

    public static synchronized <E> CachedTypeIndex<E> obtainIndex(
            Class<E> clazz) {
        
        CachedTypeIndex typeIndex = typeIndexMap.get(clazz);
        if (typeIndex == null) {
            typeIndex = new CachedTypeIndex(indexCounter++, clazz);
            typeIndexMap.put(clazz, typeIndex);
        }

        return typeIndex;
    }

    public static void putToCache(CachedTypeIndex index, Object o) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof DefaultWorkerThread) {
            ((DefaultWorkerThread) currentThread).putToCache(index, o);
        } else {
            Object[] genericCache = genericCacheAttr.get();
            if (genericCache != null && index.getIndex() < genericCache.length) {
                genericCache[index.getIndex()] = o;
                return;
            }

            final Object[] arrayToGrow =
                    (genericCache != null) ? genericCache : INITIAL_OBJECT_ARRAY;
            final int newSize = Math.max(index.getIndex() + 1,
                    (arrayToGrow.length * 3) / 2 + 1);
            
            genericCache = Arrays.copyOf(arrayToGrow, newSize);
            genericCache[index.getIndex()] = o;
            genericCacheAttr.set(genericCache);
        }
    }

    public static <E> E takeFromCache(CachedTypeIndex<E> index) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof DefaultWorkerThread) {
            return ((DefaultWorkerThread) currentThread).takeFromCache(index);
        } else {
            final Object[] genericCache = genericCacheAttr.get();
            final int idx;
            if (genericCache != null &&
                    (idx = index.getIndex()) < genericCache.length) {
                
                E tmpObj = (E) genericCache[idx];
                genericCache[idx] = null;
                return tmpObj;
            }

            return null;
        }
    }

    public static class CachedTypeIndex<E> {
        private final int index;
        private final Class clazz;

        public CachedTypeIndex(int index, Class<E> clazz) {
            this.index = index;
            this.clazz = clazz;
        }

        public int getIndex() {
            return index;
        }

        public Class getClazz() {
            return clazz;
        }
    }
}
