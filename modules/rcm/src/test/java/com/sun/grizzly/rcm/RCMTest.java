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

package com.sun.grizzly.rcm;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import java.io.IOException;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Date;
import junit.framework.TestCase;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.nio.transport.TCPNIOTransport;

/**
 * Basic RCM test.
 *
 * @author Jeanfrancois Arcand
 */
public class RCMTest extends TestCase {
    
    private TCPNIOTransport transport;

    static int port = 18891;
    
    static String folder = ".";
    
    public RCMTest() {
    }
    
    
    /*
     * @see TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        
        final ResourceAllocationFilter parser = new ResourceAllocationFilter();

        // Create a FilterChain using FilterChainBuilder
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.singleton();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(parser);
        filterChainBuilder.add(new BaseFilter() {

            /**
             * <code>CharBuffer</code> used to store the HTML response, containing
             * the headers and the body of the response.
             */
            private CharBuffer reponseBuffer = CharBuffer.allocate(4096);
            /**
             * Encoder used to encode the HTML response
             */
            private CharsetEncoder encoder =
                    Charset.forName("UTF-8").newEncoder();

            @Override
            public NextAction handleRead(FilterChainContext ctx,
                    NextAction nextAction) throws IOException {
                try {
                    final Buffer requestBuffer = (Buffer) ctx.getMessage();
                    final Connection connection = ctx.getConnection();

                    final MemoryManager memoryManager =
                            ctx.getConnection().getTransport().getMemoryManager();

                    requestBuffer.limit(requestBuffer.position());

                    reponseBuffer.clear();
                    reponseBuffer.put("HTTP/1.1 200 OK\r\n");
                    appendHeaderValue("Content-Type", "text/html");
                    appendHeaderValue("Content-Length", 0 + "");
                    appendHeaderValue("Date", new Date().toString());
                    appendHeaderValue("RCMTest", "passed");
                    appendHeaderValue("Connection", "Close");
                    reponseBuffer.put("\r\n\r\n");
                    reponseBuffer.flip();
                    ByteBuffer rBuf = encoder.encode(reponseBuffer);

                    final Buffer writeBuffer = MemoryUtils.wrap(memoryManager, rBuf);
                    ctx.write(writeBuffer, null);
                } catch (IOException t) {
                    t.printStackTrace();
                    throw t;
                }

                ctx.getConnection().close();

                return ctx.getStopAction();
            }

            /**
             * Utility to add headers to the HTTP response.
             */
            private void appendHeaderValue(String name, String value) {
                reponseBuffer.put(name);
                reponseBuffer.put(": ");
                reponseBuffer.put(value);
                reponseBuffer.put("\r\n");
            }
        });

        transport = TransportFactory.getInstance().createTCPTransport();
        transport.setProcessor(filterChainBuilder.build());
        
        transport.bind(port);
        transport.start();
    }
    
    
    /*
     * @see TestCase#tearDown()
     */
    @Override
    protected void tearDown() throws Exception {
        transport.stop();
        TransportFactory.getInstance().close();
    }

    public void testRCM(){
        try{
            Socket s = new Socket("127.0.0.1", port);
            OutputStream os = s.getOutputStream();
            s.setSoTimeout(10000);

            os.write(("GET /index.html HTTP/1.0\n").getBytes());
            os.write("\n".getBytes());

            InputStream is = s.getInputStream();
            BufferedReader bis = new BufferedReader(new InputStreamReader(is));
            String line = null;

            int index;
            while ((line = bis.readLine()) != null) {
                if (line.startsWith("RCMTest")){
                    assertTrue(true);
                    return;
                }
            }
        } catch (Throwable ex){
            System.out.println("Unable to connect to: " + port);
            ex.printStackTrace();
        }
        fail("Wrong header response");
    }    
}
