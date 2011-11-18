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

package com.sun.grizzly.http.ajp;

import java.nio.ByteBuffer;

public abstract class AjpPacket {
    public static ByteBuffer putShort(ByteBuffer target, short value) {
        return ensureCapacity(target, 2)
                .putShort(value);
    }

    public static ByteBuffer putString(ByteBuffer target, String value) {
        ByteBuffer buffer;
        if (value == null) {
            buffer = ensureCapacity(target, 2)
                    .putShort((short) 0xFFFF);
        } else {
            final byte[] bytes = value.getBytes();
            buffer = ensureCapacity(target, 3 + bytes.length)
                    .putShort((short) bytes.length);
            buffer.put(value.getBytes());
            buffer.put((byte) 0);
        }

        return buffer;
    }

    protected static ByteBuffer ensureCapacity(ByteBuffer buffer, int additional) {
        if (buffer.remaining() < additional) {
            final ByteBuffer expanded = ByteBuffer.allocate(buffer.capacity() + additional);
            buffer.flip();
            expanded.put(buffer);
            return expanded;
        }
        return buffer;
    }

    protected ByteBuffer buildPacketHeader(final short size) {
        ByteBuffer pktHeader = ByteBuffer.allocate(4);
        pktHeader.put((byte) 0x12);
        pktHeader.put((byte) 0x34);
        pktHeader.putShort(size);
        pktHeader.flip();
        return pktHeader;
    }

    public ByteBuffer toBuffer() {
        ByteBuffer header = buildContent();
        ByteBuffer pktHeader = buildPacketHeader((short) header.remaining());

        ByteBuffer packet = ByteBuffer.allocate(pktHeader.remaining()+ header.remaining());
        packet.put(pktHeader);
        packet.put(header);
        packet.flip();
        return packet;
    }

    public byte[] toByteArray() {
        final ByteBuffer byteBuffer = toBuffer();
        byte[] body = new byte[byteBuffer.remaining()];
        byteBuffer.get(body);
        
        
        return body;
    }
    
    public String toString() {
        final ByteBuffer buffer = toBuffer();
        return new String(buffer.array(), buffer.position(), buffer.limit() - buffer.position());
    }

    protected abstract ByteBuffer buildContent();
}
