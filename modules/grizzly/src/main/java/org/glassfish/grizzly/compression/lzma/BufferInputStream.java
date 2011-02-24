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

import java.io.IOException;
import java.io.InputStream;

/**
 * As the current LZMA implementation is stream-based, this class wraps
 * Grizzly {@link Buffer} and exposes it via {@link InputStream} operations.
 */
class BufferInputStream extends InputStream implements Cacheable {

    /**
     * Wrapped Buffer.
     */
    private Buffer src;


    // ---------------------------------------------------------- Public Methods


    /**
     * Set the {@link Buffer} to expose as an {@link InputStream}
     * @param src the {@link Buffer}.
     */
    public void setBuffer(Buffer src) {
        this.src = src;
    }


    // -------------------------------------------------- Methods from Cacheable


    /**
     * Resets the stream for re-use.
     */
    @Override
    public void recycle() {
        src = null;
    }


    // ----------------------------------------------- Methods from OutputStream


    /**
     * @{inheritDoc}
     */
    @Override
    public int read() throws IOException {
        if (!src.hasRemaining()) {
            return -1;
        }
        return (src.get() & 0xff);
    }

    /**
     * @{inheritDoc}
     */
    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * @{inheritDoc}
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (!src.hasRemaining()) {
            return -1;
        }
        final int cur = src.remaining();
        src.get(b, off, Math.min(src.remaining(), len));
        return (cur - src.remaining());
    }

    /**
     * This method is a no-op.
     */
    @Override
    public void close() throws IOException {
    }


}
