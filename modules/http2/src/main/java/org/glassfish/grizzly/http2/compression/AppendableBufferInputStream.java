/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http2.compression;

import java.io.IOException;
import java.io.InputStream;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * {@link InputStream} implementation backed by Grizzly {@link Buffer}.
 *
 * @author Alexey Stashok
 */
final class AppendableBufferInputStream extends InputStream {
    private final MemoryManager memoryManager;
    private Buffer buffer;
    
    
    /**
     * Creates the {@link InputStream} backed by Grizzly {@link Buffer}.
     * Constructed <tt>AppendableBufferInputStream</tt> read operations will affect the
     * passed {@link Buffer} position, which means each <tt>BufferInputStream</tt>
     * read operation will shift {@link Buffer}'s position by number of bytes,
     * which were read.
     *
     * @param memoryManager
     */
    public AppendableBufferInputStream(final MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }
    
    /**
     * Creates the {@link InputStream} backed by Grizzly {@link Buffer}.
     * Constructed <tt>AppendableBufferInputStream</tt> read operations will affect the
     * passed {@link Buffer} position, which means each <tt>BufferInputStream</tt>
     * read operation will shift {@link Buffer}'s position by number of bytes,
     * which were read.
     *
     * @param memoryManager
     * @param buffer
     */
    public AppendableBufferInputStream(final MemoryManager memoryManager,
            final Buffer buffer) {
        this.memoryManager = memoryManager;
        this.buffer = buffer;
    }

    /**
     * Appends the {@link Buffer} to the end of the input stream.
     * 
     * @param buffer the {@link Buffer} to append
     */
    public void append(final Buffer buffer) {
        this.buffer = Buffers.appendBuffers(memoryManager, this.buffer, buffer);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int read() throws IOException {
        if (!buffer.hasRemaining()) {
            return -1;
        }

        return buffer.get() & 0xFF;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(final byte[] b, final int off, final int len)
            throws IOException {
        if (!buffer.hasRemaining()) {
            return -1;
        }

        final int length = Math.min(len, available());

        buffer.get(b, off, length);

        return length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int available() throws IOException {
        return buffer.remaining();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(final long n) throws IOException {
        final int skipped = (int) Math.min(n, available());

        buffer.position(buffer.position() + skipped);
        return skipped;
    }
    
    public void cleanup() {
        buffer.dispose();
        buffer = null;
    }
}
