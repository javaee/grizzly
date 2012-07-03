/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.server.io.NIOOutputStream;
import org.glassfish.grizzly.http.server.io.OutputBuffer;
import org.glassfish.grizzly.http.server.util.MimeType;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.ArraySet;

/**
 * {@link HttpHandler}, which processes requests to a static resources.
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public class StaticHttpHandler extends HttpHandler {
    


    private static final Logger LOGGER = Grizzly.logger(StaticHttpHandler.class);

    protected final ArraySet<File> docRoots = new ArraySet<File>(File.class);

    private volatile int fileCacheFilterIdx = -1;
    
    private volatile boolean isFileCacheEnabled = true;
    
    /**
     * Create <tt>HttpHandler</tt>, which, by default, will handle requests
     * to the static resources located in the current directory.
     */
    public StaticHttpHandler() {
        addDocRoot(".");
    }


    /**
     * Create a new instance which will look for static pages located
     * under the <tt>docRoot</tt>. If the <tt>docRoot</tt> is <tt>null</tt> -
     * static pages won't be served by this <tt>HttpHandler</tt>
     *
     * @param docRoots the folder(s) where the static resource are located.
     * If the <tt>docRoot</tt> is <tt>null</tt> - static pages won't be served
     * by this <tt>HttpHandler</tt>
     */
    public StaticHttpHandler(String... docRoots) {
        if (docRoots != null) {
            for (String docRoot : docRoots) {
                addDocRoot(docRoot);
            }
        }
    }

    /**
     * Create a new instance which will look for static pages located
     * under the <tt>docRoot</tt>. If the <tt>docRoot</tt> is <tt>null</tt> -
     * static pages won't be served by this <tt>HttpHandler</tt>
     *
     * @param docRoots the folders where the static resource are located.
     * If the <tt>docRoot</tt> is empty - static pages won't be served
     * by this <tt>HttpHandler</tt>
     */
    @SuppressWarnings("UnusedDeclaration")
    public StaticHttpHandler(Set<String> docRoots) {
        if (docRoots != null) {
            for (String docRoot : docRoots) {
                addDocRoot(docRoot);
            }
        }
    }

    /**
     * Return the default directory from where files will be serviced.
     * @return the default directory from where file will be serviced.
     */
    @SuppressWarnings("UnusedDeclaration")
    public File getDefaultDocRoot() {
        final File[] array = docRoots.getArray();
        return (array != null && array.length > 0) ? array[0] : null;
    }

    /**
     * Return the list of directories where files will be serviced from.
     *
     * @return the list of directories where files will be serviced from.
     */
    public ArraySet<File> getDocRoots() {
        return docRoots;
    }

    /**
     * Add the directory to the list of directories where files will be serviced from.
     *
     * @param docRoot the directory to be added to the list of directories
     *                where files will be serviced from.
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
     * Add the directory to the list of directories where files will be serviced from.
     *
     * @param docRoot the directory to be added to the list of directories
     *                where files will be serviced from.
     */
    public final void addDocRoot(File docRoot) {
        docRoots.add(docRoot);
    }

    /**
     * Removes the directory from the list of directories where static files will be serviced from.
     *
     * @param docRoot the directory to remove.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void removeDocRoot(File docRoot) {
        docRoots.remove(docRoot);
    }

    /**
     * Returns <tt>true</tt> if this <tt>StaticHttpHandler</tt> has been
     * configured to use file cache to serve static resources,
     * or <tt>false</tt> otherwise.
     * 
     * Please note, even though this StaticHttpHandler might be configured
     * to use file cache, file cache itself might be disabled
     * {@link FileCache#isEnabled()}. In this case StaticHttpHandler will operate
     * as if file cache was disabled.
     * 
     * @return <tt>true</tt> if this <tt>StaticHttpHandler</tt> has been
     * configured to use file cache to serve static resources,
     * or <tt>false</tt> otherwise.
     */
    public boolean isFileCacheEnabled() {
        return isFileCacheEnabled;
    }

    /**
     * Set <tt>true</tt> to configure this <tt>StaticHttpHandler</tt> 
     * to use file cache to serve static resources, or <tt>false</tt> otherwise.
     * 
     * Please note, even though this StaticHttpHandler might be configured
     * to use file cache, file cache itself might be disabled
     * {@link FileCache#isEnabled()}. In this case StaticHttpHandler will operate
     * as if file cache was disabled.
     * 
     * @param isFileCacheEnabled <tt>true</tt> to configure this
     * <tt>StaticHttpHandler</tt> to use file cache to serve static resources,
     * or <tt>false</tt> otherwise.
     */
    public void setFileCacheEnabled(boolean isFileCacheEnabled) {
        this.isFileCacheEnabled = isFileCacheEnabled;
    }
    
    public static void sendFile(final Response response, final File file)
            throws IOException {
        response.setStatus(HttpStatus.OK_200);

        // In case this sendFile(...) is called directly by user - pickup the content-type
        pickupContentType(response, file);

        final long length = file.length();
        response.setContentLengthLong(length);
        response.addDateHeader(Header.Date, System.currentTimeMillis());
        if (!response.isSendFileEnabled() || response.getRequest().isSecure()) {
            sendUsingBuffers(response, file);
        } else {
            sendZeroCopy(response, file);
        }
    }

    private static void sendUsingBuffers(final Response response, final File file)
            throws FileNotFoundException, IOException {
        final int chunkSize = 8192;
        
        response.suspend();
        
        final NIOOutputStream outputStream = response.getNIOOutputStream();
        
        outputStream.notifyCanWrite(
                new NonBlockingDownloadHandler(response, outputStream,
                        file, chunkSize),
                chunkSize * 3 / 2);

    }

    private static void sendZeroCopy(final Response response, final File file)
            throws IOException {
        final OutputBuffer outputBuffer = response.getOutputBuffer();
        outputBuffer.sendfile(file, null);
    }

    public final boolean addToFileCache(final Request req,
                                        final Response res,
                                        final File resource) {
        if (isFileCacheEnabled) {
            final FilterChainContext fcContext = req.getContext();
            final FileCacheFilter fileCacheFilter = lookupFileCache(fcContext);
            if (fileCacheFilter != null) {
                final FileCache fileCache = fileCacheFilter.getFileCache();
                if (fileCache.isEnabled()) {
                    if (res != null) {
                        addCachingHeaders(res, resource);
                    }
                    fileCache.add(req.getRequest(), resource);
                    return true;
                }
            }
        }

        return false;

    }
    
    
    // ------------------------------------------------ Methods from HttpHandler
    
    
    /**
     * Based on the {@link Request} URI, try to map the file from the
     * {@link #getDocRoots()}, and send it back to a client.
     * @param request the {@link Request}
     * @param response the {@link Response}
     * @throws Exception
     */
    @Override
    public void service(final Request request, final Response response)
            throws Exception {
        final String uri = getRelativeURI(request);

        if (uri == null || !handle(uri, request, response)) {
            onMissingResource(request, response);
        }
    }
    
    
    // ------------------------------------------------------- Protected Methods
    

    protected String getRelativeURI(final Request request) {
        String uri = request.getRequestURI();
        if (uri.contains("..")) {
            return null;
        }

        final String resourcesContextPath = request.getContextPath();
        if (resourcesContextPath != null && !resourcesContextPath.isEmpty()) {
            if (!uri.startsWith(resourcesContextPath)) {
                return null;
            }

            uri = uri.substring(resourcesContextPath.length());
        }

        return uri;
    }

    /**
     * The method will be called, if the static resource requested by the {@link Request}
     * wasn't found, so {@link StaticHttpHandler} implementation may try to
     * workaround this situation.
     * The default implementation - sends a 404 response page by calling {@link #customizedErrorPage(Request, Response)}.
     *
     * @param request the {@link Request}
     * @param response the {@link Response}
     * @throws Exception
     */
    protected void onMissingResource(final Request request, final Response response)
            throws Exception {
        response.setStatus(HttpStatus.NOT_FOUND_404);
        customizedErrorPage(request, response);
    }

    /**
     * Lookup a resource based on the request URI, and send it using send file.
     *
     * @param uri The request URI
     * @param request the {@link Request}
     * @param response the {@link Response}
     * @throws Exception
     */
    protected boolean handle(final String uri,
            final Request request,
            final Response response) throws Exception {

        boolean found = false;

        final File[] fileFolders = docRoots.getArray();
        if (fileFolders == null) {
            return false;
        }

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
                LOGGER.log(Level.FINE, "File not found {0}", resource);
            }
            return false;
        }

        // If it's not HTTP GET - return method is not supported status
        if (!Method.GET.equals(request.getMethod())) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "File found {0}, but HTTP method {1} is not allowed",
                        new Object[] {resource, request.getMethod()});
            }
            response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
            response.setHeader(Header.Allow, "GET");
            return true;
        }
        
        pickupContentType(response, resource);
        
        addToFileCache(request, response, resource);
        sendFile(response, resource);

        return true;
    }

    
    // --------------------------------------------------------- Private Methods
    

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

    private static void pickupContentType(final Response response,
            final File file) {
        if (!response.getResponse().isContentTypeSet()) {
            final String path = file.getPath();
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
        }
    }

    private static void addCachingHeaders(final Response response,
                                          final File file) {
        final StringBuilder sb = new StringBuilder();

        long contentLength = file.length();
        long lastModified = file.lastModified();
        if ((contentLength >= 0) || (lastModified >= 0)) {
            sb.append('"').append(contentLength).append('-').
                    append(lastModified).append('"');
            response.setHeader(Header.ETag, sb.toString());
        }
        response.addDateHeader(Header.LastModified, lastModified);

    }
    
    private static class NonBlockingDownloadHandler implements WriteHandler {
        // keep the remaining size
        private volatile long size;
        
        private final Response response;
        private final NIOOutputStream outputStream;
        private final FileChannel fileChannel;
        private final MemoryManager mm;
        private final int chunkSize;
        
        NonBlockingDownloadHandler(final Response response,
                final NIOOutputStream outputStream, final File file,
                final int chunkSize) {
            
            try {
                fileChannel = new FileInputStream(file).getChannel();
            } catch (FileNotFoundException e) {
                throw new IllegalStateException("File should have existed", e);
            }
            
            size = file.length();
            
            this.response = response;
            this.outputStream = outputStream;
            mm = response.getRequest().getContext().getMemoryManager();
            this.chunkSize = chunkSize;
        }
        
        @Override
        public void onWritePossible() throws Exception {
            LOGGER.log(Level.FINE, "[onWritePossible]");
            // send CHUNK of data
            final boolean isWriteMore = sendChunk();

            if (isWriteMore) {
                // if there are more bytes to be sent - reregister this WriteHandler
                outputStream.notifyCanWrite(this, chunkSize * 3 /2);
            }
        }

        @Override
        public void onError(Throwable t) {
            LOGGER.log(Level.WARNING, "[onError] ", t);
            response.setStatus(500, t.getMessage());
            complete(true);
        }

        /**
         * Send next CHUNK_SIZE of file
         */
        private boolean sendChunk() throws IOException {
            // allocate Buffer
            final Buffer buffer = mm.allocate(chunkSize);
            // mark it available for disposal after content is written
            buffer.allowBufferDispose(true);

            // read file to the Buffer
            final int justReadBytes = fileChannel.read(buffer.toByteBuffer());
            if (justReadBytes <= 0) {
                complete(false);
                return false;
            }

            // prepare buffer to be written
            buffer.position(justReadBytes);
            buffer.trim();

            // write the Buffer
            outputStream.write(buffer);
            size -= justReadBytes;

            // check the remaining size here to avoid extra onWritePossible() invocation
            if (size <= 0) {
                complete(false);
                return false;
            }

            return true;
        }

        /**
         * Complete the download
         */
        private void complete(final boolean isError) {
            try {
                fileChannel.close();
            } catch (IOException e) {
                if (!isError) {
                    response.setStatus(500, e.getMessage());
                }
            }

            try {
                outputStream.close();
            } catch (IOException e) {
                if (!isError) {
                    response.setStatus(500, e.getMessage());
                }
            }

            if (response.isSuspended()) {
                response.resume();
            } else {
                response.finish();
            }
        }
    }
}
