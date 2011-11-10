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


package org.glassfish.grizzly.http.util;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * General HttpCodec utility methods.
 * 
 * @author Alexey Stashok
 */
public class HttpCodecUtils {
    public static Buffer getLongAsBuffer(final MemoryManager memoryManager,
            final long length) {
        final Buffer b = memoryManager.allocate(20);
        b.allowBufferDispose(true);
        HttpUtils.longToBuffer(length, b);
        return b;
    }

    public static Buffer put(final MemoryManager memoryManager,
            Buffer dstBuffer, final DataChunk chunk) {

        if (chunk.isNull()) return dstBuffer;

        if (chunk.getType() == DataChunk.Type.Buffer) {
            final BufferChunk bc = chunk.getBufferChunk();
            final int length = bc.getLength();
            if (dstBuffer.remaining() < length) {
                dstBuffer = resizeBuffer(memoryManager, dstBuffer, length);
            }

            dstBuffer.put(bc.getBuffer(), bc.getStart(), length);
            
            return dstBuffer;
        } else {
            return put(memoryManager, dstBuffer, chunk.toString());
        }
    }

    public static Buffer put(final MemoryManager memoryManager,
            Buffer dstBuffer, final String s) {
        final int size = s.length();
        if (dstBuffer.remaining() < size) {
            dstBuffer = resizeBuffer(memoryManager, dstBuffer, size);
        }

        // Make sure custom Strings do not contain service symbols
        final int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if ((c <= 31 && c != 9) || c == 127) {
                c = ' ';
            }

            final byte b = (byte) c;
            
            dstBuffer.put(b);
        }
        
//        dstBuffer.put8BitString(s);

        return dstBuffer;
    }

    public static Buffer put(final MemoryManager memoryManager,
            Buffer dstBuffer, final byte[] array) {

        if (dstBuffer.remaining() < array.length) {
            dstBuffer = resizeBuffer(memoryManager, dstBuffer, array.length);
        }

        dstBuffer.put(array);

        return dstBuffer;
    }

    public static Buffer put(final MemoryManager memoryManager,
            Buffer dstBuffer, final Buffer buffer) {
        
        final int addSize = buffer.remaining();

        if (dstBuffer.remaining() < addSize) {
            dstBuffer = resizeBuffer(memoryManager, dstBuffer, addSize);
        }

        dstBuffer.put(buffer);

        return dstBuffer;
    }
    
    public static Buffer put(final MemoryManager memoryManager,
            Buffer dstBuffer, final byte value) {

        if (!dstBuffer.hasRemaining()) {
            dstBuffer = resizeBuffer(memoryManager, dstBuffer, 1);
        }

        dstBuffer.put(value);

        return dstBuffer;
    }

    @SuppressWarnings({"unchecked"})
    public static Buffer resizeBuffer(final MemoryManager memoryManager,
            final Buffer buffer, final int grow) {

        return memoryManager.reallocate(buffer, Math.max(
                buffer.capacity() + grow,
                (buffer.capacity() * 3) / 2 + 1));
    }
}
