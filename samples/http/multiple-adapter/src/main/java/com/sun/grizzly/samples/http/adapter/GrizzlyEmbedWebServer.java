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

package com.sun.grizzly.samples.http.adapter;

import com.sun.grizzly.http.embed.GrizzlyWebServer;

import com.sun.grizzly.http.servlet.ServletAdapter;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Simple demo that demonstrate how to add more than one {@link GrizzlyAdapter}
 * and how the request are properly mapped to their associated {@link GrizzlyAdapter}
 * 
 * @author Jeanfrancois Arcand
 */
public class GrizzlyEmbedWebServer {

    public static void main( String args[] ) throws Exception { 
        String path = args[0];
        if (args[0] == null || path == null){
            System.out.println("Invalid static resource path");
            System.exit(-1);            
        }
        
        GrizzlyWebServer ws = new GrizzlyWebServer(path);
        ServletAdapter sa = new ServletAdapter();
        sa.setRootFolder(".");
        sa.setServletInstance(new ServletTest("Adapter-1"));
        ws.addGrizzlyAdapter(sa, new String[]{"/1"});

        ServletAdapter sa2 = new ServletAdapter();
        sa2.setRootFolder("/tmp");
        sa2.setServletInstance(new ServletTest("Adapter-2"));
        ws.addGrizzlyAdapter(sa2, new String[]{"/2"});
        
        ServletAdapter sa3 = sa.newServletAdapter(new ServletTest("Adapter-3"));
        sa3.setRootFolder("/tmp");
        ws.addGrizzlyAdapter(sa3, new String[]{"/3"});
        
        System.out.println("Grizzly WebServer listening on port 8080");
        ws.start();
    }

    static class ServletTest extends HttpServlet {

        private String key;
        
        private ServletContext sc;

        public ServletTest(String key){
            this.key = key;
        }
        
        @Override
        public void init(ServletConfig sg){
            sc = sg.getServletContext();
        }

        @Override
        public void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            doPost(request, response);
        }

        @Override
        public void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            if (key.equals("Adapter-1")){
                sc.setAttribute("share", "with-Adapter-3");
            }
            System.out.println(sc.getAttribute("share"));
            response.setContentType("text/plain");
            response.setStatus(HttpServletResponse.SC_OK);
            PrintWriter wrt = response.getWriter();
            for (int i = 0; i < 1 ; i++) {
                wrt.write(key);
            }
        }
    }
}


