/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.memcached.pool;

import org.glassfish.grizzly.utils.DataStructures;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Bongjae Chang
 */
public class BaseObjectPool<K, V> implements ObjectPool<K, V> {

    private static final int MAX_VALIDATION_RETRY_COUNT = 3;

    private final PoolableObjectFactory<K, V> factory;
    private final int min;
    private final int max;
    private final boolean validation;
    private final boolean disposable;
    private final long keepAliveTimeoutInSecs;

    private final ConcurrentHashMap<K, QueuePool<V>> keyedObjectPool = new ConcurrentHashMap<K, QueuePool<V>>();
    private final ConcurrentHashMap<V, K> managedActiveObjects = new ConcurrentHashMap<V, K>();
    private final AtomicBoolean destroyed = new AtomicBoolean();
    private final ScheduledExecutorService scheduledExecutor;
    private final ScheduledFuture<?> scheduledFuture;

    private BaseObjectPool(Builder<K, V> builder) {
        this.factory = builder.factory;
        this.min = builder.min;
        this.max = builder.max;
        this.validation = builder.validation;
        this.disposable = builder.disposable;
        this.keepAliveTimeoutInSecs = builder.keepAliveTimeoutInSecs;
        if (keepAliveTimeoutInSecs > 0) {
            this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            this.scheduledFuture = scheduledExecutor.scheduleWithFixedDelay(new EvictionTask(), keepAliveTimeoutInSecs, keepAliveTimeoutInSecs, TimeUnit.SECONDS);
        } else {
            this.scheduledExecutor = null;
            this.scheduledFuture = null;
        }
    }

    public void createAllMinObjects(final K key) throws NoValidObjectException {
        if (destroyed.get()) {
            throw new IllegalStateException("pool has already destroyed");
        }
        if (key == null) {
            throw new IllegalArgumentException("key must be not null");
        }
        QueuePool<V> pool = keyedObjectPool.get(key);
        if (pool == null) {
            final QueuePool<V> newPool = new QueuePool<V>(max);
            pool = keyedObjectPool.putIfAbsent(key, newPool) == null ? newPool : keyedObjectPool.get(key);
        }
        if (pool.destroyed.get()) {
            throw new IllegalStateException("pool already has destroyed. key=" + key);
        }
        for (int i = 0; i < max; i++) {
            V result = createIfUnderSpecificSize(min, pool, key, validation);
            if (result == null) {
                break;
            }
            if (!pool.queue.offer(result)) {
                try {
                    factory.destroyObject(key, result);
                } catch (Exception ignore) {
                }
                pool.poolSizeHint.decrementAndGet();
                break;
            }
        }
    }

    @Override
    public void removeAllObjects(final K key) {
        if (destroyed.get()) {
            throw new IllegalStateException("pool has already destroyed");
        }
        if (key == null) {
            throw new IllegalArgumentException("key must be not null");
        }
        final QueuePool<V> pool = keyedObjectPool.get(key);
        if (pool == null) {
            return;
        }
        if (pool.destroyed.get()) {
            return;
        }
        clearPool(pool, key);
    }

    @Override
    public V borrowObject(final K key, final long timeoutInMillis) throws PoolExhaustedException, NoValidObjectException, InterruptedException {
        if (destroyed.get()) {
            throw new IllegalStateException("pool already has destroyed");
        }
        if (key == null) {
            throw new IllegalArgumentException("key must be not null");
        }
        QueuePool<V> pool = keyedObjectPool.get(key);
        if (pool == null) {
            final QueuePool<V> newPool = new QueuePool<V>(max);
            pool = keyedObjectPool.putIfAbsent(key, newPool) == null ? newPool : keyedObjectPool.get(key);
        }
        if (pool.destroyed.get()) {
            throw new IllegalStateException("pool already has destroyed. key=" + key);
        }
        V result;
        int retryCount = 0;
        boolean disposableCreation;
        do {
            disposableCreation = false;
            result = createIfUnderSpecificSize(min, pool, key, false);
            if (result == null) {
                result = pool.queue.poll();
            }
            if (result == null) {
                result = createIfUnderSpecificSize(max, pool, key, false);
            }
            if (result == null) {
                if (timeoutInMillis < 0 && !disposable) {
                    result = pool.queue.take();
                } else {
                    result = pool.queue.poll(timeoutInMillis, TimeUnit.MILLISECONDS);
                }
            }
            if (result == null && disposable) {
                try {
                    result = factory.createObject(key);
                } catch (Exception e) {
                    throw new NoValidObjectException(e);
                }
                disposableCreation = true;
            }
            if (result == null) {
                throw new PoolExhaustedException("pool is exhausted");
            }
            if (validation) {
                boolean valid = false;
                try {
                    valid = factory.validateObject(key, result);
                } catch (Exception ignore) {
                }
                if (valid) {
                    break; // success
                } else {
                    try {
                        factory.destroyObject(key, result);
                    } catch (Exception ignore) {
                    }
                    if (!disposableCreation) {
                        pool.poolSizeHint.decrementAndGet();
                    }
                    result = null;
                    retryCount++; // retry
                }
            } else {
                break; // success
            }
        } while (validation && retryCount <= MAX_VALIDATION_RETRY_COUNT);
        if (validation && result == null) {
            throw new NoValidObjectException("there is no valid object");
        }
        if (result != null && pool.destroyed.get()) {
            try {
                factory.destroyObject(key, result);
            } catch (Exception ignore) {
            }
            if (!disposableCreation) {
                pool.poolSizeHint.decrementAndGet();
            }
            throw new IllegalStateException("pool already has destroyed. key=" + key);
        }
        if (result != null && !disposableCreation) {
            managedActiveObjects.put(result, key);
        }
        return result;
    }

