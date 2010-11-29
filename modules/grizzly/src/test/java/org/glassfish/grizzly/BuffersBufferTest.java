/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

import org.glassfish.grizzly.memory.BuffersBuffer;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.memory.MemoryManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BuffersBufferTest extends GrizzlyTestCase {

    private MemoryManager mm;


    // -------------------------------------------------------------- Test Setup


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mm = TransportFactory.getInstance().getDefaultMemoryManager();
    }


    // ------------------------------------------------------------ Test Methods


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

    public void testFloatEndianess() {
        BuffersBuffer buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.putFloat(1.0f);
        buffer.flip();
        assertEquals("big endian", 1.0f, buffer.getFloat());
        buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(buffer.order() == ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(1.0f);
        buffer.flip();
        assertEquals("little endian", 1.0f, buffer.getFloat());
    }

    public void testDoubleEndianess() {
        BuffersBuffer buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.putDouble(1.0d);
        buffer.flip();
        assertEquals("big endian", 1.0d, buffer.getDouble());
        buffer = createOneSevenBuffer(mm);
        assertTrue(buffer.order() == ByteOrder.BIG_ENDIAN);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(buffer.order() == ByteOrder.LITTLE_ENDIAN);
        buffer.putDouble(1.0d);
        buffer.flip();
        assertEquals("little endian", 1.0d, buffer.getDouble());
    }



    // --------------------------------------------------------- Private Methods


    private static BuffersBuffer createOneSevenBuffer(final MemoryManager mm) {
        final BuffersBuffer b = BuffersBuffer.create(mm);
        b.append(new ByteBufferWrapper(ByteBuffer.allocate(1)));
        b.append(new ByteBufferWrapper(ByteBuffer.allocate(7)));
        return b;
    }
}
