/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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

import com.sun.grizzly.BaseSelectionKeyHandler;
import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.servlet.HttpServletRequestImpl;
import com.sun.grizzly.http.servlet.HttpServletResponseImpl;
import com.sun.grizzly.http.servlet.ServletContextImpl;
import com.sun.grizzly.tcp.InputBuffer;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.tcp.http11.InternalOutputBuffer;
import com.sun.grizzly.util.ConnectionCloseHandler;
import com.sun.grizzly.util.SelectionKeyActionAttachment;
import com.sun.grizzly.util.buf.ByteChunk;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;

public class ServerNetworkHandler extends SelectionKeyActionAttachment implements ConnectionCloseHandler,
        NetworkHandler {
    private final AsyncExecutor asyncExecutor;
    private final Request request;
    private final Response response;
    private final InputBuffer inputBuffer;
    private final InternalOutputBuffer outputBuffer;
    private WebSocket socket;

    public ServerNetworkHandler(AsyncExecutor executor, Request req, Response resp) {
        asyncExecutor = executor;
        request = req;
        response = resp;
        inputBuffer = req.getInputBuffer();
        outputBuffer = (InternalOutputBuffer) resp.getOutputBuffer();
        ((BaseSelectionKeyHandler) asyncExecutor.getProcessorTask().getSelectorHandler().getSelectionKeyHandler())
                .setConnectionCloseHandler(this);
    }

    public void setWebSocket(BaseWebSocket webSocket) {
        socket = webSocket;
    }

    protected void handshake(ProcessorTask task) throws IOException, HandshakeException {
        final boolean secure = "https".equalsIgnoreCase(request.scheme().toString()) || task.getSSLSupport() != null;

        final ClientHandShake clientHS = new ClientHandShake(request, secure);

        final ServerHandShake server;
        server = new ServerHandShake(clientHS.isSecure(), clientHS.getOrigin(),
                clientHS.getServerHostName(), clientHS.getPort(),
                clientHS.getResourcePath(), clientHS.getSubProtocol(),
                clientHS.getKey1(), clientHS.getKey2(), clientHS.getKey3());

        server.respond(response);
        socket.onConnect();
    }

    public void locallyClosed(SelectionKey key) {
        try {
            key.cancel();
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void remotlyClosed(SelectionKey key) {
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public boolean timedOut(SelectionKey Key) {
        return false;
    }

    public void process(SelectionKey key) {
        if (key.isReadable()) {
            try {
                unframe();
            } catch (IOException e) {
                final ProcessorTask task = asyncExecutor.getProcessorTask();
                task.setAptCancelKey(true);
                task.terminateProcess();
                WebSocketEngine.logger.log(Level.INFO, e.getMessage(), e);
            }
        }
    }

    private void unframe() throws IOException {
        final ByteChunk chunk = new ByteChunk(WebSocketEngine.INITIAL_BUFFER_SIZE);
        while (inputBuffer.doRead(chunk, request) > 0) {
            unframe(chunk.toByteBuffer());
        }
    }

    private void unframe(ByteBuffer bytes) throws IOException {
        while (bytes.hasRemaining()) {
            final DataFrame dataFrame = new DataFrame(bytes);
            if (dataFrame.getType() != null) {
                if (dataFrame.getType() == FrameType.CLOSING) {
                    socket.onClose();
                } else {
                    socket.onMessage(dataFrame);
                }
            }
        }
    }

    private void write(byte[] bytes) throws IOException {
        ByteChunk chunk = new ByteChunk(bytes.length);
        chunk.setBytes(bytes, 0, bytes.length);
        outputBuffer.doWrite(chunk, response);
        outputBuffer.flush();
    }

    @Override
    public void postProcess(SelectionKey key) {
    }

    public void send(DataFrame frame) throws IOException {
        write(frame.frame());
    }

    public HttpServletRequest getRequest() throws IOException {
        GrizzlyRequest r = new GrizzlyRequest();
        r.setRequest(request);
        return new WSServletRequestImpl(r);
    }

    public HttpServletResponse getResponse() throws IOException {
        GrizzlyResponse r = new GrizzlyResponse();
        r.setResponse(response);
        return new HttpServletResponseImpl(r);
    }

    private static class WSServletRequestImpl extends HttpServletRequestImpl {
        public WSServletRequestImpl(GrizzlyRequest r) throws IOException {
            super(r);
            setContextImpl(new ServletContextImpl());
        }
    }
}
