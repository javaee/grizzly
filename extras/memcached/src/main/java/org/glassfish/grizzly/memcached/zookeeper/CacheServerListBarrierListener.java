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

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.memcached.MemcachedCache;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@link BarrierListener} implementation for synchronizing the cache server list among all clients which have joined the same zookeeper server
 *
 * @author Bongjae Chang
 */
public class CacheServerListBarrierListener implements BarrierListener {

    private static final Logger logger = Grizzly.logger(CacheServerListBarrierListener.class);
    private static final String DEFAULT_CHARSET = "UTF-8";

    private final String cacheName;
    private final MemcachedCache cache;
    private final Set<SocketAddress> localCacheServerSet = new CopyOnWriteArraySet<SocketAddress>();

    public CacheServerListBarrierListener(final MemcachedCache cache, final Set<SocketAddress> cacheServerSet) {
        this.cache = cache;
        if (this.cache != null) {
            cacheName = this.cache.getName();
        } else {
            cacheName = null;
        }
        if (cacheServerSet != null) {
            this.localCacheServerSet.addAll(cacheServerSet);
        }
    }

    @Override
    public void onInit(final String regionName, final String path, final byte[] remoteBytes) {
        if (remoteBytes == null || remoteBytes.length == 0) {
            return;
        }
        // check the remote cache server list of the zookeeper server is equal to local if the server has pre-defined server list
        try {
            final String remoteCacheServerList = new String(remoteBytes, DEFAULT_CHARSET);
            final Set<SocketAddress> remoteCacheServers = getAddressesFromStringList(remoteCacheServerList);
            boolean checked = true;
            for (final SocketAddress local : localCacheServerSet) {
                if (!remoteCacheServers.remove(local)) {
                    checked = false;
                    break;
                }
            }
            if (checked && !remoteCacheServers.isEmpty()) {
                checked = false;
            }
            if (!checked) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                            "failed to check the cache server list from the remote. cacheName={0}, local={1}, remote={2}",
                            new Object[]{cacheName, localCacheServerSet, remoteCacheServers});
                }
            } else {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "cache server list confirmed. cacheName={0}, list=[{1}]", new Object[]{cacheName, remoteCacheServerList});
                }
            }
        } catch (UnsupportedEncodingException uee) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "failed to check the cache server list from the remote. cacheName=" + cacheName, uee);
            }
        }
    }

    @Override
    public void onCommit(final String regionName, final String path, byte[] remoteBytes) {
        if (remoteBytes == null || remoteBytes.length == 0) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "remote bytes is null or NO_DATA(byte[0]). regionName={0}, path={1}", new Object[]{regionName, path});
            }
            return;
        }
        try {
            final String remoteDataString = new String(remoteBytes, DEFAULT_CHARSET);
            final Set<SocketAddress> remoteCacheServers = getAddressesFromStringList(remoteDataString);
            if (!remoteCacheServers.isEmpty()) {
                if (cache != null) {
                    final Set<SocketAddress> added = new HashSet<SocketAddress>();
                    final Set<SocketAddress> removed = new HashSet<SocketAddress>();
                    for (final SocketAddress local : localCacheServerSet) {
                        if (!remoteCacheServers.remove(local)) {
                            removed.add(local);
                        }
                    }
                    added.addAll(remoteCacheServers);
                    for (final SocketAddress address : added) {
                        cache.addServer(address);
                    }
                    for (final SocketAddress address : removed) {
                        cache.removeServer(address);
                    }
                    // refresh local
                    localCacheServerSet.clear();
                    localCacheServerSet.addAll(remoteCacheServers);
                }
            }
        } catch (UnsupportedEncodingException uee) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING,
                        "failed to apply the changed server list of the remote zookeeper server. regionName=" + regionName + ", path=" + path,
                        uee);
            }
        }
    }

    @Override
    public void onDestroy(final String regionName) {
    }

    @Override
    public String toString() {
        return "CacheServerListBarrierListener{" +
                "cacheName='" + cacheName + '\'' +
                ", cache=" + cache +
                ", localCacheServerSet=" + localCacheServerSet +
                '}';
    }

    /**
     * Split a string in the form of "host:port, host2:port" into a Set of
     * {@link java.net.SocketAddress} instances.
     * <p/>
     * Note that colon-delimited IPv6 is also supported. For example: ::1:11211
     *
     * @param serverList server list in the form of "host:port,host2:port"
     * @return server set
     */
    public static Set<SocketAddress> getAddressesFromStringList(final String serverList) {
        if (serverList == null) {
            throw new IllegalArgumentException("null host list");
        }
        if (serverList.trim().equals("")) {
            throw new IllegalArgumentException("no hosts in list:  ``" + serverList + "''");
        }
        final HashSet<SocketAddress> addrs = new HashSet<SocketAddress>();
        for (final String hoststuff : serverList.split("(,| )")) {
            if (hoststuff.length() == 0) {
                continue;
            }
            int finalColon = hoststuff.lastIndexOf(':');
            if (finalColon < 1) {
                throw new IllegalArgumentException("Invalid server ``" + hoststuff + "'' in list:  " + serverList);
            }
            final String hostPart = hoststuff.substring(0, finalColon);
            final String portNum = hoststuff.substring(finalColon + 1);
            addrs.add(new InetSocketAddress(hostPart, Integer.parseInt(portNum)));
        }
        return addrs;
    }

    /**
     * Convert server set into server list like "host:port,host2:port"
     *
     * @param servers {@link InetSocketAddress} set
     * @return server list in the form of "host:port,host2:port"
     */
    public static String getStringListFromAddressSet(final Set<SocketAddress> servers) {
        if (servers == null || servers.isEmpty()) {
            throw new IllegalArgumentException("Null servers");
        }
        final StringBuilder builder = new StringBuilder(256);
        for (final SocketAddress server : servers) {
            if (server instanceof InetSocketAddress) {
                final InetSocketAddress inetSocketAddress = (InetSocketAddress) server;
                builder.append(inetSocketAddress.getHostName()).append(':').append(inetSocketAddress.getPort());
                builder.append(',');
            }
        }
        final String result = builder.toString();
        final int resultLength = result.length();
        if (resultLength < 1) {
            throw new IllegalArgumentException("there is no InetSocketAddress in the server set");
        } else {
            // remove the last comma
            return result.substring(0, result.length() - 1);
        }
    }
}
