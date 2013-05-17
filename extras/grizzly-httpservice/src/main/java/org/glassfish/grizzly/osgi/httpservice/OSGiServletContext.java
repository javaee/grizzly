/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.osgi.httpservice;

import org.glassfish.grizzly.http.util.MimeType;
import org.glassfish.grizzly.servlet.FilterChainFactory;
import org.glassfish.grizzly.servlet.FilterRegistration;
import org.glassfish.grizzly.servlet.WebappContext;
import org.osgi.service.http.HttpContext;
import org.glassfish.grizzly.osgi.httpservice.util.Logger;

import javax.servlet.Filter;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.InputStream;
import java.io.IOException;
import java.util.EventListener;
import java.util.Iterator;
import java.util.Map;

import static java.text.MessageFormat.format;

/**
 * OSGi {@link WebappContext} integration.
 *
 * @author Hubert Iwaniuk
 */
public class OSGiServletContext extends WebappContext {
    /**
     * {@link HttpContext} providing OSGi integration.
     */
    private HttpContext httpContext;
    private Logger logger;


    // ------------------------------------------------------------ Constructors


    /**
     * Default constructor.
     *
     * @param httpContext {@link org.osgi.service.http.HttpContext} to provide integration with OSGi.
     * @param logger      Logger util.
     */
    public OSGiServletContext(HttpContext httpContext, Logger logger) {
        this.httpContext = httpContext;
        this.logger = logger;
        installAuthFilter(httpContext);
    }


    // ---------------------------------------------------------- Public Methods


    /**
     * OSGi integration. Uses {@link HttpContext#getResource(String)}.
     * <p/>
     * {@inheritDoc}
     */
    @Override public URL getResource(String path) throws MalformedURLException {
        if (path == null || !path.startsWith("/")) {
            throw new MalformedURLException(path);
        }

        path = normalize(path);
        if (path == null)
            return (null);

        return httpContext.getResource(path);
    }

    /**
     * OSGi integration. Uses {@link HttpContext#getResource(String)}.
     * <p/>
     * {@inheritDoc}
     */
    @Override public InputStream getResourceAsStream(String path) {
        path = normalize(path);
        if (path == null)
            return (null);

        URL resource = httpContext.getResource(path);
        if (resource == null) {
            logger.warn(format("Error getting resource ''{0}''. Message: {1}", path, "Can't locate resource."));
            return null;
        }

        try {
            return resource.openStream();
        } catch (IOException e) {
            logger.warn(format("Error getting resource ''{0}''. Message: {1}", path, e.getMessage()));
        }
        return null;
    }

    /**
     * OSGi integration. Uses {@link HttpContext#getMimeType(String)}.
     * <p/>
     * {@inheritDoc}
     */
    @Override public String getMimeType(String file) {
        String mime = httpContext.getMimeType(file);
        if (mime == null) {
            // if returned null, try figuring out by ourselfs.
            mime = MimeType.getByFilename(file);
        }
        return mime;
    }


    // ------------------------------------------------------- Protected Methods


    @Override
    protected EventListener[] getEventListeners() {
        return super.getEventListeners();
    }

    @Override
    protected FilterChainFactory getFilterChainFactory() {
        return super.getFilterChainFactory();
    }

    @Override
    protected void unregisterFilter(final Filter f) {
        super.unregisterFilter(f);
    }

    @Override
    protected void unregisterAllFilters() {
        super.unregisterAllFilters();
    }


    // --------------------------------------------------------- Private Methods


    private void installAuthFilter(HttpContext httpContext) {
        final Filter f = new OSGiAuthFilter(httpContext);
        try {
            f.init(new OSGiFilterConfig(this));
        } catch (Exception ignored) {
            // won't happen
        }
        FilterRegistration registration =
                addFilter(Integer.toString(f.hashCode()), f);
        registration.addMappingForUrlPatterns(null, "/*");
    }
}
