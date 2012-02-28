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

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.ThreadCache;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Memcached request
 * <p/>
 * {@code response} and {@code responseStatus} will be set by the filter when the response will be received.
 * And the filter will notify {@code notify} instance after it will complete the processing for the received message.
 *
 * @author Bongjae Chang
 */
public class MemcachedRequest {

    private static final int MAX_KEY_LENGTH = 250; // 250bytes
    private static final int MAX_VALUE_LENGTH = 1024 * 1024; // 1M

    private final boolean hasExtras;
    private final boolean hasKey;
    private final boolean hasValue;

    private final CommandOpcodes op;
    private final boolean noReply;
    private final int opaque;
    private final long cas;
    private final byte dataType;
    private final short vBucketId;

    // extras and body
    private final Integer flags;
    private final Long delta;
    private final Long initial;
    private final Integer expirationInSecs;
    private final Integer verbosity;
    private final BufferWrapper.BufferType originKeyType;
    private final Object originKey;
    private final Buffer key;
    private final Buffer value;

    final AtomicBoolean disposed = new AtomicBoolean();
    final CountDownLatch notify = new CountDownLatch(1);
    Object response;
    ResponseStatus responseStatus;

    private MemcachedRequest(final Builder builder) {
        this.hasExtras = builder.hasExtras;
        this.hasKey = builder.hasKey;
        this.hasValue = builder.hasValue;
        this.op = builder.op;
        this.noReply = builder.noReply;
        this.opaque = builder.opaque;
        this.cas = builder.cas;
        this.dataType = builder.dataType;
        this.vBucketId = builder.vBucketId;
        this.flags = builder.flags;
        this.delta = builder.delta;
        this.initial = builder.initial;
        this.expirationInSecs = builder.expirationInSecs;
        this.verbosity = builder.verbosity;
        this.originKeyType = builder.originKeyType;
        this.originKey = builder.originKey;
        this.key = builder.key;
        this.value = builder.value;
    }

    public boolean hasExtras() {
        return hasExtras;
    }

    public boolean hasKey() {
        return hasKey;
    }

    public boolean hasValue() {
        return hasValue;
    }

    public CommandOpcodes getOp() {
        return op;
    }

    public boolean isNoReply() {
        return noReply;
    }

    public int getOpaque() {
        return opaque;
    }

    public long getCas() {
        return cas;
    }

    public byte getDataType() {
        return dataType;
    }

    public short getvBucketId() {
        return vBucketId;
    }

    public Integer getFlags() {
        return flags;
    }

    public Long getDelta() {
        return delta;
    }

    public Long getInitial() {
        return initial;
    }

    public Integer getExpirationInSecs() {
        return expirationInSecs;
    }

    public Integer getVerbosity() {
        return verbosity;
    }

    public BufferWrapper.BufferType getOriginKeyType() {
        return originKeyType;
    }

    public Object getOriginKey() {
        return originKey;
    }

    public Buffer getKey() {
        return key;
    }

    public Buffer getValue() {
        return value;
    }

    public byte getExtrasLength() {
        if (!hasExtras) {
            return 0;
        } else {
            return (byte) (((flags != null ? 4 : 0) +
                    (delta != null ? 8 : 0) +
                    (initial != null ? 8 : 0) +
                    (expirationInSecs != null ? 4 : 0) +
                    (verbosity != null ? 4 : 0)) & 0x7f);
        }
    }

    public void fillExtras(final Buffer buffer) {
        if (!hasExtras || buffer == null) {
            return;
        }
        switch (op) {
            case Set:
            case SetQ:
            case Add:
            case AddQ:
            case Replace:
            case ReplaceQ:
                if (flags != null) {
                    buffer.putInt(flags);
                }
                if (expirationInSecs != null) {
                    buffer.putInt(expirationInSecs);
                }
                break;
            case Increment:
            case IncrementQ:
            case Decrement:
            case DecrementQ:
                if (delta != null) {
                    buffer.putLong(delta);
                }
                if (initial != null) {
                    buffer.putLong(initial);
                }
                if (expirationInSecs != null) {
                    buffer.putInt(expirationInSecs);
                }
                break;
            case Verbosity:
                if (verbosity != null) {
                    buffer.putInt(verbosity);
                }
                break;
            case GAT:
            case GATQ:
            case Touch:
            case Flush:
            case FlushQ:
                if (expirationInSecs != null) {
                    buffer.putInt(expirationInSecs);
                }
                break;
            default:
                break;
        }
    }

    public short getKeyLength() {
        return hasKey && key != null ? (short) (key.remaining() & 0x7fff) : 0;
    }

    public int getValueLength() {
        return hasValue && value != null ? value.remaining() : 0;
    }

    public static class Builder implements Cacheable {

        private static final ThreadCache.CachedTypeIndex<Builder> CACHE_IDX = ThreadCache.obtainIndex(Builder.class, 16);

