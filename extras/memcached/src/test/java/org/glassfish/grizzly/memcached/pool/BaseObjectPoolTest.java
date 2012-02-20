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

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

/**
 * @author Bongjae Chang
 */
public class BaseObjectPoolTest {

    private static final Logger logger = LoggerFactory.getLogger(BaseObjectPoolTest.class);

    @Test
    public void testBasicPoolInSingleThread() {
        final PoolableObjectFactoryImpl factory = new PoolableObjectFactoryImpl();
        final BaseObjectPool.Builder<Integer, Integer> builder = new BaseObjectPool.Builder<Integer, Integer>(factory);
        builder.max(20);
        builder.min(10);
        builder.disposable(false);
        builder.keepAliveTimeoutInSecs(-1);
        final ObjectPool<Integer, Integer> pool = builder.build();

        final int borrowObjCount = 15;
        for (int i = 0; i < 50; i++) {
            try {
                pool.createAllMinObjects(i);
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            }
            // ensure min 10
            Assert.assertEquals(10, pool.getPoolSize(i));
            Assert.assertEquals(0, pool.getActiveCount(i));
            Assert.assertEquals(10, pool.getIdleCount(i));
            Assert.assertEquals(10, pool.getPeakCount(i));

            final Integer[] objects = new Integer[borrowObjCount];
            for (int j = 0; j < borrowObjCount; j++) {
                try {
                    objects[j] = pool.borrowObject(i, -1);
                } catch (Exception e) {
                    Assert.fail(e.getMessage());
                }
                Assert.assertNotNull(objects[j]);
            }
            // 10 object in pool, 5 new object
            Assert.assertEquals(borrowObjCount, pool.getPoolSize(i));
            Assert.assertEquals(borrowObjCount, pool.getActiveCount(i));
            Assert.assertEquals(0, pool.getIdleCount(i));
            Assert.assertEquals(borrowObjCount, pool.getPeakCount(i));

            for (int j = 0; j < borrowObjCount; j++) {
                try {
                    pool.returnObject(i, objects[j]);
                } catch (Exception e) {
                    Assert.fail(e.getMessage());
                }
            }

            Assert.assertEquals(borrowObjCount, pool.getPoolSize(i));
            Assert.assertEquals(0, pool.getActiveCount(i));
            Assert.assertEquals(borrowObjCount, pool.getIdleCount(i));
            Assert.assertEquals(borrowObjCount, pool.getPeakCount(i));

            try {
                pool.removeAllObjects(i);
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            }

            Assert.assertEquals(i, factory.sequence);

            Assert.assertEquals(0, pool.getPoolSize(i));
            Assert.assertEquals(0, pool.getActiveCount(i));
            Assert.assertEquals(0, pool.getIdleCount(i));
            Assert.assertEquals(borrowObjCount, pool.getPeakCount(i));

            Integer object = null;
            try {
                object = pool.borrowObject(i, -1);
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            }

            Assert.assertEquals(1, pool.getPoolSize(i));
            Assert.assertEquals(1, pool.getActiveCount(i));
            Assert.assertEquals(0, pool.getIdleCount(i));
            Assert.assertEquals(borrowObjCount, pool.getPeakCount(i));

            try {
                pool.returnObject(i, object);
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            }
            Assert.assertEquals(i + 1, factory.sequence);

        }
        pool.destroy();
    }

