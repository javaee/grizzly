/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.cometd.client;

import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.comet.CometAsyncFilter;
import com.sun.grizzly.cometd.standalone.CometdAdapter;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.StatsThreadPool;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.Logger;

import com.sun.grizzly.util.Utils;
import junit.framework.TestCase;

import org.mortbay.cometd.client.BayeuxLoadGenerator;

/**
 * Issue 174
 * @author Shing Wai Chan
 */
public class BayeuxClientLoadTest extends TestCase {
    public static final int PORT = 18890;
    private String host = "localhost";

    private static Logger logger = Logger.getLogger("grizzly.test");

    private SelectorThread st;


    /*
     * @see TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception{
        st = new SelectorThread();
        st.setPort(PORT);
        st.setAdapter(new CometdAdapter());

        StatsThreadPool threadPool = new StatsThreadPool();
        threadPool.setMaximumPoolSize(600);
        st.setThreadPool(threadPool);
        
        st.setEnableAsyncExecution(true);
        AsyncHandler asyncHandler = new DefaultAsyncHandler();
        asyncHandler.addAsyncFilter(new CometAsyncFilter()); 
        st.setAsyncHandler(asyncHandler);
        st.setDisplayConfiguration(Utils.VERBOSE_TESTS);
        
        try {
            st.listen();
        } catch (Exception ex) {
            ex.printStackTrace();
        }        
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (st != null){
            //avoiding log spam from closed clients when stoping endpoint
            PrintStream ps = System.err;
            System.setErr(new PrintStream(new OutputStream() {
                public void write(int b) throws IOException {
             }}));
            st.stopEndpoint();
            Thread.sleep(2000);
            System.setErr(ps);
        }
    }

    public void testLoad1() throws Exception {
        doTest(100,  1, 100, 1000,  50,   100);
    }
   
   /* public void testLoad2() throws Exception {
        doTest(10,   1, 200, 100,   25000, 1000);
    }*/
    

    public void doTest(int rooms, int rooms_per_client, int nclients, int publish , int msgsize, int pause) throws Exception {
        
        String uri = "/cometd/cometd";
        String base = "/chat/demo";
        int maxLatency = 5000;

        String chat = "";
        for (int i = 0; i < msgsize; i++) {
            chat += "x";
        }
        
        int burst = 10;

        BayeuxLoadGenerator generator = new BayeuxLoadGenerator();
        generator.initSocketAddress(host, PORT);
        
        Utils.dumpErr("rooms: "+rooms +" roomsPerclient: "+rooms_per_client+
                " clients: "+nclients+" publish: "+publish+" msgsize: "+msgsize+
                " publish pause: "+pause+"ms");

        long got = generator.generateLoad(uri, base, rooms, rooms_per_client,
                maxLatency, nclients, publish, chat, pause, burst);
        publish=(publish*nclients)/rooms;
        assertTrue(got > publish*0.9);
    }

}
