/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import org.glassfish.grizzly.Buffer;

/**
 * Read-only {@link HeapBuffer implementation}.
 *
 * @since 2.0
 */
class ReadOnlyHeapBuffer extends HeapBuffer {


    // ------------------------------------------------------------ Constructors


    ReadOnlyHeapBuffer(byte[] heap, int offset, int cap) {
        super(heap, offset, cap);
    }


    // ------------------------------------------------- Methods from HeapBuffer


    @Override
    public HeapBuffer asReadOnlyBuffer() {
        return this;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public HeapBuffer prepend(Buffer header) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer put(int index, byte b) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer put(Buffer src) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer put(Buffer src, int position, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer put(ByteBuffer src) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer put(ByteBuffer src, int position, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer put(byte b) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer put(byte[] src) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer put(byte[] src, int offset, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer putChar(char value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer putChar(int index, char value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer putShort(short value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer putShort(int index, short value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer putInt(int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer putInt(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer putLong(long value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer putLong(int index, long value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer putFloat(float value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer putFloat(int index, float value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer putDouble(double value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public HeapBuffer putDouble(int index, double value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    protected HeapBuffer createHeapBuffer(final int offset, final int capacity) {
        return new ReadOnlyHeapBuffer(heap, offset, capacity);
    }

    @Override
    public ByteBuffer toByteBuffer() {
        return super.toByteBuffer().asReadOnlyBuffer();
    }

    @Override
    public ByteBuffer toByteBuffer(int position, int limit) {
        return super.toByteBuffer(position, limit).asReadOnlyBuffer();
    }
}
