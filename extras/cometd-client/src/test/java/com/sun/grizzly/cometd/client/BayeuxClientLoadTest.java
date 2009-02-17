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

package com.sun.grizzly.cometd.client;

import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.comet.CometAsyncFilter;
import com.sun.grizzly.cometd.standalone.CometdAdapter;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.StatsThreadPool;
import com.sun.grizzly.util.DefaultThreadPool;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
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
        threadPool.setMaximumPoolSize(256);
        st.setThreadPool(threadPool);
        
        st.setEnableAsyncExecution(true);
        AsyncHandler asyncHandler = new DefaultAsyncHandler();
        asyncHandler.addAsyncFilter(new CometAsyncFilter()); 
        st.setAsyncHandler(asyncHandler);
        st.setDisplayConfiguration(true);
        
        try {
            st.listen();
        } catch (Exception ex) {
            ex.printStackTrace();
        }        
    }

    public void testLoad() throws Exception {
        String uri = "/cometd/cometd";
        String base = "/chat/demo";
        int rooms = 100;
        int rooms_per_client = 1;
        int maxLatency = 5000;
        int nclients = 100;
        int publish = 1000;

        int size = 50;
        String chat = "";
        for (int i = 0; i < size; i++) {
            chat += "x";
        }

        int pause = 100;
        int burst = 10;

        BayeuxLoadGenerator generator = new BayeuxLoadGenerator();
        generator.initSocketAddress(host, PORT);
        long got = generator.generateLoad(uri, base, rooms, rooms_per_client,
                maxLatency, nclients, publish, chat, pause, burst);

        //XXX this is temporary
        int expectedAtLeast = (publish * 90) / 100;
        if (got != publish) {
            System.out.println("XXX Got " + got + " messages less than expected " + publish);   
        }
        //XXX check at least more than lower bound in this moment
        assertTrue(got > expectedAtLeast && got <= publish);
    }
}
