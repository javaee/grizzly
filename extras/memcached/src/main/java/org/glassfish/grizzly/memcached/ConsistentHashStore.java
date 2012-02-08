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

import org.glassfish.grizzly.Grizzly;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;

/**
 * The implementation class of the Consistent Hashing algorithms
 * <p/>
 * Given keys and values will be hashed by MD5 and stored in sorted map.
 * If MD5 is not supported, CRC32 will be used.
 * Values(such as server list) can be added and removed dynamically.
 * <p/>
 * This store supports keys of String, byte array and ByteBuffer type
 * <p/>
 * Ketama's logic applied partially.
 * <p/>
 * This class should be thread-safe.
 * <p/>
 * Example of use:
 *
 * @author Bongjae Chang
 * @see org.glassfish.grizzly.memcached.ConsistentHashStoreTest's test codes
 */
public class ConsistentHashStore<T> {

    private static final Logger logger = Grizzly.logger(ConsistentHashStore.class);

    private static final ThreadLocal<MessageDigest> md5ThreadLocal = new ThreadLocal<MessageDigest>();
    private volatile static boolean md5NotSupported;

    private static final int REPLICA_NUMBER = 160;

    private final ConcurrentSkipListMap<Long, T> buckets = new ConcurrentSkipListMap<Long, T>();
    private final Set<T> values = Collections.newSetFromMap(new ConcurrentHashMap<T, Boolean>());

    /**
     * Add the value such as a server name
     *
     * @param value value to be added
     */
    public void add(final T value) {
        addOrRemove(value, true);
        values.add(value);
    }

    /**
     * Remove the value which already added by {@link #add}
     *
     * @param value value to be removed in this store
     */
    public void remove(final T value) {
        addOrRemove(value, false);
        values.remove(value);
    }

    /**
     * Check if this store has {@code value}
     *
     * @param value value to be checked
     * @return true if this store already contains {@code value}
     */
    public boolean hasValue(final T value) {
        return value != null && values.contains(value);
    }

    /**
     * Clear all values and keys
     */
    public void clear() {
        buckets.clear();
        values.clear();
    }

