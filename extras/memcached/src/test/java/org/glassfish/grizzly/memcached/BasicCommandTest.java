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

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Bongjae Chang
 */
public class BasicCommandTest {

    private static final Logger logger = LoggerFactory.getLogger(BasicCommandTest.class);

    private static final int expirationTimeoutInSec = 60 * 30; // 30min
    private static final SocketAddress DEFAULT_MEMCACHED_ADDRESS = new InetSocketAddress(11211);
    public static final String DEFAULT_CHARSET = "UTF-8";

    @Test
    public void emptyTest() {
    }

    // memcached server should be booted in local
    //@Test
    public void testBasicCommand() {
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        // add, set, get, delete

        // ensure "name" doesn't exist
        userCache.delete("name", false);

        boolean result = userCache.add("name", "foo", expirationTimeoutInSec, false);
        Assert.assertTrue(result);
        String value = userCache.get("name", false);
        Assert.assertEquals("foo", value);
        result = userCache.add("name", "foo", expirationTimeoutInSec, false);
        Assert.assertFalse(result); // key exists
        result = userCache.delete("name", false);
        Assert.assertTrue(result);
        value = userCache.get("name", false);
        Assert.assertEquals(null, value); // key not found

        result = userCache.set("name", "foo", expirationTimeoutInSec, false);
        Assert.assertTrue(result);
        result = userCache.set("name", "foo", expirationTimeoutInSec, false);
        Assert.assertTrue(result);
        value = userCache.get("name", false);
        Assert.assertEquals("foo", value);

        result = userCache.delete("name", false);
        Assert.assertTrue(result);

        manager.shutdown();
    }

    // memcached server should be booted in local
    //@Test
    public void testSeveralPackets() {
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        for (int i = 0; i < 100; i++) {
            boolean result = userCache.add("name", "foo", expirationTimeoutInSec, false);
            Assert.assertTrue(result);
            final String value = userCache.get("name", false);
            Assert.assertEquals("foo", value);
            result = userCache.delete("name", false);
            Assert.assertTrue(result);
        }

        for (int i = 0; i < 100; i++) {
            boolean result = userCache.set("name" + i, "foo" + i, expirationTimeoutInSec, false);
            Assert.assertTrue(result);
            final String value = userCache.get("name" + i, false);
            Assert.assertEquals("foo" + i, value);
        }

        for (int i = 0; i < 100; i++) {
            boolean result = userCache.delete("name" + i, false);
            Assert.assertTrue(result);
        }

        manager.shutdown();
    }

    // memcached server should be booted in local
    //@Test
    public void testBasicNoReplyCommand() {
        final int retry = 3;
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        // add, set, get, delete

        // ensure "name" doesn't exist
        userCache.delete("name", false);

        boolean result = userCache.add("name", "foo", expirationTimeoutInSec, true);
        Assert.assertTrue(result);

        String value = null;
        for (int i = 0; i < retry; i++) {
            value = userCache.get("name", false);
            // retry
            if (value == null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                break;
            }
        }
        Assert.assertEquals("foo", value);

        result = userCache.add("name", "foo", expirationTimeoutInSec, true);
        Assert.assertTrue(result); // ignore key exists error

        result = userCache.delete("name", true);
        Assert.assertTrue(result);

        for (int i = 0; i < retry; i++) {
            value = userCache.get("name", false);
            if (value != null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                break;
            }
        }
        Assert.assertEquals(null, value); // key not found

        result = userCache.set("name", "foo", expirationTimeoutInSec, true);
        Assert.assertTrue(result);
        result = userCache.set("name", "foo", expirationTimeoutInSec, true);
        Assert.assertTrue(result);

        for (int i = 0; i < retry; i++) {
            value = userCache.get("name", false);
            // retry
            if (value == null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                break;
            }
        }
        Assert.assertEquals("foo", value);

        result = userCache.delete("name", true);
        Assert.assertTrue(result);

        for (int i = 0; i < retry; i++) {
            value = userCache.get("name", false);
            if (value != null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                break;
            }
        }
        Assert.assertEquals(null, value); // key not found

        manager.shutdown();
    }

