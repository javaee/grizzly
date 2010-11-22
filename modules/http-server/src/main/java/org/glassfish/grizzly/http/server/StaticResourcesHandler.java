/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.server.io.OutputBuffer;
import org.glassfish.grizzly.http.server.util.MimeType;
import org.glassfish.grizzly.http.util.HttpStatus;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.utils.ArraySet;

/**
 * Static resources handler, which handles static resources requests made to a
 * {@link HttpRequestProcessor}.
 *
 * This class doesn't not decode the {@link Request} URI and just do
 * basic security check. If you need more protection, use the {@link HttpRequestProcessor}.
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public class StaticResourcesHandler {
    private static final Logger LOGGER = Grizzly.logger(StaticResourcesHandler.class);
    
    protected final ArraySet<File> docRoots = new ArraySet<File>(File.class);

    private volatile int fileCacheFilterIdx = -1;

    public StaticResourcesHandler() {
        this(".");
    }

    public StaticResourcesHandler(String rootFolder) {
        addDocRoot(rootFolder);
    }

    public StaticResourcesHandler(File rootFolder) {
        addDocRoot(rootFolder);
    }

    /**
     * Based on the {@link Request} URI, try to map the file from the
     * {@link StaticResourcesHandler#getDocRoot()}, and send it synchronously
     * using send file.
     * @param req the {@link Request}
     * @param res the {@link Response}
     * @param resourcesContextPath the context path used for servicing resources
     * @throws Exception
     */
    public boolean handle(Request req, final Response res,
            String resourcesContextPath) throws Exception {
        
        String uri = req.getRequestURI();
        if (uri.indexOf("..") >= 0) {
            return false;
        }

        if (!"".equals(resourcesContextPath)) {
            if (!uri.startsWith(resourcesContextPath)) {
                return false;
            }

            uri = uri.substring(resourcesContextPath.length());
            return false;
        }

        return handle(uri, req, res);
    }

    /**
     * Lookup a resource based on the request URI, and send it using send file.
     *
     * @param uri The request URI
     * @param req the {@link Request}
     * @param res the {@link Response}
     * @throws Exception
     */
    protected boolean handle(final String uri,
            final Request req,
            final Response res) throws Exception {
        
        FileInputStream fis = null;
        try {

            boolean found = false;

            final File[] fileFolders = docRoots.getArray();
            if (fileFolders == null) return false;
            
            File resource = null;
            
            for (int i = 0; i < fileFolders.length; i++) {
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
                return false;
            }
            
            res.setStatus(HttpStatus.OK_200);
            String substr;
            int dot = uri.lastIndexOf(".");
            if (dot < 0) {
                substr = resource.toString();
                dot = substr.lastIndexOf(".");
            } else {
                substr = uri;
            }
            if (dot > 0) {
                String ext = substr.substring(dot + 1);
                String ct = MimeType.get(ext);
                if (ct != null) {
                    res.setContentType(ct);
                }
            } else {
                res.setContentType(MimeType.get("html"));
            }

            long length = resource.length();
            res.setContentLengthLong(length);

            addToFileCache(req, resource);
            // Send the header, and flush the bytes as we will now move to use
            // send file.
            res.flush();
            //res.sendHeaders();

            fis = new FileInputStream(resource);
            OutputBuffer outputBuffer = res.getOutputBuffer();

            byte b[] = new byte[8192];
            int rd;
            while ((rd = fis.read(b)) > 0) {
                //chunk.setBytes(b, 0, rd);
                outputBuffer.write(b, 0, rd);
            }

            return true;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    /**
     * Return the default directory from where files will be serviced.
     * @return the default directory from where file will be serviced.
     */
    public File getDefaultDocRoot() {
        final File[] array = docRoots.getArray();
        return (array != null && array.length > 0) ? array[0] : null;
    }

    /**
     * Return the list of directories from where files will be serviced.
     * @return the list of directories from where files will be serviced.
     */
    public ArraySet<File> getDocRoots() {
        return docRoots;
    }
    
    /**
     * Add the directory from where files will be serviced.
     * @param docRoot the directory from where files will be serviced.
     * 
     * @return return the {@link File} representation of the passed <code>docRoot</code>.
     */
    public final File addDocRoot(String docRoot) {
        if (docRoot == null) {
            throw new NullPointerException("docRoot can't be null");
        }

        final File file = new File(docRoot);
        addDocRoot(file);
        
        return file;
    }

    /**
     * Set the directory from where files will be serviced.
     * @param docRoot the directory from where files will be serviced.
     */
    public final void addDocRoot(File docRoot) {
        docRoots.add(docRoot);
    }

    public final boolean addToFileCache(Request req, File resource) {
        final FilterChainContext fcContext = req.getContext();
        final FileCacheFilter fileCacheFilter = lookupFileCache(fcContext);
        if (fileCacheFilter != null) {
            final FileCache fileCache = fileCacheFilter.getFileCache();
            fileCache.add(req.getRequest(), resource);
            return true;
        }

        return false;
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
