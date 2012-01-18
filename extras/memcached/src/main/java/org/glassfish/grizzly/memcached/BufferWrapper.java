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
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.BufferInputStream;
import org.glassfish.grizzly.utils.BufferOutputStream;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Bongjae Chang
 */
public class BufferWrapper<T> implements Cacheable {

    private static final Logger logger = Grizzly.logger(BufferWrapper.class);

    private static final ThreadCache.CachedTypeIndex<BufferWrapper> CACHE_IDX =
            ThreadCache.obtainIndex(BufferWrapper.class, 16);

    private Buffer buffer;
    private BufferType type;
    private T origin;

    public static enum BufferType {
        NONE(0), STRING(1), BYTE_ARRAY(2), BYTE_BUFFER(3), BYTE(4), BOOLEAN(5), SHORT(6), INTEGER(7), FLOAT(8), DOUBLE(9), LONG(10), DATE(11), OBJECT(20);

        public final int flags;

        private BufferType(int flags) {
            this.flags = flags;
        }

        public static BufferType getBufferType(final int type) {
            switch (type) {
                case 0:
                    return NONE;
                case 1:
                    return STRING;
                case 2:
                    return BYTE_ARRAY;
                case 3:
                    return BYTE_BUFFER;
                case 4:
                    return BYTE;
                case 5:
                    return BOOLEAN;
                case 6:
                    return SHORT;
                case 7:
                    return INTEGER;
                case 8:
                    return FLOAT;
                case 9:
                    return DOUBLE;
                case 10:
                    return LONG;
                case 11:
                    return DATE;
                case 20:
                    return OBJECT;
                default:
                    throw new IllegalArgumentException("invalid type");
            }
        }
    }

    private BufferWrapper(final T origin, final Buffer buffer, final BufferType type) {
        initialize(origin, buffer, type);
    }

    private void initialize(final T origin, final Buffer buffer, final BufferType type) {
        this.origin = origin;
        this.buffer = buffer;
        this.type = type;
    }

