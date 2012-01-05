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

package com.sun.grizzly.websockets.draft06;

import com.sun.grizzly.websockets.DataFrame;
import com.sun.grizzly.websockets.FramingException;
import com.sun.grizzly.websockets.ProtocolError;
import com.sun.grizzly.websockets.StrictUtf8;
import com.sun.grizzly.websockets.WebSocket;
import com.sun.grizzly.websockets.WebSocketEngine;
import com.sun.grizzly.websockets.frametypes.ClosingFrameType;
import com.sun.grizzly.websockets.frametypes.Utf8DecodingError;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

public class ClosingFrame extends DataFrame {
    public static final byte[] EMPTY_BYTES = new byte[0];
    private int code = WebSocket.NORMAL_CLOSURE;
    private String reason;

    public ClosingFrame() {
        super(new ClosingFrameType());
    }

    public ClosingFrame(int code, String reason) {
        super(new ClosingFrameType());
        if (code > 0) {
            this.code = code;
        }
        this.reason = reason;
    }

    public ClosingFrame(byte[] data) {
        super(new ClosingFrameType());
        setPayload(data);
    }

    public int getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public void setPayload(byte[] bytes) {
        if (bytes.length == 1) {
            throw new ProtocolError("Closing frame payload, if present, must be a minimum of 2 bytes in length");
        }
        if (bytes.length > 0) {
            code = (int) WebSocketEngine.toLong(bytes, 0, 2);
            if (code < 1000 || code == 1004 || code == 1005 || code == 1006 || (code > 1011 && code < 3000) || code > 4999) {
                throw new ProtocolError("Illegal status code: " + code);
            }
            if (bytes.length > 2) {
                utf8Decode(bytes);
            }
        }
    }

    @Override
    public byte[] getBytes() {
        try {
            if (code == -1) {
                return EMPTY_BYTES;
            }

            final byte[] bytes = WebSocketEngine.toArray(code);
            final byte[] reasonBytes = reason == null ? EMPTY_BYTES : reason.getBytes("UTF-8");
            final byte[] frameBytes = new byte[2 + reasonBytes.length];
            System.arraycopy(bytes, bytes.length - 2, frameBytes, 0, 2);
            System.arraycopy(reasonBytes, 0, frameBytes, 2, reasonBytes.length);

            return frameBytes;
        } catch (UnsupportedEncodingException e) {
            throw new Utf8DecodingError(e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ClosingFrame");
        sb.append("{code=").append(code);
        sb.append(", reason=").append(reason == null ? null : "'" + reason + "'");
        sb.append('}');
        return sb.toString();
    }
    
    // --------------------------------------------------------- Private Methods

    private void utf8Decode(byte[] data) {
        final ByteBuffer b = ByteBuffer.wrap(data, 2, data.length - 2);
        Charset charset = new StrictUtf8();
        final CharsetDecoder decoder = charset.newDecoder();
        int n = (int) (b.remaining() * decoder.averageCharsPerByte());
        CharBuffer cb = CharBuffer.allocate(n);
        for (; ; ) {
            CoderResult result = decoder.decode(b, cb, true);
            if (result.isUnderflow()) {
                decoder.flush(cb);
                cb.flip();
                reason = cb.toString();
                break;
            }
            if (result.isOverflow()) {
                CharBuffer tmp = CharBuffer.allocate(2 * n + 1);
                cb.flip();
                tmp.put(cb);
                cb = tmp;
                continue;
            }
            if (result.isError() || result.isMalformed()) {
                throw new Utf8DecodingError("Illegal UTF-8 Sequence");
            }
        }

    }
}
