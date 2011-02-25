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
package org.glassfish.grizzly.compression.lzma;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

import java.io.IOException;
import java.io.OutputStream;

/**
 * As the current LZMA implementation is stream-based, this class wraps
 * Grizzly {@link Buffer} and exposes it via {@link java.io.OutputStream}
 * operations.
 */
class BufferOutputStream extends OutputStream implements Cacheable {

    /**
     * Wrapped Buffer.
     */
    private Buffer dst;
    private MemoryManager manager;


    // ---------------------------------------------------------- Public Methods


    /**
     * Set the {@link Buffer} to expose as an {@link java.io.OutputStream}
     * @param dst the {@link Buffer}.
     */
    public void setBuffer(Buffer dst, MemoryManager manager) {
        this.dst = dst;
        this.manager = manager;
    }


    public Buffer getBuffer() {
        return dst;
    }


    // -------------------------------------------------- Methods from Cacheable


    /**
     * Resets the stream for re-use.
     */
    @Override
    public void recycle() {
        dst = null;
        manager = null;
    }


    // ----------------------------------------------- Methods from OutputStream


    /**
     * {@inheritDoc}
     */
    @Override
    public void write(int b)
            throws IOException {

        dst.put((byte) (b & 0xff));

    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void write(byte[] b, int off, int len)
            throws IOException {

        dst.put(b, off, len);

    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() throws IOException {
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
    }

}
