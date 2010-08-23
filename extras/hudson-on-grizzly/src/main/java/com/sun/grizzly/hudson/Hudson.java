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

package com.sun.grizzly.hudson;



import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.standalone.servlet.ServletLauncher;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.util.ClassLoaderUtil;
import java.io.IOException;
import java.net.MalformedURLException;
import javax.servlet.Filter;
import javax.servlet.Servlet;

/**
 * Basic startup class used when Grizzly standalone is used
 *
 * @author Jeanfrancois Arcand
 */
public class Hudson extends ServletLauncher{
    
    public Hudson() {
    }
    
    
    public static void main( String args[] ) throws Exception { 
        Hudson sl = new Hudson();
        sl.start(args);
    }
    
    @Override
    public Adapter configureAdapter(SelectorThread st) {
        ServletAdapter adapter = new ServletAdapter();
        adapter.setRootFolder(st.getWebAppRootPath());
        adapter.setHandleStaticResources(true);
        
        adapter.setContextPath("/hudson");
        
        // Set the Servlet
        Servlet servlet = (Servlet)ClassLoaderUtil.load("org.kohsuke.stapler.Stapler");
        adapter.setServletInstance(servlet);
        
        // Add the Filter
        Filter filter = (Filter)ClassLoaderUtil.load("hudson.security.HudsonFilter");
        adapter.addFilter(filter, "authentication-filter", null);
        
        adapter.addInitParameter("default-encoding", "UTF-8");

        // Add the Filter
        filter = (Filter)ClassLoaderUtil.load("hudson.util.PluginServletFilter");
        adapter.addFilter(filter, "plugins-filter", null);
        
        // Add the Listener
        adapter.addServletListener("hudson.WebAppMain");
                
        // load-on-startup
        adapter.setProperty("load-on-startup","1");

        return adapter;
    }   
    
    /**
     * Set HUDSON_HOME
     * @return the exploded war file location.
     */
    @Override
    public String appendWarContentToClassPath(String appliPath) throws MalformedURLException, IOException{
        String hudsonHome = super.appendWarContentToClassPath(appliPath);
        System.setProperty("HUDSON_HOME", hudsonHome);
        return hudsonHome;
    }
    
    @Override
    public boolean parseOptions(String[] args) {
        // parse options
        for (int i = 0; i < args.length - 1; i++) {
            String arg = args[i];
            
            if("-h".equals(arg) || "--help".equals(arg)) {
                printHelpAndExit();
            } else if("-a".equals(arg)) {
                i ++;
                applicationLoc = args[i];
            } else if(arg.startsWith("--application=")) {
                applicationLoc = arg.substring("--application=".length(), arg.length());
            } else if ("-p".equals(arg)) {
                i ++;
                setPort(args[i]);
            } else if (arg.startsWith("--port=")) {
                String num = arg.substring("--port=".length(), arg.length());
                setPort(num);
            }
        }
        
        if(applicationLoc == null) {
            System.err.println("Illegal War|Jar file or folder location.");
            printHelpAndExit();
        }
        return true;
    }    
   
}
