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

/**
 * The interface using the ZooKeeper for synchronizing cache server list
 * <p/>
 * Example of use:
 * {@code
 * final GrizzlyMemcachedCacheManager.Builder managerBuilder = new GrizzlyMemcachedCacheManager.Builder();
 * // setup zookeeper server
 * final ZooKeeperConfig zkConfig = ZooKeeperConfig.create("cache-manager", DEFAULT_ZOOKEEPER_ADDRESS);
 * zkConfig.setRootPath(ROOT);
 * zkConfig.setConnectTimeoutInMillis(3000);
 * zkConfig.setSessionTimeoutInMillis(30000);
 * zkConfig.setCommitDelayTimeInSecs(2);
 * managerBuilder.zooKeeperConfig(zkConfig);
 * // create a cache manager
 * final GrizzlyMemcachedCacheManager manager = managerBuilder.build();
 * final GrizzlyMemcachedCache.Builder<String, String> cacheBuilder = manager.createCacheBuilder("user");
 * // setup memcached servers
 * final Set<SocketAddress> memcachedServers = new HashSet<SocketAddress>();
 * memcachedServers.add(MEMCACHED_ADDRESS1);
 * memcachedServers.add(MEMCACHED_ADDRESS2);
 * cacheBuilder.servers(memcachedServers);
 * // create a user cache
 * final GrizzlyMemcachedCache<String, String> cache = cacheBuilder.build();
 * // ZooKeeperSupportCache's basic operations
 * if (cache.isZooKeeperSupported()) {
 * final String serverListPath = cache.getZooKeeperServerListPath();
 * final String serverList = cache.getCurrentServerListFromZooKeeper();
 * cache.setCurrentServerListOfZooKeeper("localhost:11211,localhost:11212");
 * }
 * // ...
 * // clean
 * manager.removeCache("user");
 * manager.shutdown();
 * }
 *
 * @author Bongjae Chang
 */
public interface ZooKeeperSupportCache {

    /**
     * Check if this cache supports the ZooKeeper for synchronizing the cache server list
     *
     * @return true if this cache supports it
     */
    public boolean isZooKeeperSupported();

    /**
     * Return the path of the cache server list which has been registered in the ZooKeeper server
     *
     * @return the path of the cache server list in the ZooKeeper server.
     *         "null" means this cache doesn't support the ZooKeeper or this cache is not started yet
     */
    public String getZooKeeperServerListPath();

    /**
     * Return the current cache server list string from the ZooKeeper server
     *
     * @return the current server list string
     */
    public String getCurrentServerListFromZooKeeper();

    /**
     * Set the current cache server list string with the given {@code cacheServerList}
     * <p/>
     * {@code cacheServerList} could be comma separated host:port pairs, each corresponding to a memcached server.
     * e.g. "127.0.0.1:3000,127.0.0.1:3001,127.0.0.1:3002"
     * Be careful that this operation will propagate {@code cacheServerList} to caches which has joinned the same cache name(scope)
     * because the cache list of ZooKeeper server will be changed.
     *
     * @param cacheServerList the cache server list string
     * @return true if this cache server list is set successfully
     */
    public boolean setCurrentServerListOfZooKeeper(final String cacheServerList);
}
