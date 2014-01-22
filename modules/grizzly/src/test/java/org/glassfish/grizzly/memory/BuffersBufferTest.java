/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2014 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.utils.Charsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.InvalidMarkException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class BuffersBufferTest extends AbstractMemoryManagerTest {

    public BuffersBufferTest(int mmType) {
        super(mmType);
    }


    // ------------------------------------------------------------ Test Methods

    @Test
    public void testCharEndianess() {
        BuffersBuffer buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.putChar('a');
        buffer.flip();
        assertEquals("big endian", 'a', buffer.getChar());
        buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(buffer.order() == ByteOrder.LITTLE_ENDIAN);
        buffer.putChar('a');
        buffer.flip();
        assertEquals("little endian", 'a', buffer.getChar());
    }

    @Test
    public void testShortEndianess() {
        BuffersBuffer buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 1);
        buffer.flip();
        assertEquals("big endian", ((short) 1), buffer.getShort());
        buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(buffer.order() == ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 1);
        buffer.flip();
        assertEquals("little endian", ((short) 1), buffer.getShort());
    }

    @Test
    public void testIntEndianess() {
        BuffersBuffer buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.putInt(1);
        buffer.flip();
        assertEquals("big endian", 1, buffer.getInt());
        buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(buffer.order() == ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(1);
        buffer.flip();
        assertEquals("little endian", 1, buffer.getInt());
    }

    @Test
    public void testLongEndianess() {
        BuffersBuffer buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.putLong(1L);
        buffer.flip();
        assertEquals("big endian", 1, buffer.getLong());
        buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(buffer.order() == ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(1L);
        buffer.flip();
        assertEquals("little endian", 1, buffer.getLong());
    }

    @Test
    public void testFloatEndianess() {
        BuffersBuffer buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.putFloat(1.0f);
        buffer.flip();
        assertEquals("big endian", 1.f, 1.0f, buffer.getFloat());
        buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(buffer.order() == ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(1.0f);
        buffer.flip();
        assertEquals("little endian", 1.0f, 1.0f, buffer.getFloat());
    }

    @Test
    public void testDoubleEndianess() {
        BuffersBuffer buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.putDouble(1.0d);
        buffer.flip();
        assertEquals("big endian", 1.0d, 1.0d, buffer.getDouble());
        buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(buffer.order() == ByteOrder.LITTLE_ENDIAN);
        buffer.putDouble(1.0d);
        buffer.flip();
        assertEquals("little endian", 1.0d, 1.0d, buffer.getDouble());
    }

    @Test
    public void testMarkAndReset() {
        final BuffersBuffer buffer = createOneSevenBuffer(mm);

        buffer.putShort((short)0);
        buffer.putShort((short) 1);
        buffer.mark();
        buffer.putShort((short)2);
        buffer.putShort((short) 3);
        assertTrue(buffer.remaining() == 0);
        final int lastPosition = buffer.position();

        buffer.reset();
        assertTrue(lastPosition != buffer.position());
        assertEquals(2, buffer.getShort());

        buffer.reset();
        assertEquals(2, buffer.getShort());
        assertEquals(3, buffer.getShort());

        buffer.flip();
        assertEquals(0, buffer.getShort());
        buffer.mark();
        assertEquals(1, buffer.getShort());
        assertEquals(2, buffer.getShort());

        buffer.reset();
        assertEquals(1, buffer.getShort());
        assertEquals(2, buffer.getShort());
        assertEquals(3, buffer.getShort());

        assertEquals(lastPosition, buffer.position());
        buffer.mark();
        buffer.position(2); // mark should be reset because of mark > position
        assertEquals(1, buffer.getShort());
        try {
            buffer.reset(); // exception should be thrown
            fail();
        } catch (InvalidMarkException ignore) {
        }

        assertEquals(2, buffer.getShort());
        buffer.mark();
        assertEquals(3, buffer.getShort());
        buffer.reset();
        assertEquals(3, buffer.getShort());

        buffer.flip(); // mark should be reset
        assertEquals(0, buffer.getShort());
        try {
            buffer.reset();
            fail(); // exception should be thrown because mark was already reset
        } catch (InvalidMarkException ignore) {
        }
        assertEquals(1, buffer.getShort());
    }

    @Test
    public void testBulkByteBufferGetWithEmptyBuffers() throws Exception {
        BuffersBuffer b = BuffersBuffer.create(mm);
        b.append(Buffers.wrap(mm, "Hello "));
        b.append(BuffersBuffer.create(mm));
        b.append(Buffers.wrap(mm, "world!"));
        
        ByteBuffer buffer = ByteBuffer.allocate(12);
        b.get(buffer);
        buffer.flip();
        assertEquals("Hello world!", Charsets.getCharsetDecoder(Charsets.UTF8_CHARSET).decode(buffer).toString());
    }

    @Test
    public void testBulkArrayGetWithEmptyBuffers() throws Exception {
        BuffersBuffer b = BuffersBuffer.create(mm);
        b.append(Buffers.wrap(mm, "Hello "));
        b.append(BuffersBuffer.create(mm));
        b.append(Buffers.wrap(mm, "world!"));

        byte[] bytes = new byte[12];
        b.get(bytes);
        assertEquals("Hello world!", new String(bytes));
    }


    // ------------------------------------------------------- Protected Methods



    // --------------------------------------------------------- Private Methods


    private static BuffersBuffer createOneSevenBuffer(final MemoryManager mm) {
        final BuffersBuffer b = BuffersBuffer.create(mm);
        b.append(mm.allocate(7).limit(1));
        b.append(mm.allocate(7));
        return b;
    }
}
