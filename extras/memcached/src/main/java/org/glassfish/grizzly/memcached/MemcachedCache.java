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

import java.net.SocketAddress;
import java.util.Map;
import java.util.Set;

/**
 * The memcached's cache interface
 * <p/>
 * This interface extends {@link Commands} and {@link Cache} and has methods related to operation timeout.
 * Additionally, this supports several bulk operations such as {@link #getMulti} and {@link #setMulti} for dramatic performance improvement.
 * <p/>
 * By {@link #addServer} and {@link #removeServer}, servers can be added and removed dynamically in this cache.
 * In other words, the managed server list can be changed in runtime by APIs.
 *
 * @author Bongjae Chang
 */
public interface MemcachedCache<K, V> extends Commands<K, V>, Cache<K, V> {
    // extends basic memcached commands

    public boolean set(final K key, final V value, final int expirationInSecs, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public Map<K, Boolean> setMulti(final Map<K, V> map, final int expirationInSecs);

    public Map<K, Boolean> setMulti(final Map<K, V> map, final int expirationInSecs, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public boolean add(final K key, final V value, final int expirationInSecs, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public boolean replace(final K key, final V value, final int expirationInSecs, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public boolean append(final K key, final V value, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public boolean prepend(final K key, final V value, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public boolean cas(final K key, final V value, final int expirationInSecs, final long cas, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public V get(final K key, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public Map<K, V> getMulti(final Set<K> keys);

    public Map<K, V> getMulti(final Set<K> keys, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public ValueWithKey<K, V> getKey(final K key, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public ValueWithCas<V> gets(final K key, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public V gat(final K key, final int expirationInSecs, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public boolean delete(final K key, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public Map<K, Boolean> deleteMulti(final Set<K> keys);

    public Map<K, Boolean> deleteMulti(final Set<K> keys, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public long incr(final K key, final long delta, final long initial, final int expirationInSecs, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public long decr(final K key, final long delta, final long initial, final int expirationInSecs, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public String saslAuth(final SocketAddress address, final String mechanism, final byte[] data, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public String saslStep(final SocketAddress address, final String mechanism, final byte[] data, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public String saslList(final SocketAddress address, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public Map<String, String> stats(final SocketAddress address, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public Map<String, String> statsItems(final SocketAddress address, final String item, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public boolean quit(final SocketAddress address, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public boolean flushAll(final SocketAddress address, final int expirationInSecs, final boolean noReply, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public boolean touch(final K key, final int expirationInSecs, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public boolean noop(final SocketAddress address, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public boolean verbosity(final SocketAddress address, final int verbosity, final long writeTimeoutInMillis, final long responseTimeoutInMillis);

    public String version(final SocketAddress address, final long writeTimeoutInMillis, final long responseTimeoutInMillis);


    /**
     * Add a specific server in this cache
     * 
     * @param serverAddress a specific server's {@link SocketAddress} to be added
     * @return true if the given {@code serverAddress} is added successfully
     */
    public boolean addServer(final SocketAddress serverAddress);

    /**
     * Remove the given server in this cache
     *
     * @param serverAddress the specific server's {@link SocketAddress} to be removed in this cache
     */
    public void removeServer(final SocketAddress serverAddress);

    /**
     * Check if this cache contains the given server
     * 
     * @param serverAddress the specific server's {@link SocketAddress} to be checked 
     * @return true if this cache already contains the given {@code serverAddress}
     */
    public boolean isInServerList(final SocketAddress serverAddress);
}
