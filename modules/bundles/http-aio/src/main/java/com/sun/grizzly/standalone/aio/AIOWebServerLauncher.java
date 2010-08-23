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

package com.sun.grizzly.standalone.aio;

import com.sun.grizzly.aio.http.AsyncAcceptorThread;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.tcp.DynamicContentAdapter;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import java.io.IOException;
import com.sun.grizzly.util.ClassLoaderUtil;
import com.sun.grizzly.standalone.StandaloneMainUtil;
import com.sun.grizzly.standalone.StaticStreamAlgorithm;
/**
 * Basic startup class used when Grizzly standalone is used
 *
 * @author Jeanfrancois Arcand
 */
public class AIOWebServerLauncher extends StandaloneMainUtil {

    private static final String ENABLE_RCM = "com.sun.grizzly.enableRCM";
    private static final String ENABLE_ASYNC = "com.sun.grizzly.enableARP";
    static int port = 8080;
    static String folder = ".";

    public AIOWebServerLauncher() {
    }

    public static void main(String args[]) throws Exception {
        AIOWebServerLauncher main = new AIOWebServerLauncher();
        main.start(args);
    }

    @Override
    public SelectorThread createSelectorThread(String[] args) throws Exception {
        if (args.length == 0) {
            printHelpAndExit();
        }
        final long t1 = System.currentTimeMillis();

        // parse options
        parseOptions(args);
        String appliPath = parseApplicationLocation(args);
        appliPath = appendWarContentToClassPath(appliPath);

        final SelectorThread st = new AsyncAcceptorThread() {
            @Override
            public void listen() throws InstantiationException, IOException {
                super.listen();
                System.out.println("Server started in " + (System.currentTimeMillis() - t1) + " milliseconds.");
            }
            };
        st.setAlgorithmClassName(StaticStreamAlgorithm.class.getName());
        st.setPort(port);
        SelectorThread.setWebAppRootPath(appliPath);

        st.setAdapter(configureAdapter(st));

        boolean enableRcm = Boolean.valueOf(System.getProperty(ENABLE_RCM));
        st.enableRcmSupport(enableRcm);

        boolean enableAsync = Boolean.valueOf(System.getProperty(ENABLE_ASYNC));
        st.setEnableAsyncExecution(enableAsync);
        if (enableAsync) {
            st.setAsyncHandler(new DefaultAsyncHandler());
        }
        return st;
    }

    @Override
    public void printHelpAndExit() {
        System.err.println("Usage: " + AIOWebServerLauncher.class.getCanonicalName() + " [options]");
        System.err.println();
        System.err.println("    -p, --port=port                  Runs Servlet on the specified port.");
        System.err.println("                                     Default: 8080");
        System.err.println("    -a, --apps=application path      The static resourde folder or jar or war location.");
        System.err.println("                                     Default: .");
        System.err.println("    -h, --help                       Show this help message.");
        System.exit(1);
    }

    @Override
    public boolean parseOptions(String[] args) {
        if (args.length == 0) {
            printHelpAndExit();
            return false;
        }

        // parse options
        for (int i = 0; i < args.length - 1; i++) {
            String arg = args[i];

            if ("-h".equals(arg) || "--help".equals(arg)) {
                printHelpAndExit();
            } else if ("-a".equals(arg)) {
                i++;
                folder = args[i];
            } else if (arg.startsWith("--application=")) {
                folder = arg.substring("--application=".length(), arg.length());
            } else if ("-p".equals(arg)) {
                i++;
                setPort(args[i]);
            } else if (arg.startsWith("--port=")) {
                String num = arg.substring("--port=".length(), arg.length());
                setPort(num);
            }
        }
        return true;
    }

    @Override
    public Adapter configureAdapter(SelectorThread st) {
        String adapterClass = System.getProperty(ADAPTER);
        Adapter adapter;
        if (adapterClass == null) {
            adapter = new StaticResourcesAdapter(folder){
                /**
                 * Customize the error pahe 
                 * @param req The {@link Request} object
                 * @param res The {@link Response} object
                 * @throws java.lang.Exception
                 */
                protected void customizedErrorPage(Request req,
                        Response res) throws Exception {        

                    /**
                     * With Grizzly, we just return a 404 with a simple error message.
                     */ 
                    res.setMessage("Not Found");
                    res.setStatus(404);
                    res.setContentType("text/html");
                    res.flushHeaders();        
                    req.setNote(14, "SkipAfterService");
                }
                
            };
            ((StaticResourcesAdapter)adapter).setUseSendFile(false);
        } else {
            adapter = (Adapter) ClassLoaderUtil.load(adapterClass);
        }
        

        if (adapter instanceof StaticResourcesAdapter) {
            ((StaticResourcesAdapter) adapter).setRootFolder(folder);
        }

        if (adapter instanceof DynamicContentAdapter) {
            ((DynamicContentAdapter) adapter).setContextRoot("/");
        }
        return adapter;
    }

    @Override
    public String parseApplicationLocation(String[] args) {
        return folder;
    }
}
