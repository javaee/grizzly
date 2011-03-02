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

import java.io.IOException;

/**
 * InWindow
 *
 * @author Igor Pavlov
 */
public class InWindow {

    public byte[] _bufferBase; // pointer to buffer with data
    Buffer _buffer;
    int _posLimit;  // offset (from _buffer) of first byte when new block reading must be done
    boolean _streamEndWasReached; // if (true) then _streamPos shows real end of stream
    int _pointerToLastSafePosition;
    public int _bufferOffset;
    public int _blockSize;  // Size of Allocated memory block
    public int _pos;             // offset (from _buffer) of curent byte
    int _keepSizeBefore;  // how many BYTEs must be kept in buffer before _pos
    int _keepSizeAfter;   // how many BYTEs must be kept buffer after _pos
    public int _streamPos;   // offset (from _buffer) of first not read byte from Stream

    public void moveBlock() {
        int offset = _bufferOffset + _pos - _keepSizeBefore;
        // we need one additional byte, since movePos moves on 1 byte.
        if (offset > 0) {
            offset--;
        }

        int numBytes = _bufferOffset + _streamPos - offset;

        // check negative offset ????
        for (int i = 0; i < numBytes; i++) {
            _bufferBase[i] = _bufferBase[offset + i];
        }
        _bufferOffset -= offset;
    }

    public void readBlock() throws IOException {
        if (_streamEndWasReached) {
            return;
        }
        while (true) {
            int size = (0 - _bufferOffset) + _blockSize - _streamPos;
            if (size == 0) {
                return;
            }
            int pos = _buffer.position();
            size = Math.min(size, _buffer.remaining());
            _buffer.get(_bufferBase, _bufferOffset + _streamPos, size);
            int numReadBytes = _buffer.position() - pos;
            if (numReadBytes == 0) {
                _posLimit = _streamPos;
                int pointerToPostion = _bufferOffset + _posLimit;
                if (pointerToPostion > _pointerToLastSafePosition) {
                    _posLimit = _pointerToLastSafePosition - _bufferOffset;
                }

                _streamEndWasReached = true;
                return;
            }
            _streamPos += numReadBytes;
            if (_streamPos >= _pos + _keepSizeAfter) {
                _posLimit = _streamPos - _keepSizeAfter;
            }
        }
    }

    void free() {
        _bufferBase = null;
    }

    public void create(int keepSizeBefore, int keepSizeAfter, int keepSizeReserv) {
        _keepSizeBefore = keepSizeBefore;
        _keepSizeAfter = keepSizeAfter;
        int blockSize = keepSizeBefore + keepSizeAfter + keepSizeReserv;
        if (_bufferBase == null || _blockSize != blockSize) {
            free();
            _blockSize = blockSize;
            _bufferBase = new byte[_blockSize];
        }
        _pointerToLastSafePosition = _blockSize - keepSizeAfter;
    }

    public void setBuffer(Buffer buffer) {
        _buffer = buffer;
    }

    public void releaseBuffer() {
        _buffer = null;
    }

    public void init() throws IOException {
        _bufferOffset = 0;
        _pos = 0;
        _streamPos = 0;
        _streamEndWasReached = false;
        readBlock();
    }

    public void movePos() throws IOException {
        _pos++;
        if (_pos > _posLimit) {
            int pointerToPostion = _bufferOffset + _pos;
            if (pointerToPostion > _pointerToLastSafePosition) {
                moveBlock();
            }
            readBlock();
        }
    }

    public byte getIndexByte(int index) {
        return _bufferBase[_bufferOffset + _pos + index];
    }

    // index + limit have not to exceed _keepSizeAfter;
    public int getMatchLen(int index, int distance, int limit) {
        if (_streamEndWasReached) {
            if ((_pos + index) + limit > _streamPos) {
                limit = _streamPos - (_pos + index);
            }
        }
        distance++;
        // Byte *pby = _buffer + (size_t)_pos + index;
        int pby = _bufferOffset + _pos + index;

        int i;
        for (i = 0; i < limit && _bufferBase[pby + i] == _bufferBase[pby + i - distance]; i++);
        return i;
    }

    public int getNumAvailableBytes() {
        return _streamPos - _pos;
    }

    public void reduceOffsets(int subValue) {
        _bufferOffset += subValue;
        _posLimit -= subValue;
        _pos -= subValue;
        _streamPos -= subValue;
    }
}
