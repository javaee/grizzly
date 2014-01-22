/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.memory;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.CompositeBuffer.Setter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link CompositeBuffer} test set.
 * 
 * @author Alexey Stashok
 */
@RunWith(Parameterized.class)
public class CompositeBufferTest extends AbstractMemoryManagerTest {

    public CompositeBufferTest(int mmType) {
        super(mmType);
    }

    @Test
    public void testSingleBuffer() {

        Buffer buffer = mm.allocate(1 + 2 + 2 + 4 + 8 + 4 + 8);
        CompositeBuffer compositeBuffer = createCompositeBuffer(buffer);

        byte v1 = (byte) 127;
        char v2 = 'c';
        short v3 = (short) -1;
        int v4 = Integer.MIN_VALUE;
        long v5 = Long.MAX_VALUE;
        float v6 = 0.3f;
        double v7 = -0.5;

        compositeBuffer.put(v1);
        compositeBuffer.putChar(v2);
        compositeBuffer.putShort(v3);
        compositeBuffer.putInt(v4);
        compositeBuffer.putLong(v5);
        compositeBuffer.putFloat(v6);
        compositeBuffer.putDouble(v7);

        compositeBuffer.flip();

        assertEquals(v1, compositeBuffer.get());
        assertEquals(v2, compositeBuffer.getChar());
        assertEquals(v3, compositeBuffer.getShort());
        assertEquals(v4, compositeBuffer.getInt());
        assertEquals(v5, compositeBuffer.getLong());
        assertEquals(v6, v6, compositeBuffer.getFloat());
        assertEquals(v7, v7, compositeBuffer.getDouble());
    }

    @Test
    public void testSingleBufferIndexedAccess() {

        Buffer buffer = mm.allocate(1 + 2 + 2 + 4 + 8 + 4 + 8);
        CompositeBuffer compositeBuffer = createCompositeBuffer(buffer);

        byte v1 = (byte) 127;
        char v2 = 'c';
        short v3 = (short) -1;
        int v4 = Integer.MIN_VALUE;
        long v5 = Long.MAX_VALUE;
        float v6 = 0.3f;
        double v7 = -0.5;

        compositeBuffer.put(0, v1);
        compositeBuffer.putChar(1, v2);
        compositeBuffer.putShort(3, v3);
        compositeBuffer.putInt(5, v4);
        compositeBuffer.putLong(9, v5);
        compositeBuffer.putFloat(17, v6);
        compositeBuffer.putDouble(21, v7);

        assertEquals(v1, compositeBuffer.get(0));
        assertEquals(v2, compositeBuffer.getChar(1));
        assertEquals(v3, compositeBuffer.getShort(3));
        assertEquals(v4, compositeBuffer.getInt(5));
        assertEquals(v5, compositeBuffer.getLong(9));
        assertEquals(v6, v6, compositeBuffer.getFloat(17));
        assertEquals(v7, v7, compositeBuffer.getDouble(21));
    }

    @Test
    public void testBytes() {
        doTest(new Byte[]{-1, 0, 127, -127}, 4, 1,
                new Put<Byte>() {

                    @Override
                    public void put(CompositeBuffer buffer, Byte value) {
                        buffer.put(value);
                    }
                },
                new Get<Byte>() {

                    @Override
                    public Byte get(CompositeBuffer buffer) {
                        return buffer.get();
                    }
                });
    }

    @Test
    public void testBytesIndexed() {
        doTestIndexed(new Byte[]{-1, 0, 127, -127}, 4, 1,
                new Put<Byte>() {

                    @Override
                    public void putIndexed(CompositeBuffer buffer, int index, Byte value) {
                        buffer.put(index, value);
                    }
                },
                new Get<Byte>() {

                    @Override
                    public Byte getIndexed(CompositeBuffer buffer, int index) {
                        return buffer.get(index);
                    }
                }, 1);
    }

    @Test
    public void testChars() {
        doTest(new Character[]{'a', 'b', 'c', 'd'}, 3, 3,
                new Put<Character>() {

                    @Override
                    public void put(CompositeBuffer buffer, Character value) {
                        buffer.putChar(value);
                    }
                },
                new Get<Character>() {

                    @Override
                    public Character get(CompositeBuffer buffer) {
                        return buffer.getChar();
                    }
                });
    }

    @Test
    public void testCharsIndexed() {
        doTestIndexed(new Character[]{'a', 'b', 'c', 'd'}, 3, 3,
                new Put<Character>() {

                    @Override
                    public void putIndexed(CompositeBuffer buffer, int index, Character value) {
                        buffer.putChar(index, value);
                    }
                },
                new Get<Character>() {

                    @Override
                    public Character getIndexed(CompositeBuffer buffer, int index) {
                        return buffer.getChar(index);
                    }
                }, 2);
    }