    @Test
    public void testMaxInSingleThread() {
        final PoolableObjectFactoryImpl factory = new PoolableObjectFactoryImpl();
        final BaseObjectPool.Builder<Integer, Integer> builder = new BaseObjectPool.Builder<Integer, Integer>(factory);
        builder.max(20);
        builder.min(10);
        builder.disposable(false);
        builder.keepAliveTimeoutInSecs(-1);
        final ObjectPool<Integer, Integer> pool = builder.build();

        final int key = 1;
        Assert.assertEquals(0, pool.getPoolSize(key));
        Assert.assertEquals(0, pool.getActiveCount(key));
        Assert.assertEquals(0, pool.getIdleCount(key));
        Assert.assertEquals(0, pool.getPeakCount(key));

        final List<Integer> objects = new ArrayList<Integer>();
        for (int i = 0; i < 25; i++) {
            try {
                final Integer object = pool.borrowObject(key, 10);
                if (object != null) {
                    objects.add(object);
                }
            } catch (Exception e) {
                break;
            }
        }
        // ensure max 20
        Assert.assertEquals(20, objects.size());
        objects.clear();

        pool.destroy();

        builder.disposable(true);
        final ObjectPool<Integer, Integer> disposablePool = builder.build();

        Assert.assertEquals(0, disposablePool.getPoolSize(key));
        Assert.assertEquals(0, disposablePool.getActiveCount(key));
        Assert.assertEquals(0, disposablePool.getIdleCount(key));
        Assert.assertEquals(0, disposablePool.getPeakCount(key));

        for (int i = 0; i < 25; i++) {
            try {
                final Integer object = disposablePool.borrowObject(key, 10);
                if (object != null) {
                    objects.add(object);
                }
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            }
        }
        // more than max 20
        Assert.assertEquals(25, objects.size());
        // ensure max 20
        Assert.assertEquals(20, disposablePool.getPoolSize(key));
        Assert.assertEquals(20, disposablePool.getActiveCount(key));
        Assert.assertEquals(0, disposablePool.getIdleCount(key));
        Assert.assertEquals(20, disposablePool.getPeakCount(key));

        disposablePool.destroy();
    }

    @Test
    public void testValidation() {
        final PoolableObjectFactory<Integer, Integer> factory = new PoolableObjectFactory<Integer, Integer>() {

            private int id;

            @Override
            public Integer createObject(final Integer key) throws Exception {
                return ++id;
            }

            @Override
            public void destroyObject(final Integer key, final Integer value) throws Exception {
            }

            @Override
            public boolean validateObject(final Integer key, final Integer value) throws Exception {
                return (id % 2) == 0;
            }
        };
        BaseObjectPool.Builder<Integer, Integer> builder = new BaseObjectPool.Builder<Integer, Integer>(factory);
        builder.max(20);
        builder.min(10);
        builder.disposable(false);
        builder.keepAliveTimeoutInSecs(-1);
        builder.borrowValidation(true);
        builder.returnValidation(true);
        final ObjectPool<Integer, Integer> pool = builder.build();

        final int key = 1;
        Assert.assertEquals(0, pool.getPoolSize(key));
        Assert.assertEquals(0, pool.getActiveCount(key));
        Assert.assertEquals(0, pool.getIdleCount(key));
        Assert.assertEquals(0, pool.getPeakCount(key));

        final List<Integer> objects = new ArrayList<Integer>();
        for (int i = 0; i < 25; i++) {
            try {
                final Integer object = pool.borrowObject(key, 10);
                if (object != null) {
                    if (object % 2 != 0) {
                        Assert.fail("validation failure");
                    }
                    objects.add(object);
                }
            } catch (Exception e) {
                break;
            }
        }
        // ensure success
        Assert.assertEquals(20, objects.size());
        objects.clear();

        pool.destroy();

        final PoolableObjectFactory<Integer, Integer> invalidFactory = new PoolableObjectFactory<Integer, Integer>() {

            private int id;

            @Override
            public Integer createObject(final Integer key) throws Exception {
                return ++id;
            }

            @Override
            public void destroyObject(final Integer key, final Integer value) throws Exception {
            }

            @Override
            public boolean validateObject(final Integer key, final Integer value) throws Exception {
                return false;
            }
        };

        builder = new BaseObjectPool.Builder<Integer, Integer>(invalidFactory);
        builder.borrowValidation(true);
        builder.returnValidation(true);
        final ObjectPool<Integer, Integer> invalidePool = builder.build();

        Assert.assertEquals(0, invalidePool.getPoolSize(key));
        Assert.assertEquals(0, invalidePool.getActiveCount(key));
        Assert.assertEquals(0, invalidePool.getIdleCount(key));
        Assert.assertEquals(0, invalidePool.getPeakCount(key));

        // ensure failure
        try {
            invalidePool.borrowObject(key, 10);
            Assert.fail();
        } catch (Exception e) {
        }

        invalidePool.destroy();
    }

