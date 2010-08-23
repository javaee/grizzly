/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.osgi.httpservice;

import com.sun.grizzly.osgi.httpservice.util.Logger;
import org.osgi.framework.Bundle;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.util.Dictionary;

/**
 * Grizzly OSGi HttpService implementation.
 *
 * @author Hubert Iwaniuk
 * @since Jan 20, 2009
 */
public class HttpServiceImpl implements HttpService {
    private final Logger logger;
    private final Bundle bundle;
    final OSGiMainAdapter mainAdapter;

    /**
     * {@link HttpService} constructor.
     *
     * @param bundle {@link org.osgi.framework.Bundle} that got this instance of {@link org.osgi.service.http.HttpService}.
     * @param logger {@link com.sun.grizzly.osgi.httpservice.util.Logger} utility to be used here.
     */
    public HttpServiceImpl(
            Bundle bundle, final Logger logger) {
        this.bundle = bundle;
        this.logger = logger;
        mainAdapter = new OSGiMainAdapter(logger, bundle);
    }

    /**
     * {@inheritDoc}
     */
    public HttpContext createDefaultHttpContext() {
        return new HttpContextImpl(bundle);
    }

    /**
     * {@inheritDoc}
     */
    public void registerServlet(
            final String alias, final Servlet servlet, final Dictionary initparams, HttpContext httpContext)
            throws ServletException, NamespaceException {

        logger.info(
                new StringBuilder(128).append("Registering servlet: ").append(servlet).append(", under: ").append(alias)
                        .append(", with: ").append(initparams).append(" and context: ").append(httpContext).toString());

        mainAdapter.registerServletAdapter(alias, servlet, initparams, httpContext, this);
    }

    /**
     * {@inheritDoc}
     */
    public void registerResources(final String alias, String prefix, HttpContext httpContext)
            throws NamespaceException {

        logger.info(
                new StringBuilder(128).append("Registering resource: alias: ").append(alias).append(", prefix: ")
                        .append(prefix).append(" and context: ").append(httpContext).toString());

        mainAdapter.registerResourceAdapter(alias, httpContext, prefix, this);
    }

    /**
     * {@inheritDoc}
     */
    public void unregister(final String alias) {
        logger.info(new StringBuilder(32).append("Unregistering alias: ").append(alias).toString());
        mainAdapter.unregisterAlias(alias);
    }

}