    @Test
    public void testShort() {
        doTest(new Short[]{Short.MIN_VALUE, -1}, 3, 3,
                new Put<Short>() {

                    @Override
                    public void put(CompositeBuffer buffer, Short value) {
                        buffer.putShort(value);
                    }
                },
                new Get<Short>() {

                    @Override
                    public Short get(CompositeBuffer buffer) {
                        return buffer.getShort();
                    }
                });
    }

    @Test
    public void testShortIndexed() {
        doTestIndexed(new Short[]{Short.MIN_VALUE, -1}, 3, 3,
                new Put<Short>() {

                    @Override
                    public void putIndexed(CompositeBuffer buffer, int index, Short value) {
                        buffer.putShort(index, value);
                    }
                },
                new Get<Short>() {

                    @Override
                    public Short getIndexed(CompositeBuffer buffer, int index) {
                        return buffer.getShort(index);
                    }
                }, 2);
    }

    @Test
    public void testInt() {
        doTest(new Integer[]{Integer.MIN_VALUE, -1, Integer.MAX_VALUE, 0}, 4, 5,
                new Put<Integer>() {

                    @Override
                    public void put(CompositeBuffer buffer, Integer value) {
                        buffer.putInt(value);
                    }
                },
                new Get<Integer>() {

                    @Override
                    public Integer get(CompositeBuffer buffer) {
                        return buffer.getInt();
                    }
                });
    }

    @Test
    public void testIntIndexed() {
        doTestIndexed(new Integer[]{Integer.MIN_VALUE, -1, Integer.MAX_VALUE, 0}, 4, 5,
                new Put<Integer>() {

                    @Override
                    public void putIndexed(CompositeBuffer buffer, int index, Integer value) {
                        buffer.putInt(index, value);
                    }
                },
                new Get<Integer>() {

                    @Override
                    public Integer getIndexed(CompositeBuffer buffer, int index) {
                        return buffer.getInt(index);
                    }
                }, 4);
    }

    @Test
    public void testLong() {
        doTest(new Long[]{Long.MIN_VALUE, -1L, Long.MAX_VALUE, 0L}, 4, 9,
                new Put<Long>() {

                    @Override
                    public void put(CompositeBuffer buffer, Long value) {
                        buffer.putLong(value);
                    }
                },
                new Get<Long>() {

                    @Override
                    public Long get(CompositeBuffer buffer) {
                        return buffer.getLong();
                    }
                });
    }

    @Test
    public void testFloat() {
        doTest(new Float[]{Float.MIN_VALUE, -1f, Float.MAX_VALUE, 0f}, 4, 5,
                new Put<Float>() {

                    @Override
                    public void put(CompositeBuffer buffer, Float value) {
                        buffer.putFloat(value);
                    }
                },
                new Get<Float>() {

                    @Override
                    public Float get(CompositeBuffer buffer) {
                        return buffer.getFloat();
                    }
                }
        );
    }

    @Test
    public void testLongIndexed() {
        doTestIndexed(new Long[]{Long.MIN_VALUE, -1L, Long.MAX_VALUE, 0L}, 4, 9,
                new Put<Long>() {

                    @Override
                    public void putIndexed(CompositeBuffer buffer, int index, Long value) {
                        buffer.putLong(index, value);
                    }
                },
                new Get<Long>() {

                    @Override
                    public Long getIndexed(CompositeBuffer buffer, int index) {
                        return buffer.getLong(index);
                    }
                }, 8
        );
    }

    @Test
    public void testFloatIndexed() {
        doTestIndexed(new Float[]{Float.MIN_VALUE, -1f, Float.MAX_VALUE, 0f}, 4, 5,
                new Put<Float>() {

                    @Override
                    public void putIndexed(CompositeBuffer buffer, int index, Float value) {
                        buffer.putFloat(index, value);
                    }
                },
                new Get<Float>() {

                    @Override
                    public Float getIndexed(CompositeBuffer buffer, int index) {
                        return buffer.getFloat(index);
                    }
                }, 4
        );
    }

    @Test
    public void testDouble() {
        doTest(new Double[]{Double.MIN_VALUE, -1.0, Double.MAX_VALUE, 0.0}, 4, 9,
                new Put<Double>() {

                    @Override
                    public void put(CompositeBuffer buffer, Double value) {
                        buffer.putDouble(value);
                    }
                },
                new Get<Double>() {

                    @Override
                    public Double get(CompositeBuffer buffer) {
                        return buffer.getDouble();
                    }
                }
        );
    }