    // memcached server should be booted in local
    //@Test
    public void testVariousCommands() {
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        // ensure "name" doesn't exist
        userCache.delete("name", true);

        // replace
        boolean result = userCache.replace("name", "bar", expirationTimeoutInSec, false);
        Assert.assertFalse(result); // key not found

        result = userCache.set("name", "foo", expirationTimeoutInSec, false);
        Assert.assertTrue(result);
        String value = userCache.get("name", false);
        Assert.assertEquals("foo", value);

        result = userCache.replace("name", "bar", expirationTimeoutInSec, false);
        Assert.assertTrue(result);
        value = userCache.get("name", false);
        Assert.assertEquals("bar", value);

        // append
        String origin = value;
        result = userCache.append("name", "_appended", false);
        Assert.assertTrue(result);
        value = userCache.get("name", false);
        Assert.assertEquals(origin + "_appended", value);

        // prepend
        origin = value;
        result = userCache.prepend("name", "prepended_", false);
        Assert.assertTrue(result);
        value = userCache.get("name", false);
        Assert.assertEquals("prepended_" + origin, value);

        // gets
        final ValueWithCas<String> resultWithCas = userCache.gets("name", false);
        Assert.assertNotNull(resultWithCas);
        Assert.assertEquals(value, resultWithCas.getValue());

        // cas
        final long cas = resultWithCas.getCas();
        result = userCache.cas("name", "foo", expirationTimeoutInSec, cas + 1, false);
        Assert.assertFalse(result); // invalid cas
        result = userCache.cas("name", "foo", expirationTimeoutInSec, cas, false);
        Assert.assertTrue(result);
        value = userCache.get("name", false);
        Assert.assertEquals("foo", value);

        // getKey
        final ValueWithKey<String, String> resultWithKey = userCache.getKey("name", false);
        Assert.assertNotNull(resultWithKey);
        Assert.assertEquals("name", resultWithKey.getKey());
        Assert.assertEquals("foo", resultWithKey.getValue());

        // touch
        result = userCache.touch("name", expirationTimeoutInSec);
        Assert.assertTrue(result);

        // gat
        value = userCache.gat("name", expirationTimeoutInSec, false);
        Assert.assertEquals("foo", value);

        result = userCache.delete("name", true);
        Assert.assertTrue(result);

        manager.shutdown();
    }

    // memcached server should be booted in local
    //@Test
    public void testNoop() {
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        // noop
        final boolean result = userCache.noop(DEFAULT_MEMCACHED_ADDRESS);
        Assert.assertTrue(result);

        manager.shutdown();
    }

    // memcached server should be booted in local
    //@Test
    public void testVersion() {
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        // version
        final String value = userCache.version(DEFAULT_MEMCACHED_ADDRESS);
        Assert.assertNotNull(value);
        logger.info("Current Server Version: {}", value);

        manager.shutdown();
    }

    // memcached server should be booted in local
    public void testVerbosity() {
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        // verbosity(didn't work) v1.4.10
        final boolean result = userCache.verbosity(DEFAULT_MEMCACHED_ADDRESS, 0);
        Assert.assertTrue(result);

        manager.shutdown();
    }

    // memcached server should be booted in local
    //@Test
    public void testQuitCommand() {
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        builder.borrowValidation(false);
        builder.returnValidation(false);
        builder.maxConnectionPerServer(1);
        builder.minConnectionPerServer(1);
        builder.responseTimeoutInMillis(2000);
        //builder.responseTimeoutInMillis(-1);
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        final boolean result = userCache.quit(DEFAULT_MEMCACHED_ADDRESS, false);
        Assert.assertTrue(result);

        manager.shutdown();
    }

    // memcached server should be booted in local
    //@Test
    public void testStatsCommand() {
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        Map<String, String> result;
        result = userCache.stats(DEFAULT_MEMCACHED_ADDRESS);
        Assert.assertTrue(result.size() != 0);

        // specific item didn't work v1.4.10
        //result = userCache.statsItems(DEFAULT_MEMCACHED_ADDRESS, "pid", timeout );
        //Assert.assertTrue(result.size() == 1);
        //Assert.assertNotNull(result.get("pid"));

        manager.shutdown();
    }

    // memcached server should be booted in local
    //@Test
    public void testFlushAllCommand() {
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        boolean result;
        for (int i = 0; i < 10; i++) {
            result = userCache.set("name" + i, "foo" + i, expirationTimeoutInSec, false);
            Assert.assertTrue(result);
        }

        result = userCache.flushAll(DEFAULT_MEMCACHED_ADDRESS, 1, false);
        Assert.assertTrue(result);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignore) {
        }

