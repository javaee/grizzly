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

package com.sun.grizzly.websockets.draft07;

import com.sun.grizzly.websockets.LocalNetworkHandler;
import com.sun.grizzly.websockets.DataFrame;
import com.sun.grizzly.websockets.WebSocket;
import com.sun.grizzly.websockets.draft06.ClosingFrame;
import com.sun.grizzly.websockets.frametypes.ClosingFrameType;
import com.sun.grizzly.websockets.frametypes.PingFrameType;
import com.sun.grizzly.websockets.frametypes.PongFrameType;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Random;

public class Draft07Test {
    @Test
    public void unmaskedTextFrame() {
        Draft07Handler handler = new Draft07Handler(false);
        final LocalNetworkHandler localHandler = new LocalNetworkHandler();
        handler.setNetworkHandler(localHandler);
        handler.send("Hello");
        Assert.assertArrayEquals(new byte[]{(byte) 0x81, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f}, localHandler.getArray());

        Assert.assertEquals("Hello", handler.unframe().getTextPayload());
    }

    @Test
    public void maskedTextFrame() {
        Draft07Handler handler = new Draft07Handler(true);
        final LocalNetworkHandler localHandler = new LocalNetworkHandler();
        handler.setNetworkHandler(localHandler);
        handler.send("Hello");
        Assert.assertEquals("Hello", handler.unframe().getTextPayload());
    }

    @Test
    public void unmaskedFragmentedText() {
        final LocalNetworkHandler localHandler = new LocalNetworkHandler();

        Draft07Handler clientHandler = new Draft07Handler(false);
        clientHandler.setNetworkHandler(localHandler);

        Draft07Handler serverHandler = new Draft07Handler(false);
        serverHandler.setNetworkHandler(localHandler);

        clientHandler.stream(false, "Hel");
        Assert.assertArrayEquals(new byte[]{0x01, 0x03, 0x48, 0x65, 0x6c}, localHandler.getArray());

        Assert.assertEquals("Hel", serverHandler.unframe().getTextPayload());

        clientHandler.stream(true, "lo");
        Assert.assertArrayEquals(new byte[]{(byte) 0x80, 0x02, 0x6c, 0x6f}, localHandler.getArray());
        Assert.assertEquals("lo", serverHandler.unframe().getTextPayload());
    }

    @Test
    public void pingPong() {
        final LocalNetworkHandler localHandler = new LocalNetworkHandler();
        Draft07Handler clientHandler = new Draft07Handler(false);
        clientHandler.setNetworkHandler(localHandler);

        clientHandler.send(new DataFrame(new PingFrameType(), "Hello"));
        Assert.assertArrayEquals(new byte[]{(byte) 0x89, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f}, localHandler.getArray());

        clientHandler.send(new DataFrame(new PongFrameType(), "Hello"));
        Assert.assertArrayEquals(new byte[]{(byte) 0x8A, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f}, localHandler.getArray());
    }

    @Test
    public void maskedClose() {
        final LocalNetworkHandler localHandler = new LocalNetworkHandler();

        Draft07Handler clientHandler = new Draft07Handler(true);
        clientHandler.setNetworkHandler(localHandler);

        Draft07Handler serverHandler = new Draft07Handler(false);
        serverHandler.setNetworkHandler(localHandler);

        clientHandler.close(WebSocket.NORMAL_CLOSURE, "Test Message");
        byte[] bytes = new byte[2];
        System.arraycopy(localHandler.getArray(), 0, bytes, 0, 2);
        Assert.assertArrayEquals(new byte[]{(byte) 0x88, (byte) 0x8E}, bytes);

        final DataFrame frame = serverHandler.unframe();
        System.out.println("localHandler = " + localHandler);
        Assert.assertTrue(frame.getType() instanceof ClosingFrameType);
        
        final ClosingFrame close = (ClosingFrame) frame;
        Assert.assertEquals("Test Message", close.getReason());

        
    }

    @Test
    public void unmaskedBinaryFrame() {
        checkArrays(256, new byte[]{(byte) 0x82, 0x7E, (byte) 0x01, 0x00});
    }

    @Test
    public void unmaskedLargeBinaryFrame() {
        checkArrays(65536, new byte[]{(byte) 0x82, 0x7F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00});
    }

    private void checkArrays(final int size, final byte[] expected) {
        Draft07Handler handler = new Draft07Handler(false);
        final LocalNetworkHandler localHandler = new LocalNetworkHandler();
        handler.setNetworkHandler(localHandler);
        final byte[] data = new byte[size];
        new Random().nextBytes(data);
        handler.send(data);
        final byte[] array = localHandler.getArray();
        byte[] header = new byte[expected.length];
        System.arraycopy(array, 0, header, 0, expected.length);
        Assert.assertArrayEquals(expected, header);
        Assert.assertArrayEquals(data, handler.unframe().getBytes());
    }
}