    private V createIfUnderSpecificSize(final int specificSize, final QueuePool<V> pool, final K key, final boolean validation) throws NoValidObjectException {
        if (destroyed.get()) {
            throw new IllegalStateException("pool has already destroyed");
        }
        if (pool == null) {
            throw new IllegalArgumentException("pool must be not null");
        }
        if (key == null) {
            throw new IllegalArgumentException("key must be not null");
        }
        if (specificSize >= pool.poolSizeHint.incrementAndGet()) {
            try {
                final V result = factory.createObject(key);
                if (result == null) {
                    pool.poolSizeHint.decrementAndGet();
                    throw new IllegalStateException("failed to create the object. the created object must be not null");
                } else {
                    if (validation) {
                        boolean valid = false;
                        try {
                            valid = factory.validateObject(key, result);
                        } catch (Exception ignore) {
                        }
                        if (!valid) {
                            try {
                                factory.destroyObject(key, result);
                            } catch (Exception ignore) {
                            }
                            pool.poolSizeHint.decrementAndGet();
                            return null;
                        }
                    }
                    final int currentSizeHint = pool.poolSizeHint.get();
                    if (currentSizeHint > pool.peakSizeHint) {
                        pool.peakSizeHint = currentSizeHint;
                    }
                    return result;
                }
            } catch (Exception e) {
                pool.poolSizeHint.decrementAndGet();
                throw new NoValidObjectException(e);
            }
        } else {
            pool.poolSizeHint.decrementAndGet();
            return null;
        }
    }

    @Override
    public void returnObject(final K key, final V value) {
        if (destroyed.get()) {
            throw new IllegalStateException("pool has already destroyed");
        }
        if (key == null) {
            throw new IllegalArgumentException("key must be not null");
        }
        if (value == null) {
            return;
        }
        final K managed = managedActiveObjects.remove(value);
        final QueuePool<V> pool = keyedObjectPool.get(key);
        if (pool == null || managed == null) {
            try {
                factory.destroyObject(key, value);
            } catch (Exception ignore) {
            }
            return;
        }
        if (this.validation) {
            boolean valid = false;
            try {
                valid = factory.validateObject(key, value);
            } catch (Exception ignore) {
            }
            if (!valid) {
                try {
                    factory.destroyObject(key, value);
                } catch (Exception ignore) {
                }
                pool.poolSizeHint.decrementAndGet();
                return;
            }
        }

        if (pool.destroyed.get() || !pool.queue.offer(value)) {
            try {
                factory.destroyObject(key, value);
            } catch (Exception ignore) {
            }
            pool.poolSizeHint.decrementAndGet();
        }
    }

    @Override
    public void removeObject(K key, V value) {
        if (destroyed.get()) {
            throw new IllegalStateException("pool has already destroyed");
        }
        if (key == null) {
            throw new IllegalArgumentException("key must be not null");
        }
        if (value == null) {
            return;
        }
        final K managed = managedActiveObjects.remove(value);
        final QueuePool<V> pool = keyedObjectPool.get(key);
        if (pool == null || managed == null) {
            try {
                factory.destroyObject(key, value);
            } catch (Exception ignore) {
            }
            return;
        }

        pool.queue.remove(value);
        try {
            factory.destroyObject(key, value);
        } catch (Exception ignore) {
        }
        pool.poolSizeHint.decrementAndGet();
    }