    @Test
    public void testDoubleIndexed() {
        doTestIndexed(new Double[]{Double.MIN_VALUE, -1.0, Double.MAX_VALUE, 0.0}, 4, 9,
                new Put<Double>() {

                    @Override
                    public void putIndexed(CompositeBuffer buffer, int index, Double value) {
                        buffer.putDouble(index, value);
                    }
                },
                new Get<Double>() {

                    @Override
                    public Double getIndexed(CompositeBuffer buffer, int index) {
                        return buffer.getDouble(index);
                    }
                }, 8
        );
    }

    @Test
    public void testBuffers() {

        Buffer sampleBuffer = Buffers.wrap(mm, new byte[]{-1, 0, 1, 1, 2, 3, 4});

        Buffer b1 = mm.allocate(3);
        Buffer b2 = mm.allocate(4);

        CompositeBuffer compositeBuffer = createCompositeBuffer(b1, b2);
        compositeBuffer.put(sampleBuffer);
        compositeBuffer.flip();
        sampleBuffer.flip();

        while (sampleBuffer.hasRemaining()) {
            assertEquals(sampleBuffer.get(), compositeBuffer.get());
        }
    }

    @Test
    public void testEmptyBufferPrepend() {

        Buffer buffer1 = Buffers.wrap(mm, "1234");
        buffer1.position(3);

        Buffer buffer2 = mm.allocate(0);

        CompositeBuffer compositeBuffer = createCompositeBuffer(buffer1);
        assertEquals('4', (char) compositeBuffer.get(0));

        Buffer resultBuffer = Buffers.appendBuffers(mm, buffer2, compositeBuffer);

        assertEquals(resultBuffer.toStringContent(), "4");
    }

    @Test
    public void testSplit() {
        doTestSplit(100);
    }

    @Test
    public void testToByteBufferArray() {

        final byte[] bytes = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        final List<Buffer> bufferList = new ArrayList<Buffer>();
        for (byte b : bytes) {
            final Buffer buffer = mm.allocate(1);
            buffer.put(0, b);
            bufferList.add(buffer);
        }

        final Buffer[] buffers = bufferList.toArray(new Buffer[bufferList.size()]);
        final CompositeBuffer composite = CompositeBuffer.newBuffer(mm, buffers);

        for (int i = 0; i < bytes.length; i++) {
            for (int j = i; j < bytes.length; j++) {
                final ByteBufferArray bbArray = composite.toByteBufferArray(i, j);
                
                int bytesChecked = 0;
                final ByteBuffer[] bbs = bbArray.getArray();
                for (int k = 0; k < bbArray.size(); k++) {
                    final ByteBuffer bb = bbs[k];

                    while (bb.hasRemaining()) {
                        final byte got = bb.get();
                        assertEquals("Testcase [pos=" + i + " lim=" + j + " bytenumber=" + bytesChecked + "]", bytes[i + bytesChecked], got);
                        bytesChecked++;
                    }
                }

                assertEquals(j - i, bytesChecked);
                bbArray.restore();
            }
        }
    }

    @Test
    public void testToByteBuffer() {

        final byte[] bytes = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        final List<Buffer> bufferList = new ArrayList<Buffer>();
        for (byte b : bytes) {
            final Buffer buffer = mm.allocate(1);
            buffer.put(0, b);
            bufferList.add(buffer);
        }

        final Buffer[] buffers = bufferList.toArray(new Buffer[bufferList.size()]);
        final CompositeBuffer composite = CompositeBuffer.newBuffer(mm, buffers);

        for (int i = 0; i < bytes.length; i++) {
            for (int j = i; j < bytes.length; j++) {
                final ByteBuffer bb = composite.toByteBuffer(i, j);

                int bytesChecked = 0;
                while (bb.hasRemaining()) {
                    final byte got = bb.get();
                    assertEquals("Testcase [pos=" + i + " lim=" + j + " bytenumber=" + bytesChecked + "]", bytes[i + bytesChecked], got);
                    bytesChecked++;
                }

                assertEquals(j - i, bytesChecked);
            }
        }
    }

    public void testToStringContent() {
        final CompositeBuffer composite = CompositeBuffer.newBuffer(
                mm, Buffers.wrap(mm, "hello"),
                Buffers.wrap(mm, " world"));

        assertEquals("hello world", composite.toStringContent());
    }

    @Test
    public void testToStringContent2() {
        final Charset utf16 = Charset.forName("UTF-16");
        
        final String msg = "\u043F\u0440\u0438\u0432\u0435\u0442";
        final Buffer msgBuffer = Buffers.wrap(mm, msg, utf16);
        final Buffer b1 = msgBuffer.duplicate();
        final Buffer b2 = b1.split(3);
        
        final CompositeBuffer composite = CompositeBuffer.newBuffer(
                mm, b1, b2);

        assertTrue(composite.equals(msgBuffer));

        assertEquals(msg, composite.toStringContent(utf16));
    }

