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

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class TempServer {
    public static void main(String[] args) throws Exception {
        final SelectorThread thread = simpleSelectorThread();
        thread.start();

//        ServerSocket socket = new ServerSocket(8080);
//        while (true) {
//            read(socket.accept());
//        }
    }

    private static void read(Socket s) throws IOException {
        oldHandshake(s);
//        read(stream);
        s.close();
    }

    private static Request createRequest(Socket s) throws IOException {
        final Request request = new Request();
        InternalInputBuffer buffer = new InternalInputBuffer(request);
        buffer.setInputStream(s.getInputStream());
        buffer.parseRequestLine();
        buffer.parseHeaders();
        return request;
    }

    private static void oldHandshake(Socket s) throws IOException {
        final Request request = new Request();
        request.setResponse(new Response());
        InternalInputBuffer buffer = new InternalInputBuffer(request);
        request.setInputBuffer(buffer); 
        buffer.setInputStream(s.getInputStream());
        buffer.parseRequestLine();
        buffer.parseHeaders();
        ClientHandShake clientHS = new ClientHandShake(request, false);
        ServerHandShake serverHS = new ServerHandShake(clientHS);
        final ByteBuffer bb = serverHS.generate();
        final OutputStream os = s.getOutputStream();
        os.write(bb.array());
        os.flush();
    }

    private static void read(InputStream stream) throws IOException {
        byte[] data = new byte[4096];
        int read;
        while ((read = stream.read(data)) != -1) {
            System.out.println(new String(data, 0, read));
        }
    }

    private static SelectorThread simpleSelectorThread() throws Exception {
        final EchoServlet servlet = new EchoServlet();
        final SimpleWebSocketApplication app = new SimpleWebSocketApplication();
        WebSocketEngine.getEngine().register("/echo", app);
        return WebSocketsTest.createSelectorThread(8080, new ServletAdapter(servlet));
    }
}