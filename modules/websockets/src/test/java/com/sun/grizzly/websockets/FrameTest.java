/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2011 Oracle and/or its affiliates. All rights reserved.
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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

@Test
public class FrameTest {
    public void textFrame() throws IOException {
        DataFrame frame = new DataFrame();
        frame.setPayload("Hello");
        final byte[] data = frame.frame();
        byte[] sample = {(byte) 0x84, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f};
        Assert.assertEquals(data, sample);

        frame.unframe(new ArrayNetworkHandler(data));
        Assert.assertEquals(frame.getTextPayload(), "Hello");
    }

    public void binaryFrame() throws IOException {
        byte bytes[] = new byte[256];
        new Random().nextBytes(bytes);
        byte[] sample = new byte[260];
        System.arraycopy(new byte[]{(byte) 0x85, 0x7E, 0x01, 0x00}, 0, sample, 0, 4);
        System.arraycopy(bytes, 0, sample, 4, 256);

        DataFrame frame = new DataFrame();
        frame.setPayload(bytes);
        frame.setType(FrameType.BINARY);
        final byte[] data = frame.frame();
        Assert.assertEquals(data, sample);

        frame.unframe(new ArrayNetworkHandler(data));
        Assert.assertEquals(frame.getBinaryPayload(), bytes);
    }

    public void largeBinaryFrame() throws IOException {
        byte bytes[] = new byte[65536];
        new Random().nextBytes(bytes);
        final byte[] prelude = {(byte) 0x85, 0x7F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00};
        byte[] sample = new byte[bytes.length + prelude.length];
        System.arraycopy(prelude, 0, sample, 0, prelude.length);
        System.arraycopy(bytes, 0, sample, prelude.length, 65536);

        DataFrame frame = new DataFrame();
        frame.setPayload(bytes);
        frame.setType(FrameType.BINARY);
        final byte[] data = frame.frame();
        Assert.assertEquals(data, sample);

        frame.unframe(new ArrayNetworkHandler(data));
        Assert.assertEquals(frame.getBinaryPayload(), bytes);
    }

    public void ping() throws IOException {
        DataFrame frame = new DataFrame();
        frame.setPayload("Hello");
        final byte[] data = frame.frame();
        byte[] sample = {(byte) 0x84, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f};
        Assert.assertEquals(data, sample);

        frame.unframe(new ArrayNetworkHandler(data));
        Assert.assertEquals(frame.getTextPayload(), "Hello");
    }

    public void convertToBytes() {
        compare(112);
        compare(1004);
        compare(8000);
        compare(10130);
        compare(Integer.MAX_VALUE);
        compare(Long.MAX_VALUE);
    }

    public void masking() {
        final String message = "This is a masked payload.";
        DataFrame masked = new DataFrame(message);
        final byte[] frame = masked.frame();
    }

    public void mapTypes() {
        for (int i = 0; i < 6; i++) {
            check((byte) i, FrameType.values()[i]);
            check((byte) (0x80 | i), FrameType.values()[i]);
        }
    }

    private void check(final byte opcodes, final FrameType type) {
        Assert.assertEquals(FrameType.valueOf(opcodes), type,
                String.format("Opcode %s returned the wrong type", opcodes));
    }

    private void compare(final long length) {
        DataFrame frame = new DataFrame();
        byte[] bytes = frame.convert(length);
        long value;
        if (bytes.length == 1) {
            value = bytes[0];
        } else {
            final byte[] temp = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, temp, 0, temp.length);
            value = frame.convert(temp);
        }
        Assert.assertEquals(value, length);
    }

    public void close() throws IOException {
        ClosingFrame frame = new ClosingFrame(1001, "test message");
        final byte[] bytes = frame.frame();

        ClosingFrame after = new ClosingFrame();
        after.unframe(new ArrayNetworkHandler(bytes));
        Assert.assertEquals(after.getReason(), "test message");
        Assert.assertEquals(after.getCode(), 1001);
    }

    private static class ArrayNetworkHandler implements NetworkHandler {
        private final byte[] data;
        int index = 0;

        public ArrayNetworkHandler(byte[] data) {
            this.data = data;
        }

        public void send(DataFrame frame) throws IOException {
        }

        public void setWebSocket(WebSocket webSocket) {
        }

        public byte get() throws IOException {
            return (byte) (index < data.length ? data[index++] & 0xFF : -1);
        }

        public byte[] get(int count) {
            final byte[] bytes = new byte[count];
            System.arraycopy(data, index, bytes, 0, count);
            index += count;
            return bytes;
        }
    }
}
