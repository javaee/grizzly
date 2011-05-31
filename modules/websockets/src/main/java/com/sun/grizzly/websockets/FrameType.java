/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.websockets;

import java.io.UnsupportedEncodingException;

public enum FrameType {
    CONTINUATION {
        @Override
        public void respond(WebSocket socket, DataFrame frame) {
            socket.onFragment(frame.isLast(), frame.getBytes());
        }
    },
    CLOSING {
        @Override
        public DataFrame create() {
            return new ClosingFrame();
        }

        @Override
        public void respond(WebSocket socket, DataFrame frame) {
            socket.onClose(frame);
        }
    },
    PING {
        @Override
        public void respond(WebSocket socket, DataFrame frame) {
            socket.onPing(frame);
        }
    },
    PONG {
        @Override
        public void respond(WebSocket socket, DataFrame frame) {
            socket.onPong(frame);
        }
    },
    TEXT {
        @Override
        public void unframe(DataFrame frame, byte[] data) {
            try {
                frame.setPayload(new String(data, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new FramingException(e.getMessage(), e);
            }
        }

        @Override
        public byte[] frame(DataFrame dataFrame) {
            try {
                return dataFrame.getTextPayload().getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new FramingException(e.getMessage(), e);
            }
        }

        @Override
        public void respond(WebSocket socket, DataFrame frame) {
            socket.onMessage(frame.getTextPayload());
        }
    },
    BINARY {
        @Override
        public void respond(WebSocket socket, DataFrame frame) {
            if(!frame.isLast()) {
                socket.onFragment(frame.isLast(), frame.getBytes());
            } else {
                socket.onMessage(frame.getBytes());
            }
        }
    };

    public void unframe(DataFrame frame, byte[] data) {
        frame.setPayload(data);
        frame.setType(this);
    }

    public byte[] frame(DataFrame dataFrame) {
        return dataFrame.getBytes();
    }

    public abstract void respond(WebSocket socket, DataFrame frame);

    public final byte setOpcode(byte b) {
        return (byte) (b | ordinal());
    }

    public static FrameType valueOf(byte opcodes) {
        return values()[(opcodes & 0xF)];
    }

    public DataFrame create() {
        final DataFrame frame = new DataFrame();
        frame.setType(this);
        return frame;
    }
}
