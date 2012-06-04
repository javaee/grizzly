/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.nio.channels.FileChannel;

public class AjpOutputBuffer extends SocketChannelOutputBuffer {

    private static final byte[] END_RESPONSE_AJP_PACKET =
            AjpMessageUtils.createAjpPacket(AjpConstants.JK_AJP13_END_RESPONSE,
                    new byte[] {1});
    
    private static final byte[] TERMINATING_BYTE = new byte[1];

    public AjpOutputBuffer(Response response, int sendBufferSize, boolean bufferResponse) {
        super(response, sendBufferSize, bufferResponse);
    }

    @Override
    public final boolean isSupportFileSend() {
        return false;
    }
    
    public void sendHeaders() {
        final ByteChunk encodeHeaders = AjpMessageUtils.encodeHeaders(
                (AjpHttpResponse) response);
        
        try {
            writeEncodedAjpMessage(encodeHeaders.getBuffer(),
                    encodeHeaders.getStart(), encodeHeaders.getLength());
        } catch (IOException e) {
        }
    }

    public void writeEncodedAjpMessage(final byte[] cbuf, final int off,
            final int len) throws IOException {
    
        writeEncodedAjpMessage(cbuf, off, len, false);
    } 
    
    public void writeEncodedAjpMessage(final byte[] cbuf, final int off,
            final int len, final boolean isFlush) throws IOException {
    
        super.realWriteBytes(cbuf, off, len);
        if (isFlush) {
            flushBuffer();
        }
    } 

    @Override
    public void realWriteBytes(byte[] cbuf, int off, int len) throws IOException {
        if (response.isCommitted()) { // serialize payload

            final ByteChunk bc = ((AjpHttpResponse) response).tmpHeaderByteChunk;
            int written = 0;
            while (written < len) {
                final int count = Math.min(AjpConstants.MAX_BODY_SIZE, len - written);
                
                bc.recycle();
                
                bc.append('A');
                bc.append('B');
                AjpMessageUtils.putShort(bc, (short) (count + 4));
                
                bc.append(AjpConstants.JK_AJP13_SEND_BODY_CHUNK);
                AjpMessageUtils.putShort(bc, (short) count);
                super.realWriteBytes(bc.getBytes(), bc.getStart(), bc.getLength()); // AJP header
                super.realWriteBytes(cbuf, off + written, count); // payload
                super.realWriteBytes(TERMINATING_BYTE, 0, 1); // terminating byte
                
                written += count;
            }
        } else {  // serialize headers
            throw new IllegalStateException("Headers serialization shouldn't reach this point");
        }
    }

    @Override
    public long sendFile(FileChannel fileChannel, long position, final long length) throws IOException {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void endRequest() throws IOException {
        if (!finished) {
            super.endRequest();
            
            writeEncodedAjpMessage(END_RESPONSE_AJP_PACKET, 0,
                    END_RESPONSE_AJP_PACKET.length);
            flushBuffer();
        }
    }

    void setFinished(boolean isFinished) {
        finished = isFinished;
    }
}
