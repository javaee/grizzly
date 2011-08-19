/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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
package com.sun.grizzly.http;

import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.util.Utils;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;

/**
 * Request/NamedDispatcher Test
 *
 * @author Bongjae Chang
 */
public class RequestDispatcherTest extends GrizzlyWebServerAbstractTest {

    private static final int PORT = 18890 + 12;

    public void testForward() throws IOException {
        Utils.dumpOut("testForward");
        try {
            newGWS( PORT );

            String contextPath = "/webapp";

            ServletAdapter servletHandler1 = new ServletAdapter();
            servletHandler1.setServletInstance( new HttpServlet() {
                @Override
                public void doGet( HttpServletRequest request, HttpServletResponse response )
                        throws ServletException, IOException {
                    PrintWriter out = response.getWriter();
                    out.println( "Hello, world! I am a servlet1" );

                    // relative path test
                    RequestDispatcher dispatcher = request.getRequestDispatcher( "servlet2" );
                    assertNotNull( dispatcher );
                    dispatcher.forward( request, response );
                    out.close();
                }
            } );
            servletHandler1.setContextPath( contextPath );
            servletHandler1.setServletPath( "/servlet1" );
            addAdapter( contextPath + "/servlet1", servletHandler1 );

            ServletAdapter servletHandler2 = new ServletAdapter();
            servletHandler2.setServletInstance( new HttpServlet() {
                @Override
                public void doGet( HttpServletRequest request, HttpServletResponse response )
                        throws ServletException, IOException {
                    PrintWriter out = response.getWriter();
                    out.println( "Hello, world! I am a servlet2" );
                    out.close();
                }
            } );
            servletHandler2.setContextPath( contextPath );
            servletHandler2.setServletPath( "/servlet2" );
            addAdapter( contextPath + "/servlet2", servletHandler2 );

            gws.start();
            HttpURLConnection conn = getConnection( "/webapp/servlet1", PORT );
            assertEquals( HttpServletResponse.SC_OK, getResponseCodeFromAlias( conn ) );
            assertEquals( "Hello, world! I am a servlet2", readMultilineResponse( conn ).toString().trim() );
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testInclude() throws IOException {
        Utils.dumpOut( "testInclude" );
        try {
            newGWS( PORT );

            String contextPath = "/webapp";

            ServletAdapter servletHandler1 = new ServletAdapter();
            servletHandler1.setServletInstance( new HttpServlet() {
                @Override
                public void doGet( HttpServletRequest request, HttpServletResponse response )
                        throws ServletException, IOException {
                    PrintWriter out = response.getWriter();
                    out.println( "Hello, world! I am a servlet1" );

                    // relative path test
                    RequestDispatcher dispatcher = request.getRequestDispatcher( "servlet2" );
                    assertNotNull( dispatcher );
                    dispatcher.include( request, response );
                    out.close();
                }
            } );
            servletHandler1.setContextPath( contextPath );
            servletHandler1.setServletPath( "/servlet1" );
            addAdapter( contextPath + "/servlet1", servletHandler1 );

            ServletAdapter servletHandler2 = new ServletAdapter();
            servletHandler2.setServletInstance( new HttpServlet() {
                @Override
                public void doGet( HttpServletRequest request, HttpServletResponse response )
                        throws ServletException, IOException {
                    PrintWriter out = response.getWriter();
                    out.println( "Hello, world! I am a servlet2" );
                    out.close();
                }
            } );
            servletHandler2.setContextPath( contextPath );
            servletHandler2.setServletPath( "/servlet2" );
            addAdapter( contextPath + "/servlet2", servletHandler2 );

            gws.start();
            HttpURLConnection conn = getConnection( "/webapp/servlet1", PORT );
            assertEquals( HttpServletResponse.SC_OK, getResponseCodeFromAlias( conn ) );
            assertEquals( "Hello, world! I am a servlet1\nHello, world! I am a servlet2", readMultilineResponse( conn ).toString().trim() );
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testNamedDispatcherForward() throws IOException {
        Utils.dumpOut( "testNamedDispatcherForward" );
        try {
            newGWS( PORT );

            String contextPath = "/webapp";

            ServletAdapter servletHandler1 = new ServletAdapter();
            servletHandler1.setServletInstance( new HttpServlet() {
                @Override
                public void doGet( HttpServletRequest request, HttpServletResponse response )
                        throws ServletException, IOException {
                    PrintWriter out = response.getWriter();
                    out.println( "Hello, world! I am a servlet1" );

                    ServletContext servletCtx = getServletContext();
                    assertNotNull( servletCtx );
                    RequestDispatcher dispatcher = servletCtx.getNamedDispatcher( "servlet2" );
                    assertNotNull( dispatcher );
                    dispatcher.forward( request, response );
                    out.close();
                }
            } );
            servletHandler1.setContextPath( contextPath );
            servletHandler1.setServletPath( "/servlet1" );
            addAdapter( contextPath + "/servlet1", servletHandler1 );

            ServletAdapter servletHandler2 = new ServletAdapter(".", "servlet2");
            servletHandler2.setServletInstance( new HttpServlet() {
                @Override
                public void doGet( HttpServletRequest request, HttpServletResponse response )
                        throws ServletException, IOException {
                    PrintWriter out = response.getWriter();
                    out.println( "Hello, world! I am a servlet2" );
                    out.close();
                }
            } );
            servletHandler2.setContextPath( contextPath );
            servletHandler2.setServletPath( "/servlet2" );
            addAdapter( contextPath + "/servlet2", servletHandler2 );

            gws.start();
            HttpURLConnection conn = getConnection( "/webapp/servlet1", PORT );
            assertEquals( HttpServletResponse.SC_OK, getResponseCodeFromAlias( conn ) );
            assertEquals( "Hello, world! I am a servlet2", readMultilineResponse( conn ).toString().trim() );
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testNamedDispatcherInclude() throws IOException {
        Utils.dumpOut( "testNamedDispatcherInclude" );
        try {
            newGWS( PORT );

            String contextPath = "/webapp";

            ServletAdapter servletHandler1 = new ServletAdapter();
            servletHandler1.setServletInstance( new HttpServlet() {
                @Override
                public void doGet( HttpServletRequest request, HttpServletResponse response )
                        throws ServletException, IOException {
                    PrintWriter out = response.getWriter();
                    out.println( "Hello, world! I am a servlet1" );

                    ServletContext servletCtx = getServletContext();
                    assertNotNull( servletCtx );
                    RequestDispatcher dispatcher = servletCtx.getNamedDispatcher( "servlet2" );
                    assertNotNull( dispatcher );
                    dispatcher.include( request, response );
                    out.close();
                }
            } );
            servletHandler1.setContextPath( contextPath );
            servletHandler1.setServletPath( "/servlet1" );
            addAdapter( contextPath + "/servlet1", servletHandler1 );

            ServletAdapter servletHandler2 = new ServletAdapter(".", "servlet2");
            servletHandler2.setServletInstance( new HttpServlet() {
                @Override
                public void doGet( HttpServletRequest request, HttpServletResponse response )
                        throws ServletException, IOException {
                    PrintWriter out = response.getWriter();
                    out.println( "Hello, world! I am a servlet2" );
                    out.close();
                }
            } );
            servletHandler2.setContextPath( contextPath );
            servletHandler2.setServletPath( "/servlet2" );
            addAdapter( contextPath + "/servlet2", servletHandler2 );

            gws.start();
            HttpURLConnection conn = getConnection( "/webapp/servlet1", PORT );
            assertEquals( HttpServletResponse.SC_OK, getResponseCodeFromAlias( conn ) );
            assertEquals( "Hello, world! I am a servlet1\nHello, world! I am a servlet2", readMultilineResponse( conn ).toString().trim() );
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testCrossContextForward() throws IOException {
        Utils.dumpOut( "testCrossContextForward" );
        try {
            newGWS( PORT );

            ServletAdapter servletHandler1 = new ServletAdapter();
            servletHandler1.setServletInstance( new HttpServlet() {
                @Override
                public void doGet( HttpServletRequest request, HttpServletResponse response )
                        throws ServletException, IOException {
                    PrintWriter out = response.getWriter();
                    out.println( "Hello, world! I am a servlet1" );

                    ServletContext servletCtx1 = getServletContext();
                    assertNotNull( servletCtx1 );
                    RequestDispatcher dispatcher = request.getRequestDispatcher( "servlet2" );
                    assertNull( dispatcher );

                    // cross context
                    ServletContext servletCtx2 = servletCtx1.getContext( "/webapp2" );
                    assertNotNull( servletCtx2 );
                    // The pathname must begin with a "/"
                    dispatcher = servletCtx2.getRequestDispatcher( "/servlet2" );
                    assertNotNull( dispatcher );
                    dispatcher.forward( request, response );
                    out.close();
                }
            } );
            servletHandler1.setContextPath( "/webapp1" );
            servletHandler1.setServletPath( "/servlet1" );
            addAdapter( "/webapp1/servlet1", servletHandler1 );

            ServletAdapter servletHandler2 = new ServletAdapter();
            servletHandler2.setServletInstance( new HttpServlet() {
                @Override
                public void doGet( HttpServletRequest request, HttpServletResponse response )
                        throws ServletException, IOException {
                    PrintWriter out = response.getWriter();
                    out.println( "Hello, world! I am a servlet2" );
                    out.close();
                }
            } );
            servletHandler2.setContextPath( "/webapp2" );
            servletHandler2.setServletPath( "/servlet2" );
            addAdapter( "/webapp2/servlet2", servletHandler2 );

            gws.start();
            HttpURLConnection conn = getConnection( "/webapp1/servlet1", PORT );
            assertEquals( HttpServletResponse.SC_OK, getResponseCodeFromAlias( conn ) );
            assertEquals( "Hello, world! I am a servlet2", readMultilineResponse( conn ).toString().trim() );
        } finally {
            stopGrizzlyWebServer();
        }
    }

    public void testComplexDispatch() throws IOException {
        // servlet1 --> dispatcher forward by ServletRequest's API(servlet2) ->
        // named dispatcher include(servlet3) -> cross context, dispatcher include by ServletContext's API(servlet4)
        Utils.dumpOut( "testComplexDispatch" );
        try {
            newGWS( PORT );

            // webapp1(servlet1, servlet2, servlet3)
            ServletAdapter servletHandler1 = new ServletAdapter();
            servletHandler1.setServletInstance( new HttpServlet() {
                @Override
                public void doGet( HttpServletRequest request, HttpServletResponse response )
                        throws ServletException, IOException {
                    PrintWriter out = response.getWriter();
                    out.println( "Hello, world! I am a servlet1" );

                    RequestDispatcher dispatcher = request.getRequestDispatcher( "servlet2" );
                    assertNotNull( dispatcher );
                    dispatcher.forward( request, response );
                    out.close();
                }
            } );
            servletHandler1.setContextPath( "/webapp1" );
            servletHandler1.setServletPath( "/servlet1" );
            addAdapter( "/webapp1/servlet1", servletHandler1 );

            ServletAdapter servletHandler2 = new ServletAdapter();
            servletHandler2.setServletInstance( new HttpServlet() {
                @Override
                public void doGet( HttpServletRequest request, HttpServletResponse response )
                        throws ServletException, IOException {
                    PrintWriter out = response.getWriter();
                    out.println( "Hello, world! I am a servlet2" );

                    ServletContext servletCtx = getServletContext();
                    assertNotNull( servletCtx );
                    RequestDispatcher dispatcher = servletCtx.getNamedDispatcher( "servlet3" );
                    assertNotNull( dispatcher );
                    dispatcher.include( request, response );
                    out.close();
                }
            } );
            servletHandler2.setContextPath( "/webapp1" );
            servletHandler2.setServletPath( "/servlet2" );
            addAdapter( "/webapp1/servlet2", servletHandler2 );

            ServletAdapter servletHandler3 = new ServletAdapter(".", "servlet3");
            servletHandler3.setServletInstance( new HttpServlet() {
                @Override
                public void doGet( HttpServletRequest request, HttpServletResponse response )
                        throws ServletException, IOException {
                    PrintWriter out = response.getWriter();
                    out.println( "Hello, world! I am a servlet3" );

                    ServletContext servletCtx1 = getServletContext();
                    assertNotNull( servletCtx1 );
                    ServletContext servletCtx2 = servletCtx1.getContext( "/webapp2" );
                    assertNotNull( servletCtx2 );
                    RequestDispatcher dispatcher = servletCtx2.getRequestDispatcher( "/servlet4" );
                    dispatcher.include( request, response );
                    out.close();
                }
            } );
            servletHandler3.setContextPath( "/webapp1" );
            servletHandler3.setServletPath( "/servlet3" );
            addAdapter( "/webapp1/servlet3", servletHandler3 );

            // webapp2(servlet4)
            ServletAdapter servletHandler4 = new ServletAdapter();
            servletHandler4.setServletInstance( new HttpServlet() {
                @Override
                public void doGet( HttpServletRequest request, HttpServletResponse response )
                        throws ServletException, IOException {
                    PrintWriter out = response.getWriter();
                    out.println( "Hello, world! I am a servlet4" );
                    out.close();
                }
            } );
            servletHandler4.setContextPath( "/webapp2" );
            servletHandler4.setServletPath( "/servlet4" );
            addAdapter( "/webapp2/servlet4", servletHandler4 );

            gws.start();
            HttpURLConnection conn = getConnection( "/webapp1/servlet1", PORT );
            assertEquals( HttpServletResponse.SC_OK, getResponseCodeFromAlias( conn ) );
            assertEquals( "Hello, world! I am a servlet2\nHello, world! I am a servlet3\nHello, world! I am a servlet4",
                          readMultilineResponse( conn ).toString().trim() );
        } finally {
            stopGrizzlyWebServer();
        }
    }
}
