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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Simple wrapper class for {@link Buffer}, which has original message {@code origin}, original object type {@link BufferType} and converted {@link Buffer}
 * <p/>
 * Messages which should be sent to the remote peer via network will be always converted into {@link Buffer} instance by {@link #wrap},
 * and the received packet will be always restored to its original messages by {@link #unwrap}
 *
 * @author Bongjae Chang
 */
public class BufferWrapper<T> implements Cacheable {

    private static final Logger logger = Grizzly.logger(BufferWrapper.class);

    private static final ThreadCache.CachedTypeIndex<BufferWrapper> CACHE_IDX =
            ThreadCache.obtainIndex(BufferWrapper.class, 16);
    private static final String DEFAULT_CHARSET = "UTF-8";

    // if the size of packets is more than 16K, packets will be compressed by GZIP
    public static final int DEFAULT_COMPRESSION_THRESHOLD = 16 * 1024; // 16K

    private Buffer buffer;
    private BufferType type;
    private T origin;

    public static enum BufferType {
        NONE(0), STRING(1), STRING_COMPRESSED(2), BYTE_ARRAY(3), BYTE_ARRAY_COMPRESSED(4), BYTE_BUFFER(5), BYTE_BUFFER_COMPRESSED(6), BYTE(7), BOOLEAN(8), SHORT(9), INTEGER(10), FLOAT(11), DOUBLE(12), LONG(13), DATE(14), OBJECT(15), OBJECT_COMPRESSED(16);

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
                    return STRING_COMPRESSED;
                case 3:
                    return BYTE_ARRAY;
                case 4:
                    return BYTE_ARRAY_COMPRESSED;
                case 5:
                    return BYTE_BUFFER;
                case 6:
                    return BYTE_BUFFER_COMPRESSED;
                case 7:
                    return BYTE;
                case 8:
                    return BOOLEAN;
                case 9:
                    return SHORT;
                case 10:
                    return INTEGER;
                case 11:
                    return FLOAT;
                case 12:
                    return DOUBLE;
                case 13:
                    return LONG;
                case 14:
                    return DATE;
                case 15:
                    return OBJECT;
                case 16:
                    return OBJECT_COMPRESSED;
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

    /**
     * Return original object
     *
     * @return original object
     */
    public T getOrigin() {
        return origin;
    }

    /**
     * Return buffer corresponding to original object
     *
     * @return the converted buffer
     */
    public Buffer getBuffer() {
        return buffer;
    }

    /**
     * Return original object's type
     *
     * @return buffer type
     */
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

    /**
     * Return {@code BufferWrapper} instance from original object
     *
     * @param origin        original object
     * @param memoryManager the memory manager for allocating {@link Buffer}
     * @return the buffer wrapper which included origin, buffer type and buffer corresponding to {@code origin}
     * @throws IllegalArgumentException if given parameters are not valid
     */
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
            if (buffer.remaining() > DEFAULT_COMPRESSION_THRESHOLD) {
                bufferWrapper = create(origin, compressBuffer(buffer, memoryManager), BufferType.STRING_COMPRESSED);
            } else {
                bufferWrapper = create(origin, buffer, BufferWrapper.BufferType.STRING);
            }
        } else if (origin instanceof byte[]) {
            final byte[] originBytes = (byte[]) origin;
            if (originBytes.length > DEFAULT_COMPRESSION_THRESHOLD) {
                buffer = Buffers.wrap(memoryManager, compress(originBytes));
                bufferWrapper = create(origin, buffer, BufferType.BYTE_ARRAY_COMPRESSED);
            } else {
                buffer = Buffers.wrap(memoryManager, originBytes);
                bufferWrapper = create(origin, buffer, BufferType.BYTE_ARRAY);
            }
        } else if (origin instanceof ByteBuffer) {
            buffer = Buffers.wrap(memoryManager, (ByteBuffer) origin);
            if (buffer.remaining() > DEFAULT_COMPRESSION_THRESHOLD) {
                bufferWrapper = create(origin, compressBuffer(buffer, memoryManager), BufferType.BYTE_BUFFER_COMPRESSED);
            } else {
                bufferWrapper = create(origin, buffer, BufferWrapper.BufferType.BYTE_BUFFER);
            }
        } else if (origin instanceof Byte) {
            buffer = memoryManager.allocate(1);
            buffer.put((Byte) origin);
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferType.BYTE);
        } else if (origin instanceof Boolean) {
            buffer = memoryManager.allocate(1);
            if ((Boolean) origin) {
                buffer.put((byte) '1');
            } else {
                buffer.put((byte) '0');
            }
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferType.BOOLEAN);
        } else if (origin instanceof Short) {
            buffer = memoryManager.allocate(2);
            buffer.putShort((Short) origin);
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferType.SHORT);
        } else if (origin instanceof Integer) {
            buffer = memoryManager.allocate(4);
            buffer.putInt((Integer) origin);
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferType.INTEGER);
        } else if (origin instanceof Float) {
            buffer = memoryManager.allocate(4);
            buffer.putFloat((Float) origin);
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferType.FLOAT);
        } else if (origin instanceof Double) {
            buffer = memoryManager.allocate(8);
            buffer.putDouble((Double) origin);
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferType.DOUBLE);
        } else if (origin instanceof Long) {
            buffer = memoryManager.allocate(8);
            buffer.putLong((Long) origin);
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferType.LONG);
        } else if (origin instanceof Date) {
            buffer = memoryManager.allocate(8);
            buffer.putLong(((Date) origin).getTime());
            buffer.flip();
            bufferWrapper = create(origin, buffer, BufferType.DATE);
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
                if (buffer.remaining() > DEFAULT_COMPRESSION_THRESHOLD) {
                    bufferWrapper = create(origin, compressBuffer(buffer, memoryManager), BufferType.OBJECT_COMPRESSED);
                } else {
                    bufferWrapper = create(origin, buffer, BufferType.OBJECT);
                }
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

    /**
     * Return the original instance from {@code buffer}
     *
     * @param buffer        the {@link Buffer} for being restored
     * @param type          the type of original object
     * @param memoryManager the memory manager which will be used for decompressing packets
     * @return the restored object
     */
    public static Object unwrap(final Buffer buffer, final BufferWrapper.BufferType type, final MemoryManager memoryManager) {
        if (buffer == null) {
            return null;
        }
        return unwrap(buffer, buffer.position(), buffer.limit(), type, memoryManager);
    }

    /**
     * Return the original instance from {@code buffer}
     *
     * @param buffer        the {@link Buffer} for being restored
     * @param position      the position of buffer to convert {@code buffer} into origin
     * @param limit         the limit of buffer to convert {@code buffer} into origin
     * @param type          the type of original object
     * @param memoryManager the memory manager which will be used for decompressing packets
     * @return the restored object
     */
    public static Object unwrap(final Buffer buffer, final int position, final int limit, final BufferWrapper.BufferType type, final MemoryManager memoryManager) {
        if (buffer == null || position > limit || type == null) {
            return null;
        }
        switch (type) {
            case NONE:
                return buffer;
            case STRING:
                final int length = limit - position;
                final byte[] bytes = new byte[length];
                buffer.get(bytes, 0, length);
                try {
                    return new String(bytes, DEFAULT_CHARSET);
                } catch (UnsupportedEncodingException uee) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "failed to decode the buffer", uee);
                    }
                    return null;
                }
            case STRING_COMPRESSED:
                final int length2 = limit - position;
                final byte[] bytes2 = new byte[length2];
                buffer.get(bytes2, 0, length2);
                final byte[] decompressedBytes = decompress(bytes2);
                if (decompressedBytes == null) {
                    return null;
                }
                try {
                    return new String(decompressedBytes, DEFAULT_CHARSET);
                } catch (UnsupportedEncodingException uee) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "failed to decode the buffer", uee);
                    }
                    return null;
                }
            case BYTE_ARRAY:
                final int length3 = limit - position;
                final byte[] bytes3 = new byte[length3];
                buffer.get(bytes3, 0, length3);
                return bytes3;
            case BYTE_ARRAY_COMPRESSED:
                final ByteBuffer byteBuffer = buffer.toByteBuffer();
                return decompress(byteBuffer.array(), byteBuffer.arrayOffset() + position, limit - position);
            case BYTE_BUFFER:
                return buffer.toByteBuffer(position, limit);
            case BYTE_BUFFER_COMPRESSED:
                final Buffer decompressedBuffer2 = decompressBuffer(buffer, position, limit, memoryManager);
                if (decompressedBuffer2 == null) {
                    return null;
                }
                final ByteBuffer result = decompressedBuffer2.toByteBuffer();
                decompressedBuffer2.tryDispose();
                return result;
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
            case OBJECT_COMPRESSED:
                final Buffer decompressedBuffer3 = decompressBuffer(buffer, position, limit, memoryManager);
                if (decompressedBuffer3 == null) {
                    return null;
                }
                BufferInputStream bis2 = null;
                ObjectInputStream ois2 = null;
                Object obj2 = null;
                try {
                    bis2 = new BufferInputStream(decompressedBuffer3);
                    ois2 = new ObjectInputStream(bis2);
                    obj2 = ois2.readObject();
                } catch (IOException ie) {
                    if (logger.isLoggable(Level.SEVERE)) {
                        logger.log(Level.SEVERE, "failed to read the object", ie);
                    }
                } catch (ClassNotFoundException cnfe) {
                    if (logger.isLoggable(Level.SEVERE)) {
                        logger.log(Level.SEVERE, "failed to read the object", cnfe);
                    }
                } finally {
                    if (ois2 != null) {
                        try {
                            ois2.close();
                        } catch (IOException ignore) {
                        }
                    }
                    if (bis2 != null) {
                        try {
                            bis2.close();
                        } catch (IOException ignore) {
                        }
                    }
                }
                decompressedBuffer3.tryDispose();
                return obj2;
            default:
                return null;
        }
    }

    private static Buffer compressBuffer(final Buffer buffer, final MemoryManager memoryManager) {
        if (buffer == null) {
            throw new IllegalArgumentException("failed to compress the buffer. buffer should be not null");
        }
        final ByteBuffer byteBuffer = buffer.toByteBuffer();
        final byte[] compressed = compress(byteBuffer.array(),
                byteBuffer.arrayOffset() + byteBuffer.position(),
                byteBuffer.remaining());
        buffer.tryDispose();
        return Buffers.wrap(memoryManager, compressed);
    }

    private static byte[] compress(final byte[] in) {
        return compress(in, 0, in.length);
    }

    private static byte[] compress(final byte[] in, final int offset, final int length) {
        if (in == null) {
            throw new IllegalArgumentException("Can't compress null");
        }
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPOutputStream gz = null;
        try {
            gz = new GZIPOutputStream(bos);
            gz.write(in, offset, length);
        } catch (IOException ie) {
            throw new RuntimeException("IO exception compressing data", ie);
        } finally {
            if (gz != null) {
                try {
                    gz.close();
                } catch (IOException ignore) {
                }
            }
            try {
                bos.close();
            } catch (IOException ignore) {
            }
        }
        return bos.toByteArray();
    }

    private static Buffer decompressBuffer(final Buffer buffer, final int position, final int limit, final MemoryManager memoryManager) {
        if (buffer == null || position > limit) {
            return null;
        }
        final ByteBuffer byteBuffer = buffer.toByteBuffer();
        final byte[] decompressed = decompress(byteBuffer.array(), byteBuffer.arrayOffset() + position, limit - position);
        if (decompressed == null) {
            return null;
        }
        return Buffers.wrap(memoryManager, decompressed);
    }

    private static byte[] decompress(final byte[] in) {
        if (in == null) {
            return null;
        }
        return decompress(in, 0, in.length);
    }

    private static byte[] decompress(final byte[] in, final int offset, final int length) {
        ByteArrayOutputStream bos;
        byte[] result = null;
        if (in != null) {
            ByteArrayInputStream bis = new ByteArrayInputStream(in, offset, length);
            bos = new ByteArrayOutputStream();
            GZIPInputStream gis = null;
            try {
                gis = new GZIPInputStream(bis);

                byte[] buf = new byte[8192];
                int r;
                while ((r = gis.read(buf)) > 0) {
                    bos.write(buf, 0, r);
                }
                result = bos.toByteArray();
            } catch (IOException ie) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "failed to decompress bytes", ie);
                }
                bos = null;
            } finally {
                if (gis != null) {
                    try {
                        gis.close();
                    } catch (IOException ignore) {
                    }
                }
                try {
                    bis.close();
                } catch (IOException ignore) {
                }
                if (bos != null) {
                    try {
                        bos.close();
                    } catch (IOException ignore) {
                    }
                }
            }
        }
        return result;
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
