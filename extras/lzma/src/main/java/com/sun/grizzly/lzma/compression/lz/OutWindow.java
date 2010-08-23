/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.lzma.compression.lz;

import java.io.IOException;

/**
 * OutWindow
 *
 * @author Igor Pavlov
 */
public class OutWindow {

    byte[] _buffer;
    int _pos;
    int _windowSize = 0;
    int _streamPos;
    java.io.OutputStream _stream;

    public void Create(int windowSize) {
        if (_buffer == null || _windowSize != windowSize) {
            _buffer = new byte[windowSize];
        }
        _windowSize = windowSize;
        _pos = 0;
        _streamPos = 0;
    }

    public void SetStream(java.io.OutputStream stream) throws IOException {
        ReleaseStream();
        _stream = stream;
    }

    public void ReleaseStream() throws IOException {
        Flush();
        _stream = null;
    }

    public void Init(boolean solid) {
        if (!solid) {
            _streamPos = 0;
            _pos = 0;
        }
    }

    public void Flush() throws IOException {
        int size = _pos - _streamPos;
        if (size == 0) {
            return;
        }
        _stream.write(_buffer, _streamPos, size);
        if (_pos >= _windowSize) {
            _pos = 0;
        }
        _streamPos = _pos;
    }

    public void CopyBlock(int distance, int len) throws IOException {
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
                Flush();
            }
        }
    }

    public void PutByte(byte b) throws IOException {
        _buffer[_pos++] = b;
        if (_pos >= _windowSize) {
            Flush();
        }
    }

    public byte GetByte(int distance) {
        int pos = _pos - distance - 1;
        if (pos < 0) {
            pos += _windowSize;
        }
        return _buffer[pos];
    }
}
