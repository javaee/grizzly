/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.samples.http.download;

import java.io.CharConversionException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * Simple Web server implementation, which locates requested resources in a
 * local filesystem and transfers it asynchronously to a client.
 * 
 * @author Alexey Stashok
 */
public class WebServerFilter extends BaseFilter {
    private static final Logger logger = Grizzly.logger(WebServerFilter.class);
    private final File rootFolderFile;

    /**
     * Construct a WebServer
     * @param rootFolder Root folder in a local filesystem, where server will look
     *                   for resources
     */
    public WebServerFilter(String rootFolder) {
        if (rootFolder != null) {
            this.rootFolderFile = new File(rootFolder);
        } else {
            // if root folder wasn't specified - use the current folder
            this.rootFolderFile = new File(".");
        }

        // check whether the root folder
        if (!rootFolderFile.isDirectory()) {
            throw new IllegalStateException("Directory " + rootFolder + " doesn't exist");
        }
    }

    /**
     * The method is called once we have received some {@link HttpContent}.
     *
     * Filter gets {@link HttpContent}, which represents a part or complete HTTP
     * request. If it's just a chunk of a complete HTTP request - filter checks
     * whether it's the last chunk, if not - swallows content and returns.
     * If incoming {@link HttpContent} represents complete HTTP request or it is
     * the last HTTP request - it initiates file download and sends the file
     * asynchronously to the client.
     *
     * @param ctx Request processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleRead(FilterChainContext ctx)
            throws IOException {

        // Get the incoming message as HttpContent
        final HttpContent httpContent = (HttpContent) ctx.getMessage();
        // Get HTTP request message header
        final HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();

        // Check if it's the last HTTP request chunk
        if (!httpContent.isLast()) {
            // if not
            // swallow content
            return ctx.getStopAction();
        }

        // if entire request was parsed
        // extract requested resource URL path
        final String localURL = extractLocalURL(request);

        // Locate corresponding file
        final File file = new File(rootFolderFile, localURL);

        logger.log(Level.INFO, "Request file: {0}", file.getAbsolutePath());

        if (!file.isFile()) {
            // If file doesn't exist - response 404
            final HttpPacket response = create404(request);
            ctx.write(response);

            // return stop action
            return ctx.getStopAction();
        } else {
            // if file exists
            // suspend HttpRequestPacket processing to send the HTTP response
            // asynchronously
            ctx.suspend();
            final NextAction suspendAction = ctx.getSuspendAction();

            // Start asynchronous file download
            downloadFile(ctx, request, file);
            // return suspend action
            return suspendAction;
        }
    }

    /**
     * Start asynchronous file download
     *
     * @param ctx HttpRequestPacket processing context
     * @param request HttpRequestPacket
     * @param file local file
     * 
     * @throws IOException
     */
    private void downloadFile(FilterChainContext ctx,
            HttpRequestPacket request, File file) throws IOException {
        // Create DownloadCompletionHandler, responsible for asynchronous
        // file transferring
        final DownloadCompletionHandler downloadHandler =
                new DownloadCompletionHandler(ctx, request, file);
        // Start the download
        downloadHandler.start();
    }

    /**
     * Create a 404 HttpResponsePacket packet
     * @param request original HttpRequestPacket
     *
     * @return 404 HttpContent
     */
    private static HttpPacket create404(HttpRequestPacket request)
            throws CharConversionException {
        // Build 404 HttpResponsePacket message headers
        final HttpResponsePacket responseHeader = HttpResponsePacket.builder(request).
                protocol(request.getProtocol()).status(404).
                reasonPhrase("Not Found").build();
        
        // Build 404 HttpContent on base of HttpResponsePacket message header
        return responseHeader.httpContentBuilder().
                    content(Buffers.wrap(null,
                                         "Can not find file, corresponding to URI: "
                                                 + request.getRequestURIRef().getDecodedURI())).
                          build();
    }

    /**
     * Extract URL path from the HttpRequestPacket
     *
     * @param request HttpRequestPacket message header
     * @return requested URL path
     */
    private static String extractLocalURL(HttpRequestPacket request)
            throws CharConversionException {
        // Get requested URL
        String url = request.getRequestURIRef().getDecodedURI();

        // Extract path
        final int idx;
        if ((idx = url.indexOf("://")) != -1) {
            final int localPartStart = url.indexOf('/', idx + 3);
            if (localPartStart != -1) {
                url = url.substring(localPartStart + 1);
            } else {
                url = "/";
            }
        }

        return url;
    }

