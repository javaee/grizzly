/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
 *
 */
package org.glassfish.grizzly.web;

import com.sun.grizzly.Context;
import org.glassfish.grizzly.web.portunif.HttpProtocolFinder;
import org.glassfish.grizzly.web.utils.EmptyHttpStreamAlgorithm;
import org.glassfish.grizzly.web.utils.SelectorThreadUtils;
import com.sun.grizzly.portunif.PUProtocolRequest;
import com.sun.grizzly.portunif.ProtocolFinder;
import com.sun.grizzly.portunif.ProtocolHandler;
import org.glassfish.grizzly.web.container.http11.GrizzlyAdapter;
import org.glassfish.grizzly.web.container.http11.GrizzlyRequest;
import org.glassfish.grizzly.web.container.http11.GrizzlyResponse;
import org.glassfish.grizzly.web.container.util.OutputWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

/**
 *
 * @author Alexey Stashok
 */
public class PortUnificationTest extends TestCase {

    public static final int PORT = 18889;

    /**
     * Test how HTTP protocol could share same port with some custom protocol
     */
    public void testHttpPortUnification() throws IOException {
        String simpleProtocolId = "AAA";
        SelectorThread httpSelectorThread = createSelectorThread(PORT, 2);
        // Define HTTP finder
        ProtocolFinder httpFinder = new HttpProtocolFinder();
        // Define custom protocol finder
        ProtocolFinder simpleFinder = new SimpleProtocolFinder(simpleProtocolId);

        // We don't need to define HTTP protocol handler, as it will be
        // processed by default filter chain

        // Define custom protocol handler
        ProtocolHandler simpleHandler = new SimpleProtocolHandler(simpleProtocolId);

        List<ProtocolFinder> finders = Arrays.asList(httpFinder, simpleFinder);
        List<ProtocolHandler> handlers = Arrays.asList(simpleHandler);

        // Configure port unification
        httpSelectorThread.configurePortUnification(finders, handlers, null);

        // Set custom HTTP adapter
        httpSelectorThread.setAdapter(new CustomHttpAdapter());

        try {
            // Start to listen
            SelectorThreadUtils.startSelectorThread(httpSelectorThread);


            // ------------- Test HTTP protocol --------------------------
            String testString = "HTTP test";
            OutputStream os = null;
            DataInputStream is = null;
            HttpURLConnection connection = null;

            try {
                URL url = new URL("http://localhost:" + PORT);
                connection =
                        (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                os = connection.getOutputStream();
                os.write(testString.getBytes());
                os.flush();
                assertEquals(200, connection.getResponseCode());
                is = new DataInputStream(connection.getInputStream());

                is.readInt();
            } finally {
                if (os != null) {
                    os.close();
                }

                if (is != null) {
                    is.close();
                }

                if (connection != null) {
                    connection.disconnect();
                }
            }

            // ------------- Test Simple protocol --------------------------
            Socket s = new Socket("localhost", PORT);
            String simpleTestString = simpleProtocolId + "This is simple protocol!";
            OutputStream output = s.getOutputStream();
            output.write(simpleTestString.getBytes());

            byte[] incomingBytes = new byte[simpleTestString.length()];
            InputStream input = s.getInputStream();
            int readSize = 0;
            while(readSize < incomingBytes.length) { 
                int len = input.read(incomingBytes, readSize,
                        incomingBytes.length - readSize);
                if (len == -1) {
                    throw new EOFException();
                }
                readSize += len;
            }

            assertEquals(simpleTestString, new String(incomingBytes));
        } finally {
            SelectorThreadUtils.stopSelectorThread(httpSelectorThread);
        }
    }

    public class CustomHttpAdapter extends GrizzlyAdapter {

        @Override
        public void service(GrizzlyRequest request, GrizzlyResponse response) {
            DataOutputStream os = null;
            try {
                os = new DataOutputStream(response.getOutputStream());
                os.writeInt(Integer.MAX_VALUE);
            } catch (IOException ex) {
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException ex) {
                    }
                }
            }
        }
    }

    public class SimpleProtocolFinder implements ProtocolFinder {

        private String protocolId;
        private byte[] protocolIdBytes;

        public SimpleProtocolFinder(String protocolId) {
            this.protocolId = protocolId;
            protocolIdBytes = protocolId.getBytes();
        }

        public String find(Context context, PUProtocolRequest protocolRequest) throws IOException {
            ByteBuffer buffer = protocolRequest.getByteBuffer();
            int position = buffer.position();
            int limit = buffer.limit();
            try {
                // Check if incoming buffer has protocol signature
                buffer.flip();
                if (buffer.remaining() >= protocolId.length()) {
                    for (int i = 0; i < protocolId.length(); i++) {
                        if (buffer.get(i) != protocolIdBytes[i]) {
                            return null;
                        }
                    }

                    return protocolId;
                }
            } finally {
                buffer.limit(limit);
                buffer.position(position);
            }

            return null;
        }
    }
    
    public class SimpleProtocolHandler implements ProtocolHandler {

        private String protocolId;

        public SimpleProtocolHandler(String protocolId) {
            this.protocolId = protocolId;
        }

        public String[] getProtocols() {
            return new String[] {protocolId};
        }

        public boolean handle(Context context, PUProtocolRequest protocolRequest) throws IOException {
            ByteBuffer buffer = protocolRequest.getByteBuffer();
            SelectionKey key = protocolRequest.getSelectionKey();

            buffer.flip();
            try {
                OutputWriter.flushChannel(key.channel(), buffer);
            } catch (Exception e) {
                context.getSelectorHandler().getSelectionKeyHandler().cancel(key);
            } finally {
                context.getSelectorHandler().register(key, SelectionKey.OP_READ);
                buffer.clear();
            }

            return true;
        }

        public boolean expireKey(SelectionKey key) {
            return true;
        }

        public ByteBuffer getByteBuffer() {
            // Use thread associated byte buffer
            return null;
        }
    }

    private SelectorThread createSelectorThread(int port, int selectorReadThreadsCount) {
        final SelectorThread selectorThread = new SelectorThread();
        selectorThread.setPort(port);
        selectorThread.setSelectorReadThreadsCount(selectorReadThreadsCount);

        return selectorThread;
    }
}
