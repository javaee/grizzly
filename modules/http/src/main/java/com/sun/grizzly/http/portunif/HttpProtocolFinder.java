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
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.http.portunif;

import com.sun.grizzly.Context;
import com.sun.grizzly.portunif.PUProtocolRequest;
import com.sun.grizzly.portunif.ProtocolFinder;
import com.sun.grizzly.portunif.TLSPUPreProcessor;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A <code>ProtocolFinder</code> implementation that parse the available
 * SocketChannel bytes looking for the 'http' bytes. An http request will
 * always has the form of:
 *
 * METHOD URI PROTOCOL/VERSION
 *
 * example: GET / HTTP/1.1
 *
 * The algorithm will try to find the protocol token. 
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public class HttpProtocolFinder implements ProtocolFinder {
    public static final String HTTP_REQUEST_URL = "HTTP_REQUEST_URL";
    
    public String find(Context context, PUProtocolRequest protocolRequest) throws IOException {
        ByteBuffer byteBuffer = protocolRequest.getByteBuffer();

        int curPosition = byteBuffer.position();
        int curLimit = byteBuffer.limit();

        byteBuffer.flip();
        
        int state = 0;
        int start = 0;
        int end = 0;

        try {
            byte c = 0;
            byte c2;

            // Rule b - try to determine the context-root
            while (byteBuffer.hasRemaining()) {
                c2 = c;
                c = byteBuffer.get();
                // State Machine
                // 0 - Search for the first SPACE ' ' between the method and the
                //     the request URI
                // 1 - Search for the second SPACE ' ' between the request URI
                //     and the method
                switch (state) {
                    case 0:
                        // Search for first ' '
                        if (c == 0x20) {
                            state = 1;
                            start = byteBuffer.position();
                        }
                        break;
                    case 1:
                        // Search for next ' '
                        if (c == 0x20) {
                            state = 2;
                            end = byteBuffer.position() - 1;
                            byteBuffer.position(start);
                            byte[] requestURI = new byte[end - start];
                            byteBuffer.get(requestURI);
                            context.setAttribute(HTTP_REQUEST_URL, requestURI);
                        }
                        break;
                    case 2:
                        // Search for P/ (part of HTTP/)
                        if (c == 0x2f && c2 == 'P') {
                            // find SSL preprocessor
                            return protocolRequest.isPreProcessorPassed(
                                    TLSPUPreProcessor.ID) ? "https" : "http";
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected state");
                }
            }
        } finally {
            byteBuffer.limit(curLimit);
            byteBuffer.position(curPosition);
        }
        
        return null;
    }

}
