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
package org.glassfish.grizzly.websockets.draft76;

import java.io.ByteArrayOutputStream;
import java.net.URI;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.FramingException;
import org.glassfish.grizzly.websockets.HandShake;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocketException;

public class Draft76Handler extends ProtocolHandler {
    public Draft76Handler() {
        super(false);
    }

    @Override
    public byte[] frame(DataFrame frame) {
        return frame.getType().getBytes(frame);
    }

    @Override
    public DataFrame parse(Buffer buffer) {
        if (buffer.remaining() < 2) {
            // Don't have enough bytes to read opcode and one more byte
            return null;
        }
        
        byte b = buffer.get();
        DataFrame frame = null;
        
        switch (b) {
            case 0x00:
                ByteArrayOutputStream raw = new ByteArrayOutputStream();
                while (buffer.hasRemaining()) {
                    if ((b = buffer.get()) == (byte) 0xFF) {
                        frame = new DataFrame(Draft76FrameType.TEXT, raw.toByteArray());                        
                        break;
                    }
                    
                    raw.write(b);                    
                }
                
                break;
            case (byte) 0xFF:
                frame = new DataFrame(Draft76FrameType.CLOSING, new byte[]{b, buffer.get()});
                break;
            default:
                throw new FramingException("Unknown frame type: " + b);
        }
        return frame;
    }

    @Override
    protected HandShake createHandShake(HttpContent requestContent) {
        return new HandShake76(requestContent);
    }

    @Override
    protected HandShake createHandShake(URI uri) {
        return new HandShake76(uri);
    }

    @Override
    public GrizzlyFuture<DataFrame> send(String data) {
        return send(new DataFrame(Draft76FrameType.TEXT, data));
    }

    @Override
    public GrizzlyFuture<DataFrame> close(int code, String reason) {
        return send(new DataFrame(Draft76FrameType.CLOSING, (String)null, true));
    }

    @Override
    public GrizzlyFuture<DataFrame> send(byte[] data) {
        throw new WebSocketException("Binary data not supported in draft 76");
    }

    @Override
    public GrizzlyFuture<DataFrame> stream(boolean last, byte[] bytes, int off, int len) {
        throw new WebSocketException("Streaming not supported in draft 76");
    }

    @Override
    public GrizzlyFuture<DataFrame> stream(boolean last, String data) {
        throw new WebSocketException("Streaming not supported in draft 76");
    }

    @Override
    protected boolean isControlFrame(byte opcode) {
        return false;
    }
}
