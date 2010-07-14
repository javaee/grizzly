/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public enum FrameType {
    TEXT {
        @Override
        public boolean accept(NetworkHandler handler) throws IOException {
            return handler.peek((byte) 0x00);
        }

        @Override
        public byte[] unframe(NetworkHandler handler) throws IOException {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            byte b;
            handler.get();
            while ((b = handler.get()) != (byte) 0xFF) {
                raw.write(b);
            }

            return raw.toByteArray();
        }

        public byte[] frame(byte[] data) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 2);
            out.write((byte) 0x00);
            out.write(data, 0, data.length);
            out.write((byte) 0xFF);
            return out.toByteArray();
        }
        @Override
        public void respond(WebSocket socket, DataFrame frame) throws IOException {
            socket.onMessage(frame);
        }},

    CLOSING {
        @Override
        public boolean accept(NetworkHandler handler) throws IOException {
            return handler.peek((byte) 0xFF, (byte) 0x00);
        }

        @Override
        public byte[] unframe(NetworkHandler handler) {
            return new byte[]{(byte) 0xFF, 0x00};
        }

        @Override
        public byte[] frame(byte[] data) {
            return new byte[]{(byte) 0xFF, 0x00};
        }

        @Override
        public void respond(WebSocket socket, DataFrame frame) throws IOException {
            socket.close();
        }};

    public abstract boolean accept(NetworkHandler handler) throws IOException;

    public abstract byte[] unframe(NetworkHandler handler) throws IOException;

    public abstract byte[] frame(byte[] data);

    public abstract void respond(WebSocket socket, DataFrame frame) throws IOException;

    public FrameType next() {
        final FrameType[] types = FrameType.values();
        return ordinal() < types.length ? types[ordinal() + 1] : null;
    }
}