    @Test
    public void testEviction() {
        final PoolableObjectFactoryImpl factory = new PoolableObjectFactoryImpl();
        final BaseObjectPool.Builder<Integer, Integer> builder = new BaseObjectPool.Builder<Integer, Integer>(factory);
        builder.max(20);
        builder.min(10);
        builder.disposable(true);
        builder.keepAliveTimeoutInSecs(3);
        final ObjectPool<Integer, Integer> pool = builder.build();

        final int key = 1;
        Assert.assertEquals(0, pool.getPoolSize(key));
        Assert.assertEquals(0, pool.getActiveCount(key));
        Assert.assertEquals(0, pool.getIdleCount(key));
        Assert.assertEquals(0, pool.getPeakCount(key));

        final List<Integer> objects = new ArrayList<Integer>();
        for (int i = 0; i < 25; i++) {
            try {
                final Integer object = pool.borrowObject(key, 10);
                if (object != null) {
                    objects.add(object);
                }
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            }
        }
        // 20 objects: managed, 5 objects: disposable
        Assert.assertEquals(25, objects.size());

        for (int i = 0; i < 15; i++) {
            try {
                pool.returnObject(key, objects.remove(0));
            } catch (Exception e) {
                e.printStackTrace();
                Assert.fail(e.getMessage());
            }
        }

        Assert.assertEquals(20, pool.getPoolSize(key));
        Assert.assertEquals(5, pool.getActiveCount(key));
        Assert.assertEquals(15, pool.getIdleCount(key));
        Assert.assertEquals(20, pool.getPeakCount(key));

        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignore) {
        }

        // evict 10 idle objects
        Assert.assertEquals(10, pool.getPoolSize(key));
        Assert.assertEquals(5, pool.getActiveCount(key));
        Assert.assertEquals(5, pool.getIdleCount(key));
        Assert.assertEquals(20, pool.getPeakCount(key));

        // 5 objects: managed, 5 objects: disposable
        Assert.assertEquals(10, objects.size());

        for (Integer object : objects) {
            try {
                pool.returnObject(key, object);
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            }
        }
        objects.clear();

