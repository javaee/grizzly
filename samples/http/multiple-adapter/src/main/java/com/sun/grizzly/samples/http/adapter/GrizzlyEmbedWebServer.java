/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
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


