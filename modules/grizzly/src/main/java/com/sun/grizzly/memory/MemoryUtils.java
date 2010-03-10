/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */

package com.sun.grizzly.memory;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.TransportFactory;

/**
 * Class has useful methods to simplify the work with {@link Buffer}s.
 *
 * @see MemoryManager
 * @see WrapperAware
 * 
 * @author Alexey Stashok
 */
public class MemoryUtils {
    public static ByteBuffer allocateByteBuffer(MemoryManager memoryManager,
            int size) {
        if (memoryManager instanceof ByteBufferAware) {
            return ((ByteBufferAware) memoryManager).allocateByteBuffer(size);
        }

        return ByteBuffer.allocate(size);
    }

    public static ByteBuffer reallocateByteBuffer(MemoryManager memoryManager,
            ByteBuffer oldByteBuffer, int size) {
        if (memoryManager instanceof ByteBufferAware) {
            return ((ByteBufferAware) memoryManager).reallocateByteBuffer(
                    oldByteBuffer, size);
        }

        return ByteBuffer.allocate(size);
    }

    public static void releaseByteBuffer(MemoryManager memoryManager,
            ByteBuffer byteBuffer) {
        if (memoryManager instanceof ByteBufferAware) {
            ((ByteBufferAware) memoryManager).releaseByteBuffer(byteBuffer);
        }
    }


    /**
     * Returns {@link Buffer}, which wraps the {@link String}.
     *
     * @param memoryManager {@link MemoryManager}, which should be
     *                       used for wrapping.
     * @param s {@link String}
     *
     * @return {@link Buffer} wrapper on top of passed {@link String}.
     */
    public static <E extends Buffer> E wrap(MemoryManager<E> memoryManager,
            String s) {
        return MemoryUtils.wrap(memoryManager, s, Charset.defaultCharset());
    }

    /**
     * Returns {@link Buffer}, which wraps the {@link String} with the specific
     * {@link Charset}.
     *
     * @param memoryManager {@link MemoryManager}, which should be
     *                       used for wrapping.
     * @param s {@link String}
     * @param charset {@link Charset}, which will be used, when converting
     * {@link String} to byte array.
     *
     * @return {@link Buffer} wrapper on top of passed {@link String}.
     */
    public static <E extends Buffer> E wrap(MemoryManager<E> memoryManager,
            String s, Charset charset) {
        try {
            byte[] byteRepresentation = s.getBytes(charset.name());
            return MemoryUtils.wrap(memoryManager, byteRepresentation);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns {@link Buffer}, which wraps the byte array.
     *
     * @param memoryManager {@link MemoryManager}, which should be
     *                       used for wrapping.
     * @param data byte array to wrap.
     *
     * @return {@link Buffer} wrapper on top of passed byte array.
     */
    public static <E extends Buffer> E wrap(MemoryManager<E> memoryManager,
            byte[] array) {
        return wrap(memoryManager, array, 0, array.length);
    }

    /**
     * Returns {@link Buffer}, which wraps the part of byte array with
     * specific offset and length.
     *
     * @param memoryManager {@link MemoryManager}, which should be
     *                       used for wrapping.
     * @param data byte array to wrap
     * @param offset byte buffer offset
     * @param length byte buffer length
     *
     * @return {@link Buffer} wrapper on top of passed byte array.
     */
    public static <E extends Buffer> E wrap(MemoryManager<E> memoryManager,
            byte[] array, int offset, int length) {
        if (memoryManager == null) {
            memoryManager = TransportFactory.getInstance().getDefaultMemoryManager();
        }
        
        if (memoryManager instanceof WrapperAware) {
            return ((WrapperAware<E>) memoryManager).wrap(array, offset, length);
        }
        
        E buffer = memoryManager.allocate(length);
        buffer.put(array, offset, length);
        buffer.flip();
        return buffer;
    }

    /**
     * Returns {@link Buffer}, which wraps the {@link ByteBuffer}.
     *
     * @param memoryManager {@link MemoryManager}, which should be
     *                       used for wrapping.
     * @param byteBuffer {@link ByteBuffer} to wrap
     *
     * @return {@link Buffer} wrapper on top of passed {@link ByteBuffer}.
     */
    public static <E extends Buffer> E wrap(MemoryManager<E> memoryManager,
            ByteBuffer byteBuffer) {
        if (memoryManager instanceof WrapperAware) {
            return ((WrapperAware<E>) memoryManager).wrap(byteBuffer);
        } else if (byteBuffer.hasArray()) {
            return wrap(memoryManager, byteBuffer.array(),
                    byteBuffer.arrayOffset() + byteBuffer.position(),
                    byteBuffer.remaining());
        }

        throw new IllegalStateException("Can not wrap ByteBuffer");
    }
}