    @Override
    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }

        for (Map.Entry<K, QueuePool<V>> entry : keyedObjectPool.entrySet()) {
            final K key = entry.getKey();
            final QueuePool<V> pool = entry.getValue();
            pool.destroyed.compareAndSet(false, true);
            clearPool(pool, key);
        }
        keyedObjectPool.clear();
        for (Map.Entry<V, K> entry : managedActiveObjects.entrySet()) {
            final V object = entry.getKey();
            final K key = entry.getValue();
            try {
                factory.destroyObject(key, object);
            } catch (Exception ignore) {
            }
        }
        managedActiveObjects.clear();
    }

    private void clearPool(final QueuePool<V> pool, final K key) {
        if (pool == null || key == null) {
            return;
        }
        V object;
        while ((object = pool.queue.poll()) != null) {
            try {
                factory.destroyObject(key, object);
            } catch (Exception ignore) {
            }
            pool.poolSizeHint.decrementAndGet();
        }
    }

    @Override
    public int getPoolSize(final K key) {
        if (destroyed.get()) {
            return 0;
        }
        if (key == null) {
            throw new IllegalArgumentException("key must be not null");
        }
        final QueuePool<V> pool = keyedObjectPool.get(key);
        if (pool == null) {
            return 0;
        }
        return pool.poolSizeHint.get();
    }

    @Override
    public int getPeakCount(final K key) {
        if (destroyed.get()) {
            return 0;
        }
        if (key == null) {
            throw new IllegalArgumentException("key must be not null");
        }
        final QueuePool<V> pool = keyedObjectPool.get(key);
        if (pool == null) {
            return 0;
        }
        return pool.peakSizeHint;
    }

    @Override
    public int getActiveCount(final K key) {
        if (destroyed.get()) {
            return 0;
        }
        if (key == null) {
            throw new IllegalArgumentException("key must be not null");
        }
        final QueuePool<V> pool = keyedObjectPool.get(key);
        if (pool == null) {
            return 0;
        }
        return pool.poolSizeHint.get() - pool.queue.size();
    }

    @Override
    public int getIdleCount(final K key) {
        if (destroyed.get()) {
            return 0;
        }
        if (key == null) {
            throw new IllegalArgumentException("key must be not null");
        }
        final QueuePool<V> pool = keyedObjectPool.get(key);
        if (pool == null) {
            return 0;
        }
        return pool.queue.size();
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public boolean isValidation() {
        return validation;
    }

    public boolean isDisposable() {
        return disposable;
    }

    public long getKeepAliveTimeoutInSecs() {
        return keepAliveTimeoutInSecs;
    }

    private static class QueuePool<V> {
        private final AtomicInteger poolSizeHint = new AtomicInteger();
        private volatile int peakSizeHint = 0;
        private final BlockingQueue<V> queue;
        private final AtomicBoolean destroyed = new AtomicBoolean();

        private QueuePool(final int max) {
            if( max <= 0 || max == Integer.MAX_VALUE) {
                queue = DataStructures.getLTQInstance();
            } else {
                queue = new LinkedBlockingQueue<V>(max);
            }
        }
    }

    private class EvictionTask implements Runnable {

        private final AtomicBoolean running = new AtomicBoolean();

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            try {
                for (Map.Entry<K, QueuePool<V>> entry : keyedObjectPool.entrySet()) {
                    final K key = entry.getKey();
                    final QueuePool<V> pool = entry.getValue();
                    if (pool.destroyed.get()) {
                        continue;
                    }
                    final V[] snapshot = (V[]) pool.queue.toArray();
                    for (V object : snapshot) {
                        if (!pool.destroyed.get() && min < pool.queue.size() && pool.queue.remove(object)) {
                            try {
                                factory.destroyObject(key, object);
                            } catch (Exception ignore) {
                            }
                            pool.poolSizeHint.decrementAndGet();
                        }
                    }
                }
            } finally {
                running.set(false);
            }
        }
    }

    public static class Builder<K, V> {
        private static final int DEFAULT_MIN = 5;
        private static final int DEFAULT_MAX = Integer.MAX_VALUE;
        private static final boolean DEFAULT_VALIDATION = false;
        private static final boolean DEFAULT_DISPOSABLE = false;
        private static final long DEFAULT_KEEP_ALIVE_TIMEOUT_IN_SEC = 30 * 60; // 30min
        private final PoolableObjectFactory<K, V> factory;
        private int min = DEFAULT_MIN;
        private int max = DEFAULT_MAX;
        private boolean validation = DEFAULT_VALIDATION;
        private boolean disposable = DEFAULT_DISPOSABLE;
        private long keepAliveTimeoutInSecs = DEFAULT_KEEP_ALIVE_TIMEOUT_IN_SEC;

        public Builder(PoolableObjectFactory<K, V> factory) {
            this.factory = factory;
        }

        public Builder<K, V> min(final int min) {
            if (min >= 0) {
                this.min = min;
            }
            return this;
        }

        public Builder<K, V> max(final int max) {
            if (max > 1) {
                this.max = max;
            }
            return this;
        }

        public Builder<K, V> validation(final boolean validation) {
            this.validation = validation;
            return this;
        }

        public Builder<K, V> disposable(final boolean disposable) {
            this.disposable = disposable;
            return this;
        }

        public Builder<K, V> keepAliveTimeoutInSecs(final long keepAliveTimeoutInSecs) {
            this.keepAliveTimeoutInSecs = keepAliveTimeoutInSecs;
            return this;
        }

        public ObjectPool<K, V> build() {
            if (min > max) {
                max = min;
            }
            return new BaseObjectPool<K, V>(this);
        }
    }

    @Override
    public String toString() {
        return "BaseObjectPool{" +
                "keepAliveTimeoutInSecs=" + keepAliveTimeoutInSecs +
                ", disposable=" + disposable +
                ", validation=" + validation +
                ", max=" + max +
                ", min=" + min +
                ", factory=" + factory +
                '}';
    }
}