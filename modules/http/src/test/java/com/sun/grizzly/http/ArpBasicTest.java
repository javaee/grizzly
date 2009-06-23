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
package com.sun.grizzly.http;

import com.sun.grizzly.ControllerStateListenerAdapter;
import com.sun.grizzly.arp.AsyncFilter;
import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;

/**
 * TEST ARP.
 * @author Jeanfrancois Arcand
 */
public class ArpBasicTest extends TestCase {

    private static Logger logger = Logger.getLogger("grizzly.test");
    private SelectorThread st;

    public void createSelectorThread(int port) {
        st = new SelectorThread() {

            /**
             * Start the SelectorThread using its own thread and don't block the Thread.
             *  This method should be used when Grizzly is embedded.
             */
            @Override
            public void listen() throws IOException, InstantiationException {
                initEndpoint();
                final CountDownLatch latch = new CountDownLatch(1);
                controller.addStateListener(new ControllerStateListenerAdapter() {

                    @Override
                    public void onReady() {
                        enableMonitoring();
                        latch.countDown();
                    }

                    @Override
                    public void onException(Throwable e) {
                        if (latch.getCount() > 0) {
                            logger().log(Level.SEVERE, "Exception during " +
                                    "starting the controller", e);
                            latch.countDown();
                        } else {
                            logger().log(Level.SEVERE, "Exception during " +
                                    "controller processing", e);
                        }
                    }
                });

                super.start();

                try {
                    latch.await();
                } catch (InterruptedException ex) {
                }

                if (!controller.isStarted()) {
                    throw new IllegalStateException("Controller is not started!");
                }
            }
        };

        st.setPort(port);
        st.setDisplayConfiguration(true);

    }
    
    protected HttpURLConnection getConnection(String alias, int port) throws IOException {
        URL url = new URL("http", "localhost", port, alias);
        HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
        urlConn.connect();
        return urlConn;
    }

    public void testMultipleAsyncFilter() throws Exception {
        System.out.println("Test: testMultipleAsyncFilter");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "HelloWorld";
        final byte[] testData = testString.getBytes();
        try {
            createSelectorThread(0);
            AsyncHandler handler = new DefaultAsyncHandler();
            handler.addAsyncFilter(new MyAsyncFilter());
            handler.addAsyncFilter(new MyAsyncFilter());
            st.setAdapter(new MyAdapter());
            st.setAsyncHandler(handler);
            st.setEnableAsyncExecution(true);
            st.listen();

            HttpURLConnection conn = getConnection("/", st.getPort());
            String s = conn.getHeaderField("AsyncFilter-1");
            assertEquals(s, "1");
            
            s = conn.getHeaderField("AsyncFilter-2");
            assertEquals(s, "2");

            s = conn.getHeaderField("Count");
            assertEquals(s, "1");
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
            pe.shutdown();
        }
    }

    static class MyAsyncFilter implements AsyncFilter {
        private static int count = 0;

        public boolean doFilter(AsyncExecutor executor) {
            try {
                if (count++ == 0){
                    System.out.println(this + "-execute");
                    executor.getProcessorTask().getRequest().getResponse()
                            .addHeader("AsyncFilter-1", "1");
                    executor.execute();
                } else {
                    executor.getProcessorTask().getRequest().getResponse()
                            .addHeader("AsyncFilter-2", "2");
                    System.out.println(this + "-postExecute");
                    executor.postExecute();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return true;
        }
    }

    static class MyAdapter extends GrizzlyAdapter {
        private static int count = 0;

        public void service(GrizzlyRequest request, GrizzlyResponse response){
            count++;
            System.out.println(count);
            response.addHeader("Count", String.valueOf(count));
        }
    }
}
