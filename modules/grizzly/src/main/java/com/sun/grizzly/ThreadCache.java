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
    private static final ObjectCacheElement[] INITIAL_OBJECT_ARRAY = new ObjectCacheElement[16];
    
    private static final Map<Class, CachedTypeIndex> typeIndexMap =
            new HashMap<Class, CachedTypeIndex>();
    
    private static int indexCounter;

    private static ThreadLocal<ObjectCache> genericCacheAttr =
            new ThreadLocal<ObjectCache>();

    public static synchronized <E> CachedTypeIndex<E> obtainIndex(
            Class<E> clazz, int size) {
        
        CachedTypeIndex typeIndex = typeIndexMap.get(clazz);
        if (typeIndex == null) {
            typeIndex = new CachedTypeIndex(indexCounter++, clazz, size);
            typeIndexMap.put(clazz, typeIndex);
        }

        return typeIndex;
    }

    public static void putToCache(CachedTypeIndex index, Object o) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof DefaultWorkerThread) {
            ((DefaultWorkerThread) currentThread).putToCache(index, o);
        } else {
            ObjectCache genericCache = genericCacheAttr.get();
            if (genericCache == null) {
                genericCache = new ObjectCache();
                genericCacheAttr.set(genericCache);
            }
            
            genericCache.put(index, o);
        }
    }

    public static <E> E takeFromCache(CachedTypeIndex<E> index) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof DefaultWorkerThread) {
            return ((DefaultWorkerThread) currentThread).takeFromCache(index);
        } else {
            final ObjectCache genericCache = genericCacheAttr.get();
            if (genericCache != null) {
                genericCache.get(index);
            }

            return null;
        }
    }

    public static final class ObjectCache {
        private ObjectCacheElement[] objectCacheElements;

        public void put(CachedTypeIndex index, Object o) {
            if (objectCacheElements != null &&
                    index.getIndex() < objectCacheElements.length) {
                ObjectCacheElement objectCache = objectCacheElements[index.getIndex()];
                if (objectCache == null) {
                    objectCache = new ObjectCacheElement(index.size);
                    objectCacheElements[index.getIndex()] = objectCache;
                }

                objectCache.put(o);
                return;
            }

            final ObjectCacheElement[] arrayToGrow =
                    (objectCacheElements != null) ?
                        objectCacheElements : INITIAL_OBJECT_ARRAY;
            final int newSize = Math.max(index.getIndex() + 1,
                    (arrayToGrow.length * 3) / 2 + 1);

            objectCacheElements = Arrays.copyOf(arrayToGrow, newSize);

            final ObjectCacheElement objectCache = new ObjectCacheElement(index.getSize());
            objectCache.put(o);
            objectCacheElements[index.getIndex()] = objectCache;
        }

        public <E> E get(CachedTypeIndex<E> index) {
            final int idx;
            if (objectCacheElements != null &&
                    (idx = index.getIndex()) < objectCacheElements.length) {

                final ObjectCacheElement objectCache = objectCacheElements[idx];
                if (objectCache == null) return null;

                return (E) objectCache.get();
            }

            return null;
        }
    }
    
    public static final class ObjectCacheElement {
        private final int size;
        private final Object[] cache;
        private int index;
        
        public ObjectCacheElement(int size) {
            this.size = size;
            cache = new Object[size];
        }

        public void put(Object o) {
            if (index < size) {
                cache[index++] = o;
            }
        }

        public Object get() {
            if (index > 0) {
                index--;
                
                final Object o = cache[index];
                cache[index] = null;
                return o;
            }

            return null;
        }
    }
    
    public static class CachedTypeIndex<E> {
        private final int index;
        private final Class clazz;
        private final int size;

        public CachedTypeIndex(int index, Class<E> clazz, int size) {
            this.index = index;
            this.clazz = clazz;
            this.size = size;
        }

        public int getIndex() {
            return index;
        }

        public Class getClazz() {
            return clazz;
        }

        public int getSize() {
            return size;
        }
    }
}
