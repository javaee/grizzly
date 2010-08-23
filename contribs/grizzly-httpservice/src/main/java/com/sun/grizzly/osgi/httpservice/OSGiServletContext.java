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

import org.osgi.service.http.HttpContext;
import com.sun.grizzly.http.servlet.ServletContextImpl;
import com.sun.grizzly.osgi.httpservice.util.Logger;
import com.sun.grizzly.util.http.MimeMap;

import java.net.URL;
import java.net.MalformedURLException;
import java.io.InputStream;
import java.io.IOException;
import static java.text.MessageFormat.format;

/**
 * OSGi {@link ServletContextImpl} integration.
 *
 * @author Hubert Iwaniuk
 */
public class OSGiServletContext extends ServletContextImpl {

    private static final MimeMap MIME_MAP = new MimeMap();
    /**
     * {@link HttpContext} providing OSGi integration.
     */
    private HttpContext httpContext;
    private Logger logger;

    /**
     * Default constructor.
     *
     * @param httpContext {@link org.osgi.service.http.HttpContext} to provide integration with OSGi.
     * @param logger      Logger util.
     */
    public OSGiServletContext(HttpContext httpContext, Logger logger) {
        this.httpContext = httpContext;
        this.logger = logger;
    }

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

        try {
            return httpContext.getResource(path).openStream();
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
            mime = MIME_MAP.getContentTypeFor(file);
        }
        return mime;
    }
}
