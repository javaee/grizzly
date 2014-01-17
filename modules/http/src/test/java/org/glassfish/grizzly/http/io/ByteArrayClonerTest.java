/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.io;

import java.util.Arrays;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test {@link OutputBuffer#ByteArrayCloner}.
 * 
 * @author Alexey Stashok
 */
public class ByteArrayClonerTest {
    private static final int EXTRA_BUFFER_SIZE = 1024;
    private static final int TEMP_BUFFER_SIZE = 2048;
    
    private final MemoryManager mm = MemoryManager.DEFAULT_MEMORY_MANAGER;
    
    @Test
    public void testSimpleBuffer() {
        
        TemporaryHeapBuffer buffer = new TemporaryHeapBuffer();
        
        // Test simple buffer with offset = 0
        byte[] array = new byte[TEMP_BUFFER_SIZE];
        buffer.reset(array, 0, array.length);
        
        fill(buffer);
        OutputBuffer.ByteArrayCloner cloner =
                new OutputBuffer.ByteArrayCloner(buffer);
        
        Buffer newBuffer = cloner.clone0(mm, buffer);
        clean(array);
        checkContent(newBuffer, 'A');
        
        
        // Test simple buffer with offset != 0
        
        array = new byte[TEMP_BUFFER_SIZE];
        int offset = 1111;
        buffer.reset(array, offset, array.length - offset);
        
        fill(buffer);
        cloner = new OutputBuffer.ByteArrayCloner(buffer);
        
        newBuffer = cloner.clone0(mm, buffer);
        clean(array);
        assertEquals(array.length - offset, newBuffer.remaining());
        checkContent(newBuffer, 'A');
        
        // Test simple buffer with offset != 0 and position > 0
        
        array = new byte[TEMP_BUFFER_SIZE];
        offset = 1111;
        int position = 15;
        
        buffer.reset(array, offset, array.length - offset);
        
        fill(buffer);
        buffer.position(buffer.position() + position);
        
        cloner = new OutputBuffer.ByteArrayCloner(buffer);
        
        newBuffer = cloner.clone0(mm, buffer);
        clean(array);
        assertEquals(array.length - offset - position, newBuffer.remaining());
        checkContent(newBuffer, 'A' + position);
    }
    
    @Test
    public void testSingleElementCompositeBuffer() {
        CompositeBuffer cb = CompositeBuffer.newBuffer();
        TemporaryHeapBuffer buffer = new TemporaryHeapBuffer();
        
        // Test composite buffer with offset = 0
        byte[] array = new byte[TEMP_BUFFER_SIZE];
        buffer.reset(array, 0, array.length);
        cb.append(buffer);
        
        fill(cb);
        OutputBuffer.ByteArrayCloner cloner =
                new OutputBuffer.ByteArrayCloner(buffer);
        
        Buffer newBuffer = cloner.clone0(mm, cb);
        clean(array);
        checkContent(newBuffer, 'A');
        
        
        // Test composite buffer with offset != 0
        
        cb = CompositeBuffer.newBuffer();

        array = new byte[TEMP_BUFFER_SIZE];
        int offset = 1111;
        buffer.reset(array, offset, array.length - offset);
        cb.append(buffer);
        
        fill(cb);
        cloner = new OutputBuffer.ByteArrayCloner(buffer);
        
        newBuffer = cloner.clone0(mm, cb);
        clean(array);
        assertEquals(array.length - offset, newBuffer.remaining());
        checkContent(newBuffer, 'A');
        
        // Test composite buffer with offset != 0 and position > 0
        
        cb = CompositeBuffer.newBuffer();

        array = new byte[TEMP_BUFFER_SIZE];
        offset = 1111;
        int position = 15;
        
        buffer.reset(array, offset, array.length - offset);
        cb.append(buffer);
        
        fill(cb);
        cb.position(cb.position() + position);
        
        cloner = new OutputBuffer.ByteArrayCloner(buffer);
        
        newBuffer = cloner.clone0(mm, cb);
        clean(array);
        assertEquals(array.length - offset - position, newBuffer.remaining());
        checkContent(newBuffer, 'A' + position);
    }
    
    /**
     * T - stands for TemporaryHeapBuffer
     * B - stands for any other Buffer
     */
    @Test
    public void testBTBCompositeBuffer() {
        CompositeBuffer cb = CompositeBuffer.newBuffer();
        TemporaryHeapBuffer buffer = new TemporaryHeapBuffer();
        
        // Test position = 0
        byte[] array = new byte[TEMP_BUFFER_SIZE];
        buffer.reset(array, 0, array.length);
        cb.append(MemoryManager.DEFAULT_MEMORY_MANAGER.allocate(EXTRA_BUFFER_SIZE));
        cb.append(buffer);
        cb.append(MemoryManager.DEFAULT_MEMORY_MANAGER.allocate(EXTRA_BUFFER_SIZE));
        
        fill(cb);
        OutputBuffer.ByteArrayCloner cloner =
                new OutputBuffer.ByteArrayCloner(buffer);
        
        Buffer newBuffer = cloner.clone0(mm, cb);
        clean(array);
        checkContent(newBuffer, 'A');
        
        // Test position in the middle of the first B
        cb = CompositeBuffer.newBuffer();
        array = new byte[TEMP_BUFFER_SIZE];
        int position = 15;

        buffer.reset(array, 0, array.length);
        cb.append(MemoryManager.DEFAULT_MEMORY_MANAGER.allocate(EXTRA_BUFFER_SIZE));
        cb.append(buffer);
        cb.append(MemoryManager.DEFAULT_MEMORY_MANAGER.allocate(EXTRA_BUFFER_SIZE));

        fill(cb);
        cb.position(cb.position() + position);
        
        cloner = new OutputBuffer.ByteArrayCloner(buffer);
        
        newBuffer = cloner.clone0(mm, cb);
        clean(array);
        
        assertEquals(TEMP_BUFFER_SIZE + EXTRA_BUFFER_SIZE * 2 - position, newBuffer.remaining());
        checkContent(newBuffer, 'A' + position);
        
        // Test position in the middle of T
        cb = CompositeBuffer.newBuffer();
        array = new byte[TEMP_BUFFER_SIZE];
        position = EXTRA_BUFFER_SIZE + 15;

        buffer.reset(array, 0, array.length);
        cb.append(MemoryManager.DEFAULT_MEMORY_MANAGER.allocate(EXTRA_BUFFER_SIZE));
        cb.append(buffer);
        cb.append(MemoryManager.DEFAULT_MEMORY_MANAGER.allocate(EXTRA_BUFFER_SIZE));

        fill(cb);
        cb.position(cb.position() + position);
        
        cloner = new OutputBuffer.ByteArrayCloner(buffer);
        
        newBuffer = cloner.clone0(mm, cb);
        clean(array);
        
        assertEquals(TEMP_BUFFER_SIZE + EXTRA_BUFFER_SIZE * 2 - position, newBuffer.remaining());
        checkContent(newBuffer, 'A' + position);
        
        // Test position in the middle of the second B
        cb = CompositeBuffer.newBuffer();
        array = new byte[TEMP_BUFFER_SIZE];
        position = EXTRA_BUFFER_SIZE + TEMP_BUFFER_SIZE + 15;

        buffer.reset(array, 0, array.length);
        cb.append(MemoryManager.DEFAULT_MEMORY_MANAGER.allocate(EXTRA_BUFFER_SIZE));
        cb.append(buffer);
        cb.append(MemoryManager.DEFAULT_MEMORY_MANAGER.allocate(EXTRA_BUFFER_SIZE));

        fill(cb);
        cb.position(cb.position() + position);
        
        cloner = new OutputBuffer.ByteArrayCloner(buffer);
        
        newBuffer = cloner.clone0(mm, cb);
        clean(array);
        
        assertEquals(TEMP_BUFFER_SIZE + EXTRA_BUFFER_SIZE * 2 - position, newBuffer.remaining());
        checkContent(newBuffer, 'A' + position);               
    }
    
    @Test
    public void testTSplit() {
        int splitPos = TEMP_BUFFER_SIZE / 4;
        
        // Test offset = 0
        TemporaryHeapBuffer t1 = new TemporaryHeapBuffer();
        
        byte[] array = fill(new byte[TEMP_BUFFER_SIZE]);
        t1.reset(array, 0, array.length);
        
        Buffer t2 = t1.split(splitPos);
        clean(array);
        
        CompositeBuffer cb = CompositeBuffer.newBuffer();
        cb.append(t1);
        cb.append(t2);

        checkContent(cb, 'A');               
        
        // Test with offset
        
        int offset = 345;
        t1 = new TemporaryHeapBuffer();
        
        array = fill(new byte[TEMP_BUFFER_SIZE]);
        t1.reset(array, offset, array.length - offset);
        
        t2 = t1.split(splitPos);
        clean(array);
        
        cb = CompositeBuffer.newBuffer();
        cb.append(t1);
        cb.append(t2);

        assertEquals(TEMP_BUFFER_SIZE - offset, cb.remaining());
        checkContent(cb, 'A' + offset);               
        
    }
    
    private void checkContent(final Buffer b,
            final int startSym) {
        
        final int pos = b.position();
        int a = (startSym - 'A') % ('Z' - 'A');
        while (b.hasRemaining()) {
            assertEquals((char) ('A' + a), (char) b.get());
            a = (++a) % ('Z' - 'A');
        }
        
        b.position(pos);
        
    }
    
    private Buffer fill(final Buffer b) {
        final int pos = b.position();
        int a = 0;
        while (b.hasRemaining()) {
            b.put((byte) ('A' + a));
            a = (++a) % ('Z' - 'A');
        }
        
        b.position(pos);
        
        return b;
    }
    
    private byte[] fill(final byte[] array) {
        int a = 0;
        for (int i = 0; i < array.length; i++) {
            array[i] = ((byte) ('A' + a));
            a = (++a) % ('Z' - 'A');
        }
        
        return array;
    }
    
    
    private void clean(final byte[] array) {
        Arrays.fill(array, 0, array.length, (byte) 0);
    }

    private void clean(final TemporaryHeapBuffer b) {
        final int pos = b.position();
        int a = 0;
        while (b.hasRemaining()) {
            b.put((byte) 0);
        }
        
        b.position(pos);
    }
}
