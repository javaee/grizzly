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

package org.glassfish.grizzly.memcached.zookeeper;

import org.glassfish.grizzly.memcached.GrizzlyMemcachedCache;
import org.glassfish.grizzly.memcached.GrizzlyMemcachedCacheManager;

import static org.junit.Assert.*;

import org.junit.AfterClass;
import org.junit.Test;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Bongjae Chang
 */
public class ZooKeeperSupportCacheTest {

    private static final String DEFAULT_CHARSET = CacheServerListBarrierListener.DEFAULT_SERVER_LIST_CHARSET;
    private static final int DEFAULT_KEY_SET_COUNT = 100;
    private static final int DEFAULT_MANY_CACHE_COUNT = 10;

    // zookeeper 
    private static final String DEFAULT_ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final String ROOT = "/zktest";

    // memcached
    private static final int expirationTimeoutInSec = 60 * 30; // 30min
    private static final SocketAddress MEMCACHED_ADDRESS1 = new InetSocketAddress("localhost", 11211);
    private static final SocketAddress MEMCACHED_ADDRESS2 = new InetSocketAddress("localhost", 11212);
    private static final SocketAddress MEMCACHED_ADDRESS3 = new InetSocketAddress("localhost", 11213);

    @Test
    public void emptyTest() {
    }

    // 3 memcached server should be booted in local
    // zookeeper server should be booted in local
    //@Test
    public void basicTest() {
        final GrizzlyMemcachedCacheManager.Builder managerBuilder = new GrizzlyMemcachedCacheManager.Builder();
        // setup zookeeper server
        final ZooKeeperConfig zkConfig = ZooKeeperConfig.create("cache-manager", DEFAULT_ZOOKEEPER_ADDRESS);
        zkConfig.setRootPath(ROOT);
        zkConfig.setConnectTimeoutInMillis(3000);
        zkConfig.setSessionTimeoutInMillis(30000);
        zkConfig.setCommitDelayTimeInSecs(2);
        managerBuilder.zooKeeperConfig(zkConfig);
        // create a cache manager
        final GrizzlyMemcachedCacheManager manager = managerBuilder.build();
        final GrizzlyMemcachedCache.Builder<Integer, User> cacheBuilder = manager.createCacheBuilder("user");
        // setup memcached servers
        final Set<SocketAddress> memcachedServers = new HashSet<SocketAddress>();
        memcachedServers.add(MEMCACHED_ADDRESS1);
        memcachedServers.add(MEMCACHED_ADDRESS2);
        memcachedServers.add(MEMCACHED_ADDRESS3);
        cacheBuilder.servers(memcachedServers);
        // create a user cache
        final GrizzlyMemcachedCache<Integer, User> cache = cacheBuilder.build();

        // test basic informations
        final String serverListPath = cache.getZooKeeperServerListPath();
        assertNotNull(serverListPath);
        assertNotNull(cache.getCurrentServerListFromZooKeeper());

        // set users
        final Map<Integer, User> users = new HashMap<Integer, User>();
        for (int i = 0; i < DEFAULT_KEY_SET_COUNT; i++) {
            users.put(i, new User(i, "foo" + i, "street" + i, "kr" + i));
        }
        cache.setMulti(users, expirationTimeoutInSec);
        // get users
        final Map<Integer, User> cachedUsers = cache.getMulti(users.keySet());
        // ensure if results are valid
        for (final Map.Entry<Integer, User> entry : users.entrySet()) {
            final User user = cachedUsers.get(entry.getKey());
            assertNotNull("cache user is null", user);
            assertTrue("cache user is invalid", entry.getValue().equals(user));
        }
        // delete users
        for (final int id : users.keySet()) {
            cache.delete(id, false);
        }

        // clean
        manager.removeCache("user");
        manager.shutdown();
    }