    private void addOrRemove(final T value, boolean add) {
        if (value == null) {
            return;
        }
        final MessageDigest md5 = getMessageDigest();
        if (md5 == null) {
            for (int i = 0; i < REPLICA_NUMBER; i++) {
                final StringBuilder stringBuilder = new StringBuilder(64);
                stringBuilder.append(value).append('-').append(i);
                CRC32 crc32 = new CRC32();
                crc32.update(stringBuilder.toString().getBytes());
                long hashKey = crc32.getValue() >> 16 & 0x7fff;
                if (add) {
                    buckets.putIfAbsent(hashKey, value);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "added {0} to the bucket successfully. key={1}", new Object[]{value, hashKey});
                    }
                } else {
                    buckets.remove(hashKey);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "removed {0} to the bucket successfully. key={1}", new Object[]{value, hashKey});
                    }
                }
            }
        } else {
            for (int i = 0; i < REPLICA_NUMBER / 4; i++) {
                final StringBuilder stringBuilder = new StringBuilder(64);
                stringBuilder.append(value).append('-').append(i);
                byte[] digest = md5.digest(stringBuilder.toString().getBytes());
                for (int j = 0; j < 4; j++) {
                    long hashKey = ((long) (digest[3 + j * 4] & 0xFF) << 24)
                            | ((long) (digest[2 + j * 4] & 0xFF) << 16)
                            | ((long) (digest[1 + j * 4] & 0xFF) << 8)
                            | ((long) (digest[j * 4] & 0xFF));
                    if (add) {
                        buckets.putIfAbsent(hashKey, value);
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "added {0} to the bucket successfully. key={1}", new Object[]{value, hashKey});
                        }
                    } else {
                        buckets.remove(hashKey);
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "removed {0} to the bucket successfully. key={1}", new Object[]{value, hashKey});
                        }
                    }
                }
            }
        }
    }

    /**
     * Get the value corresponding to the given key
     *
     * @param key String key
     * @return the selected value corresponding to the {@code key}
     */
    public T get(final String key) {
        if (key == null) {
            return null;
        }
        return get(key.getBytes());
    }

    /**
     * Get the value corresponding to the given key
     *
     * @param key byte array key
     * @return the selected value corresponding to the {@code key}
     */
    public T get(final byte[] key) {
        if (key == null) {
            return null;
        }
        if (buckets.size() == 0) {
            return null;
        }
        if (buckets.size() == 1) {
            return buckets.firstEntry().getValue();
        }
        // ceilingKey returns the least key greater than or equal to the given key,
        // or null if no such key.
        Long hashKey = buckets.ceilingKey(calculateHash(key));
        // if none found, it must be at the end, return the lowest in the tree
        // (we go over the end the continuum to the first entry)
        if (hashKey == null) {
            hashKey = buckets.firstKey();
        }
        return buckets.get(hashKey);
    }

    /**
     * Get the value corresponding to the given key
     *
     * @param key {@link ByteBuffer} key
     * @return the selected value corresponding to the {@code key}
     */
    public T get(final ByteBuffer key) {
        if (key == null) {
            return null;
        }
        if (buckets.size() == 0) {
            return null;
        }
        if (buckets.size() == 1) {
            return buckets.firstEntry().getValue();
        }
        // ceilingKey returns the least key greater than or equal to the given key,
        // or null if no such key.
        Long hashKey = buckets.ceilingKey(calculateHash(key));
        // if none found, it must be at the end, return the lowest in the tree
        // (we go over the end the continuum to the first entry)
        if (hashKey == null) {
            hashKey = buckets.firstKey();
        }
        return buckets.get(hashKey);

    }

    private long calculateHash(final byte[] key) {
        if (key == null) {
            return 0;
        }
        final long hash;
        final MessageDigest md5 = getMessageDigest();
        if (md5 == null) {
            CRC32 crc32 = new CRC32();
            crc32.update(key);
            hash = crc32.getValue() >> 16 & 0x7fff;
        } else {
            md5.reset();
            final byte[] digest = md5.digest(key);
            hash = ((long) (digest[3] & 0xFF) << 24) | ((long) (digest[2] & 0xFF) << 16) | ((long) (digest[1] & 0xFF) << 8) | (long) (digest[0] & 0xFF);
        }
        return hash;
    }

    private long calculateHash(final ByteBuffer key) {
        if (key == null) {
            return 0;
        }
        final long hash;
        final MessageDigest md5 = getMessageDigest();
        if (md5 == null) {
            if (key.hasArray()) {
                CRC32 crc32 = new CRC32();
                byte[] b = key.array();
                int ofs = key.arrayOffset();
                int pos = key.position();
                int lim = key.limit();
                crc32.update(b, ofs + pos, lim - pos);
                key.position(lim);
                hash = crc32.getValue() >> 16 & 0x7fff;
            } else {
                hash = key.hashCode();
            }
        } else {
            md5.reset();
            md5.update(key);
            final byte[] digest = md5.digest();
            hash = ((long) (digest[3] & 0xFF) << 24) | ((long) (digest[2] & 0xFF) << 16) | ((long) (digest[1] & 0xFF) << 8) | (long) (digest[0] & 0xFF);
        }
        return hash;
    }

    private static MessageDigest getMessageDigest() {
        if (md5NotSupported) {
            return null;
        }
        MessageDigest md5 = md5ThreadLocal.get();
        if (md5 == null) {
            try {
                md5 = MessageDigest.getInstance("MD5");
                md5ThreadLocal.set(md5);
            } catch (NoSuchAlgorithmException nsae) {
                md5NotSupported = true;
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "failed to get the md5", nsae);
                }
            }
        }
        return md5;
    }
}
