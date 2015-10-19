/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.servlet;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.server.FileCacheFilter;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.util.MimeType;
import org.glassfish.grizzly.utils.ArraySet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This servlet will be invoked when no other servlet matches the request URI.
 *
 * TODO:  This needs more work.  Review the DefaultServlet implementations
 *   included with popular servlet containers to get an understanding of
 *   what may be added
 *
 *   @since 2.2
 */
public class DefaultServlet extends HttpServlet {

    private static final Logger LOGGER = Grizzly.logger(DefaultServlet.class);

    private final ArraySet<File> docRoots;

    private volatile int fileCacheFilterIdx = -1;

    // ------------------------------------------------------------ Constructors


    protected DefaultServlet(final ArraySet<File> docRoots) {

        this.docRoots = docRoots;

    }


    // ------------------------------------------------ Methods from HttpServlet


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        boolean found = false;

        final String uri = getRelativeURI(req);
        final File[] fileFolders = docRoots.getArray();
        if (fileFolders == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        File resource = null;
        for (int i = 0, len = fileFolders.length; i < len; i++) {
            final File webDir = fileFolders[i];
            // local file
            resource = new File(webDir, uri);
            final boolean exists = resource.exists();
            final boolean isDirectory = resource.isDirectory();

            if (exists && isDirectory) {
                final File f = new File(resource, "/index.html");
                if (f.exists()) {
                    resource = f;
                    found = true;
                    break;
                }
            }

            if (isDirectory || !exists) {
                found = false;
            } else {
                found = true;
                break;
            }
        }

        if (!found) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "File not found  {0}", resource);
            }
            
            resp.reset();
            resp.sendError(404);
            return;
        }

        sendFile(req, resp, resource);


    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(405);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(405);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(405);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(405);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(405);
    }

    @Override
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(405);
    }


    // --------------------------------------------------------- Private Methods

    private void sendFile(final HttpServletRequest request,
                          final HttpServletResponse response,
                          final File file)
            throws IOException {
        final String path = file.getPath();
        FileInputStream fis;
        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException fnfe) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            response.setStatus(HttpServletResponse.SC_OK);
            String substr;
            int dot = path.lastIndexOf('.');
            if (dot < 0) {
                substr = file.toString();
                dot = substr.lastIndexOf('.');
            } else {
                substr = path;
            }
            if (dot > 0) {
                String ext = substr.substring(dot + 1);
                String ct = MimeType.get(ext);
                if (ct != null) {
                    response.setContentType(ct);
                }
            } else {
                response.setContentType(MimeType.get("html"));
            }

            final long length = file.length();
            // if length is larger than Integer.MAX_VALUE, then we have
            // to rely on chunking.
            if (length <= Integer.MAX_VALUE) {
                response.setContentLength((int) length);
            }
            addToFileCache(request, file);

            final OutputStream out = response.getOutputStream();

            byte b[] = new byte[8192];
            int rd;
            while ((rd = fis.read(b)) > 0) {
                //chunk.setBytes(b, 0, rd);
                out.write(b, 0, rd);
            }
        } finally {
            try {
                fis.close();
            } catch (IOException ignore) {
            }
        }
    }

    private String getRelativeURI(final HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.contains("..")) {
            return null;
        }

        final String resourcesContextPath = request.getContextPath();
        if (resourcesContextPath.length() > 0) {
            if (!uri.startsWith(resourcesContextPath)) {
                return null;
            }

            uri = uri.substring(resourcesContextPath.length());
        }

        return uri;
    }


    // --------------------------------------------------------- Private Methods


    private void addToFileCache(HttpServletRequest req, File resource) {
        if (req instanceof HttpServletRequestImpl) {
            final Request request = ((HttpServletRequestImpl) req).getRequest();
            final FilterChainContext fcContext = request.getContext();
            final FileCacheFilter fileCacheFilter = lookupFileCache(fcContext);
            if (fileCacheFilter != null) {
                final FileCache fileCache = fileCacheFilter.getFileCache();
                fileCache.add(request.getRequest(), resource);
            }
        }
    }

    private FileCacheFilter lookupFileCache(final FilterChainContext fcContext) {
        final FilterChain fc = fcContext.getFilterChain();
        final int lastFileCacheIdx = fileCacheFilterIdx;

        if (lastFileCacheIdx != -1) {
            final Filter filter = fc.get(lastFileCacheIdx);
            if (filter instanceof FileCacheFilter) {
                return (FileCacheFilter) filter;
            }
        }

        final int size = fc.size();
        for (int i = 0; i < size; i++) {
            final Filter filter = fc.get(i);

            if (filter instanceof FileCacheFilter) {
                fileCacheFilterIdx = i;
                return (FileCacheFilter) filter;
            }
        }

        fileCacheFilterIdx = -1;
        return null;
    }
}