    // 3 memcached server should be booted in local
    // zookeeper server should be booted in local
    //@Test
    public void oneCacheOneClientTest() {
        final int commitDelayInSecs = 3; // 3secs

        final GrizzlyMemcachedCacheManager.Builder managerBuilder = new GrizzlyMemcachedCacheManager.Builder();
        // setup zookeeper server
        final ZooKeeperConfig zkConfig = ZooKeeperConfig.create("cache-manager", DEFAULT_ZOOKEEPER_ADDRESS);
        zkConfig.setRootPath(ROOT);
        zkConfig.setConnectTimeoutInMillis(3000);
        zkConfig.setSessionTimeoutInMillis(30000);
        zkConfig.setCommitDelayTimeInSecs(commitDelayInSecs);
        managerBuilder.zooKeeperConfig(zkConfig);
        // create a cache manager
        final GrizzlyMemcachedCacheManager manager = managerBuilder.build();
        final GrizzlyMemcachedCache.Builder<Integer, User> cacheBuilder = manager.createCacheBuilder("user");
        // setup memcached servers
        final Set<SocketAddress> memcachedServers = new HashSet<SocketAddress>();
        memcachedServers.add(MEMCACHED_ADDRESS1);
        memcachedServers.add(MEMCACHED_ADDRESS2);
        memcachedServers.add(MEMCACHED_ADDRESS3);
        cacheBuilder.servers(memcachedServers);
        // create a user cache
        final GrizzlyMemcachedCache<Integer, User> cache = cacheBuilder.build();

        // test basic informations
        final String serverListPath = cache.getZooKeeperServerListPath();
        assertNotNull(serverListPath);
        assertNotNull(cache.getCurrentServerListFromZooKeeper());

        // set users
        final Map<Integer, User> users = new HashMap<Integer, User>();
        for (int i = 0; i < DEFAULT_KEY_SET_COUNT; i++) {
            users.put(i, new User(i, "foo" + i, "street" + i, "kr" + i));
        }
        cache.setMulti(users, expirationTimeoutInSec);
        // get users
        final Map<Integer, User> cachedUsers = cache.getMulti(users.keySet());
        // ensure if results are valid
        for (final Map.Entry<Integer, User> entry : users.entrySet()) {
            final User user = cachedUsers.get(entry.getKey());
            assertNotNull("cache user is null", user);
            assertTrue("cache user is invalid", entry.getValue().equals(user));
        }
        // delete users
        for (final int id : users.keySet()) {
            cache.delete(id, false);
        }

        // ensure current servers
        assertTrue(cache.isInServerList(MEMCACHED_ADDRESS1));
        assertTrue(cache.isInServerList(MEMCACHED_ADDRESS2));
        assertTrue(cache.isInServerList(MEMCACHED_ADDRESS3));
        // change 3 cache servers into 1 server
        cache.setCurrentServerListOfZooKeeper("localhost:11211");
        // ensure 3 servers exist because the commit delay is about 3 secs.
        assertTrue(cache.isInServerList(MEMCACHED_ADDRESS1));
        assertTrue(cache.isInServerList(MEMCACHED_ADDRESS2));
        assertTrue(cache.isInServerList(MEMCACHED_ADDRESS3));
        // wait for commiting
        try {
            Thread.sleep(commitDelayInSecs * 1000 + 1000);
        } catch (InterruptedException ignore) {
            return;
        }
        // ensure only 1 server exists
        assertTrue(MEMCACHED_ADDRESS1.toString() + " should exist", cache.isInServerList(MEMCACHED_ADDRESS1));
        assertFalse(MEMCACHED_ADDRESS2.toString() + " should not exist", cache.isInServerList(MEMCACHED_ADDRESS2));
        assertFalse(MEMCACHED_ADDRESS3.toString() + " should not exist", cache.isInServerList(MEMCACHED_ADDRESS3));

        // clean
        manager.removeCache("user");
        manager.shutdown();
    }

