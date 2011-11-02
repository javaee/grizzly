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

import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.SocketChannelOutputBuffer;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;
import com.sun.grizzly.util.buf.MessageBytes;
import java.io.IOException;
import java.nio.Buffer;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AjpProcessorTask extends ProcessorTask {
    private final static Logger logger = SelectorThread.logger();
    
    public AjpProcessorTask(boolean initialize) {
        super(initialize, false);
    }
    
    @Override
    protected Request createRequest() {
        return new AjpHttpRequest();
    }

    @Override
    protected Response createResponse() {
        return new AjpHttpResponse();
    }

    @Override
    protected InternalInputBuffer createInputBuffer(Request request, int requestBufferSize) {
        return new AjpInputBuffer(request, requestBufferSize);
    }

    @Override
    public void sendHeaders() {
        ((AjpOutputBuffer) outputBuffer).sendHeaders();
    }

    @Override
    protected SocketChannelOutputBuffer createOutputBuffer(Response response, int sendBufferSize,
            boolean bufferResponse) {
        return new AjpOutputBuffer(response, sendBufferSize, bufferResponse);
    }

    @Override
    public boolean parseRequest() throws Exception {
        ((AjpInputBuffer) inputBuffer).readAjpMessageHeader();
        
        super.parseRequest();
        
        return false;
    }

    @Override
    public void invokeAdapter() {
        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;

        switch (ajpRequest.getType()) {
            case AjpConstants.JK_AJP13_FORWARD_REQUEST:
            {
                ajpRequest.setForwardRequestProcessing(true);
                if (ajpRequest.isExpectContent()) {
                    try {
                        // if expect content - parse following data chunk
                        ((AjpInputBuffer) request.getInputBuffer()).parseDataChunk();
                    } catch (IOException e) {
                        logger.log(Level.FINE,
                                "Exception during parsing data chunk", e);

                        error = true;                        
                    }
                }
                
                super.invokeAdapter();
                break;
            }   
            case AjpConstants.JK_AJP13_SHUTDOWN:
            {
                processShutdown();
                break;
            }
            case AjpConstants.JK_AJP13_CPING_REQUEST:
            {
                try {
                    processCPing();
                } catch (IOException e) {
                    logger.log(Level.FINE,
                            "Exception during sending CPONG reply", e);
                    
                    error = true;
                }
                
                break;
            }
            default:
                throw new IllegalStateException("Invalid packet type: " +
                        ajpRequest.getType());
        }
    }

    private void processShutdown() {

        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
        String shutdownSecret = null;

        if (ajpRequest.getLength() > 1) { // request contains not just type byte
            // Secret is available
            final MessageBytes tmpMessageBytes = ajpRequest.tmpMessageBytes;
            ((AjpInputBuffer) inputBuffer).getBytesToMB(tmpMessageBytes);
            
            shutdownSecret = tmpMessageBytes.toString();
            tmpMessageBytes.recycle();
        }

        final AjpSelectorThread ajpSelectorThread = (AjpSelectorThread) getSelectorThread();
        final String secret = ajpSelectorThread.getSecret();
        
        if (secret != null &&
                secret.equals(shutdownSecret)) {
            throw new IllegalStateException("Secret doesn't match, no shutdown");
        }

        final Queue<ShutdownHandler> shutdownHandlers = ajpSelectorThread.getShutdownHandlers();
        for (ShutdownHandler handler : shutdownHandlers) {
            try {
                handler.onShutdown(key.channel());
            } catch (Exception e) {
                logger.log(Level.WARNING,
                        "Exception during ShutdownHandler execution", e);
            }
        }
    }
    
    /**
     * Process CPing request message.
     * We send CPong response back as plain Grizzly {@link Buffer}.
     *
     * @param ctx
     * @param message
     * @return
     * @throws IOException
     */
    private void processCPing() throws IOException {
        AjpHttpResponse.writeCPongReply(outputStream);
        outputStream.flush();
    }

    @Override
    public void setBufferSize(int requestBufferSize) {
        if (requestBufferSize < AjpConstants.MAX_PACKET_SIZE * 2) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Buffer size is set to {0} instead of {1} for performance reasons",
                        new Object[]{AjpConstants.MAX_PACKET_SIZE * 2, requestBufferSize});
            }
            
            requestBufferSize = AjpConstants.MAX_PACKET_SIZE * 2;
        }
        
        super.setBufferSize(requestBufferSize);
    }
    
    
}