    /**
     * {@link org.glassfish.grizzly.CompletionHandler}, responsible for asynchronous file transferring
     * via HTTP protocol.
     */
    private static class DownloadCompletionHandler
            extends EmptyCompletionHandler<WriteResult>{

        // MemoryManager, used to allocate Buffers
        private final MemoryManager memoryManager;
        // Downloading FileInputStream
        private final InputStream in;
        // Suspended HttpRequestPacket processing context
        private final FilterChainContext ctx;
        // HttpResponsePacket message header
        private final HttpResponsePacket response;

        // Completion flag
        private volatile boolean isDone;

        /**
         * Construct a DownloadCompletionHandler
         * 
         * @param ctx Suspended HttpRequestPacket processing context
         * @param request HttpRequestPacket message header
         * @param file local file to be sent
         * @throws FileNotFoundException
         */
        public DownloadCompletionHandler(FilterChainContext ctx,
                HttpRequestPacket request, File file) throws FileNotFoundException {
            
            // Open file input stream
            in = new FileInputStream(file);
            this.ctx = ctx;
            // Build HttpResponsePacket message header (send file using chunked HTTP messages).
            response = HttpResponsePacket.builder(request).
                protocol(request.getProtocol()).status(200).
                reasonPhrase("OK").chunked(true).build();
            memoryManager = ctx.getMemoryManager();
        }

        /**
         * Start the file transferring
         * 
         * @throws IOException
         */
        public void start() throws IOException {
            sendFileChunk();
        }

        /**
         * Send the next file chunk
         * @throws IOException
         */
        public void sendFileChunk() throws IOException {
            // Allocate a new buffer
            final Buffer buffer = memoryManager.allocate(1024);
            
            // prepare byte[] for InputStream.read(...)
            final byte[] bufferByteArray = buffer.array();
            final int offset = buffer.arrayOffset();
            final int length = buffer.remaining();

            // Read file chunk from the file input stream
            int bytesRead = in.read(bufferByteArray, offset, length);
            final HttpContent content;
            
            if (bytesRead == -1) {
                // if the file was completely sent
                // build the last HTTP chunk
                content = response.httpTrailerBuilder().build();
                isDone = true;
            } else {
                // Prepare the Buffer
                buffer.limit(bytesRead);
                // Create HttpContent, based on HttpResponsePacket message header
                content = response.httpContentBuilder().content(buffer).build();
            }

            // Send a file chunk asynchronously.
            // Once the chunk will be sent, the DownloadCompletionHandler.completed(...) method
            // will be called, or DownloadCompletionHandler.failed(...) is error will happen.
            ctx.write(content, this);
        }

        /**
         * Method gets called, when file chunk was successfully sent.
         * @param result the result
         */
        @Override
        public void completed(WriteResult result) {
            try {
                if (!isDone) {
                    // if transfer is not completed - send next file chunk
                    sendFileChunk();
                } else {
                    // if transfer is completed - close the local file input stream.
                    close();
                    // resume(finishing) HttpRequestPacket processing
                    resume();
                }
            } catch (IOException e) {
                failed(e);
            }
        }

        /**
         * The method will be called, when file transferring was canceled
         */
        @Override
        public void cancelled() {
            // Close local file input stream
            close();
            // resume the HttpRequestPacket processing
            resume();
        }

        /**
         * The method will be called, if file transferring was failed.
         * @param throwable the cause
         */
        @Override
        public void failed(Throwable throwable) {
            // Close local file input stream
            close();
            // resume the HttpRequestPacket processing
            resume();
        }

        /**
         * Returns <tt>true</tt>, if file transfer was completed, or
         * <tt>false</tt> otherwise.
         *
         * @return <tt>true</tt>, if file transfer was completed, or
         * <tt>false</tt> otherwise.
         */
        public boolean isDone() {
            return isDone;
        }

        /**
         * Close the local file input stream.
         */
        private void close() {
            try {
                in.close();
            } catch (IOException e) {
                logger.fine("Error closing a downloading file");
            }
        }

        /**
         * Resume the HttpRequestPacket processing
         */
        private void resume() {
            // Resume the request processing
            ctx.resume(ctx.getStopAction());
        }
    }
}