    @Test
    public void testGetByteBuffer() {

        final byte[] bytes = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        final List<Buffer> bufferList = new ArrayList<Buffer>();
        for (byte b : bytes) {
            final Buffer buffer = mm.allocate(1);
            buffer.put(0, b);
            bufferList.add(buffer);
        }

        final Buffer[] buffers = bufferList.toArray(new Buffer[bufferList.size()]);
        final CompositeBuffer composite = CompositeBuffer.newBuffer(mm, buffers);

        final ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length * 2);
        final int position = byteBuffer.remaining() / 4;
        for (int i = 0; i < bytes.length; i++) {
            Buffers.setPositionLimit(composite, 0, i);
            Buffers.setPositionLimit(byteBuffer, position, position + i);
            composite.get(byteBuffer);
            
            Buffers.setPositionLimit(composite, 0, i);
            Buffers.setPositionLimit(byteBuffer, position, position + i);

            assertTrue(composite.equals(Buffers.wrap(mm, byteBuffer)));
        }
    }

    @Test
    public void testBulk() {
        final Charset ascii = Charset.forName("ASCII");
        
        final CompositeBuffer composite = CompositeBuffer.newBuffer(
                mm, Buffers.wrap(mm, "hello", ascii),
                Buffers.wrap(mm, " world", ascii));

        composite.bulk(new CompositeBuffer.BulkOperation() {

            @Override
            public boolean processByte(byte value, final Setter setter) {
                setter.set((byte) Character.toUpperCase(value));
                return false;
            }
        });
        
        assertEquals("HELLO WORLD", composite.toStringContent(ascii));
    }
    
    private <E> void doTest(E[] testData,
            int buffersNum, int bufferSize, Put<E> put, Get<E> get) {


        Buffer[] buffers = new Buffer[buffersNum];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = mm.allocate(bufferSize);
        }

        CompositeBuffer compositeBuffer = createCompositeBuffer(buffers);

        for (int i = 0; i < testData.length; i++) {
            put.put(compositeBuffer, testData[i]);
        }

        compositeBuffer.flip();

        for (int i = 0; i < testData.length; i++) {
            assertEquals(testData[i], get.get(compositeBuffer));
        }
    }

    private <E> void doTestIndexed(E[] testData,
            int buffersNum, int bufferSize, Put<E> put, Get<E> get,
            int eSizeInBytes) {


        Buffer[] buffers = new Buffer[buffersNum];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = mm.allocate(bufferSize);
        }

        CompositeBuffer compositeBuffer = createCompositeBuffer(buffers);

        for (int i = 0; i < testData.length; i++) {
            put.putIndexed(compositeBuffer, i * eSizeInBytes, testData[i]);
        }

        for (int i = 0; i < testData.length; i++) {
            assertEquals(testData[i], get.getIndexed(compositeBuffer, i * eSizeInBytes));
        }
    }

    private void doTestSplit(int size) {


        for (int i = 1; i <= size; i++) {
            final int num = size / i;
            final int remainder = size - (num * i);

            int j = 1;

            try {
                for (; j <= size; j++) {

                    Buffer[] buffers = new Buffer[num];
                    for (int k = 0; k < buffers.length; k++) {
                        buffers[k] = mm.allocate(i);
                    }

                    if (remainder > 0) {
                        buffers = Arrays.copyOf(buffers, num + 1);
                        buffers[num] = mm.allocate(remainder);
                    }

                    CompositeBuffer compositeBuffer = createCompositeBuffer(buffers);

                    for (int k = 0; k < compositeBuffer.remaining(); k++) {
                        compositeBuffer.put(k, (byte) (k % 127));
                    }


                    Buffer slice2 = compositeBuffer.split(j);

                    assertEquals("Slice1 unexpected length", j, compositeBuffer.remaining());
                    assertEquals("Slice2  unexpected length", size - j, slice2.remaining());
                }
            } catch (Exception e) {
                throw new IllegalStateException("Exception happened. size=" + size + " chunkSize=" + i + " splitPos=" + j, e);
            }
        }



    }

    private static class Put<E> {

        public void put(CompositeBuffer buffer, E value) {
        }

        public void putIndexed(CompositeBuffer buffer, int index, E value) {
        }
    }

    private static class Get<E> {

        public E get(CompositeBuffer buffer) {
            return null;
        }

        public E getIndexed(CompositeBuffer buffer, int index) {
            return null;
        }
    }

    private CompositeBuffer createCompositeBuffer(Buffer... buffers) {
        return CompositeBuffer.newBuffer(
                MemoryManager.DEFAULT_MEMORY_MANAGER, buffers);
    }
}
