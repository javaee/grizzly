/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.portunif;

import com.sun.grizzly.Context;
import com.sun.grizzly.portunif.PUProtocolRequest;
import com.sun.grizzly.portunif.ProtocolFinder;
import com.sun.grizzly.portunif.TLSPUPreProcessor;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A {@link ProtocolFinder} implementation that parse the available
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
            byte c2 = -1;
            byte c3 = -1;

            // Rule b - try to determine the context-root
            while (byteBuffer.hasRemaining()) {
                c3 = c2;
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
                            state = 3;
                        }
                        
                        break;
                        
                    case 3:
                        // Search end of HTTP header
                        if ((c == '\n' && c2 == '\n') ||
                                (c == '\n' && c2 == '\r' && c3 == '\n')) {
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
