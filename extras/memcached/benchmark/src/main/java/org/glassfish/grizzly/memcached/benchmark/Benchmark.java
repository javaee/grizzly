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

package org.glassfish.grizzly.memcached.benchmark;

import com.danga.MemCached.MemCachedClient;
import com.danga.MemCached.SockIOPool;
import com.schooner.MemCached.BinaryClient;
import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.MemcachedClientBuilder;
import net.rubyeye.xmemcached.XMemcachedClientBuilder;
import net.rubyeye.xmemcached.command.BinaryCommandFactory;
import net.rubyeye.xmemcached.exception.MemcachedException;
import net.spy.memcached.AddrUtil;
import net.spy.memcached.BinaryConnectionFactory;
import net.spy.memcached.internal.OperationFuture;
import org.glassfish.grizzly.memcached.GrizzlyMemcachedCache;
import org.glassfish.grizzly.memcached.GrizzlyMemcachedCacheManager;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Bongjae Chang
 */
public class Benchmark {

    private final String host;
    private final int port;
    private final int valueSize;
    private final int threadNums;
    private final int totalKeyCnts;
    private final int loopCnts;

    private final String value;
    private final TestMemcachedClient[] clients;

    private enum TestType {
        GET, GET_MULTI, SET, SET_MULTI
    }

    private enum ClientType {
        GRIZZLY, SPY, JAVA, X
    }

    private static final long RESPONSE_TIMEOUT_IN_MILLIS = 10000; // 10secs
    private static final int DEFAULT_LOOP_COUNTS = 200;
    private static final int DEFAULT_TOTAL_KEY_COUNTS = 200;

    private final AtomicInteger totalMissedCnts = new AtomicInteger();

    private Benchmark(final String host,
                      final int port,
                      final int valueSize,
                      final int threadNums) throws Exception {
        final Set<SocketAddress> addressSet = new HashSet<SocketAddress>();
        addressSet.add(new InetSocketAddress(host, port));
        clients = makeClients(ClientType.values(), addressSet);
        this.host = host;
        this.port = port;
        this.valueSize = valueSize;
        this.threadNums = threadNums;
        this.totalKeyCnts = DEFAULT_TOTAL_KEY_COUNTS;
        this.loopCnts = DEFAULT_LOOP_COUNTS;
        this.value = makeValue(valueSize);
    }

    private TestMemcachedClient[] getClients() {
        return clients;
    }

