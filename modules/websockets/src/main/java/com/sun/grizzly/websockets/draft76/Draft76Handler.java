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
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
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

package com.sun.grizzly.websockets.draft76;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.net.URL;
import com.sun.grizzly.websockets.DataFrame;
import com.sun.grizzly.websockets.FrameType;
import com.sun.grizzly.websockets.FramingException;
import com.sun.grizzly.websockets.HandShake;
import com.sun.grizzly.websockets.WebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

public class Draft76Handler extends WebSocketHandler {
    public byte[] frame(DataFrame frame) {
        switch(frame.getType()) {
            case TEXT:
                final byte[] data = frame.getBytes();
                ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 2);
                out.write((byte) 0x00);
                out.write(data, 0, data.length);
                out.write((byte) 0xFF);
                return out.toByteArray();
            case CLOSING:
                return new byte[]{(byte) 0xFF, 0x00};
        }

        return new byte[0];
    }

    public void readFrame() {
        while (handler.ready()) {
            try {
                respond(unframe());
            } catch(FramingException fe) {
                fe.printStackTrace();
                getWebSocket().close();
            }
        }

    }

    private void respond(DataFrame frame) {
        switch(frame.getType()) {
            case TEXT:
                getWebSocket().onMessage(frame.getTextPayload());
                break;
            case CLOSING:
                getWebSocket().close();
                break;
        }
    }

    private DataFrame unframe() {
        byte b = handler.get();
        DataFrame frame;
        switch (b) {
            case 0x00:
                frame = new DataFrame(FrameType.TEXT);
                ByteArrayOutputStream raw = new ByteArrayOutputStream();
                while ((b = handler.get()) != (byte) 0xFF) {
                    raw.write(b);
                }
                try {
                    frame.setPayload(new String(raw.toByteArray(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new FramingException(e.getMessage(), e);
                }
                break;
            case (byte) 0xFF:
                frame = new DataFrame(FrameType.CLOSING, new byte[]{b, handler.get()});
                break;
            default:
                throw new FramingException("Unknown frame type: " + b);
        }

        return frame;
    }

    @Override
    protected HandShake createHandShake(Request request) {
        return new HandShake76(getNetworkHandler(), request.getMimeHeaders());
    }

    @Override
    protected HandShake createHandShake(URL url) {
        return new HandShake76(getNetworkHandler(), url);
    }
}