    public T getOrigin() {
        return origin;
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public BufferType getType() {
        return type;
    }

    @Override
    public void recycle() {
        origin = null;
        buffer = null;
        type = null;
        ThreadCache.putToCache(CACHE_IDX, this);
    }

    @SuppressWarnings("unchecked")
    private static <T> BufferWrapper<T> create(final T origin, final Buffer buffer, final BufferType type) {
        final BufferWrapper<T> bufferWrapper = ThreadCache.takeFromCache(CACHE_IDX);
        if (bufferWrapper != null) {
            bufferWrapper.initialize(origin, buffer, type);
            return bufferWrapper;
        }
        return new BufferWrapper<T>(origin, buffer, type);
    }

    public static <T> BufferWrapper<T> wrap(final T origin, MemoryManager memoryManager) throws IllegalArgumentException {
        if (origin == null) {
            throw new IllegalArgumentException("object must be not null");
        }
        if (origin instanceof Buffer) {
            return create(origin, (Buffer) origin, BufferWrapper.BufferType.NONE);
        }
        if (memoryManager == null) {
            memoryManager = MemoryManager.DEFAULT_MEMORY_MANAGER;
        }
        final Buffer buffer;
        final BufferWrapper<T> bufferWrapper;
        if (origin instanceof String) {
            buffer = Buffers.wrap(memoryManager, (String) origin);
            bufferWrapper = create(origin, buffer, BufferWrapper.BufferType.STRING);
        } else if (origin instanceof byte[]) {
            buffer = Buffers.wrap(memoryManager, (byte[]) origin);
            bufferWrapper = create(origin, buffer, BufferWrapper.BufferType.BYTE_ARRAY);
        } else if (origin instanceof ByteBuffer) {
            buffer = Buffers.wrap(memoryManager, (ByteBuffer) origin);
            bufferWrapper = create(origin, buffer, BufferWrapper.BufferType.BYTE_BUFFER);
        } else if (origin instanceof Byte) {
            buffer = memoryManager.allocate(1);
            buffer.put((Byte) origin);
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferWrapper.BufferType.BYTE);
        } else if (origin instanceof Boolean) {
            buffer = memoryManager.allocate(1);
            if ((Boolean) origin) {
                buffer.put((byte) '1');
            } else {
                buffer.put((byte) '0');
            }
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferWrapper.BufferType.BOOLEAN);
        } else if (origin instanceof Short) {
            buffer = memoryManager.allocate(2);
            buffer.putShort((Short) origin);
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferWrapper.BufferType.SHORT);
        } else if (origin instanceof Integer) {
            buffer = memoryManager.allocate(4);
            buffer.putInt((Integer) origin);
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferWrapper.BufferType.INTEGER);
        } else if (origin instanceof Float) {
            buffer = memoryManager.allocate(4);
            buffer.putFloat((Float) origin);
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferWrapper.BufferType.FLOAT);
        } else if (origin instanceof Double) {
            buffer = memoryManager.allocate(8);
            buffer.putDouble((Double) origin);
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferWrapper.BufferType.DOUBLE);
        } else if (origin instanceof Long) {
            buffer = memoryManager.allocate(8);
            buffer.putLong((Long) origin);
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferWrapper.BufferType.LONG);
        } else if (origin instanceof Date) {
            buffer = memoryManager.allocate(8);
            buffer.putLong(((Date) origin).getTime());
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferWrapper.BufferType.DATE);
        } else {
            BufferOutputStream bos = null;
            ObjectOutputStream oos = null;
            try {
                bos = new BufferOutputStream(memoryManager) {
                    @Override
                    protected Buffer allocateNewBuffer(
                            final MemoryManager memoryManager, final int size) {
                        final Buffer b = memoryManager.allocate(size);
                        b.allowBufferDispose(true);
                        return b;
                    }
                };
                oos = new ObjectOutputStream(bos);
                oos.writeObject(origin);
                buffer = bos.getBuffer();
                buffer.flip();
                bufferWrapper = create(origin, buffer, BufferWrapper.BufferType.OBJECT);
            } catch (IOException ie) {
                throw new IllegalArgumentException("Non-serializable object", ie);
            } finally {
                if (oos != null) {
                    try {
                        oos.close();
                    } catch (IOException ignore) {
                    }
                }
                if (bos != null) {
                    try {
                        bos.close();
                    } catch (IOException ignore) {
                    }
                }
            }
        }
        if (buffer != null) {
            buffer.allowBufferDispose(true);
        }
        return bufferWrapper;
    }

    public static Object unwrap(final Buffer buffer, final BufferWrapper.BufferType type) {
        if (buffer == null) {
            return null;
        }
        return unwrap(buffer, buffer.position(), buffer.limit(), type);
    }

    public static Object unwrap(final Buffer buffer, final int position, final int limit, final BufferWrapper.BufferType type) {
        if (buffer == null || position > limit || type == null) {
            return null;
        }
        switch (type) {
            case NONE:
                return buffer;
            case STRING:
                return buffer.toStringContent(null, position, limit);
            case BYTE_ARRAY:
                return buffer.toByteBuffer(position, limit).array();
            case BYTE_BUFFER:
                return buffer.toByteBuffer(position, limit);
            case BYTE:
                return buffer.get();
            case BOOLEAN:
                return buffer.get() == '1';
            case SHORT:
                return buffer.getShort();
            case INTEGER:
                return buffer.getInt();
            case FLOAT:
                return buffer.getFloat();
            case DOUBLE:
                return buffer.getDouble();
            case LONG:
                return buffer.getLong();
            case DATE:
                long dateLong = buffer.getLong();
                return new Date(dateLong);
            case OBJECT:
                BufferInputStream bis = null;
                ObjectInputStream ois = null;
                Object obj = null;
                try {
                    bis = new BufferInputStream(buffer, position, limit);
                    ois = new ObjectInputStream(bis);
                    obj = ois.readObject();
                } catch (IOException ie) {
                    if (logger.isLoggable(Level.SEVERE)) {
                        logger.log(Level.SEVERE, "failed to read the object", ie);
                    }
                } catch (ClassNotFoundException cnfe) {
                    if (logger.isLoggable(Level.SEVERE)) {
                        logger.log(Level.SEVERE, "failed to read the object", cnfe);
                    }
                } finally {
                    if (ois != null) {
                        try {
                            ois.close();
                        } catch (IOException ignore) {
                        }
                    }
                    if (bis != null) {
                        try {
                            bis.close();
                        } catch (IOException ignore) {
                        }
                    }
                }
                return obj;
            default:
                return null;
        }
    }

    @Override
    public String toString() {
        return "BufferWrapper{" +
                "buffer=" + buffer +
                ", type=" + type +
                ", origin=" + origin +
                '}';
    }
}
