/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.utils;

import java.io.IOException;
import java.io.InputStream;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.Buffers;

/**
 * {@link InputStream} implementation over Grizzly {@link Buffer}.
 *
 * @author Alexey Stashok
 */
public class BufferInputStream extends InputStream {

    private final Buffer buffer;

    private final boolean isMovingPosition;
    
    private int position;
    private final int limit;
    
    /**
     * Create the {@link InputStream} over Grizzly {@link Buffer}.
     * Constructed <tt>BufferInputStream</tt> read operations will affect the
     * passed {@link Buffer} position, which means each <tt>BufferInputStream</tt>
     * read operation will shift {@link Buffer}'s position by number of bytes,
     * which were read.
     *
     * @param buffer
     */
    public BufferInputStream(final Buffer buffer) {
        isMovingPosition = true;
        this.buffer = buffer;
        this.position = buffer.position();
        this.limit = buffer.limit();
    }

    /**
     * Create the {@link InputStream} over Grizzly {@link Buffer}.
     * Constructed <tt>BufferInputStream</tt> read operations will *not* affect
     * the passed {@link Buffer} position, which means the passed {@link Buffer}
     * position will never be changed during <tt>BufferInputStream</tt>
     *
     * @param buffer
     */
    public BufferInputStream(final Buffer buffer, final int position,
            final int limit) {
        isMovingPosition = false;
        this.buffer = buffer;
        this.position = position;
        this.limit = limit;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int read() throws IOException {
        if (position >= limit) {
            return -1;
        }

        final int result = buffer.get(position++) & 0xFF;

        if (isMovingPosition) {
            buffer.position(position);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(final byte[] b, final int off, final int len)
            throws IOException {
        if (position >= limit) {
            return -1;
        }

        final int length = Math.min(len, available());

        final int oldPos = buffer.position();
        final int oldLim = buffer.limit();

        if (!isMovingPosition) {
            Buffers.setPositionLimit(buffer, position, limit);
        }
        
        try {
            buffer.get(b, off, length);
        } finally {
            if (!isMovingPosition) {
                Buffers.setPositionLimit(buffer, oldPos, oldLim);
            }
        }

        position += length;

        return length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int available() throws IOException {
        return limit - position;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(final long n) throws IOException {
        final int skipped = (int) Math.min(n, available());

        position += skipped;
        if (isMovingPosition) {
            buffer.position(position);
        }
        
        return skipped;
    }
}
