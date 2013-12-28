/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.threadpool.DefaultWorkerThread;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author oleksiys
 */
public final class ThreadCache {
    private static final ObjectCacheElement[] INITIAL_OBJECT_ARRAY = new ObjectCacheElement[16];
    
    private static final Map<String, CachedTypeIndex> typeIndexMap =
            new HashMap<String, CachedTypeIndex>();
    
    private static int indexCounter;

    private static final ThreadLocal<ObjectCache> genericCacheAttr =
            new ThreadLocal<ObjectCache>();

    public static synchronized <E> CachedTypeIndex<E> obtainIndex(
            Class<E> clazz, int size) {
        return obtainIndex(clazz.getName(), clazz, size);

    }

    @SuppressWarnings("unchecked")
    public static synchronized <E> CachedTypeIndex<E> obtainIndex(String name,
            Class<E> clazz, int size) {

        CachedTypeIndex<E> typeIndex = typeIndexMap.get(name);
        if (typeIndex == null) {
            typeIndex = new CachedTypeIndex<E>(indexCounter++, name, clazz, size);
            typeIndexMap.put(name, typeIndex);
        }

        return typeIndex;
    }

    public static <E> boolean putToCache(final CachedTypeIndex<E> index, final E o) {
        return putToCache(Thread.currentThread(), index, o);
    }
    
    public static <E> boolean putToCache(final Thread currentThread,
            final CachedTypeIndex<E> index, final E o) {
        if (currentThread instanceof DefaultWorkerThread) {
            return ((DefaultWorkerThread) currentThread).putToCache(index, o);
        } else {
            ObjectCache genericCache = genericCacheAttr.get();
            if (genericCache == null) {
                genericCache = new ObjectCache();
                genericCacheAttr.set(genericCache);
            }
            
            return genericCache.put(index, o);
        }
    }
    
    /**
     * Get the cached object with the given type index from cache.
     * Unlike {@link #takeFromCache(org.glassfish.grizzly.ThreadCache.CachedTypeIndex)}, the
     * object won't be removed from cache.
     *
     * @param <E>
     * @param index the cached object type index.
     * @return cached object.
     */
    public static <E> E getFromCache(final CachedTypeIndex<E> index) {
        return getFromCache(Thread.currentThread(), index);
    }
    
    /**
     * Get the cached object with the given type index from cache.
     * Unlike {@link #takeFromCache(org.glassfish.grizzly.ThreadCache.CachedTypeIndex)}, the
     * object won't be removed from cache.
     *
     * @param <E>
     * @param currentThread current {@link Thread}
     * @param index the cached object type index.
     * @return cached object.
     */
    public static <E> E getFromCache(final Thread currentThread,
            final CachedTypeIndex<E> index) {
        assert currentThread == Thread.currentThread();
        
        if (currentThread instanceof DefaultWorkerThread) {
            return ((DefaultWorkerThread) currentThread).getFromCache(index);
        } else {
            final ObjectCache genericCache = genericCacheAttr.get();
            if (genericCache != null) {
                return genericCache.get(index);
            }

            return null;
        }
    }

    /**
     * Take the cached object with the given type index from cache.
     * Unlike {@link #getFromCache(org.glassfish.grizzly.ThreadCache.CachedTypeIndex)}, the
     * object will be removed from cache.
     *
     * @param <E>
     * @param index the cached object type index
     * @return cached object
     */
    public static <E> E takeFromCache(final CachedTypeIndex<E> index) {
        return takeFromCache(Thread.currentThread(), index);
    }
    
    /**
     * Take the cached object with the given type index from cache.
     * Unlike {@link #getFromCache(org.glassfish.grizzly.ThreadCache.CachedTypeIndex)}, the
     * object will be removed from cache.
     *
     * @param <E>
     * @param currentThread current {@link Thread}
     * @param index the cached object type index
     * @return cached object
     */
    public static <E> E takeFromCache(final Thread currentThread,
            final CachedTypeIndex<E> index) {
        if (currentThread instanceof DefaultWorkerThread) {
            return ((DefaultWorkerThread) currentThread).takeFromCache(index);
        } else {
            final ObjectCache genericCache = genericCacheAttr.get();
            if (genericCache != null) {
                return genericCache.take(index);
            }

            return null;
        }
    }