    // 3 memcached server should be booted in local
    // zookeeper server should be booted in local
    @SuppressWarnings("unchecked")
    //@Test
    public void manyCacheOneClientTest() {
        final int cacheCount = DEFAULT_MANY_CACHE_COUNT;
        final int commitDelayInSecs = 3; // 3secs

        final GrizzlyMemcachedCacheManager.Builder managerBuilder = new GrizzlyMemcachedCacheManager.Builder();
        // setup zookeeper server
        final ZooKeeperConfig zkConfig = ZooKeeperConfig.create("cache-manager", DEFAULT_ZOOKEEPER_ADDRESS);
        zkConfig.setRootPath(ROOT);
        zkConfig.setConnectTimeoutInMillis(3000);
        zkConfig.setSessionTimeoutInMillis(30000);
        zkConfig.setCommitDelayTimeInSecs(commitDelayInSecs);
        managerBuilder.zooKeeperConfig(zkConfig);
        // create a cache manager
        final GrizzlyMemcachedCacheManager manager = managerBuilder.build();

        final GrizzlyMemcachedCache[] caches = new GrizzlyMemcachedCache[cacheCount];
        for (int i = 0; i < cacheCount; i++) {
            final GrizzlyMemcachedCache.Builder<String, User> cacheBuilder = manager.createCacheBuilder("user" + i);
            // setup memcached servers
            final Set<SocketAddress> memcachedServers = new HashSet<SocketAddress>();
            memcachedServers.add(MEMCACHED_ADDRESS1);
            memcachedServers.add(MEMCACHED_ADDRESS2);
            memcachedServers.add(MEMCACHED_ADDRESS3);
            cacheBuilder.servers(memcachedServers);
            // create a user cache
            caches[i] = cacheBuilder.build();

            // set users
            final Map<String, User> users = new HashMap<String, User>();
            for (int j = 0; j < DEFAULT_KEY_SET_COUNT; j++) {
                users.put(i + "-" + j, new User(i, "foo" + i, "street" + i, "kr" + i));
            }
            caches[i].setMulti(users, expirationTimeoutInSec);
            // get users
            final Map<String, User> cachedUsers = caches[i].getMulti(users.keySet());
            // ensure if results are valid
            for (final Map.Entry<String, User> entry : users.entrySet()) {
                final User user = cachedUsers.get(entry.getKey());
                assertNotNull("cache user is null", user);
                assertTrue("cache user is invalid", entry.getValue().equals(user));
            }
            // delete users
            for (final String id : users.keySet()) {
                caches[i].delete(id, false);
            }
            // ensure current servers
            assertTrue(caches[i].isInServerList(MEMCACHED_ADDRESS1));
            assertTrue(caches[i].isInServerList(MEMCACHED_ADDRESS2));
            assertTrue(caches[i].isInServerList(MEMCACHED_ADDRESS3));
        }

        for (int i = 0; i < cacheCount; i++) {
            if (i % 2 == 0) {
                // change 3 cache servers into 1 server 
                caches[i].setCurrentServerListOfZooKeeper("localhost:11211");
                // ensure 3 servers exist because the commit delay is about 3 secs.
                assertTrue(caches[i].isInServerList(MEMCACHED_ADDRESS1));
                assertTrue(caches[i].isInServerList(MEMCACHED_ADDRESS2));
                assertTrue(caches[i].isInServerList(MEMCACHED_ADDRESS3));
            }
        }
        // wait for commiting
        try {
            Thread.sleep(commitDelayInSecs * 1000 + 2000);
        } catch (InterruptedException ignore) {
            return;
        }

        for (int i = 0; i < cacheCount; i++) {
            if (i % 2 == 0) {
                // ensure only 1 server exists
                assertTrue(MEMCACHED_ADDRESS1.toString() + " should exist", caches[i].isInServerList(MEMCACHED_ADDRESS1));
                assertFalse(MEMCACHED_ADDRESS2.toString() + " should not exist", caches[i].isInServerList(MEMCACHED_ADDRESS2));
                assertFalse(MEMCACHED_ADDRESS3.toString() + " should not exist", caches[i].isInServerList(MEMCACHED_ADDRESS3));
            } else {
                // ensure servers are not changed
                assertTrue(MEMCACHED_ADDRESS1.toString() + " should exist", caches[i].isInServerList(MEMCACHED_ADDRESS1));
                assertTrue(MEMCACHED_ADDRESS2.toString() + " should not exist", caches[i].isInServerList(MEMCACHED_ADDRESS2));
                assertTrue(MEMCACHED_ADDRESS3.toString() + " should not exist", caches[i].isInServerList(MEMCACHED_ADDRESS3));
            }
        }

        // clean
        for (int i = 0; i < cacheCount; i++) {
            manager.removeCache("user" + i);
        }
        manager.shutdown();
    }

