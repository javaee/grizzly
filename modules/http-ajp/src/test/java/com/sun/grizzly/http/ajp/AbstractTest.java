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

package com.sun.grizzly.http.ajp;

import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.util.Utils;
import java.io.*;
import org.junit.After;

import java.net.Socket;
import java.nio.ByteBuffer;

public abstract class AbstractTest {
    protected static final int PORT = 19012;
    protected AjpSelectorThread selectorThread;
    private Socket socket;

    @After
    public void after() throws IOException {
        if (socket != null) {
            socket.close();
        }
        if (selectorThread != null) {
            selectorThread.stopEndpoint();
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
        final OutputStream outputStream = socket.getOutputStream();
        outputStream.write(request);
        outputStream.flush();
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
        readFully(stream, tmpHeaderBuffer, 0, 4);

        if (tmpHeaderBuffer[0] != 'A' || tmpHeaderBuffer[1] != 'B') {
            throw new IllegalStateException("Incorrect protocol magic");
        }
        
        final int length = AjpMessageUtils.getShort(tmpHeaderBuffer, 2);
        
        final byte[] ajpMessage = new byte[4 + length];
        System.arraycopy(tmpHeaderBuffer, 0, ajpMessage, 0, 4);
        
        readFully(stream, ajpMessage, 4, length);
        
        return ajpMessage;
    }

    protected void configureHttpServer(final Adapter adapter) throws IOException, InstantiationException {
        selectorThread = new AjpSelectorThread();
        selectorThread.setSsBackLog(8192);
        selectorThread.setCoreThreads(2);
        selectorThread.setMaxThreads(2);
        selectorThread.setPort(PORT);
        selectorThread.setDisplayConfiguration(Utils.VERBOSE_TESTS);
        selectorThread.setAdapter(adapter);
        selectorThread.setTcpNoDelay(true);
        selectorThread.setUseChunking(false);
        selectorThread.setKeepAliveTimeoutInSeconds(-1);
        selectorThread.setSendBufferSize(512);
        InputReader.setDefaultReadTimeout(3000);

        selectorThread.listen();
    }

    private static void readFully(final InputStream stream,
            final byte[] buffer, final int offset, final int length) throws IOException {
        int read = 0;
        while (read < length) {
            final int justRead = stream.read(buffer, offset + read, length - read);
            if (justRead == -1) {
                throw new EOFException();
            }
            
            read += justRead;
        }
    }
}
