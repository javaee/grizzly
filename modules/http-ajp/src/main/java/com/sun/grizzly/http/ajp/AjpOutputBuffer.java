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

package com.sun.grizzly.http.ajp;

import com.sun.grizzly.http.SocketChannelOutputBuffer;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.CharChunk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

public class AjpOutputBuffer extends SocketChannelOutputBuffer {

    public AjpOutputBuffer(Response response, int sendBufferSize, boolean bufferResponse) {
        super(response, sendBufferSize, bufferResponse);
    }

    public void sendHeaders() {
        write(AjpMessageUtils.encodeHeaders((AjpHttpResponse) response));
    }

    @Override
    public void realWriteBytes(byte[] cbuf, int off, int len) throws IOException {
        if (response.isCommitted()) {
            final ByteBuffer wrap = ByteBuffer.allocate(len+3);
            wrap.putShort((short) len);
            wrap.put(cbuf, off, len);
            wrap.put((byte) 0);
            wrap.flip();
            final ByteBuffer packet =
                    AjpMessageUtils.createAjpPacket(AjpConstants.JK_AJP13_SEND_BODY_CHUNK, wrap);
            super.realWriteBytes(packet.array(), packet.position(), packet.limit());
        } else {
            super.realWriteBytes(cbuf, off, len);
        }
    }

    @Override
    protected void write(ByteChunk bc) {
        super.write(bc);
    }

    @Override
    protected void write(CharChunk cc) {
        super.write(cc);
    }

    @Override
    protected void write(byte[] b) {
        super.write(b);
    }

    @Override
    protected void write(String s) {
        super.write(s);
    }

    @Override
    protected void write(int i) {
        super.write(i);
    }

    @Override
    public long sendFile(FileChannel fileChannel, long position, final long length) throws IOException {
        return fileChannel.transferTo(position, length,
                new WritableByteChannel() {
                    public int write(ByteBuffer src) throws IOException {
                        ByteBuffer buffer = ByteBuffer.allocate((int) (3 + length));
                        buffer.putShort((short) length);
                        buffer.put(src);
                        buffer.put((byte) 0);
                        buffer.flip();
                        ((SocketChannel) channel).write(AjpMessageUtils
                                .createAjpPacket(AjpConstants.JK_AJP13_SEND_BODY_CHUNK, buffer));
                        return (int) length;
                    }

                    public boolean isOpen() {
                        return channel.isOpen();
                    }

                    public void close() throws IOException {
                        channel.close();
                    }
                });
    }

    @Override
    public void endRequest() throws IOException {
        if (!finished) {
            super.endRequest();
            ((SocketChannel) channel).write(AjpMessageUtils.createAjpPacket(AjpConstants.JK_AJP13_END_RESPONSE,
                    ByteBuffer.wrap(new byte[]{1})));
        }
    }
}
