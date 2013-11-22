/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.http.io.OutputBuffer;
import org.glassfish.grizzly.http.util.MimeType;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.Futures;
import org.glassfish.grizzly.utils.GenericAdapter;

/**
 * The basic class for {@link HttpHandler} implementations,
 * which processes requests to a static resources.
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public abstract class StaticHttpHandlerBase extends HttpHandler {
    private static final Logger LOGGER = Grizzly.logger(StaticHttpHandlerBase.class);

    private volatile boolean isFileCacheEnabled = true;
    
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
    @SuppressWarnings("UnusedDeclaration")
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
    @SuppressWarnings("UnusedDeclaration")
    public void setFileCacheEnabled(boolean isFileCacheEnabled) {
        this.isFileCacheEnabled = isFileCacheEnabled;
    }
    
    /**
     * <p>
     * Depending on {@link Response#isSendFileEnabled()} value, the method
     * will either use zero-copy {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)},
     * or auxiliary buffer to send file to the remote endpoint.
     * Note that all headers necessary for the file transfer must be set prior
     * to invoking this method as this will case the HTTP header to be flushed
     * to the client prior to sending the file content. This should also be the
     * last call to write any content to the remote endpoint.
     * </p>
     * 
     * <p>
     * It's required that the response be suspended when using this functionality.
     * It will be assumed that if the response wasn't suspended when this method
     * is called, that it's desired that this method manages the suspend/resume cycle.
     * </p>
     *
     * <p>
     * After calling this method the HTTP request processing will be completely
     * delegated to Grizzly and once send file is complete, Grizzly will release
     * all the associated resources.
     * It's possible to pass {@link CompletionHandler} to track send file progress.
     * </p>
     * 
     * @param response
     * @param file
     * @param completionHandler
     * 
     * @since 2.3
     */
    public static void sendFile(final Response response, final File file,
            final CompletionHandler<File> completionHandler) {
        response.setStatus(HttpStatus.OK_200);

        // In case this sendFile(...) is called directly by user - pickup the content-type
        pickupContentType(response, file.getPath());

        final long length = file.length();
        response.setContentLengthLong(length);
        response.addDateHeader(Header.Date, System.currentTimeMillis());
        if (!response.isSendFileEnabled() || response.getRequest().isSecure()) {
            sendUsingBuffers(response, file, completionHandler);
        } else {
            sendZeroCopy(response, file, completionHandler);
        }
    }

    private static void sendUsingBuffers(final Response response,
            final File file, final CompletionHandler<File> completionHandler) {
        final int chunkSize = 8192;
        
        final NIOOutputStream outputStream = response.getOutputStream();
        
        final NonBlockingDownloadHandler nonBlockingDownloadHandler =
                new NonBlockingDownloadHandler(response, outputStream,
                    file, completionHandler, chunkSize);

        if (!response.isSuspended()) {
            response.suspend();
        }
        
        outputStream.notifyWritePossible(nonBlockingDownloadHandler);
    }
    
    private static void sendZeroCopy(final Response response, final File file,
            final CompletionHandler<File> completionHandler) {
        
        final OutputBuffer outputBuffer = response.getOutputBuffer();
        outputBuffer.sendfile(file,
                Futures.<File, WriteResult>toAdaptedCompletionHandler(
                null, completionHandler, new GenericAdapter<WriteResult, File>() {

            @Override
            public File adapt(final WriteResult result) {
                return file;
            }
        }));
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
        response.sendError(404);
    }

    /**
     * Lookup a resource based on the request URI, and send it using send file.
     *
     * @param uri The request URI
     * @param request the {@link Request}
     * @param response the {@link Response}
     * @throws Exception
     */
    protected abstract boolean handle(final String uri,
            final Request request,
            final Response response) throws Exception;

    
    // --------------------------------------------------------- Private Methods
    

    protected FileCacheFilter lookupFileCache(final FilterChainContext fcContext) {
        return fcContext.getFilterChain().getByType(FileCacheFilter.class);
    }

    protected static void pickupContentType(final Response response,
            final String path) {
        if (!response.getResponse().isContentTypeSet()) {
            int dot = path.lastIndexOf('.');

            if (dot > 0) {
                String ext = path.substring(dot + 1);
                String ct = MimeType.get(ext);
                if (ct != null) {
                    response.setContentType(ct);
                }
            } else {
                response.setContentType(MimeType.get("html"));
            }
        }
    }

    protected static void addCachingHeaders(final Response response,
                                          final File file) {
        final StringBuilder sb = new StringBuilder();

        final long fileLength = file.length();
        final long lastModified = file.lastModified();
        if ((fileLength >= 0) || (lastModified >= 0)) {
            sb.append('"').append(fileLength).append('-').
                    append(lastModified).append('"');
            response.setHeader(Header.ETag, sb.toString());
        }
        response.addDateHeader(Header.LastModified, lastModified);

    }
    
    private static class NonBlockingDownloadHandler implements WriteHandler {
        // keep the remaining size
        private final File file;
        private final CompletionHandler<File> completionHandler;
        private final Response response;
        private final NIOOutputStream outputStream;
        private final FileChannel fileChannel;
        private final MemoryManager mm;
        private final int chunkSize;
        
        private volatile long size;
        
        NonBlockingDownloadHandler(final Response response,
                final NIOOutputStream outputStream, final File file,
                final CompletionHandler<File> completionHandler,
                final int chunkSize) {
            
            try {
                fileChannel = new FileInputStream(file).getChannel();
            } catch (FileNotFoundException e) {
                throw new IllegalStateException("File should have existed", e);
            }

            this.file = file;
            this.completionHandler = completionHandler;
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
                outputStream.notifyWritePossible(this);
            }
        }

        @Override
        public void onError(Throwable t) {
            LOGGER.log(Level.FINE, "[onError] ", t);
            response.setStatus(500, t.getMessage());
            fail(t);
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
            final int justReadBytes = (int) Buffers.readFromFileChannel(
                    fileChannel, buffer);
            
            if (justReadBytes <= 0) {
                complete();
                return false;
            }

            // prepare buffer to be written
            buffer.trim();

            // write the Buffer
            outputStream.write(buffer);
            size -= justReadBytes;

            // check the remaining size here to avoid extra onWritePossible() invocation
            if (size <= 0) {
                complete();
                return false;
            }

            return true;
        }

        /**
         * Complete the download
         */
        private void complete() {
            try {
                fileChannel.close();
            } catch (IOException e) {
                response.setStatus(500, e.getMessage());
            }

            try {
                outputStream.close();
            } catch (IOException e) {
                response.setStatus(500, e.getMessage());
            }

            if (completionHandler != null) {
                completionHandler.completed(file);
            }
            
            if (response.isSuspended()) {
                response.resume();
            } else {
                response.finish();
            }
        }
        
        /**
         * Complete the download
         */
        private void fail(final Throwable t) {
            try {
                fileChannel.close();
            } catch (IOException e) {
            }

            try {
                outputStream.close();
            } catch (IOException e) {
            }

            if (completionHandler != null) {
                completionHandler.failed(t);
            }
            
            if (response.isSuspended()) {
                response.resume();
            } else {
                response.finish();
            }
        }
    }
}
