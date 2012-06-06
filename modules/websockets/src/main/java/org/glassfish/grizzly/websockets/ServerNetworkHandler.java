/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.websockets;

import org.glassfish.grizzly.Buffer;

public class ServerNetworkHandler extends BaseNetworkHandler {
//    private final Request request;
//    private final Response response;
//    private final NIOInputStream inputBuffer;
//    private final OutputBuffer outputBuffer;

//    public ServerNetworkHandler(Request req, Response resp) {
//        request = req;
//        response = resp;
//        inputBuffer = req.getInputStream(false);
//        outputBuffer = resp.getOutputBuffer();
//    }

    public ServerNetworkHandler(Buffer buffer) {
        this.buffer = buffer;
    }

/*
    @Override
    protected int read() {
        int read = 0;
        ByteChunk newChunk = new ByteChunk(WebSocketEngine.INITIAL_BUFFER_SIZE);
        try {
            ByteChunk bytes = new ByteChunk(WebSocketEngine.INITIAL_BUFFER_SIZE);
            if (buffer.getLength() > 0) {
                newChunk.append(buffer);
            }
            int count = WebSocketEngine.INITIAL_BUFFER_SIZE;
            while (count == WebSocketEngine.INITIAL_BUFFER_SIZE) {
                count = inputBuffer.doRead(bytes, request);
                newChunk.append(bytes);
                read += count;
            }
        } catch (IOException e) {
            throw new WebSocketException(e.getMessage(), e);
        }

        if(read == -1) {
            throw new WebSocketException("Read -1 bytes.  Connection closed?");
        }
        buffer.setBytes(newChunk.getBytes(), 0, newChunk.getEnd());
        return read;
    }
*/

    public byte get() {
        return buffer.get();
    }

    public byte[] get(int count) {
        byte[] bytes = new byte[count];
        buffer.get(bytes);
        return bytes;
    }

    public void write(byte[] bytes) {
/*
        synchronized (outputBuffer) {
            try {
                ByteChunk buffer = new ByteChunk();
                buffer.setBytes(bytes, 0, bytes.length);
                outputBuffer.doWrite(buffer, response);
                outputBuffer.flush();
            } catch (IOException e) {
                throw new WebSocketException(e.getMessage(), e);
            }
        }
*/
    }

    public boolean ready() {
        return buffer.hasRemaining();
    }
}
