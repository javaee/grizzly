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

package com.sun.grizzly.standalone.messagesbus;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.messagesbus.MessagesBus;
import com.sun.grizzly.standalone.comet.Comet;
import com.sun.grizzly.tcp.Adapter;

/**
 * Basic startup class used when Grizzly MessagesBus is used as a standalone
 * Servlet.
 *
 * @author Jeanfrancois Arcand
 */
public class Main extends Comet{
    
    public Main() {
    }
    
    public static void main( String args[] ) throws Exception { 
        Main sl = new Main();
        sl.start(args);
    }   
     
    
    @Override
    public Adapter configureAdapter(SelectorThread st) {
        ServletAdapter adapter = new ServletAdapter();
        adapter.setRootFolder(SelectorThread.getWebAppRootPath());
        st.setAdapter(adapter);
        adapter.setHandleStaticResources(false);
        MessagesBus mb = new MessagesBus();
        mb.setExpirationDelay(-1);
        adapter.setServletInstance(mb);
        return adapter;
    }   
    
    
    @Override
    public boolean parseOptions(String[] args) {
        return true;
    }
    
    
    /**
     * Create a single SelectorThread.
     * @param args The command line arguments.
     */
    @Override
    public SelectorThread createSelectorThread(String args[]) throws Exception {        
       if (args.length == 0){
           args = new String[]{"-p","8080"};
       }
       return super.createSelectorThread(args);
    }

    
    
    @Override
    public String parseApplicationLocation(String[] args){
        return "";
    }
    
    
    @Override
    public void printHelpAndExit() {
        Thread.dumpStack();
        System.err.println("Usage: " + Main.class.getCanonicalName());
        System.err.println();
        System.err.println("    -p, --port=port                  Runs Servlet on the specified port.");
        System.err.println("                                     Default: 8080");
        System.err.println("    -h, --help                       Show this help message.");
        System.exit(1);
    }
}
