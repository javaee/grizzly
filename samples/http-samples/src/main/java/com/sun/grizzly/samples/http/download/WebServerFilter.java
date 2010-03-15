/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */
package com.sun.grizzly.samples.http.download;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.EmptyCompletionHandler;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpPacket;
import com.sun.grizzly.http.HttpRequest;
import com.sun.grizzly.http.HttpResponse;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.MemoryUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

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
     * The method is called once we have received a {@link HttpChunk}.
     *
     * Filter gets {@link HttpChunk}, which represents a part or complete HTTP
     * request. If it's just a chunk of a complete HTTP request - filter checks
     * whether it's the last chunk, if not - swallows content and returns.
     * If incoming {@link HttpChunk} represents complete HTTP request or it is
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

        // Get the incoming message
        final Object message = ctx.getMessage();
        
        // Check if this is DownloadCompletionHandler, which means download has
        // been completed and HTTP request processing was resumed.
        if (message instanceof DownloadCompletionHandler) {
            // Download completed
            return ctx.getStopAction();
        }

        // Otherwise cast message to a HttpContent
        final HttpContent httpContent = (HttpContent) ctx.getMessage();
        // Get HTTP request message header
        final HttpRequest request = (HttpRequest) httpContent.getHttpHeader();

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

        logger.info("Request file: " + file.getAbsolutePath());

        if (!file.isFile()) {
            // If file doesn't exist - response 404
            final HttpPacket response = create404(request);
            ctx.write(response);

            // return stop action
            return ctx.getStopAction();
        } else {
            // if file exists
            // suspend HttpRequest processing to send the HTTP response
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
     * @param ctx HttpRequest processing context
     * @param request HttpRequest
     * @param file local file
     * 
     * @throws IOException
     */
    private void downloadFile(FilterChainContext ctx,
            HttpRequest request, File file) throws IOException {
        // Create DownloadCompletionHandler, responsible for asynchronous
        // file transferring
        final DownloadCompletionHandler downloadHandler =
                new DownloadCompletionHandler(ctx, request, file);
        // Start the download
        downloadHandler.start();
    }

    /**
     * Create a 404 HttpResponse packet
     * @param request original HttpRequest
     *
     * @return 404 HttpContent
     */
    private static HttpPacket create404(HttpRequest request) {
        // Build 404 HttpResponse message headers
        final HttpResponse responseHeader = HttpResponse.builder().
                protocol(request.getProtocol()).status(404).
                reasonPhrase("Not Found").build();
        
        // Build 404 HttpContent on base of HttpResponse message header
        final HttpContent content =
                responseHeader.httpContentBuilder().
                content(MemoryUtils.wrap(null,
                "Can not find file, corresponding to URI: " + request.getRequestURIRef().getDecodedURI())).
                build();
        return content;
    }

    /**
     * Extract URL path from the HttpRequest
     *
     * @param request HttpRequest message header
     * @return requested URL path
     */
    private static String extractLocalURL(HttpRequest request) {
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
     * {@link com.sun.grizzly.CompletionHandler}, responsible for asynchronous file transferring
     * via HTTP protocol.
     */
    private static class DownloadCompletionHandler
            extends EmptyCompletionHandler<WriteResult>{

        // MemoryManager, used to allocate Buffers
        private final MemoryManager memoryManager;
        // Downloading FileInputStream
        private final InputStream in;
        // Suspended HttpRequest processing context
        private final FilterChainContext ctx;
        // HttpResponse message header
        private final HttpResponse response;

        // Completion flag
        private volatile boolean isDone;

        /**
         * Construct a DownloadCompletionHandler
         * 
         * @param ctx Suspended HttpRequest processing context
         * @param request HttpRequest message header
         * @param file local file to be sent
         * @throws FileNotFoundException
         */
        public DownloadCompletionHandler(FilterChainContext ctx,
                HttpRequest request, File file) throws FileNotFoundException {
            
            // Open file input stream
            in = new FileInputStream(file);
            this.ctx = ctx;
            // Build HttpResponse message header (send file using chunked HTTP messages).
            response = HttpResponse.builder().
                protocol(request.getProtocol()).status(200).
                reasonPhrase("OK").chunked(true).build();
            memoryManager = ctx.getConnection().getTransport().getMemoryManager();
        }

        /**
         * Start the file tranferring
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
            final byte[] bufferByteArray = buffer.toByteBuffer().array();
            final int offset = buffer.toByteBuffer().arrayOffset();
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
                // Create HttpContent, based on HttpResponse message header
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
                    // resume(finishing) HttpRequest processing
                    resume();
                }
            } catch (IOException e) {
                failed(e);
            }
        }

        /**
         * The method will be called, when file transferring was cancelled
         */
        @Override
        public void cancelled() {
            // Close local file input stream
            close();
            // resume the HttpRequest processing
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
            // resume the HttpRequest processing
            resume();
        }

        /**
         * Returns <tt>true</tt>, if file transfer was completed, or
         * <tt>failse</tt> otherwise.
         *
         * @return <tt>true</tt>, if file transfer was completed, or
         * <tt>failse</tt> otherwise.
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
         * Resume the HttpRequest processing
         */
        private void resume() {
            // Set this DownloadCompletionHandler as message
            ctx.setMessage(this);
            // Resume the request processing
            // After resume will be called - filter chain will execute
            // WebServerFilter.handleRead(...) again with the ctx as FilterChainContext.
            ctx.resume();
        }
    }
}