    private void testBenchmark(final TestMemcachedClient client, final TestType testType) throws Exception {
        if (client == null) {
            throw new IllegalStateException("client must not be null");
        }

        // prepare test
        final List<String[]> keyArrayPerThread = new ArrayList<String[]>();
        final List<Map<String, Object>> keyValueMapPerThread = new ArrayList<Map<String, Object>>();
        switch (testType) {
            case GET:
            case GET_MULTI:
                // ensure keys exist
                for (int i = 0; i < threadNums; i++) {
                    final String[] keyArray = new String[totalKeyCnts];
                    final Map<String, Object> keyValueMap = new HashMap<String, Object>();
                    for (int j = 0; j < totalKeyCnts; j++) {
                        final String key = "key" + j + "t" + i;
                        client.set(key, value);
                        keyArray[j] = key;
                        keyValueMap.put(key, value);
                    }
                    keyArrayPerThread.add(keyArray);
                    keyValueMapPerThread.add(keyValueMap);
                }
                break;
            case SET:
            case SET_MULTI:
                // ensure keys don't exist
                for (int i = 0; i < threadNums; i++) {
                    final String[] keyArray = new String[totalKeyCnts];
                    final Map<String, Object> keyValueMap = new HashMap<String, Object>();
                    for (int j = 0; j < totalKeyCnts; j++) {
                        final String key = "key" + j + "t" + i;
                        client.delete(key);
                        keyArray[j] = key;
                        keyValueMap.put(key, value);
                    }
                    keyArrayPerThread.add(keyArray);
                    keyValueMapPerThread.add(keyValueMap);
                }
                break;
            default:
                throw new IllegalStateException("invalid test type");
        }

        // warming up
        final int warmingupLoopCnts = loopCnts / 3;
        //System.out.println("Start warming up: loop=" + warmingupLoopCnts);
        final Thread[] threads = new Thread[threadNums];
        CyclicBarrier allStarted = new CyclicBarrier(threadNums);
        CountDownLatch allFinished = new CountDownLatch(threadNums);
        for (int i = 0; i < threadNums; i++) {
            threads[i] = new TestThread(client, testType, keyArrayPerThread.get(i), keyValueMapPerThread.get(i), keyValueMapPerThread.get(i).keySet(), warmingupLoopCnts, i, allStarted, allFinished);
            threads[i].start();
        }
        try {
            allFinished.await();
        } catch (InterruptedException ignore) {
        }
        int missed;
        //missed = totalMissedCnts.get();
        //System.out.println("Finished: missedCnts=" + missed);

        // start benchmarking
        totalMissedCnts.set(0);
        allStarted = new CyclicBarrier(threadNums + 1);
        allFinished = new CountDownLatch(threadNums);
        for (int i = 0; i < threadNums; i++) {
            threads[i] = new TestThread(client, testType, keyArrayPerThread.get(i), keyValueMapPerThread.get(i), keyValueMapPerThread.get(i).keySet(), loopCnts, i, allStarted, allFinished);
            threads[i].start();
        }
        try {
            allStarted.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (BrokenBarrierException ignore) {
            return;
        }
        //System.out.println("Start benchmarking: loop=" + loopCnts);
        final long startTime = System.currentTimeMillis();
        try {
            allFinished.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        final long elapse = System.currentTimeMillis() - startTime;
        missed = totalMissedCnts.get();
        //System.out.println("Finished: elapse=" + elapse + "ms" + ", missedCnts=" + missed);
        final long tps = (long) (((double) loopCnts * (double) threadNums * (double) totalKeyCnts - (double) missed) / ((double) elapse / (double) 1000));
        System.out.format("%26s : %s%n", client + "_" + testType, "TPS=" + tps + ", elapse=" + elapse + "ms" + ", missed=" + missed);
    }

    private void stop() {
        if (clients != null) {
            for (TestMemcachedClient client : clients) {
                try {
                    client.stop();
                } catch (Exception ignore) {
                }
            }
        }
    }

    private class TestThread extends Thread {

        private final TestMemcachedClient client;
        private final TestType testType;
        private final String[] keyArray;
        private final Map<String, Object> keyValueMap;
        private final Set<String> keys;
        private final int loopCnts;
        private final int threadIndex;
        private final CyclicBarrier allStarted;
        private final CountDownLatch allFinished;

        private TestThread(final TestMemcachedClient client,
                           final TestType testType,
                           final String[] keyArray,
                           final Map<String, Object> keyValueMap,
                           final Set<String> keys,
                           final int loopCnts,
                           final int threadIndex,
                           final CyclicBarrier allStarted,
                           final CountDownLatch allFinished) {
            this.client = client;
            this.keyArray = keyArray;
            this.keyValueMap = keyValueMap;
            this.keys = keys;
            this.testType = testType;
            this.loopCnts = loopCnts;
            this.threadIndex = threadIndex;
            this.allStarted = allStarted;
            this.allFinished = allFinished;
        }

        @Override
        public void run() {
            try {
                if (keyArray == null ||
                        keyValueMap == null ||
                        keys == null ||
                        testType == null ||
                        client == null ||
                        allStarted == null ||
                        allFinished == null) {
                    throw new IllegalStateException();
                }
                try {
                    allStarted.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (BrokenBarrierException ignore) {
                    return;
                }
                int missedCnts = 0;
                switch (testType) {
                    case SET:
                        for (int i = 0; i < loopCnts; i++) {
                            for (int j = 0; j < totalKeyCnts; j++) {
                                if (!client.set(keyArray[j], value)) {
                                    missedCnts++;
                                    //System.out.println("set error");
                                }
                            }
                        }
                        break;
                    case SET_MULTI:
                        for (int i = 0; i < loopCnts; i++) {
                            if (!client.setMulti(keyValueMap)) {
                                missedCnts += totalKeyCnts;
                                //System.out.println("set multi error");
                            }
                        }
                        break;
                    case GET:
                        final Map<String, Object> resultMap = new HashMap<String, Object>();
                        for (int i = 0; i < loopCnts; i++) {
                            for (int j = 0; j < totalKeyCnts; j++) {
                                final String result = client.get(keyArray[j]);
                                if (result != null) {
                                    resultMap.put(keyArray[j], result);
                                } else {
                                    //System.out.println("get error");
                                }
                            }
                            missedCnts += checkData(resultMap);
                            resultMap.clear();
                        }
                        break;
                    case GET_MULTI:
                        Map<String, Object> resultMap2;
                        for (int i = 0; i < loopCnts; i++) {
                            resultMap2 = client.getMulti(keys, keyArray);
                            if (resultMap2 != null) {
                                missedCnts += checkData(resultMap2);
                            } else {
                                missedCnts += totalKeyCnts;
                                //System.out.println("get multi error");
                            }
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("testType is not valid");
                }
                if (missedCnts != 0) {
                    totalMissedCnts.addAndGet(missedCnts);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                allFinished.countDown();
            }
        }

        private int checkData(Map<String, Object> result) {
            if (result == null) {
                throw new IllegalArgumentException("result must not be null");
            }
            int missed = 0;
            for (int i = 0; i < totalKeyCnts; i++) {
                final String key = "key" + i + "t" + threadIndex;
                final String value = (String) result.get(key);
                if (value == null || value.length() != valueSize) {
                    missed++;
                }
            }
            return missed;
        }
    }

    private interface TestMemcachedClient {
        public void start(final Set<SocketAddress> servers) throws Exception;

        public void stop() throws Exception;

        public void delete(String key);

        public boolean set(String key, String value);

        public boolean setMulti(Map<String, Object> map);

        public String get(String key);

        public Map<String, Object> getMulti(Set<String> keys, String[] keyArray);

        public boolean isMultiClient();

        public TestType[] supportedTypes();
    }

    private class GrizzlyMemcached implements TestMemcachedClient {
        private GrizzlyMemcachedCacheManager cacheManager;
        private GrizzlyMemcachedCache<String, Object> cache;
        private final String REGION = "test";

        @Override
        public void start(final Set<SocketAddress> servers) throws Exception {
            if (servers == null || servers.isEmpty()) {
                throw new IllegalArgumentException("server list is null or empty");
            }
            final GrizzlyMemcachedCacheManager cacheManager = new GrizzlyMemcachedCacheManager.Builder().build();
            final GrizzlyMemcachedCache.Builder<String, Object> builder = cacheManager.createCacheBuilder(REGION);
            builder.servers(servers);
            builder.writeTimeoutInMillis(3000);
            builder.connectTimeoutInMillis(-1);
            builder.responseTimeoutInMillis(RESPONSE_TIMEOUT_IN_MILLIS);
            builder.minConnectionPerServer(threadNums / 2);
            final GrizzlyMemcachedCache<String, Object> cache = builder.build();

            this.cacheManager = cacheManager;
            this.cache = cache;
        }

        @Override
        public void stop() throws Exception {
            if (cache != null) {
                cache.stop();
            }
            if (cacheManager != null) {
                cacheManager.shutdown();
            }
        }

        @Override
        public void delete(String key) {
            if (key == null) {
                throw new IllegalArgumentException("keys must not be null");
            }
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            cache.delete(key, false);
        }

        @Override
        public boolean set(String key, String value) {
            if (key == null) {
                throw new IllegalArgumentException("keys must not be null");
            }
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            return cache.set(key, value, 0, false);
        }

        @Override
        public boolean setMulti(Map<String, Object> map) {
            if (map == null) {
                throw new IllegalArgumentException("keys must not be null");
            }
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            Map<String, Boolean> result = cache.setMulti(map, 0);
            return result != null && result.size() == map.size();
        }

        @Override
        public String get(String key) {
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            return (String) cache.get(key, false);
        }

        @Override
        public Map<String, Object> getMulti(Set<String> keys, String[] keyArray) {
            if (keys == null) {
                throw new IllegalArgumentException("keys must not be null");
            }
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            return cache.getMulti(keys);
        }

        @Override
        public boolean isMultiClient() {
            return false;
        }

        @Override
        public TestType[] supportedTypes() {
            return new TestType[]{TestType.GET, TestType.GET_MULTI, TestType.SET, TestType.SET_MULTI};
        }

        @Override
        public String toString() {
            return "GrizzlyMemcached";
        }
    }

    private class XMemcached implements TestMemcachedClient {
        private MemcachedClient cache;

        @Override
        public void start(final Set<SocketAddress> servers) throws Exception {
            if (servers == null || servers.isEmpty()) {
                throw new IllegalArgumentException("server list is null or empty");
            }
            final List<InetSocketAddress> serverList = new ArrayList<InetSocketAddress>();
            for (SocketAddress address : servers) {
                if (address instanceof InetSocketAddress) {
                    final InetSocketAddress inet = (InetSocketAddress) address;
                    serverList.add(inet);
                }
            }
            final MemcachedClientBuilder builder = new XMemcachedClientBuilder(serverList);
            builder.setCommandFactory(new BinaryCommandFactory());
            this.cache = builder.build();
        }

        @Override
        public void stop() throws Exception {
            if (cache != null) {
                cache.shutdown();
            }
        }

        @Override
        public void delete(String key) {
            if (key == null) {
                throw new IllegalArgumentException("keys must not be null");
            }
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            try {
                cache.delete(key);
            } catch (TimeoutException ignore) {
            } catch (InterruptedException ignore) {
            } catch (MemcachedException ignore) {
            }
        }

        @Override
        public boolean set(String key, String value) {
            if (key == null) {
                throw new IllegalArgumentException("keys must not be null");
            }
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            try {
                return cache.set(key, 0, value, RESPONSE_TIMEOUT_IN_MILLIS);
            } catch (TimeoutException ignore) {
            } catch (InterruptedException ignore) {
            } catch (MemcachedException ignore) {
            }
            return false;
        }

        @Override
        public boolean setMulti(Map<String, Object> map) {
            return false;
        }

        @Override
        public String get(String key) {
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            try {
                return cache.get(key, RESPONSE_TIMEOUT_IN_MILLIS);
            } catch (TimeoutException ignore) {
            } catch (InterruptedException ignore) {
            } catch (MemcachedException ignore) {
            }
            return null;
        }

        @Override
        public Map<String, Object> getMulti(Set<String> keys, String[] keyArray) {
            if (keys == null) {
                throw new IllegalArgumentException("keys must not be null");
            }
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            try {
                return cache.get(keys, RESPONSE_TIMEOUT_IN_MILLIS);
            } catch (TimeoutException ignore) {
            } catch (InterruptedException ignore) {
            } catch (MemcachedException ignore) {
            }
            return null;
        }

        @Override
        public boolean isMultiClient() {
            return false;
        }

        @Override
        public TestType[] supportedTypes() {
            return new TestType[]{TestType.GET, TestType.GET_MULTI, TestType.SET};
        }

        @Override
        public String toString() {
            return "XMemcached";
        }
    }

    private class XMultiMemcached extends XMemcached {
        @Override
        public boolean isMultiClient() {
            return true;
        }
    }

    private class JavaMemcached implements TestMemcachedClient {
        private MemCachedClient cache;
        private SockIOPool pool;

        @Override
        public void start(final Set<SocketAddress> servers) throws Exception {
            if (servers == null || servers.isEmpty()) {
                throw new IllegalArgumentException("server list is null or empty");
            }
            final SockIOPool pool = SockIOPool.getInstance();
            pool.setMinConn(threadNums / 2);
            pool.setMaxConn(10000);
            pool.setMaxIdle(60 * 60 * 1000);
            pool.setSocketTO((int) RESPONSE_TIMEOUT_IN_MILLIS);
            for (SocketAddress address : servers) {
                if (address instanceof InetSocketAddress) {
                    final InetSocketAddress inet = (InetSocketAddress) address;
                    final String host = inet.getHostName();
                    final int port = inet.getPort();
                    pool.setServers(new String[]{host + ":" + port});
                    break;
                }
            }
            pool.initialize();
            this.cache = new BinaryClient();
            this.pool = pool;
        }

        @Override
        public void stop() throws Exception {
            if (pool != null) {
                pool.shutDown();
            }
        }

        @Override
        public void delete(String key) {
            if (key == null) {
                throw new IllegalArgumentException("keys must not be null");
            }
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            cache.delete(key);
        }

        @Override
        public boolean set(String key, String value) {
            if (key == null) {
                throw new IllegalArgumentException("keys must not be null");
            }
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            return cache.set(key, value);
        }

        @Override
        public boolean setMulti(Map<String, Object> map) {
            return false;
        }

        @Override
        public String get(String key) {
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            return (String) cache.get(key);
        }

        @Override
        public Map<String, Object> getMulti(Set<String> keys, String[] keyArray) {
            if (keys == null || keyArray == null) {
                throw new IllegalArgumentException("keys must not be null");
            }
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            return cache.getMulti(keyArray);
        }

        @Override
        public boolean isMultiClient() {
            return false;
        }

        @Override
        public TestType[] supportedTypes() {
            return new TestType[]{TestType.GET, TestType.GET_MULTI, TestType.SET};
        }

        @Override
        public String toString() {
            return "JavaMemcached";
        }
    }

    private class JavaMultiMemcached extends JavaMemcached {
        @Override
        public boolean isMultiClient() {
            return true;
        }
    }

    private class SpyMemcached implements TestMemcachedClient {
        protected net.spy.memcached.MemcachedClient cache;

        @Override
        public void start(final Set<SocketAddress> servers) throws Exception {
            if (servers == null || servers.isEmpty()) {
                throw new IllegalArgumentException("server list is null or empty");
            }
            String server = "";
            for (SocketAddress address : servers) {
                if (address instanceof InetSocketAddress) {
                    final InetSocketAddress inet = (InetSocketAddress) address;
                    final String host = inet.getHostName();
                    final int port = inet.getPort();
                    server = host + ":" + port;
                    break;
                }
            }
            cache = new net.spy.memcached.MemcachedClient(new BinaryConnectionFactory() {
                @Override
                public long getOperationTimeout() {
                    return RESPONSE_TIMEOUT_IN_MILLIS;
                }
            }, AddrUtil.getAddresses(server));
        }

        @Override
        public void stop() throws Exception {
            if (cache != null) {
                cache.shutdown();
            }
        }

        @Override
        public void delete(String key) {
            if (key == null) {
                throw new IllegalArgumentException("keys must not be null");
            }
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            cache.delete(key);
        }

        @Override
        public boolean set(String key, String value) {
            if (key == null) {
                throw new IllegalArgumentException("keys must not be null");
            }
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            OperationFuture<Boolean> future = cache.set(key, 0, value);
            try {
                return future.get();
            } catch (InterruptedException e) {
                return false;
            } catch (ExecutionException e) {
                return false;
            }
        }

        @Override
        public boolean setMulti(Map<String, Object> map) {
            return false;
        }

        @Override
        public String get(String key) {
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            return (String) cache.get(key);
        }

        @Override
        public Map<String, Object> getMulti(Set<String> keys, String[] keyArray) {
            if (keys == null || keyArray == null) {
                throw new IllegalArgumentException("keys must not be null");
            }
            if (cache == null) {
                throw new IllegalStateException("cache must not be null");
            }
            return cache.getBulk(keys);
        }

        @Override
        public boolean isMultiClient() {
            return false;
        }

        @Override
        public TestType[] supportedTypes() {
            return new TestType[]{TestType.GET, TestType.GET_MULTI, TestType.SET};
        }

        @Override
        public String toString() {
            return "SPYMemcached";
        }
    }

    private class SpyMultiMemcached extends SpyMemcached {
        @Override
        public boolean isMultiClient() {
            return true;
        }
    }

    private TestMemcachedClient[] makeClients(final ClientType[] clientTypes, final Set<SocketAddress> addressSet) throws Exception {
        if (clientTypes == null) {
            throw new IllegalArgumentException("client types must be not null");
        }
        final TestMemcachedClient[] clients = new TestMemcachedClient[clientTypes.length];
        for (int i = 0; i < clientTypes.length; i++) {
            final ClientType clientType = clientTypes[i];
            switch (clientType) {
                case GRIZZLY:
                    clients[i] = new GrizzlyMemcached();
                    break;
                case SPY:
                    clients[i] = new SpyMemcached();
                    break;
                case JAVA:
                    clients[i] = new JavaMemcached();
                    break;
                case X:
                    clients[i] = new XMemcached();
                    break;
                default:
                    throw new IllegalArgumentException("client type is not valid");
            }
            clients[i].start(addressSet);
        }
        return clients;
    }

    private static String makeValue(final int valueSize) {
        if (valueSize <= 0) {
            throw new IllegalArgumentException("valueSize should be positive");
        }
        final StringBuilder builder = new StringBuilder(valueSize);
        for (int i = 0; i < valueSize; i++) {
            builder.append(i % 10);
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return "Benchmark{" +
                "host=" + host +
                ", port=" + port +
                ", valueSize=" + valueSize +
                ", threadNums=" + threadNums +
                ", totalKeyCnts=" + totalKeyCnts +
                ", loopCnts=" + loopCnts +
                '}';
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            printUsage();
            return;
        }
        final Benchmark test = new Benchmark(args[0], // host
                Integer.parseInt(args[1]), // port
                Integer.parseInt(args[2]), // value size
                Integer.parseInt(args[3])); // thread numbers

        final TestMemcachedClient[] clients = test.getClients();
        if (clients == null || clients.length <= 0) {
            throw new IllegalStateException("failed to set up clients");
        }

        System.out.println(test);
        for (TestMemcachedClient client : clients) {
            System.out.println("----------------");
            for (TestType testType : client.supportedTypes()) {
                test.testBenchmark(client, testType);
            }
        }
        test.stop();
    }

    private static void printUsage() {
        System.out.println("4 Params are needed: host port valueSize threadNums");
    }
}