        Assert.assertEquals(10, pool.getPoolSize(key));
        Assert.assertEquals(0, pool.getActiveCount(key));
        Assert.assertEquals(10, pool.getIdleCount(key));
        Assert.assertEquals(20, pool.getPeakCount(key));

        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignore) {
        }

        // there should be no evicted objects
        Assert.assertEquals(10, pool.getPoolSize(key));
        Assert.assertEquals(0, pool.getActiveCount(key));
        Assert.assertEquals(10, pool.getIdleCount(key));
        Assert.assertEquals(20, pool.getPeakCount(key));

        Assert.assertEquals(10, factory.sequence);

        try {
            pool.removeAllObjects(key);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        Assert.assertEquals(0, pool.getPoolSize(key));
        Assert.assertEquals(0, pool.getActiveCount(key));
        Assert.assertEquals(0, pool.getIdleCount(key));
        Assert.assertEquals(20, pool.getPeakCount(key));

        Assert.assertEquals(0, factory.sequence);

        pool.destroy();
    }

    @Test
    public void testInMultiThreads() {
        final int threadCount = 100;
        final Random random = new Random(System.currentTimeMillis());

        final PoolableObjectFactoryImpl factory = new PoolableObjectFactoryImpl();
        final BaseObjectPool.Builder<Integer, Integer> builder = new BaseObjectPool.Builder<Integer, Integer>(factory);
        builder.max(50);
        builder.min(10);
        builder.disposable(false);
        builder.keepAliveTimeoutInSecs(-1);
        final ObjectPool<Integer, Integer> pool = builder.build();
        final int key = 1;

        final ConcurrentLinkedQueue<Integer> borrowObjects = new ConcurrentLinkedQueue<Integer>();
        final CountDownLatch startFlag = new CountDownLatch(1);
        final CountDownLatch finishFlag = new CountDownLatch(threadCount * 2);
        for (int i = 0; i < threadCount; i++) {
            final Thread borrowThread = new Thread() {
                @Override
                public void run() {
                    try {
                        startFlag.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    for (int j = 0; j < 5; j++) {
                        try {
                            Thread.sleep(random.nextInt(5));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        try {
                            final Integer object = pool.borrowObject(key, 10);
                            Assert.assertNotNull(object);
                            Assert.assertTrue(borrowObjects.offer(object));
                        } catch (Exception ignore) {
                        }
                    }
                    finishFlag.countDown();
                }
            };
            borrowThread.start();

            final Thread returnThread = new Thread() {
                @Override
                public void run() {
                    try {
                        startFlag.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    for (int j = 0; j < 5; j++) {
                        try {
                            Thread.sleep(random.nextInt(5));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        try {
                            final Integer object = borrowObjects.poll();
                            if (object != null) {
                                pool.returnObject(key, object);
                            }
                        } catch (Exception e) {
                            Assert.fail(e.getMessage());
                        }
                    }
                    finishFlag.countDown();
                }
            };
            returnThread.start();
        }
        startFlag.countDown();
        try {
            finishFlag.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("**threads finished**");
        logger.info("pool size = {}", pool.getPoolSize(key));
        logger.info("active = {}", pool.getActiveCount(key));
        logger.info("idle = {}", pool.getIdleCount(key));
        logger.info("peak = {}", pool.getPeakCount(key));
        logger.info("borrow = {}", borrowObjects.size());
        logger.info("remain = {}", factory.sequence);

        Assert.assertEquals(pool.getPoolSize(key), pool.getActiveCount(key) + pool.getIdleCount(key));
        Assert.assertEquals(borrowObjects.size(), pool.getActiveCount(key));
        Assert.assertEquals(factory.sequence, borrowObjects.size() + pool.getIdleCount(key));

        for (Integer object : borrowObjects) {
            try {
                pool.returnObject(key, object);
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            }
        }
        borrowObjects.clear();

        logger.info("**borrowed objects returned**");
        logger.info("pool size = {}", pool.getPoolSize(key));
        logger.info("active = {}", pool.getActiveCount(key));
        logger.info("idle = {}", pool.getIdleCount(key));
        logger.info("peak = {}", pool.getPeakCount(key));
        logger.info("borrow = {}", borrowObjects.size());
        logger.info("remain = {}", factory.sequence);

        Assert.assertEquals(0, pool.getActiveCount(key));
        Assert.assertEquals(pool.getIdleCount(key), factory.sequence);
        try {
            pool.removeAllObjects(key);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        logger.info("**pool cleared**");
        logger.info("pool size = {}", pool.getPoolSize(key));
        logger.info("active = {}", pool.getActiveCount(key));
        logger.info("idle = {}", pool.getIdleCount(key));
        logger.info("peak = {}", pool.getPeakCount(key));
        logger.info("borrow = {}", borrowObjects.size());
        logger.info("remain = {}", factory.sequence);

        // ensure all objects has been destroyed
        Assert.assertEquals(0, factory.sequence);

        pool.destroy();
    }

    @Test
    public void testDisposableInMultiThreads() {
        final int threadCount = 100;
        final Random random = new Random(System.currentTimeMillis());

        final PoolableObjectFactoryImpl factory = new PoolableObjectFactoryImpl();
        final BaseObjectPool.Builder<Integer, Integer> builder = new BaseObjectPool.Builder<Integer, Integer>(factory);
        builder.max(50);
        builder.min(10);
        builder.disposable(true);
        builder.keepAliveTimeoutInSecs(-1);
        final ObjectPool<Integer, Integer> pool = builder.build();
        final int key = 1;

        final ConcurrentLinkedQueue<Integer> borrowObjects = new ConcurrentLinkedQueue<Integer>();
        final CountDownLatch startFlag = new CountDownLatch(1);
        final CountDownLatch finishFlag = new CountDownLatch(threadCount * 2);
        for (int i = 0; i < threadCount; i++) {
            final Thread borrowThread = new Thread() {
                @Override
                public void run() {
                    try {
                        startFlag.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    for (int j = 0; j < 5; j++) {
                        try {
                            Thread.sleep(random.nextInt(5));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        try {
                            final Integer object = pool.borrowObject(key, 10);
                            Assert.assertNotNull(object);
                            Assert.assertTrue(borrowObjects.offer(object));
                        } catch (Exception ignore) {
                        }
                    }
                    finishFlag.countDown();
                }
            };
            borrowThread.start();

            final Thread returnThread = new Thread() {
                @Override
                public void run() {
                    try {
                        startFlag.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    for (int j = 0; j < 4; j++) {
                        try {
                            Thread.sleep(random.nextInt(5));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        try {
                            final Integer object = borrowObjects.poll();
                            if (object != null) {
                                pool.returnObject(key, object);
                            }
                        } catch (Exception e) {
                            Assert.fail(e.getMessage());
                        }
                    }
                    finishFlag.countDown();
                }
            };
            returnThread.start();
        }
        startFlag.countDown();
        try {
            finishFlag.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("**threads finished**");
        logger.info("pool size = {}", pool.getPoolSize(key));
        logger.info("active = {}", pool.getActiveCount(key));
        logger.info("idle = {}", pool.getIdleCount(key));
        logger.info("peak = {}", pool.getPeakCount(key));
        logger.info("borrow = {}", borrowObjects.size());
        logger.info("remain = {}", factory.sequence);

        Assert.assertEquals(pool.getPoolSize(key), pool.getActiveCount(key) + pool.getIdleCount(key));
        Assert.assertTrue(pool.getPoolSize(key) <= 50);
        Assert.assertEquals(factory.sequence, borrowObjects.size() + pool.getIdleCount(key));

        for (Integer object : borrowObjects) {
            try {
                pool.returnObject(key, object);
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            }
        }
        borrowObjects.clear();

        logger.info("**borrowed objects returned**");
        logger.info("pool size = {}", pool.getPoolSize(key));
        logger.info("active = {}", pool.getActiveCount(key));
        logger.info("idle = {}", pool.getIdleCount(key));
        logger.info("peak = {}", pool.getPeakCount(key));
        logger.info("borrow = {}", borrowObjects.size());
        logger.info("remain = {}", factory.sequence);

        Assert.assertEquals(0, pool.getActiveCount(key));
        Assert.assertEquals(pool.getIdleCount(key), factory.sequence);
        Assert.assertTrue(pool.getIdleCount(key) <= 50);
        try {
            pool.removeAllObjects(key);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        logger.info("**pool cleared**");
        logger.info("pool size = {}", pool.getPoolSize(key));
        logger.info("active = {}", pool.getActiveCount(key));
        logger.info("idle = {}", pool.getIdleCount(key));
        logger.info("peak = {}", pool.getPeakCount(key));
        logger.info("borrow = {}", borrowObjects.size());
        logger.info("remain = {}", factory.sequence);

        // ensure all objects has been destroyed
        Assert.assertEquals(0, factory.sequence);

        pool.destroy();
    }

    @Test
    public void testComplexMultiThreads() {
        final int threadCount = 100;
        final int removeThreadCount = 30;
        final Random random = new Random(System.currentTimeMillis());

        final PoolableObjectFactoryImpl factory = new PoolableObjectFactoryImpl();
        final BaseObjectPool.Builder<Integer, Integer> builder = new BaseObjectPool.Builder<Integer, Integer>(factory);
        builder.max(50);
        builder.min(10);
        builder.disposable(false);
        builder.keepAliveTimeoutInSecs(-1);
        final ObjectPool<Integer, Integer> pool = builder.build();
        final int key = 1;

        final ConcurrentLinkedQueue<Integer> borrowObjects = new ConcurrentLinkedQueue<Integer>();
        final CountDownLatch startFlag = new CountDownLatch(1);
        final CountDownLatch finishFlag = new CountDownLatch(threadCount * 2 + removeThreadCount);
        final CountDownLatch removeFlag = new CountDownLatch(1);
        for (int i = 0; i < threadCount; i++) {
            final Thread borrowThread = new Thread() {
                @Override
                public void run() {
                    try {
                        startFlag.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    for (int j = 0; j < 5; j++) {
                        try {
                            Thread.sleep(random.nextInt(5));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        try {
                            final Integer object = pool.borrowObject(key, 10);
                            Assert.assertNotNull(object);
                            Assert.assertTrue(borrowObjects.offer(object));
                        } catch (Exception ignore) {
                        }
                    }
                    finishFlag.countDown();
                }
            };
            borrowThread.start();

            final Thread returnThread = new Thread() {
                @Override
                public void run() {
                    try {
                        startFlag.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    for (int j = 0; j < 5; j++) {
                        try {
                            Thread.sleep(random.nextInt(5));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        try {
                            final Integer object = borrowObjects.poll();
                            if (object != null) {
                                pool.returnObject(key, object);
                            }
                        } catch (Exception e) {
                            Assert.fail(e.getMessage());
                        }
                    }
                    finishFlag.countDown();
                }
            };
            returnThread.start();
        }
        for (int i = 0; i < removeThreadCount; i++) {
            final Thread removeThread = new Thread() {
                @Override
                public void run() {
                    try {
                        removeFlag.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    try {
                        Thread.sleep(random.nextInt(5));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    try {
                        pool.removeAllObjects(key);
                    } catch (Exception e) {
                        Assert.fail(e.getMessage());
                    }
                    finishFlag.countDown();
                }
            };
            removeThread.start();
        }
        startFlag.countDown();
        removeFlag.countDown();

        try {
            finishFlag.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("**threads finished**");
        logger.info("pool size = {}", pool.getPoolSize(key));
        logger.info("active = {}", pool.getActiveCount(key));
        logger.info("idle = {}", pool.getIdleCount(key));
        logger.info("peak = {}", pool.getPeakCount(key));
        logger.info("borrow = {}", borrowObjects.size());
        logger.info("remain = {}", factory.sequence);

        Assert.assertEquals(pool.getPoolSize(key), pool.getActiveCount(key) + pool.getIdleCount(key));
        Assert.assertEquals(factory.sequence, borrowObjects.size() + pool.getIdleCount(key));

        for (Integer object : borrowObjects) {
            try {
                pool.returnObject(key, object);
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            }
        }
        borrowObjects.clear();

        logger.info("**borrowed objects returned**");
        logger.info("pool size = {}", pool.getPoolSize(key));
        logger.info("active = {}", pool.getActiveCount(key));
        logger.info("idle = {}", pool.getIdleCount(key));
        logger.info("peak = {}", pool.getPeakCount(key));
        logger.info("borrow = {}", borrowObjects.size());
        logger.info("remain = {}", factory.sequence);

        Assert.assertEquals(0, pool.getActiveCount(key));
        Assert.assertEquals(pool.getIdleCount(key), factory.sequence);

        try {
            pool.removeAllObjects(key);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        logger.info("**pool cleared**");
        logger.info("pool size = {}", pool.getPoolSize(key));
        logger.info("active = {}", pool.getActiveCount(key));
        logger.info("idle = {}", pool.getIdleCount(key));
        logger.info("peak = {}", pool.getPeakCount(key));
        logger.info("borrow = {}", borrowObjects.size());
        logger.info("remain = {}", factory.sequence);

        // ensure all objects has been destroyed
        Assert.assertEquals(0, factory.sequence);

        pool.destroy();
    }

    private static class PoolableObjectFactoryImpl implements PoolableObjectFactory<Integer, Integer> {

        private int sequence = 0;
        private int id = 0;

        @Override
        public synchronized Integer createObject(final Integer key) throws Exception {
            sequence++;
            id++;
            return id;
        }

        @Override
        public synchronized void destroyObject(final Integer key, final Integer value) throws Exception {
            sequence--;
        }

        @Override
        public boolean validateObject(final Integer key, final Integer value) throws Exception {
            return true;
        }
    }
}
