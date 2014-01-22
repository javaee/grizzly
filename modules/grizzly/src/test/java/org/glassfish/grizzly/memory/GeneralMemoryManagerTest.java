/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import org.glassfish.grizzly.Buffer;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class GeneralMemoryManagerTest extends AbstractMemoryManagerTest {


    // ------------------------------------------------------------ Constructors


    public GeneralMemoryManagerTest(int mmType) {
        super(mmType);
    }


    // ------------------------------------------------------------ Test Methods

    @Test
    public void testBufferEquals() {
        final HeapMemoryManager hmm = new HeapMemoryManager();
        final ByteBufferManager bbm = new ByteBufferManager();
        final PooledMemoryManager pmm = new PooledMemoryManager();

        Buffer[] buffers = new Buffer[4];
        buffers[0] = Buffers.wrap(hmm, "Value#1");
        buffers[1] = Buffers.wrap(bbm, "Value#1");
        buffers[2] = Buffers.wrap(pmm, "Value#1");

        Buffer b11 = Buffers.wrap(hmm, "Val");
        Buffer b12 = Buffers.wrap(bbm, "ue");
        Buffer b13 = Buffers.wrap(pmm, "#1");

        Buffer tmp = Buffers.appendBuffers(bbm, b11, b12);
        buffers[3] = Buffers.appendBuffers(bbm, tmp, b13);

        for (int i = 0; i < buffers.length; i++) {
            for (int j = 0; j < buffers.length; j++) {
                assertEquals(buffers[i], buffers[j]);
            }
        }
    }

    @Test
    public void testBufferPut() {
        final Buffer b = mm.allocate(127);
        if (!(b instanceof HeapBuffer)) {
            return;
        }

        int i = 0;
        while (b.hasRemaining()) {
            b.put((byte) i++);
        }

        b.flip();

        b.put(b, 10, 127 - 10).flip();


        assertEquals(127 - 10, b.remaining());

        i = 10;
        while (b.hasRemaining()) {
            assertEquals(i++, b.get());
        }
    }

    @Test
    public void testBufferSlice() {
        Buffer b = mm.allocate(10);
        b.putInt(1);
        ByteBuffer bb = b.slice().toByteBuffer().slice();
        bb.rewind();
        bb.putInt(2);
        b.rewind();
        assertEquals(1, b.getInt());
    }
}
