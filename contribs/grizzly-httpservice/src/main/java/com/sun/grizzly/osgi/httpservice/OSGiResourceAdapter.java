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

import com.sun.grizzly.http.servlet.HttpServletRequestImpl;
import com.sun.grizzly.http.servlet.HttpServletResponseImpl;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.tcp.http11.GrizzlyOutputStream;
import com.sun.grizzly.util.http.MimeMap;
import com.sun.grizzly.osgi.httpservice.util.Logger;
import org.osgi.service.http.HttpContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * OSGi Resource Adapter.
 * <p/>
 * OSGi Resource registration integration.
 *
 * @author Hubert Iwaniuk
 */
public class OSGiResourceAdapter extends GrizzlyAdapter implements OSGiGrizzlyAdapter {
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private String alias;
    private String prefix;
    private HttpContext httpContext;
    private Logger logger;
    private static final MimeMap MIME_MAP = new MimeMap();

    /**
     * Default constructor.
     *
     * @param alias       Registered under this alias.
     * @param prefix      Internal prefix.
     * @param httpContext Backing {@link org.osgi.service.http.HttpContext}.
     * @param logger      Logger utility.
     */
    public OSGiResourceAdapter(String alias, String prefix, HttpContext httpContext, Logger logger) {
        super();
        //noinspection AccessingNonPublicFieldOfAnotherObject
        super.commitErrorResponse = false;
        this.alias = alias;
        this.prefix = prefix;
        this.httpContext = httpContext;
        this.logger = logger;
    }

    /**
     * {@inheritDoc}
     */
    public void service(GrizzlyRequest request, GrizzlyResponse response) {
        String requestURI = request.getDecodedRequestURI();
        logger.debug(new StringBuilder(128).append("OSGiResourceAdapter requestURI: ").append(requestURI).toString());
        String path = requestURI.replaceFirst(alias, prefix);
        try {
            // authentication
            if (!authenticate(request, response, new OSGiServletContext(httpContext, logger))) {
                logger.debug("OSGiResourceAdapter Request not authenticated (" + requestURI + ").");
                return;
            }
        } catch (IOException e) {
            logger.warn("Error while authenticating request: " + request, e);
        }

        // find resource
        URL resource = httpContext.getResource(path);
        if (resource == null) {
            logger.debug(new StringBuilder(128).append("OSGiResourceAdapter \'").append(alias).append("\' Haven't found '").append(path)
                    .append("'.").toString());
            response.setStatus(404);
            return;
        } else {
            response.setStatus(200);
        }

        // MIME handling
        String mime = httpContext.getMimeType(path);
        if (mime == null) {
            mime = MIME_MAP.getContentTypeFor(path);
        }
        if (mime != null) {
            response.setContentType(mime);
        }

        try {
            final URLConnection urlConnection = resource.openConnection();
            final int length = urlConnection.getContentLength();
            final InputStream is = urlConnection.getInputStream();
            final GrizzlyOutputStream os = response.getOutputStream();

            byte buff[] = new byte[1024*8];
            int read, total = 0;
            while ((read = is.read(buff)) != -1) {
                total += read;
                os.write(buff, 0, read);
            }
            os.flush();
            response.finishResponse();
            if (total != length) {
                logger.warn("Was supposed to send " + length + ", but sent " + total);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks authentication.
     * <p/>
     * Calls {@link HttpContext#handleSecurity} to authenticate.
     *
     * @param request  Request to authenticate.
     * @param response Response to populate if authentication not performed but needed.
     * @param servletContext Context neede for proper HttpServletRequest creation.
     * @return <code>true</code> iff authenticated and can proceed with processing, else <code>false</code>.
     * @throws IOException Propaget exception thrown by {@link HttpContext#handleSecurity}.
     */
    private boolean authenticate(GrizzlyRequest request, GrizzlyResponse response, OSGiServletContext servletContext) throws IOException {
        HttpServletRequestImpl httpRequest = new OSGiHttpServletRequest(request, servletContext);
        return httpContext.handleSecurity(httpRequest, new HttpServletResponseImpl(response));
    }

    /**
     * {@inheritDoc}
     */
    public ReentrantReadWriteLock.ReadLock getProcessingLock() {
        return lock.readLock();
    }

    /**
     * {@inheritDoc}
     */
    public ReentrantReadWriteLock.WriteLock getRemovalLock() {
        return lock.writeLock();
    }

    private class OSGiHttpServletRequest extends HttpServletRequestImpl {
        public OSGiHttpServletRequest(
                GrizzlyRequest request, OSGiServletContext context) throws IOException {
            super(request);
            setContextImpl(context);
        }
    }
}
