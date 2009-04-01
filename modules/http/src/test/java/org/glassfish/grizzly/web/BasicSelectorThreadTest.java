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

import org.glassfish.grizzly.web.container.http11.GrizzlyAdapter;
import org.glassfish.grizzly.web.container.http11.GrizzlyRequest;
import org.glassfish.grizzly.web.container.http11.GrizzlyResponse;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

/**
 * Basic {@link SelectoThread} test.
 * 
 * @author Jeanfrancois Arcand
 */
public class BasicSelectorThreadTest extends TestCase {

    public static final int PORT = 18890;
    private static Logger logger = Logger.getLogger("grizzly.test");
    private TCPNIOTransport transport;
    private WebFilter webFilter;

    public void initTransport() throws IOException {
        transport = TransportFactory.getInstance().createTCPTransport();
        webFilter = new WebFilter(Integer.toString(PORT));
        webFilter.getConfig().setDisplayConfiguration(true);
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(webFilter);
    }


    public void testHelloWorldGrizzlyAdapter() throws IOException {
        System.out.println("Test: testHelloWorldGrizzlyAdapter");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "HelloWorld";
        final byte[] testData = testString.getBytes();
        try {
            initTransport();
            webFilter.setAdapter(new HelloWorldAdapter());

            try {
                webFilter.init();
                webFilter.enableMonitoring();
                transport.bind(PORT);
                transport.start();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            sendRequest(testData, testString);


        } finally {
            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    public class HelloWorldAdapter extends GrizzlyAdapter{

        @Override
        public void service(GrizzlyRequest request, GrizzlyResponse response) {
            try {
                response.getWriter().println("HelloWorld");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } 
    }



    private String sendRequest(byte[] testData, String testString)
            throws MalformedURLException, ProtocolException, IOException {

        return sendRequest(testData, testString, true);
    }

    private String sendRequest(byte[] testData, String testString, boolean assertTrue)
            throws MalformedURLException, ProtocolException, IOException {
        byte[] response = new byte[testData.length];

        URL url = new URL("http://localhost:" + PORT);
        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        OutputStream os = connection.getOutputStream();
        os.write("Hello".getBytes());
        os.flush();

        InputStream is = new DataInputStream(connection.getInputStream());
        response = new byte[testData.length];
        is.read(response);


        String r = new String(response);
        if (assertTrue) {
            System.out.println("Response: " + r);
            assertEquals(testString, r);
        }
        connection.disconnect();
        return r;
    }
}