        private boolean hasExtras;
        private boolean hasKey;
        private boolean hasValue;

        private CommandOpcodes op;
        private boolean noReply;
        private int opaque;
        private long cas;
        private byte dataType;
        private short vBucketId;

        // extras and body
        private Integer flags;
        private Long delta;
        private Long initial;
        private Integer expirationInSecs;
        private Integer verbosity;
        private BufferWrapper.BufferType originKeyType;
        private Object originKey;
        private Buffer key;
        private Buffer value;

        public static Builder create(final boolean hasExtras, final boolean hasKey, final boolean hasValue) {
            final Builder builder = ThreadCache.takeFromCache(CACHE_IDX);
            if (builder != null) {
                builder.initialize(hasExtras, hasKey, hasValue);
                return builder;
            }
            return new Builder(hasExtras, hasKey, hasValue);
        }

        private Builder(final boolean hasExtras, final boolean hasKey, final boolean hasValue) {
            initialize(hasExtras, hasKey, hasValue);
        }

        private void initialize(final boolean hasExtras, final boolean hasKey, final boolean hasValue) {
            this.hasExtras = hasExtras;
            this.hasKey = hasKey;
            this.hasValue = hasValue;
        }

        public Builder op(final CommandOpcodes op) {
            this.op = op;
            return this;
        }

        public Builder noReply(final boolean noReply) {
            this.noReply = noReply;
            return this;
        }

        public Builder opaque(final int opaque) {
            this.opaque = opaque;
            return this;
        }

        public Builder cas(final long cas) {
            this.cas = cas;
            return this;
        }

        public Builder dataType(final byte dataType) {
            this.dataType = dataType;
            return this;
        }

        public Builder vBucketId(final short vBucketId) {
            this.vBucketId = vBucketId;
            return this;
        }

        public Builder flags(final Integer flags) {
            this.flags = flags;
            return this;
        }

        public Builder delta(final Long delta) {
            this.delta = delta;
            return this;
        }

        public Builder initial(final Long initial) {
            this.initial = initial;
            return this;
        }

        public Builder expirationInSecs(final Integer expirationInSecs) throws IllegalArgumentException {
            if (expirationInSecs < 0) {
                throw new IllegalArgumentException("expiration must be greater than 0");
            }
            this.expirationInSecs = expirationInSecs;
            return this;
        }

        public Builder verbosity(final Integer verbosity) {
            this.verbosity = verbosity;
            return this;
        }

        public Builder originKeyType(final BufferWrapper.BufferType originKeyType) throws IllegalArgumentException {
            if (originKeyType != null) {
                this.originKeyType = originKeyType;
            }
            return this;
        }

        public Builder originKey(final Object originKey) throws IllegalArgumentException {
            if (originKey != null) {
                this.originKey = originKey;
            }
            return this;
        }

        public Builder key(final Buffer key) throws IllegalArgumentException {
            if (key != null) {
                final int keyLen = key.remaining();
                if (keyLen > MAX_KEY_LENGTH) {
                    throw new IllegalArgumentException("key length is in excess of " + MAX_KEY_LENGTH + "bytes. keyLen=" + keyLen + "bytes");
                }
                this.key = key;
            }
            return this;
        }

        public Builder value(final Buffer value) throws IllegalArgumentException {
            if (value != null) {
                final int valueLen = value.remaining();
                if (valueLen > MAX_VALUE_LENGTH) {
                    throw new IllegalArgumentException("value length is in excess of " + MAX_VALUE_LENGTH + "bytes. valueLen=" + valueLen + "bytes");
                }
                this.value = value;
            }
            return this;
        }

        public MemcachedRequest build() {
            return new MemcachedRequest(this);
        }

        @Override
        public void recycle() {
            hasExtras = false;
            hasKey = false;
            hasValue = false;
            op = null;
            noReply = false;
            opaque = 0;
            cas = 0;
            dataType = 0;
            vBucketId = 0;
            flags = null;
            delta = null;
            initial = null;
            expirationInSecs = null;
            verbosity = null;
            originKey = null;
            key = null;
            value = null;
            ThreadCache.putToCache(CACHE_IDX, this);
        }
    }

    @Override
    public String toString() {
        return "MemcachedRequest{" +
                "hasExtras=" + hasExtras +
                ", hasKey=" + hasKey +
                ", hasValue=" + hasValue +
                ", op=" + op +
                ", noReply=" + noReply +
                ", opaque=" + opaque +
                ", cas=" + cas +
                ", dataType=" + dataType +
                ", vBucketId=" + vBucketId +
                ", flags=" + flags +
                ", delta=" + delta +
                ", initial=" + initial +
                ", expirationInSecs=" + expirationInSecs +
                ", verbosity=" + verbosity +
                ", originKeyType=" + originKeyType +
                ", originKey=" + originKey +
                ", key=" + key +
                ", response=" + response +
                ", responseStatus=" + responseStatus +
                ", value=" + value +
                '}';
    }
}
