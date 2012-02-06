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

package org.glassfish.grizzly.memcached;

import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.MemcachedClientBuilder;
import net.rubyeye.xmemcached.XMemcachedClientBuilder;
import net.rubyeye.xmemcached.command.BinaryCommandFactory;
import net.rubyeye.xmemcached.exception.MemcachedException;
import net.rubyeye.xmemcached.utils.AddrUtil;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * @author Bongjae Chang
 */
public class BasicBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(BasicBenchmark.class);
    private static final SocketAddress DEFAULT_MEMCACHED_ADDRESS = new InetSocketAddress(11211);
    private static final int expirationTimeoutInSec = 60 * 30; // 30min

    // memcached server should be booted in local
    //@Test
    public void testBenchmarkingBasicCommand() {
        final int loop = 50000;
        final long timeout = 5000;
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        builder.borrowValidation(false);
        builder.returnValidation(false);
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        // add, set, get, delete

        // ensure "name" doesn't exist
        userCache.delete("name", false);

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < loop; i++) {
            boolean result = userCache.add("name", "foo", expirationTimeoutInSec, false);
            Assert.assertTrue(result);
            String value = userCache.get("name", false);
            Assert.assertEquals("foo", value);
            result = userCache.delete("name", false);
            Assert.assertTrue(result);

            result = userCache.set("name", "foo", expirationTimeoutInSec, false);
            Assert.assertTrue(result);
            result = userCache.set("name", "foo", expirationTimeoutInSec, false);
            Assert.assertTrue(result);
            value = userCache.get("name", false);
            Assert.assertEquals("foo", value);
            result = userCache.delete("name", false);
            Assert.assertTrue(result);
        }
        logger.info("grizzly memcached client elapse = {}", (System.currentTimeMillis() - startTime) + "ms");

        manager.shutdown();


        final MemcachedClient xmemcached;
        try {
            MemcachedClientBuilder builder1 = new XMemcachedClientBuilder(AddrUtil.getAddresses("localhost:11211"));
            builder1.setCommandFactory(new BinaryCommandFactory());
            xmemcached = builder1.build();
        } catch (IOException ignore) {
            return;
        }

        startTime = System.currentTimeMillis();
        for (int i = 0; i < loop; i++) {
            try {
                boolean result = xmemcached.add("name", expirationTimeoutInSec, "foo", timeout);
                Assert.assertTrue(result);
                String value = xmemcached.get("name", timeout);
                Assert.assertEquals("foo", value);
                result = xmemcached.delete("name", timeout);
                Assert.assertTrue(result);

                result = xmemcached.set("name", expirationTimeoutInSec, "foo", timeout);
                Assert.assertTrue(result);
                result = xmemcached.set("name", expirationTimeoutInSec, "foo", timeout);
                Assert.assertTrue(result);
                value = xmemcached.get("name", timeout);
                Assert.assertEquals("foo", value);
                result = xmemcached.delete("name", timeout);
                Assert.assertTrue(result);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        logger.info("xmemcached client elapse = {}", (System.currentTimeMillis() - startTime) + "ms");

        try {
            xmemcached.shutdown();
        } catch (IOException ignore) {
        }
    }

    // memcached server should be booted in local
    //@Test
    public void testBenchmarkingGetMultiCommand() throws MemcachedException, TimeoutException, InterruptedException {
        final int multiSize = 20000;
        long startTime;
        Map<String, String> result = null;
        String data = "";
        for (int i = 0; i < 10; i++) {
            data += "foo";
        }

        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        builder.borrowValidation(false);
        builder.returnValidation(false);
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        // pre set
        Set<String> keys = new HashSet<String>();
        for (int i = 0; i < multiSize; i++) {
            final String key = "name" + i;
            keys.add(key);
            userCache.set(key, data + i, expirationTimeoutInSec, false);
        }

        startTime = System.currentTimeMillis();
        result = userCache.getMulti(keys);
        logger.info("grizzly memcached client elapse = {}", (System.currentTimeMillis() - startTime));

        Assert.assertEquals(multiSize, result.size());
        for (int i = 0; i < multiSize; i++) {
            final String key = "name" + i;
            final String value = result.get(key);
            Assert.assertNotNull(value);
            Assert.assertEquals(data + i, value);
        }

        startTime = System.currentTimeMillis();
        result = userCache.getMulti(keys);
        logger.info("grizzly memcached client elapse2 = {}", (System.currentTimeMillis() - startTime));

        Assert.assertEquals(multiSize, result.size());
        for (int i = 0; i < multiSize; i++) {
            final String key = "name" + i;
            final String value = result.get(key);
            Assert.assertNotNull(value);
            Assert.assertEquals(data + i, value);
        }

        startTime = System.currentTimeMillis();
        for (String key : keys) {
            userCache.get(key, false);
        }
        logger.info("grizzly memcached client elapse(no multi) = {}", (System.currentTimeMillis() - startTime));

        for (String key : keys) {
            userCache.delete(key, false);
        }

        manager.shutdown();

        // sometimes, memcached client will be blocked.
        /*
        MemcachedClient xmemcached = null;
        try {
            MemcachedClientBuilder builder1 = new XMemcachedClientBuilder(AddrUtil.getAddresses("localhost:11211"));
            builder1.setCommandFactory(new BinaryCommandFactory());
            xmemcached = builder1.build();
        } catch (IOException ignore) {
            return;
        }

        // pre set
        Set<String> keys2 = new HashSet<String>();
        for (int i = 0; i < multiSize; i++) {
            final String key = "namo" + i;
            keys2.add(key);
            xmemcached.set(key, expirationTimeoutInSec, data + i, timeout);
        }

        startTime = System.currentTimeMillis();
        result = xmemcached.get(keys2, timeout);
        logger.info("xmemcached client elapse = {}", (System.currentTimeMillis() - startTime));

        Assert.assertEquals(multiSize, result.size());
        for (int i = 0; i < multiSize; i++) {
            final String key = "namo" + i;
            final String value = result.get(key);
            Assert.assertNotNull(value);
            Assert.assertEquals(data + i, value);
        }

        startTime = System.currentTimeMillis();
        result = xmemcached.get(keys2, timeout);
        logger.info("xmemcached client elapse2 = {}", (System.currentTimeMillis() - startTime));

        Assert.assertEquals(multiSize, result.size());
        for (int i = 0; i < multiSize; i++) {
            final String key = "namo" + i;
            final String value = result.get(key);
            Assert.assertNotNull(value);
            Assert.assertEquals(data + i, value);
        }

        startTime = System.currentTimeMillis();
        for (String key : keys2) {
            xmemcached.get(key, timeout);
        }
        logger.log("xmemcached client elapse(no multi) = {}", (System.currentTimeMillis() - startTime));

        for (String key : keys2) {
            xmemcached.delete(key, timeout);
        }

        try {
            xmemcached.shutdown();
        } catch (IOException ignore) {
        }
        */
    }

    @SuppressWarnings("unchecked")
    // memcached server should be booted in local
    //@Test
    public void testBenchmarkingSetMulti() {
        final int multiSize = 20000;
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        final Map<String, String> map = new HashMap<String, String>();
        for (int i = 0; i < multiSize; i++) {
            final String key = "name" + i;
            final String value = "foo" + i;
            map.put(key, value);
        }

        long startTime = System.currentTimeMillis();
        final Map<String, Boolean> result = ((GrizzlyMemcachedCache) userCache).setMulti(map, expirationTimeoutInSec);
        logger.info("grizzly memcached client elapse = {}", (System.currentTimeMillis() - startTime) + "ms");
        for (Boolean success : result.values()) {
            Assert.assertTrue(success);
        }

        for (int i = 0; i < multiSize; i++) {
            final String key = "name" + i;
            final String value = map.get(key);
            Assert.assertNotNull(value);
            Assert.assertEquals("foo" + i, value);

            // clean
            userCache.delete(key, false);
        }

        startTime = System.currentTimeMillis();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            userCache.set(entry.getKey(), entry.getValue(), expirationTimeoutInSec, false);
        }
        logger.info("grizzly memcached client elapse(no multi) = {}", (System.currentTimeMillis() - startTime) + "ms");

        for (int i = 0; i < multiSize; i++) {
            final String key = "name" + i;
            final String value = map.get(key);
            Assert.assertNotNull(value);
            Assert.assertEquals("foo" + i, value);

            // clean
            userCache.delete(key, false);
        }

        manager.shutdown();
    }
}
