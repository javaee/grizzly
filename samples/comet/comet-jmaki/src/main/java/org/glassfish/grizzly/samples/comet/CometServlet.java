/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

/* You may not modify, use, reproduce, or distribute this software except in compliance with the terms of the License at: 
 http://developer.sun.com/berkeley_license.html
 $Id: CometServlet.java,v 1.6 2007/07/14 07:14:30 gmurray71 Exp $ */
package org.glassfish.grizzly.samples.comet;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.glassfish.grizzly.comet.CometContext;
import org.glassfish.grizzly.comet.CometEngine;
import org.glassfish.grizzly.comet.CometEvent;
import org.glassfish.grizzly.comet.CometHandler;
import org.glassfish.grizzly.http.server.Response;

/**
 * Simple Flickr Magnet Demo.
 *
 * @author Jeanfrancois Arcand
 * @author Greg Murray
 */
public class CometServlet extends HttpServlet{
    private final static String JUNK = "<!-- Comet is a programming technique that enables web " +
            "servers to send data to the client without having any need " +
            "for the client to request it. -->\n";
    private static final long DEFAULT_EXPIRATION_DELAY = 60 * 6 * 1000;
    
    /**
     * The Comet enabled context-path
     */
    private String contextPath;

    /**
     * Simple counter
     */
    private int counter;
    
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
                
        String cometUrlPattern = config.getInitParameter("cometUrlPattern");
        if (cometUrlPattern == null){
            cometUrlPattern = "/words";
        }
        
        contextPath = config.getServletContext().getContextPath() + cometUrlPattern;
        CometEngine cometEngine = CometEngine.getEngine();
        CometContext<String> context = cometEngine.register(contextPath);
        
        String expire = config.getInitParameter("expirationDelay");
        if (expire == null) {
            context.setExpirationDelay(DEFAULT_EXPIRATION_DELAY);
        } else {
            context.setExpirationDelay(Long.parseLong(expire));
        }
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        doPost(request,response);
    }
    
    
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        
        String action = request.getParameter("action");
        CometEngine cometEngine = CometEngine.getEngine();
        CometContext<String> cometContext = cometEngine.getCometContext(contextPath);
        
        if (action != null) {
            if ("post".equals(action)){
                String message = request.getParameter("message");
                String callback = request.getParameter("callback");
                if (callback== null) {callback = "alert";}
                
                // Notify other registered CometHandler.
                cometContext.notify("<script id='comet_" + counter++ + "'>window.parent." + callback
                    + "(" +  message + ");</script>");
                response.getWriter().println("ok");
                return;
            } else if ("start".equals(action)) {
                response.setContentType("text/html");
                // For IE, Safari and Chrome, we must output some junk to enable streaming
                for (int i = 0; i < 10; i++) {
                    response.getWriter().write(JUNK);
                }
                response.getWriter().flush();
                String callback = request.getParameter("callback");
                if (callback== null) {callback = "alert";}
                
                String message = "{ message : 'Welcome'}";
                response.getWriter().println("<script id='comet_" + counter++ + "'>"
                        + "window.parent." + callback + "(" +  message + ");</script>");
                
                // Create and register a CometHandler.
                CometRequestHandler<String> handler = new CometRequestHandler<String>();
                handler.attach(response.getWriter());
                cometContext.addCometHandler(handler);
                return;
            }       
        }
    }
    
    public class CometRequestHandler<String> implements CometHandler<String>{
        
        protected PrintWriter printWriter;
        private Response response;
        private CometContext<String> cometContext;

        public void attach(PrintWriter printWriter){
            this.printWriter = printWriter;
        }

        public Response getResponse() {
            return response;
        }

        public void setResponse(Response response) {
            this.response = response;
        }

        public CometContext<String> getCometContext() {
            return cometContext;
        }

        public void setCometContext(CometContext<String> context) {
            cometContext = context;
        }

        public void onEvent(CometEvent event) throws IOException{
            try{                
                if (event.getType() != CometEvent.Type.READ){
                    printWriter.println(event.attachment());
                    printWriter.flush();
                }
            } catch (Throwable t){
                t.printStackTrace();
            }
        }
        
        public void onInitialize(CometEvent event) throws IOException{
            printWriter.println("<html><head><title>jMaki Grizzly Comet Words Sample</title></head><body bgcolor=\"#FFFFFF\">");
            printWriter.flush();
        }
        
        public void onTerminate(CometEvent event) throws IOException{
            onInterrupt(event);
        }

        public void onInterrupt(CometEvent event) throws IOException{
            printWriter.println("jMaki Grizzly Comet Words Sample closed<br/>");
            printWriter.println("</body></html>");
            printWriter.flush();
            printWriter.close();
        }
    }
}
