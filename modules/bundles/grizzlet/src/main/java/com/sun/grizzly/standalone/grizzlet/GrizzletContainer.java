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
package com.sun.grizzly.standalone.grizzlet;

import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.comet.CometAsyncFilter;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.container.GrizzletAdapter;
import com.sun.grizzly.grizzlet.Grizzlet;
import com.sun.grizzly.standalone.StandaloneMainUtil;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.util.ClassLoaderUtil;


/**
 * Start the Grizzlet Container.
 *
 * @author Jeanfrancois Arcand
 */
public class GrizzletContainer extends StandaloneMainUtil {

    private static String grizzletClassName;
    private static String applicationLoc;

    public GrizzletContainer() {
    }

    public static void main(String args[]) throws Exception {
        GrizzletContainer main = new GrizzletContainer();
        main.start(args);
    }    
    
    
    @Override
    public SelectorThread createSelectorThread(String[] args) throws Exception{
        SelectorThread st = super.createSelectorThread(args);
        st.setEnableAsyncExecution(true);
        st.setBufferResponse(false);    
        st.setFileCacheIsEnabled(false);
        st.setLargeFileCacheEnabled(false);
        AsyncHandler asyncHandler = new DefaultAsyncHandler();
        asyncHandler.addAsyncFilter(new CometAsyncFilter()); 
        st.setAsyncHandler(asyncHandler);
        return st;
    }   
    
    
    public void printHelpAndExit() {
        System.err.println("Usage: " + GrizzletContainer.class.getCanonicalName() + " [options] GRIZZLET_NAME");
        System.err.println();
        System.err.println("    -p, --port=port                  Runs GrizzletContainer on the specified port.");
        System.err.println("                                     Default: 8080");
        System.err.println("    -a, --apps=application path      The Grizzlet folder or jar or war location.");
        System.err.println("                                     Default: .");
        System.err.println("    -h, --help                       Show this help message.");
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
        
        if (applicationLoc == null){
             System.err.println("No --application or -a specified.");
             printHelpAndExit();           
        }

        grizzletClassName = args[args.length - 1];
        if (grizzletClassName == null) {
            System.err.println("Illegal Grizzlet name.");
            printHelpAndExit();
        }
        return true;
    }

    @Override
    public Adapter configureAdapter(SelectorThread st) {
        GrizzletAdapter adapter = new GrizzletAdapter("/comet");
        adapter.setRootFolder(SelectorThread.getWebAppRootPath());
        st.setAdapter(adapter);
        Grizzlet grizzlet = (Grizzlet) ClassLoaderUtil.load(grizzletClassName);

        if (grizzlet == null) {
            throw new IllegalStateException("Invalid Grizzlet ClassName");
        } else {

            System.out.println("Launching Grizzlet: " +
                    grizzlet.getClass().getName());

            adapter.setGrizzlet(grizzlet);
        }
        return adapter;
    }

    
    @Override
    public String parseApplicationLocation(String[] args) {
        return applicationLoc;
    }
}
