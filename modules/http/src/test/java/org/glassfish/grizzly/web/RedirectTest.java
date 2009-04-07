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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.web.container.http11.GrizzlyAdapter;
import org.glassfish.grizzly.web.container.http11.GrizzlyRequest;
import org.glassfish.grizzly.web.container.http11.GrizzlyResponse;

/**
 * Units test for RFE 401
 * 
 * @author Jeanfrancois Arcand
 */
public class RedirectTest extends TestCase {

    public static final int PORT = 18890;
    private static Logger logger = Logger.getLogger("grizzly.test");
    private TCPNIOTransport transport;
    private WebFilter webFilter;

    public void initTransport(GrizzlyAdapter adapter) {
        transport = TransportFactory.getInstance().createTCPTransport();
        
        WebFilterConfig webConfig = new WebFilterConfig();
        webConfig.setAdapter(adapter);
        webConfig.setDisplayConfiguration(true);

        webFilter = new WebFilter("redirect-test", webConfig);
        webFilter.enableMonitoring();

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

    public void testRedirectWithHyperlink() throws IOException {
        System.out.println("Test: testRedirectWithHyperlink");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);

        try {
            initTransport(new GrizzlyAdapter() {

                @Override
                public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                    try{
                        res.sendRedirect("/foo/password.txt");
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            });
            
            Socket s = new Socket("localhost", PORT);
            s.setSoTimeout(5000);
            OutputStream os = s.getOutputStream();

            System.out.println(("GET /foo/password.txt HTTP/1.1\n"));
            os.write(("GET /foo/password.txt HTTP/1.1\n").getBytes());
            os.write(("Host: localhost:" + PORT + "\n").getBytes());
            os.write("\n".getBytes());

            try{
                InputStream is = new DataInputStream(s.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line = null;
                while( (line = br.readLine()) != null){
                    System.out.println("-> " + line);
                    if (line.contains("http://localhost:" + PORT + "/foo/password.txt"))   {
                        assertTrue(true);
                        return;
                    }
                }
            } catch (IOException ex){
                ex.printStackTrace();
                assertFalse(false);
            }
        } finally {
            stopTransport();
            pe.shutdown();
        }
    }
         
         
    public void testRedirectWithInvalidHyperlink() throws IOException {
        System.out.println("Test: testRedirectWithHyperlink");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        try {
            initTransport(new GrizzlyAdapter() {

                @Override
                public void service(final GrizzlyRequest req, final GrizzlyResponse res) {
                    try{
                        res.sendRedirect("/foo/../../password.txt");
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            });

            URL url = new URL("http://localhost:" + PORT);
            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();
            os.write("Hello".getBytes());
            os.flush();

            try{
                InputStream is = new DataInputStream(connection.getInputStream());
            } catch (IOException ex){
                if (connection.getResponseCode() == 400){
                    assertTrue(true);
                } else {
                    assertFalse(false);
                }
            }
        } finally {
            stopTransport();
            pe.shutdown();
        }
    }

}