        for (int i = 0; i < 10; i++) {
            String value = userCache.get("name" + i, false);
            Assert.assertNull(value); // expired
        }

        manager.shutdown();
    }

    // memcached server should be booted in local
    //@Test
    public void testGetMulti() {
        final int multiSize = 100;
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        Set<String> keys = new HashSet<String>();
        for (int i = 0; i < multiSize; i++) {
            final String key = "name" + i;
            keys.add(key);
            userCache.set(key, "foo" + i, expirationTimeoutInSec, false);
        }
        Map<String, String> result = userCache.getMulti(keys);
        Assert.assertEquals(multiSize, result.size());

        for (int i = 0; i < multiSize; i++) {
            final String key = "name" + i;
            final String value = result.get(key);
            Assert.assertNotNull(value);
            Assert.assertEquals("foo" + i, value);

            // clean
            userCache.delete(key, false);
        }

        manager.shutdown();
    }

    // memcached server should be booted in local
    //@Test
    public void testMissingGetMulti() {
        final int multiSize = 5;
        final int missingSize = 2;
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        Set<String> keys = new HashSet<String>();
        for (int i = 0; i < multiSize; i++) {
            final String key = "name" + i;
            keys.add(key);
            if (i < multiSize - missingSize) {
                userCache.set(key, "foo" + i, expirationTimeoutInSec, false);
            }
        }
        Map<String, String> result = userCache.getMulti(keys);
        Assert.assertEquals(multiSize - missingSize, result.size());

        for (int i = 0; i < multiSize; i++) {
            final String key = "name" + i;
            if (i < multiSize - missingSize) {
                final String value = result.get(key);
                Assert.assertNotNull(value);
                Assert.assertEquals("foo" + i, value);
            } else {
                final String value = result.get(key);
                Assert.assertNull(value);
            }

            // clean
            userCache.delete(key, false);
        }

        manager.shutdown();
    }

    // memcached server should be booted in local
    @SuppressWarnings("unchecked")
    //@Test
    public void testSetMulti() {
        final int multiSize = 100;
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
        final Map<String, Boolean> result = ((GrizzlyMemcachedCache) userCache).setMulti(map, expirationTimeoutInSec);
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

        manager.shutdown();
    }

    // memcached server should be booted in local
    @SuppressWarnings("unchecked")
    //@Test
    public void testDeleteMulti() {
        final int multiSize = 100;
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
        Map<String, Boolean> result = ((GrizzlyMemcachedCache) userCache).setMulti(map, expirationTimeoutInSec);
        for (Boolean success : result.values()) {
            Assert.assertTrue(success);
        }
        // ensure all values are set correctly
        for (int i = 0; i < multiSize; i++) {
            final String key = "name" + i;
            final String value = map.get(key);
            Assert.assertNotNull(value);
            Assert.assertEquals("foo" + i, value);
        }
        // put one missing key
        final String missingKey = "missingName";
        map.put(missingKey, "foo");
        result = ((GrizzlyMemcachedCache) userCache).deleteMulti(map.keySet());
        for (Boolean success : result.values()) {
            // always true
            Assert.assertTrue(success);
        }
        Assert.assertEquals(multiSize, result.size());

        for (int i = 0; i < multiSize; i++) {
            final String key = "name" + i;
            Assert.assertNull(userCache.get(key, false));
        }

        manager.shutdown();
    }

    // memcached server should be booted in local
    //@Test
    public void testIncrAndDecr() {
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        // ensure "age" doesn't exist
        userCache.delete("age", true);

        // initial age
        long age = userCache.incr("age", 1, 35, expirationTimeoutInSec, false);
        Assert.assertEquals(35, age);

        // increase 1
        age = userCache.incr("age", 1, 35, expirationTimeoutInSec, false);
        Assert.assertEquals(36, age);

        // decrease 1
        age = userCache.decr("age", 1, 35, expirationTimeoutInSec, false);
        Assert.assertEquals(35, age);

        userCache.delete("age", true);

        manager.shutdown();
    }

    // memcached server should be booted in local
    //@Test
    public void testCompress() {
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, String> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, String> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        final int valueSize = BufferWrapper.DEFAULT_COMPRESSION_THRESHOLD * 10;
        final StringBuilder stringBuilder = new StringBuilder(valueSize);
        for (int i = 0; i < valueSize; i++) {
            stringBuilder.append("o");
        }
        final String largeValue = stringBuilder.toString();

        // ensure "name" doesn't exist
        userCache.delete("name", false);

        boolean result = userCache.add("name", largeValue, expirationTimeoutInSec, false);
        Assert.assertTrue(result);
        String value = userCache.get("name", false);
        Assert.assertEquals(largeValue, value);

        // clear
        result = userCache.delete("name", false);
        Assert.assertTrue(result);

        manager.shutdown();
    }

    // memcached server should be booted in local
    //@Test
    public void testCompressObject() {
        final GrizzlyMemcachedCacheManager manager = new GrizzlyMemcachedCacheManager.Builder().build();
        final GrizzlyMemcachedCache.Builder<String, User> builder = manager.createCacheBuilder("user");
        final MemcachedCache<String, User> userCache = builder.build();
        userCache.addServer(DEFAULT_MEMCACHED_ADDRESS);

        final int valueSize = BufferWrapper.DEFAULT_COMPRESSION_THRESHOLD * 10;
        final StringBuilder stringBuilder = new StringBuilder(valueSize);
        for (int i = 0; i < valueSize; i++) {
            stringBuilder.append("o");
        }
        final String largeValue = stringBuilder.toString();

        // ensure "name" doesn't exist
        userCache.delete("user1", false);

        final User user1 = new User(1, largeValue, largeValue, largeValue);
        boolean result = userCache.add("user1", user1, expirationTimeoutInSec, false, -1, -1);
        Assert.assertTrue(result);
        User user2 = userCache.get("user1", false, -1, -1);
        Assert.assertEquals(user1, user2);

        // clear
        result = userCache.delete("user1", false);
        Assert.assertTrue(result);

        manager.shutdown();
    }

    private static class User implements Externalizable {
        private int id;
        private String name;
        private String address;
        private String country;

        public User() {
        }

        private User(final int id, final String name, final String address, final String country) {
            if (name == null || address == null || country == null) {
                throw new IllegalArgumentException("invalid parameters");
            }
            this.id = id;
            this.name = name;
            this.address = address;
            this.country = country;
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(obj instanceof User)) {
                return false;
            }
            if (this == obj) {
                return true;
            }
            final User user = (User) obj;
            return this.id == user.id &&
                    this.name.equals(user.name) &&
                    this.address.equals(user.address) &&
                    this.country.equals(user.country);
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + id;
            result = 31 * result + name.hashCode();
            result = 31 * result + address.hashCode();
            result = 31 * result + country.hashCode();
            return result;
        }

        @Override
        public void writeExternal(final ObjectOutput out) throws IOException {
            if (out == null) {
                return;
            }
            final byte[] nameBytes = name.getBytes(DEFAULT_CHARSET);
            final int nameBytesLen = nameBytes.length;
            final byte[] addressBytes = address.getBytes(DEFAULT_CHARSET);
            final int addressBytesLen = addressBytes.length;
            final byte[] countryBytes = country.getBytes(DEFAULT_CHARSET);
            final int countryBytesLen = countryBytes.length;
            out.writeInt(id);
            out.writeInt(nameBytesLen);
            out.write(nameBytes);
            out.writeInt(addressBytesLen);
            out.write(addressBytes);
            out.writeInt(countryBytesLen);
            out.write(countryBytes);
        }

        @Override
        public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
            final int id = in.readInt();
            final int nameBytesLen = in.readInt();
            final byte[] nameBytes = new byte[nameBytesLen];
            int read = 0;
            do {
                read += in.read(nameBytes, read, nameBytesLen - read);
            } while (read < nameBytesLen);
            final String name = new String(nameBytes, DEFAULT_CHARSET);
            final int addressBytesLen = in.readInt();
            final byte[] addressBytes = new byte[addressBytesLen];
            read = 0;
            do {
                read += in.read(addressBytes, read, addressBytesLen - read);
            } while (read < addressBytesLen);
            final String address = new String(addressBytes, DEFAULT_CHARSET);
            final int countryBytesLen = in.readInt();
            final byte[] countryBytes = new byte[countryBytesLen];
            read = 0;
            do {
                read += in.read(countryBytes, read, countryBytesLen - read);
            } while (read < countryBytesLen);
            final String country = new String(countryBytes, DEFAULT_CHARSET);

            this.id = id;
            this.name = name;
            this.address = address;
            this.country = country;
        }

        @Override
        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", address='" + address + '\'' +
                    ", country='" + country + '\'' +
                    '}';
        }
    }
}