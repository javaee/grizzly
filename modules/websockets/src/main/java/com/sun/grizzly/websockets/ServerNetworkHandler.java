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

package com.sun.grizzly.websockets;

import com.sun.grizzly.arp.AsyncProcessorTask;
import com.sun.grizzly.arp.AsyncTask;
import com.sun.grizzly.http.servlet.HttpServletRequestImpl;
import com.sun.grizzly.http.servlet.HttpServletResponseImpl;
import com.sun.grizzly.http.servlet.ServletContextImpl;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;
import com.sun.grizzly.tcp.http11.InternalOutputBuffer;
import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.buf.UDecoder;
import com.sun.grizzly.util.http.HttpRequestURIDecoder;
import com.sun.grizzly.util.http.mapper.Mapper;
import com.sun.grizzly.util.http.mapper.MappingData;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerNetworkHandler extends BaseNetworkHandler {
    private static final Logger LOGGER = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    private final Request request;
    private final Response response;
    private final HttpServletRequest httpServletRequest;
    private final HttpServletResponse httpServletResponse;
    private final InternalInputBuffer inputBuffer;
    private final InternalOutputBuffer outputBuffer;
    private UDecoder urlDecoder = new UDecoder();
    
    private final ProtocolHandler protocolHandler;
    private boolean isClosed;
    
    public ServerNetworkHandler(Request req, Response resp,
            ProtocolHandler protocolHandler, Mapper mapper) {
        request = req;
        response = resp;
        GrizzlyRequest grizzlyRequest = new GrizzlyRequest();
        grizzlyRequest.setRequest(request);
        GrizzlyResponse grizzlyResponse = new GrizzlyResponse();
        grizzlyResponse.setResponse(response);
        grizzlyRequest.setResponse(grizzlyResponse);
        grizzlyResponse.setRequest(grizzlyRequest);
        try {
            httpServletRequest = new WSServletRequestImpl(grizzlyRequest, mapper);
            httpServletResponse = new HttpServletResponseImpl(grizzlyResponse);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        this.protocolHandler = protocolHandler;
        inputBuffer = (InternalInputBuffer) req.getInputBuffer();
        outputBuffer = (InternalOutputBuffer) resp.getOutputBuffer();
        
    }

    @Override
    protected int read() {
        int read = 0;
        ByteChunk newChunk = new ByteChunk(WebSocketEngine.INITIAL_BUFFER_SIZE);
        
        Throwable error = null;
        
        try {
            ByteChunk bytes = new ByteChunk();
            if (chunk.getLength() > 0) {
                newChunk.append(chunk);
            }

            read = inputBuffer.doRead(bytes, request);
            if (read > 0) {
                newChunk.append(bytes);
            }
        } catch (Throwable e) {
            error = e;
            read = -1;
        }

        if(read == -1) {
            throw new WebSocketException("Connection closed", error);
        }
        chunk.setBytes(newChunk.getBytes(), 0, newChunk.getEnd());
        return read;
    }

    public byte get() {
        synchronized (chunk) {
            fill();
            try {
                return (byte) chunk.substract();
            } catch (IOException e) {
                throw new WebSocketException(e.getMessage(), e);
            }
        }
    }

    public byte[] get(int count) {
        synchronized (chunk) {
            try {
                byte[] bytes = new byte[count];
                int total = 0;
                while (total < count) {
                    if (chunk.getLength() < count) {
                        read();
                    }
                    total += chunk.substract(bytes, total, count - total);
                }
                return bytes;
            } catch (IOException e) {
                throw new WebSocketException(e.getMessage(), e);
            }
        }
    }

    private void fill() {
        synchronized (chunk) {
            if (chunk.getLength() == 0) {
                read();
            }
        }
    }

    public void write(byte[] bytes) {
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
    }

    public boolean ready() {
        synchronized (chunk) {
            return chunk.getLength() != 0;
        }
    }

    public HttpServletRequest getRequest() throws IOException {
        return httpServletRequest;
    }

    public HttpServletResponse getResponse() throws IOException {
        return httpServletResponse;
    }

    public synchronized void close() {
        if (!isClosed) {
            isClosed = true;
            //            key.cancel();
            protocolHandler.getProcessorTask().setAptCancelKey(true);
//            protocolHandler.getProcessorTask().terminateProcess();
            final AsyncProcessorTask asyncProcessorTask =
                    protocolHandler.getAsyncTask();
            asyncProcessorTask.setStage(AsyncTask.FINISH);
            try {
                asyncProcessorTask.doTask();
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            protocolHandler.getWebSocket().onClose(null);
        }
    }
    
    private class WSServletRequestImpl extends HttpServletRequestImpl {
        private String pathInfo;
        private String servletPath;
        private String contextPath;
        public WSServletRequestImpl(GrizzlyRequest r, Mapper mapper) throws IOException {
            super(r);
            setContextImpl(new ServletContextImpl());
            if (mapper != null) {
                updatePaths(r, mapper);
            }
        }

        @Override
        public String getContextPath() {
            return contextPath;
        }

        @Override
        public String getServletPath() {
            return servletPath;
        }

        @Override
        public String getPathInfo() {
            return pathInfo;
        }

        private void updatePaths(GrizzlyRequest r, Mapper mapper) {
            final Request req = r.getRequest();
            try {
                MessageBytes decodedURI = req.decodedURI();
                decodedURI.duplicate(req.requestURI());
                HttpRequestURIDecoder.decode(decodedURI, urlDecoder, null, null);
                MappingData data = new MappingData();
                mapper.map(req.remoteHost(), decodedURI, data);
                pathInfo = data.pathInfo.getString();
                servletPath = data.wrapperPath.getString();
                contextPath = data.contextPath.getString();
            } catch (Exception e) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Unable to map request", e);
                }
                pathInfo = null;
                servletPath = null;
                contextPath = null;
            }
        }

    } // END WSServletRequestImpl

    @Override
    public String toString() {
        final InputReader inputStream = (InputReader) inputBuffer.getInputStream();
        final int remoteSocketAddress =
                ((SocketChannel) inputStream.key.channel()).socket().getPort();
        final StringBuilder sb = new StringBuilder();
        sb.append("SNH[");
        sb.append(remoteSocketAddress);
        sb.append(",").append(super.toString());
        sb.append(']');
        return sb.toString();
    }
}
