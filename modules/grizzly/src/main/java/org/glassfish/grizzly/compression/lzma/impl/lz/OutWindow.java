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

package org.glassfish.grizzly.compression.lzma.impl.lz;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.MemoryManager;

import java.io.IOException;
import org.glassfish.grizzly.compression.lzma.LZMADecoder;

/**
 * OutWindow
 *
 * @author Igor Pavlov
 */
public class OutWindow {

    LZMADecoder.LZMAInputState _decoderState;
    byte[] _buffer;
    int _pos;
    int _windowSize = 0;
    int _streamPos;

    public void create(int windowSize) {
        if (_buffer == null || _windowSize != windowSize) {
            _buffer = new byte[windowSize];
        }
        _windowSize = windowSize;
        _pos = 0;
        _streamPos = 0;
    }

    public void initFromState(LZMADecoder.LZMAInputState decoderState) throws IOException {
        _decoderState = decoderState;
    }

    public void releaseBuffer() throws IOException {
        //Flush();
        _decoderState = null;
    }

    public void init(boolean solid) {
        if (!solid) {
            _streamPos = 0;
            _pos = 0;
        }
    }

    public void flush() throws IOException {
        int size = _pos - _streamPos;
        if (size == 0) {
            return;
        }

        Buffer dst = _decoderState.getDst();

        if (dst == null || dst.remaining() < size) {
            dst = resizeBuffer(_decoderState.getMemoryManager(), dst, size);
            _decoderState.setDst(dst);
        }
        dst.put(_buffer, _streamPos, size);
        dst.trim();
        dst.position(dst.limit());
        
        if (_pos >= _windowSize) {
            _pos = 0;
        }
        _streamPos = _pos;
    }

    public void copyBlock(int distance, int len) throws IOException {
        int pos = _pos - distance - 1;
        if (pos < 0) {
            pos += _windowSize;
        }
        for (; len != 0; len--) {
            if (pos >= _windowSize) {
                pos = 0;
            }
            _buffer[_pos++] = _buffer[pos++];
            if (_pos >= _windowSize) {
                flush();
            }
        }
    }

    public void putByte(byte b) throws IOException {
        _buffer[_pos++] = b;
        if (_pos >= _windowSize) {
            flush();
        }
    }

    public byte getByte(int distance) {
        int pos = _pos - distance - 1;
        if (pos < 0) {
            pos += _windowSize;
        }
        return _buffer[pos];
    }

    @SuppressWarnings({"unchecked"})
    private static Buffer resizeBuffer(final MemoryManager memoryManager,
                                         final Buffer buffer, final int grow) {
        if (buffer == null) {
            return memoryManager.allocate(Math.max(grow, 4096));
        }

        return memoryManager.reallocate(buffer, Math.max(
                buffer.capacity() + grow,
                (buffer.capacity() * 3) / 2 + 1));
    }

}