    // 3 memcached server should be booted in local
    // zookeeper server should be booted in local
    @SuppressWarnings("unchecked")
    //@Test
    public void oneCacheManyClientTest() {
        final int clientCount = 10;
        final int commitDelayInSecs = 3; // 3secs

        final GrizzlyMemcachedCacheManager[] managers = new GrizzlyMemcachedCacheManager[clientCount];
        final GrizzlyMemcachedCache[] caches = new GrizzlyMemcachedCache[clientCount];
        for (int i = 0; i < clientCount; i++) {
            final GrizzlyMemcachedCacheManager.Builder managerBuilder = new GrizzlyMemcachedCacheManager.Builder();
            // setup zookeeper server
            final ZooKeeperConfig zkConfig = ZooKeeperConfig.create("cache-manager" + i, DEFAULT_ZOOKEEPER_ADDRESS);
            zkConfig.setRootPath(ROOT);
            zkConfig.setConnectTimeoutInMillis(3000);
            zkConfig.setSessionTimeoutInMillis(30000);
            zkConfig.setCommitDelayTimeInSecs(commitDelayInSecs);
            managerBuilder.zooKeeperConfig(zkConfig);
            // create a cache manager
            managers[i] = managerBuilder.build();
            final GrizzlyMemcachedCache.Builder<String, User> cacheBuilder = managers[i].createCacheBuilder("user");
            // setup memcached servers
            final Set<SocketAddress> memcachedServers = new HashSet<SocketAddress>();
            memcachedServers.add(MEMCACHED_ADDRESS1);
            memcachedServers.add(MEMCACHED_ADDRESS2);
            memcachedServers.add(MEMCACHED_ADDRESS3);
            cacheBuilder.servers(memcachedServers);
            // create a user cache
            caches[i] = cacheBuilder.build();

            // test basic informations
            final String serverListPath = caches[i].getZooKeeperServerListPath();
            assertNotNull(serverListPath);
            assertNotNull(caches[i].getCurrentServerListFromZooKeeper());

            // set users
            final Map<String, User> users = new HashMap<String, User>();
            for (int j = 0; j < DEFAULT_KEY_SET_COUNT; j++) {
                users.put(i + "-" + j, new User(i, "foo" + i, "street" + i, "kr" + i));
            }
            caches[i].setMulti(users, expirationTimeoutInSec);
            // get users
            final Map<String, User> cachedUsers = caches[i].getMulti(users.keySet());
            // ensure if results are valid
            for (final Map.Entry<String, User> entry : users.entrySet()) {
                final User user = cachedUsers.get(entry.getKey());
                assertNotNull("cache user is null", user);
                assertTrue("cache user is invalid", entry.getValue().equals(user));
            }
            // delete users
            for (final String id : users.keySet()) {
                caches[i].delete(id, false);
            }

            // ensure current servers
            assertTrue(caches[i].isInServerList(MEMCACHED_ADDRESS1));
            assertTrue(caches[i].isInServerList(MEMCACHED_ADDRESS2));
            assertTrue(caches[i].isInServerList(MEMCACHED_ADDRESS3));
        }
        // select one client and change 3 cache servers into 1 server
        caches[0].setCurrentServerListOfZooKeeper("localhost:11211");

        // ensure 3 servers exist because the commit delay is about 3 secs.
        for (int i = 0; i < clientCount; i++) {
            assertTrue(caches[i].isInServerList(MEMCACHED_ADDRESS1));
            assertTrue(caches[i].isInServerList(MEMCACHED_ADDRESS2));
            assertTrue(caches[i].isInServerList(MEMCACHED_ADDRESS3));
        }
        // wait for commiting
        try {
            Thread.sleep(commitDelayInSecs * 1000 + 1000);
        } catch (InterruptedException ignore) {
            return;
        }
        for (int i = 0; i < clientCount; i++) {
            // ensure only 1 server exists
            assertTrue(MEMCACHED_ADDRESS1.toString() + " should exist", caches[i].isInServerList(MEMCACHED_ADDRESS1));
            assertFalse(MEMCACHED_ADDRESS2.toString() + " should not exist", caches[i].isInServerList(MEMCACHED_ADDRESS2));
            assertFalse(MEMCACHED_ADDRESS3.toString() + " should not exist", caches[i].isInServerList(MEMCACHED_ADDRESS3));
        }

        // clean
        for (int i = 0; i < clientCount; i++) {
            managers[i].removeCache("user");
            managers[i].shutdown();
        }
    }

