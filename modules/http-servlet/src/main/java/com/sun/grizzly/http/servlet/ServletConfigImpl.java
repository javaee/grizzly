

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
 */




package com.sun.grizzly.http.servlet;

import com.sun.grizzly.util.http.Enumerator;
import java.util.Enumeration;
import java.util.HashMap;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

/**
 *
 * @author Jeanfrancois Arcand
 */
public class ServletConfigImpl implements ServletConfig{
    
    /**
     * The initialization parameters for this servlet, keyed by
     * parameter name.
     */
    private HashMap<String,String> parameters = new HashMap<String,String>();
    
    
    private String name;

    
    private ServletContextImpl servletContextImpl;
    
    
    public ServletConfigImpl(ServletContextImpl servletContextImpl){
        this.servletContextImpl = servletContextImpl;
    }
    
    
    public String getServletName() {
        return name;
    }

    
    public ServletContext getServletContext() {
        return servletContextImpl;
    }

    
    public String getInitParameter(String name) {
        return findInitParameter(name);
    }
    
    
    /**
     * Set the name of this servlet. 
     *
     * @param name The new name of this servlet
     */
    protected void setServletName(String name) {
        this.name = name;
    }
    
    
    /**
     * Add a new servlet initialization parameter for this servlet.
     *
     * @param name Name of this initialization parameter to add
     * @param value Value of this initialization parameter to add
     */
    public void addInitParameter(String name, String value) {

        synchronized (parameters) {
            parameters.put(name, value);
        }

        //TODO: Not yet supported
       /* if (notifyContainerListeners) {
            fireContainerEvent("addInitParameter", name);
        }*/
    }    
    
    /**
     * Return the value for the specified initialization parameter name,
     * if any; otherwise return <code>null</code>.
     *
     * @param name Name of the requested initialization parameter
     */
    protected String findInitParameter(String name) {
        synchronized (parameters) {
            return parameters.get(name);
        }
    }    
    
    
    /**
     * Return the set of initialization parameter names defined for this
     * servlet.  If none are defined, an empty Enumeration is returned.
     */
    public Enumeration getInitParameterNames() {
        synchronized (parameters) {
            return (new Enumerator(parameters.keySet()));
        }
    }
}
