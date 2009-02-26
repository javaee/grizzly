/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.http.utils;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.standalone.StaticStreamAlgorithm;
import com.sun.grizzly.util.OutputWriter;
import com.sun.grizzly.util.SSLOutputWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Algorithm sends echo HTTP response to sender
 *
 * @author Alexey Stashok
 */
public class EmptyHttpStreamAlgorithm extends StaticStreamAlgorithm {
    private static AtomicInteger requestNum = new AtomicInteger(0);
    
    /**
     * HTTP end line.
     */
    private final static String NEWLINE = "\r\n";
    
    /**
     * Header String.
     */
    private final static String headers = "Connection:close" + NEWLINE
                + "Cache-control: private" + NEWLINE
                + "Content-Type: application/octet-stream" + NEWLINE
                + "Content-Length: ";
    
    /**
     * HTTP SC_FOUND header.
     */
    private final static ByteBuffer SC_FOUND =
            ByteBuffer.wrap( ("HTTP/1.1 200 OK" 
                + NEWLINE).getBytes());


    private boolean isSecured;
    
    public EmptyHttpStreamAlgorithm() {
    }

    public EmptyHttpStreamAlgorithm(boolean isSecured) {
        this.isSecured = isSecured;
    }

    @Override
    public boolean parse(ByteBuffer buffer) {
        buffer.flip();
        if (buffer.hasRemaining()) {
            byte[] data = new byte[buffer.remaining()];
            int position = buffer.position();
            buffer.get(data);
            buffer.position(position);

            try {
                ByteBuffer intBuf = ByteBuffer.allocate(4);
                int reply = requestNum.incrementAndGet();
                intBuf.putInt(0, reply);
                
                if (!isSecured) {
                    OutputWriter.flushChannel(socketChannel, SC_FOUND.slice());
                    OutputWriter.flushChannel(socketChannel, ByteBuffer.wrap((headers + 4 + NEWLINE + NEWLINE).getBytes()));
                    OutputWriter.flushChannel(socketChannel, intBuf);
                } else {
                    SSLOutputWriter.flushChannel(socketChannel, SC_FOUND.slice());
                    SSLOutputWriter.flushChannel(socketChannel, ByteBuffer.wrap((headers + 4 + NEWLINE + NEWLINE).getBytes()));
                    SSLOutputWriter.flushChannel(socketChannel, intBuf);
                }
            } catch (IOException e) {
                SelectorThread.logger().log(Level.FINE, 
                        "Exception (could happen if http request comes in " +
                        "several chunks. If yes - ignore) HttpEcho \"" 
                        + new String(data) + "\"", e);
            }
        }

        buffer.clear();
        return false;
    }
    
    public static void resetRequestCounter() {
        requestNum.set(0);
    }
}