    // zookeeper server should be booted in local
    //@AfterClass
    public static void cleanupZooKeeperRepository() {
        final ZKClient zkClient = new ZKClient.Builder("cleanup-client", DEFAULT_ZOOKEEPER_ADDRESS).rootPath(ROOT).build();
        try {
            zkClient.connect();
        } catch (IOException e) {
            return;
        } catch (InterruptedException e) {
            return;
        }
        for (int i = 0; i < DEFAULT_MANY_CACHE_COUNT; i++) {
            clearRegionRepository(zkClient, "user" + i);
        }
        clearRegionRepository(zkClient, "user");
        clearBaseRepository(zkClient);
        zkClient.shutdown();
    }

    private static void clearRegionRepository(final ZKClient zkClient, final String regionName) {
        if (zkClient == null) {
            return;
        }
        final String regionPath = ROOT + "/barrier/" + regionName;
        final String dataPath = regionPath + "/data";
        final String currentPath = regionPath + "/current";
        final String participantsPath = regionPath + "/participants";

        if (zkClient.exists(dataPath, false) != null) {
            zkClient.delete(dataPath, -1);
        }
        if (zkClient.exists(currentPath, false) != null) {
            final List<String> currentNodes = zkClient.getChildren(currentPath, false);
            if (currentNodes == null || currentNodes.isEmpty()) {
                zkClient.delete(currentPath, -1);
            } else {
                for (String node : currentNodes) {
                    zkClient.delete(currentPath + "/" + node, -1);
                }
                zkClient.delete(currentPath, -1);
            }
        }
        if (zkClient.exists(participantsPath, false) != null) {
            final List<String> paticipants = zkClient.getChildren(participantsPath, false);
            if (paticipants == null || paticipants.isEmpty()) {
                zkClient.delete(participantsPath, -1);
            } else {
                for (String node : paticipants) {
                    zkClient.delete(participantsPath + "/" + node, -1);
                }
                zkClient.delete(participantsPath, -1);
            }
        }
        zkClient.delete(regionPath, -1);
    }

    private static void clearBaseRepository(final ZKClient zkClient) {
        zkClient.delete(ROOT + "/barrier", -1);
        zkClient.delete(ROOT, -1);
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
            in.read(nameBytes);
            final String name = new String(nameBytes, DEFAULT_CHARSET);
            final int addressBytesLen = in.readInt();
            final byte[] addressBytes = new byte[addressBytesLen];
            in.read(addressBytes);
            final String address = new String(addressBytes, DEFAULT_CHARSET);
            final int countryBytesLen = in.readInt();
            final byte[] countryBytes = new byte[countryBytesLen];
            in.read(countryBytes);
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
