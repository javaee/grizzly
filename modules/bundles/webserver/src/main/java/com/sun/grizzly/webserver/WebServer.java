/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.webserver;

import com.sun.grizzly.comet.CometAsyncFilter;
import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.cometd.standalone.CometdAdapter;
import com.sun.grizzly.standalone.StandaloneMainUtil;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.Controller;
import com.sun.grizzly.tcp.http11.GrizzlyAdapterChain;
import com.sun.grizzly.util.ClassLoaderUtil;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.Servlet;

/**
 * Basic startup class used when Grizzly standalone is used
 *
 * @author Jeanfrancois Arcand
 */
public class WebServer extends StandaloneMainUtil {

    static int port = 8080;
    private String applicationLoc;
    private String servletClassName;
    private static Logger logger = Controller.logger();

    public WebServer() {
    }

    public static void main(String args[]) throws Exception {
        WebServer main = new WebServer();
        main.start(args);
    }

    @Override
    public SelectorThread createSelectorThread(String[] args) throws Exception {
        SelectorThread st = super.createSelectorThread(args);
        st.setEnableAsyncExecution(true);

        AsyncHandler asyncHandler = new DefaultAsyncHandler();
        asyncHandler.addAsyncFilter(new CometAsyncFilter());

        st.setAsyncHandler(asyncHandler);

        logger.log(Level.INFO, "Starting Grizzly WebServer with support for Comet, " +
                "Bayeux, Servlet and Static Resources");
        return st;
    }

    @Override
    public void printHelpAndExit() {
        logger.severe("Usage: " + WebServer.class.getCanonicalName() + " [options]");
        logger.severe("\n");
        logger.severe("    -p, --port=port                  Runs WebServer on the specified port.");
        logger.severe("                                     Default: 8080");
        logger.severe("    -a, --apps=application path      The static resource folder or jar or war location.");
        logger.severe("                                     Default: .");
        logger.severe("    -h, --help                       Show this help message.");
        System.exit(1);
    }

    @Override
    public boolean parseOptions(String[] args) {
        // parse options
        for (int i = 0; i < args.length - 1; i++) {
            String arg = args[i];

            if ("-h".equals(arg) || "--help".equals(arg)) {
                printHelpAndExit();
            } else if ("-a".equals(arg)) {
                i++;
                applicationLoc = args[i];
            } else if (arg.startsWith("--application=")) {
                applicationLoc = arg.substring("--application=".length(), arg.length());
            } else if ("-p".equals(arg)) {
                i++;
                setPort(args[i]);
            } else if (arg.startsWith("--port=")) {
                String num = arg.substring("--port=".length(), arg.length());
                setPort(num);
            }
        }

        if (applicationLoc == null) {
            logger.severe("Illegal War|Jar file or folder location.");
            printHelpAndExit();
        }

        if (args.length > 4) {
            servletClassName = args[args.length - 1];
        }
        return true;
    }

    @Override
    public Adapter configureAdapter(SelectorThread st) {
        GrizzlyAdapterChain adapter = new GrizzlyAdapterChain();

        if (servletClassName != null) {
            ServletAdapter sa = new ServletAdapter();
            Servlet servlet = (Servlet) ClassLoaderUtil.load(servletClassName);
            String urlName = servletClassName.substring(servletClassName.lastIndexOf(".") + 1);
            logger.info("Servlet requests will be serviced" +
                    " from http://host:port/" + urlName);
            sa.setContextPath("/" + urlName);
            sa.setHandleStaticResources(true);
            sa.setRootFolder(st.getWebAppRootPath());

            if (servlet == null) {
                throw new IllegalStateException("Invalid Servlet ClassName");
            } else {
                sa.setServletInstance(servlet);
            }
            adapter.addGrizzlyAdapter(sa, new String[]{"/" + urlName});
        }
        adapter.addGrizzlyAdapter(new CometdAdapter(),new String[]{"/cometd"});
        return adapter;
    }

    @Override
    public String parseApplicationLocation(String[] args) {
        return applicationLoc;
    }
}
