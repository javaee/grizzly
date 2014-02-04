/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.ajp;

import org.junit.After;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.junit.Before;

public class AjpTestBase {
    static final int PORT = 19012;
    static final String LISTENER_NAME = "ajp";
    HttpServer httpServer;
    
    private Socket socket;

    AjpAddOn ajpAddon;

    @Before
    public void before() throws Exception {
        ByteBufferWrapper.DEBUG_MODE = true;
        configureHttpServer();
    }

    @After
    public void after() throws IOException {
        if (socket != null) {
            socket.close();
        }
        if (httpServer != null) {
            httpServer.shutdownNow();
        }
    }

    protected byte[] readFile(String name) throws IOException {
        ByteArrayOutputStream out;
        final FileInputStream stream = new FileInputStream(name);
        try {
            out = new ByteArrayOutputStream();
            byte[] read = new byte[4096];
            int count;
            while ((count = stream.read(read)) != -1) {
                out.write(read, 0, count);
            }
        } finally {
            stream.close();
        }
        return out.toByteArray();
    }

    protected ByteBuffer read(String file) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(file)));
        try {
            while (reader.ready()) {
                String[] line = reader.readLine().split(" ");
                int index = 0;
                while (index < 19 && index < line.length) {
                    if (!"".equals(line[index])) {
                        stream.write(Integer.parseInt(line[index], 16));
                    }
                    index++;
                }
            }
        } finally {
            reader.close();
        }

        return ByteBuffer.wrap(stream.toByteArray());
    }

    @SuppressWarnings({"unchecked"})
    protected void send(byte[] request) throws IOException {
        if (socket == null || socket.isClosed()) {
            socket = new Socket("localhost", PORT);
            socket.setSoTimeout(5000);
        }
        final OutputStream os = socket.getOutputStream();
        os.write(request);
        os.flush();
    }

    protected void closeClient() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
            }
            
            socket = null;
        }
    }
    
    @SuppressWarnings({"unchecked"})
    protected byte[] readAjpMessage() throws IOException {
        final byte[] tmpHeaderBuffer = new byte[4];
        
        final InputStream stream = socket.getInputStream();
        Utils.readFully(stream, tmpHeaderBuffer, 0, 4);

        if (tmpHeaderBuffer[0] != 'A' || tmpHeaderBuffer[1] != 'B') {
            throw new IllegalStateException("Incorrect protocol magic");
        }
        
        final int length = Utils.getShort(tmpHeaderBuffer, 2);
        
        final byte[] ajpMessage = new byte[4 + length];
        System.arraycopy(tmpHeaderBuffer, 0, ajpMessage, 0, 4);
        
        Utils.readFully(stream, ajpMessage, 4, length);
        
        return ajpMessage;
    }
    private void configureHttpServer() throws Exception {
        httpServer = new HttpServer();
        final NetworkListener listener =
                new NetworkListener(LISTENER_NAME,
                NetworkListener.DEFAULT_NETWORK_HOST,
                PORT);

        ajpAddon = new AjpAddOn();
        listener.registerAddOn(ajpAddon);
        
        httpServer.addListener(listener);
    }

    void startHttpServer(HttpHandler httpHandler, String... mappings) throws Exception {
        httpServer.getServerConfiguration().addHttpHandler(httpHandler, mappings);
        httpServer.start();
    }
}
