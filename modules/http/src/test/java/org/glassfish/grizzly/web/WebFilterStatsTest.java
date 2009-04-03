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

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.threadpool.ExtendedThreadPool;
import org.glassfish.grizzly.web.container.Adapter;
import org.glassfish.grizzly.web.container.OutputBuffer;
import org.glassfish.grizzly.web.container.Request;
import org.glassfish.grizzly.web.container.Response;
import org.glassfish.grizzly.web.container.util.buf.ByteChunk;

/**
 *
 * @author Peter Speck
 * @author Jeanfrancois Arcand
 */
public class WebFilterStatsTest extends TestCase {

    public static final int PORT = 18890;
    private static Logger logger = Logger.getLogger("grizzly.test");

    private TCPNIOTransport transport;
    private WebFilter webFilter;

    public void initTransport() {
        transport = TransportFactory.getInstance().createTCPTransport();
        ((ExtendedThreadPool) transport.getWorkerThreadPool()).setMaximumPoolSize(50);

        webFilter = new WebFilter("stats-test");
        
        webFilter.setAdapter(new MyAdapter());
        webFilter.getConfig().setCompression("off"); // don't let proxy compress stuff that's already compressed.
        webFilter.getConfig().setDisplayConfiguration(true);

        FileCache fileCache = new FileCache(webFilter);
        fileCache.setLargeFileCacheEnabled(false);
        webFilter.setFileCache(fileCache);

        webFilter.getConfig().setBufferResponse(false);
        webFilter.getConfig().setKeepAliveTimeoutInSeconds(30000);

        
        webFilter.getConfig().setRequestBufferSize(32768);
        webFilter.getConfig().setMaxKeepAliveRequests(8196);
        webFilter.enableMonitoring();
        
        //st.setKeepAliveThreadCount(500);

        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(webFilter);
        try {
            webFilter.initialize();
            transport.bind(PORT);
            transport.start();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void stopTransport() throws IOException {
        transport.stop();
        TransportFactory.getInstance().close();
    }

    public void testKeepAliveConnection() throws IOException {
        try {
            initTransport();
            String testString = "KAS:  conns=1, flushes=0, hits=1, refusals=0, timeouts=0";

            byte[] testData = testString.getBytes();
            byte[] response = new byte[testData.length];

            URL url = new URL("http://localhost:" + PORT);
            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();
            os.write(testString.getBytes());
            os.flush();
            InputStream is = new DataInputStream(connection.getInputStream());
            response = new byte[testData.length];
            is.read(response);
            System.out.println("Response: " + new String(response));
            assertEquals(testString, new String(response));
            connection.disconnect();
        } finally {
            stopTransport();
        }
    }

    public void testGetHits() throws IOException {

        try {
            initTransport();
            String testString = "KAS:  conns=1, flushes=0, hits=2, refusals=0, timeouts=0";

            byte[] testData = testString.getBytes();
            String response = "";

            
            Socket socket = new Socket("localhost", PORT);
            socket.setSoTimeout(30 * 1000);
            DataOutputStream os = new DataOutputStream(socket.getOutputStream());

            os.write("POST / HTTP/1.1\r\n".getBytes());
            os.write("Content-type: text/plain\r\n".getBytes());
            os.write("Host: localhost\r\n".getBytes());
            os.write(("Content-Length: " + testData.length +"\r\n\r\n").getBytes());
            os.write(testData);
            os.flush();

            InputStream is = socket.getInputStream();
            BufferedReader bis = new BufferedReader(new InputStreamReader(is));
            String line = null;

            boolean flip = true;
            boolean first = true;
            while ((line = bis.readLine()) != null) {
                System.out.println("-> " + line);
                if (line.startsWith("HTTP/1.1 200") && flip){
                    System.out.println("Post second request");
                    os.write("POST / HTTP/1.1\r\n".getBytes());
                    os.write("Content-type: text/plain\r\n".getBytes());
                    os.write("Host: localhost\r\n".getBytes());
                    os.write(("Content-Length: " + testData.length +"\r\n\r\n").getBytes());
                    os.write(testData);
                    os.flush();        
                    flip = false;
                } else if (line.startsWith("KAS: ")){
                    if (first) {
                        first = false;
                        continue;
                    } else {
                        response = line;
                        break;
                    }         
                }
            }  

            System.out.println("Response: " + response);
             
            assertEquals(testString, response);
        } finally {
            stopTransport();
        }
    }

    class MyAdapter implements Adapter {
        // Just in case this code is cut&pasted
        public synchronized void service(Request request, Response response) throws Exception {
            
            System.out.println("Request: " + request);
            
            KeepAliveStats kas = webFilter.getKeepAliveStats();
            String s;
            if (kas == null) {
                s = "KAS: missing\n";
            } else {
                s = "KAS:  conns=" + kas.getCountConnections() + ", flushes="
                        + kas.getCountFlushes() + ", hits=" + kas.getCountHits()
                        + ", refusals=" + kas.getCountRefusals() + ", timeouts=" 
                        + kas.getCountTimeouts() + "\n";
            }
            System.out.println("----->" + s);
            byte[] b = s.getBytes("iso-8859-1");
            sendPlainText(response, b);
        }

        private void sendPlainText(Response response, byte[] b) throws IOException {
            response.setContentType("text/plain");
            response.setContentLength(b.length);
            ByteChunk chunk = new ByteChunk();
            chunk.append(b, 0, b.length);
            OutputBuffer buffer = response.getOutputBuffer();
            buffer.doWrite(chunk, response);
            response.finish();
        }

        public void afterService(Request request, Response response) throws Exception {
            request.recycle();
            response.recycle();
        }

        public void fireAdapterEvent(String string, Object object) {
        }
    }
}
