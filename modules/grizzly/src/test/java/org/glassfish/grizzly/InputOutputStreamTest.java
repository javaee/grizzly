/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.Arrays;
import org.glassfish.grizzly.memory.MemoryManager;
import org.junit.Test;
import org.glassfish.grizzly.utils.BufferInputStream;
import org.glassfish.grizzly.utils.BufferOutputStream;
import static org.junit.Assert.*;

/**
 * Testing {@link BufferInputStream} and {@link BufferOutputStream}.
 * 
 * @author Alexey Stashok
 */
public class InputOutputStreamTest {
    @Test
    public void testInputStream() throws IOException {
        final MemoryManager mm = MemoryManager.DEFAULT_MEMORY_MANAGER;
        
        Buffer b = mm.allocate(10);
        b.put((byte) 0x1);
        b.put((byte) 0xFF);
        
        byte[] bytes = new byte[] {(byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5,
            (byte) 6, (byte) 7, (byte) 8};
        
        b.put(bytes);
        
        b.flip();
        
        BufferInputStream bis = new BufferInputStream(b);
        
        assertEquals(0x1, bis.read());
        assertEquals(0xFF, bis.read());
        
        byte[] readBytes = new byte[bytes.length];
        bis.read(readBytes);
        
        assertArrayEquals(bytes, readBytes);
    }
    
    @Test
    public void testOutputStreamReallocate() throws IOException {
        final MemoryManager mm = MemoryManager.DEFAULT_MEMORY_MANAGER;
        
        final byte[] initialBytes = "initial info".getBytes("ASCII");
        final Buffer initialBuffer = mm.allocate(initialBytes.length * 2);
        initialBuffer.put(initialBytes);
        
        BufferOutputStream bos = new BufferOutputStream(mm, initialBuffer, true);
        
        for (int i = 0; i < 9; i++) {
            final byte[] b = new byte[32768];
            Arrays.fill(b, (byte) i);
            bos.write(b);
        }
        
        bos.close();
        
        final Buffer resultBuffer = bos.getBuffer().flip();
        
        byte[] initialCheckBytes = new byte[initialBytes.length];
        resultBuffer.get(initialCheckBytes);
        assertArrayEquals(initialBytes, initialCheckBytes);
        
        for (int i = 0; i < 9; i++) {
            final byte[] pattern = new byte[32768];
            Arrays.fill(pattern, (byte) i);
            
            final byte[] b = new byte[32768];
            resultBuffer.get(b);
            assertArrayEquals(pattern, b);
        }
    }
    
    @Test
    public void testOutputStreamWithoutReallocate() throws IOException {
        final MemoryManager mm = MemoryManager.DEFAULT_MEMORY_MANAGER;
        
        final byte[] initialBytes = "initial info".getBytes("ASCII");
        final Buffer initialBuffer = mm.allocate(initialBytes.length * 2);
        initialBuffer.put(initialBytes);
        
        BufferOutputStream bos = new BufferOutputStream(mm, initialBuffer, false);
        
        for (int i = 0; i < 9; i++) {
            final byte[] b = new byte[32768];
            Arrays.fill(b, (byte) i);
            bos.write(b);
        }
        
        bos.close();
        
        final Buffer resultBuffer = bos.getBuffer().flip();
        
        assertTrue(resultBuffer.isComposite());
        
        byte[] initialCheckBytes = new byte[initialBytes.length];
        resultBuffer.get(initialCheckBytes);
        assertArrayEquals(initialBytes, initialCheckBytes);
        
        for (int i = 0; i < 9; i++) {
            final byte[] pattern = new byte[32768];
            Arrays.fill(pattern, (byte) i);
            
            final byte[] b = new byte[32768];
            resultBuffer.get(b);
            assertArrayEquals(pattern, b);
        }
    }     
}