    public static final class ObjectCache {
        private ObjectCacheElement[] objectCacheElements;

        public boolean put(final CachedTypeIndex index, final Object o) {
            if (objectCacheElements != null &&
                    index.getIndex() < objectCacheElements.length) {
                ObjectCacheElement objectCache = objectCacheElements[index.getIndex()];
                if (objectCache == null) {
                    objectCache = new ObjectCacheElement(index.size);
                    objectCacheElements[index.getIndex()] = objectCache;
                }

                return objectCache.put(o);
            }

            final ObjectCacheElement[] arrayToGrow =
                    (objectCacheElements != null) ?
                        objectCacheElements : INITIAL_OBJECT_ARRAY;
            final int newSize = Math.max(index.getIndex() + 1,
                    (arrayToGrow.length * 3) / 2 + 1);

            objectCacheElements = Arrays.copyOf(arrayToGrow, newSize);

            final ObjectCacheElement objectCache = new ObjectCacheElement(index.getSize());
            objectCacheElements[index.getIndex()] = objectCache;
            return objectCache.put(o);
        }

        /**
         * Get the cached object with the given type index from cache.
         * Unlike {@link #take(org.glassfish.grizzly.ThreadCache.CachedTypeIndex)}, the
         * object won't be removed from cache.
         * 
         * @param <E>
         * @param index the cached object type index.
         * @return cached object.
         */
        @SuppressWarnings("unchecked")
        public <E> E get(final CachedTypeIndex<E> index) {
            final int idx;
            if (objectCacheElements != null &&
                    (idx = index.getIndex()) < objectCacheElements.length) {

                final ObjectCacheElement objectCache = objectCacheElements[idx];
                if (objectCache == null) return null;

                return (E) objectCache.get();
            }

            return null;
        }

        /**
         * Take the cached object with the given type index from cache.
         * Unlike {@link #get(org.glassfish.grizzly.ThreadCache.CachedTypeIndex)}, the
         * object will be removed from cache.
         *
         * @param <E>
         * @param index the cached object type index.
         * @return cached object.
         */
        @SuppressWarnings("unchecked")
        public <E> E take(final CachedTypeIndex<E> index) {
            final int idx;
            if (objectCacheElements != null &&
                    (idx = index.getIndex()) < objectCacheElements.length) {

                final ObjectCacheElement objectCache = objectCacheElements[idx];
                if (objectCache == null) return null;

                return (E) objectCache.take();
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

        public boolean put(Object o) {
            if (index < size) {
                cache[index++] = o;
                return true;
            }

            return false;
        }

        /**
         * Get (peek) the object from cache.
         * Unlike {@link #take()} the object will not be removed from cache.
         *
         * @return object from cache.
         */
        public Object get() {
            if (index > 0) {
                final Object o = cache[index - 1];
                return o;
            }

            return null;
        }

        /**
         * Take (poll) the object from cache.
         * Unlike {@link #get()} the object will be removed from cache.
         *
         * @return object from cache.
         */
        public Object take() {
            if (index > 0) {
                index--;
                
                final Object o = cache[index];
                cache[index] = null;
                return o;
            }

            return null;
        }
    }
    
    public static final class CachedTypeIndex<E> {
        private final int index;
        private final Class clazz;
        private final int size;
        private final String name;

        public CachedTypeIndex(final int index, final String name,
                final Class<E> clazz, final int size) {
            this.index = index;
            this.name = name;
            this.clazz = clazz;
            this.size = size;
        }

        public int getIndex() {
            return index;
        }

        public String getName() {
            return name;
        }

        public Class getClazz() {
            return clazz;
        }

        public int getSize() {
            return size;
        }
    }
}
