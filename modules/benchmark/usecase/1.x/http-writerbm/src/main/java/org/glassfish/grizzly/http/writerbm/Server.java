/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.writerbm;

import com.sun.grizzly.Controller;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.StatsThreadPool;
import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.tcp.http11.GrizzlySession;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.TimeUnit;

final class Server {

    private GrizzlyWebServer httpServer;
    private final Settings settings;

    // -------------------------------------------------------- Constructors


    public Server(final Settings settings) {
        this.settings = settings;
        httpServer = new GrizzlyWebServer(settings.getPort());
        httpServer.addGrizzlyAdapter(new BlockingEchoAdapter(), new String[] { "/echo" });
        final int poolSize = (settings.getWorkerThreads() + settings.getSelectorThreads());
        final SelectorThread selector = httpServer.getSelectorThread();
        StatsThreadPool pool = new StatsThreadPool(poolSize, poolSize, -1, StatsThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT, TimeUnit.MILLISECONDS);
        pool.start();
        selector.setCoreThreads(poolSize);
        selector.setMaxThreads(poolSize);
        selector.setThreadPool(pool);
        selector.setUseChunking(settings.isChunked());
        final Controller controller = new Controller();
        controller.setReadThreadsCount(settings.getSelectorThreads() - 1);
        controller.setHandleReadWriteConcurrently(true);
        controller.useLeaderFollowerStrategy(settings.isUseLeaderFollower());
        selector.setController(controller);

    }


    // ------------------------------------------------------ Public Methods


    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    public static void main(String[] args) {
        final Settings settings = Settings.parse(args);
        Server server = new Server(settings);
        try {
            server.run();
            System.out.println(settings.toString());
            Thread.currentThread().join();
        } catch (Exception ioe) {
            System.err.println(ioe);
            System.exit(1);
        } finally {
            try {
                server.shutdownNow();
            } catch (IOException ioe) {
                System.err.println(ioe);
            }
        }
    }

    public void run() throws IOException {
        httpServer.start();
        System.out.println("Echo Server listener on port: " + settings.getPort());
    }

    public void stop() throws IOException {
        httpServer.shutdownNow();
    }


    // ------------------------------------------------------ Nested Classes


    private final class BlockingEchoAdapter extends GrizzlyAdapter {


        // ---------------------------------------- Methods from HttpService


        /**
         * This method should contains the logic for any http extension to the
         * Grizzly HTTP Webserver.
         *
         * @param request  The  {@link com.sun.grizzly.tcp.http11.GrizzlyRequest}
         * @param response The  {@link com.sun.grizzly.tcp.http11.GrizzlyResponse}
         */
        @Override
        public void service(GrizzlyRequest request, GrizzlyResponse response)
                throws IOException {
            Writer out = response.getWriter();

//            GrizzlySession session;

            try {
                response.setContentType("text/html");
                response.setHeader("X-Powered-By", "JSP/2.2");

                out.write("<!doctype html public \"-//w3c/dtd HTML 4.0//en\">\n<html>\n<!-- Copyright (c) 1999-2000 by BEA Systems, Inc. All Rights Reserved.-->\n<head>\n<title>Simple Session</title>\n</head>\n\n<body bgcolor=\"#FFFFFF\">\n<p><img src=\"images/BEA_Button_Final_web.gif\" align=right>\n<font face=\"Helvetica\">\n\n<h2>\n<font color=#DB1260>\nSimple Session\n</font>\n</h2>\n\n<p>\nThis JSP shows simple principles of session management\nby incrementing a counter each time a user accesses a page.\n\n<p>\n\n");
                out.write('\n');
                out.write('\n');

//                session = request.getSession(true);

//                Integer ival = (Integer) session.getAttribute("simplesession.counter");
                Integer ival = null;
                if (ival == null) {
                    ival = new Integer(1);
                } else {
                    ival = new Integer(ival.intValue() + 1);
                }
//                session.setAttribute("simplesession.counter", ival);

                out.write('\n');
                out.write('\n');

//                Integer cnt = (Integer) session.getAttribute("simplesession.hitcount");
                Integer cnt = null;
                if (cnt == null) {
                    cnt = new Integer(1);
                } else {
                    cnt = new Integer(cnt.intValue() + 1);
                }

//                session.setAttribute("simplesession.hitcount", cnt);

                out.write("\n\n<table border=1 cellpadding=6><tr><td width=50% valign=top>\n<font face=\"Helvetica\">\n<h3>\nYou have hit this page <font color=red> ");
                out.write(String.valueOf(ival));
                out.write("</font> time");
                out.write((ival.intValue() == 1) ? "" : "s");
                out.write(", <br>before the session times out.\n</h3>\nThe value in <font color=red><b>red</b></font> is stored in the HTTP session (<font face=\"Courier New\" size=-1>javax.servlet.http.HttpSession</font>), in an object named <font face=\"Courier New\" size=-1>simplesession.counter</font>. This object has <i>session</i> scope and its integer value is re-set to <font color=red><b>1</b></font> when you reload the page after the session has timed out.\n<p>\nYou can change the time interval after which a session times out. For more information, see the Administrators Guide on <a href= http://e-docs.bea.com/wls/docs60/adminguide/config_web_app.html#session-timeout>Session Timeouts</a>.\n</font></td>\n\n<td width=50% valign=top><font face=\"Helvetica\">\n<h3>You have hit this page a total of <font color=green> ");
                out.write(String.valueOf(cnt));
                out.write("</font> time");
                out.write((cnt.intValue() == 1) ? "" : "s");
                out.write("!\n</h3>\t\n\nThe value in <font color=green><b>green</b></font> is stored in the\nServlet Context (<font face=\"Courier New\" size=-1>javax.servlet.ServletContext)</font>, in an object named <font face=\"Courier New\" size=-1>simplesession.hitcount</font>. This object\nhas <i>application</i> scope and its integer value is incremented each time you\nreload the page.\n\n</font>\n</td>\n</tr></table>\n\n<p>\n<font size=-1>Copyright (c) 1999-2000 by BEA Systems, Inc. All Rights Reserved.\n</font>\n\n</font>\n</body>\n</html>\n");
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

    } // END BlockingEchoAdapter


} // END EchoServer
