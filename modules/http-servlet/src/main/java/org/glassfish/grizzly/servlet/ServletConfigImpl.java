/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.servlet;

import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import org.glassfish.grizzly.http.server.util.Enumerator;

/**
 * Basic {@link ServletConfig} implementation.
 * 
 * @author Jeanfrancois Arcand
 */
public class ServletConfigImpl implements ServletConfig {

    protected String name;
    protected final ConcurrentMap<String, String> initParameters =
            new ConcurrentHashMap<>(16, 0.75f, 64);
    protected final WebappContext servletContextImpl;

    protected ServletConfigImpl(WebappContext servletContextImpl) {
        this.servletContextImpl = servletContextImpl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getServletName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServletContext getServletContext() {
        return servletContextImpl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getInitParameter(String name) {
        return initParameters.get(name);
    }

    protected void setInitParameters(Map<String, String> parameters) {
        if (parameters != null && !parameters.isEmpty()) {
            this.initParameters.clear();
            this.initParameters.putAll(parameters);
        }
    }

    /**
     * Set the name of this servlet. 
     *
     * @param name The new name of this servlet
     */
    public void setServletName(String name) {
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public Enumeration<String> getInitParameterNames() {
        return (new Enumerator(initParameters.keySet()));
    }
}
