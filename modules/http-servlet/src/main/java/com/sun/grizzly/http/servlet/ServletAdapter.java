/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * Portions Copyright Apache Software Foundation.
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
 * Contributor(s):& 0
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
 */

package com.sun.grizzly.http.servlet;

import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import java.util.HashMap;
import java.util.logging.Level;
import javax.servlet.Servlet;

/**
 * Adapter class that can initiate a Servlet and execute it.
 * 
 * @author Jeanfrancois Arcand
 */
public class ServletAdapter extends GrizzlyAdapter {
    public static final int REQUEST_RESPONSE_NOTES = 12;
    public static final int SERVLETCONFIG_NOTES = 13;    
    private Servlet servletInstance = null;
    
    /**
     * The merged context initialization parameters for this Context.
     */
    private HashMap<String,String> parameters = new HashMap<String,String>();
    
    
    public ServletAdapter() {
        super();
    }

    
    public ServletAdapter(String publicDirectory) {
        super(publicDirectory);
    }

    
    @Override
    public void service(GrizzlyRequest request, GrizzlyResponse response) {
        try {
            Request req = request.getRequest();
            Response res = response.getResponse();

            HttpServletRequestImpl httpRequest = (HttpServletRequestImpl) req.getNote(REQUEST_RESPONSE_NOTES);
            HttpServletResponseImpl httpResponse = (HttpServletResponseImpl) res.getNote(REQUEST_RESPONSE_NOTES);
            ServletConfigImpl servletConfig = (ServletConfigImpl) req.getNote(SERVLETCONFIG_NOTES);

            if (httpRequest == null) {
                httpRequest = new HttpServletRequestImpl(request);
                httpResponse = new HttpServletResponseImpl(response);
                ServletContextImpl servletCtx = new ServletContextImpl();
                servletCtx.setInitParameter(parameters);
                servletConfig = new ServletConfigImpl(servletCtx);

                req.setNote(REQUEST_RESPONSE_NOTES, httpRequest);
                req.setNote(SERVLETCONFIG_NOTES, servletConfig);
                res.setNote(REQUEST_RESPONSE_NOTES, httpResponse);
            }

            // TRy to load the instance using the System.getProperties();
            if (servletInstance == null) {
                String servletClassName = System.getProperty("com.sun.grizzly.servletClass");
                if (servletClassName != null) {
                    servletInstance = loadServletInstance(servletClassName);
                }

                if (servletInstance == null) {
                    throw new RuntimeException("No Servlet defined");
                }
            }

            httpResponse.addHeader("server", "grizzly/1.7");
            servletInstance.init(servletConfig);
            //System.out.println("Executing PHP script");
            servletInstance.service(httpRequest, httpResponse);
            //System.out.println("Execution completed");
        } catch (Throwable ex) {
            logger.log(Level.SEVERE, "service exception:", ex);
        }
        //System.out.println("Execution completed");
    }

    
    @Override
    public void afterService(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
        Request req = request.getRequest();
        Response res = response.getResponse();        
        HttpServletRequestImpl httpRequest = (HttpServletRequestImpl) req.getNote(REQUEST_RESPONSE_NOTES);
        HttpServletResponseImpl httpResponse = (HttpServletResponseImpl) req.getNote(REQUEST_RESPONSE_NOTES);

        httpRequest.recycle();
        httpResponse.recycle();
    }
    
    
    /**
     * Add a new servlet initialization parameter for this servlet.
     *
     * @param name Name of this initialization parameter to add
     * @param value Value of this initialization parameter to add
     */
    public void addInitParameter(String name, String value){
        parameters.put(name, value);
    }

    
    private static Servlet loadServletInstance(String servletClass) {
        Class className = null;
        try {
            className = Class.forName(servletClass, true, Thread.currentThread().getContextClassLoader());
            return (Servlet) className.newInstance();
        } catch (Throwable t) {
            t.printStackTrace();
            // Log me
        }
        return null;
    }

    
    public Servlet getServletInstance() {
        return servletInstance;
    }

    
    public void setServletInstance(Servlet servletInstance) {
        this.servletInstance = servletInstance;
    }
}
