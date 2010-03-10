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
import com.sun.grizzly.http.core.HttpContent;
import com.sun.grizzly.http.core.HttpPacket;
import com.sun.grizzly.http.core.HttpRequest;
import com.sun.grizzly.http.core.HttpResponse;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.MemoryUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 *
 * @author oleksiys
 */
public class WebServerFilter extends BaseFilter {
    private static final Logger logger = Grizzly.logger(WebServerFilter.class);
    private final File rootFolderFile;

    public WebServerFilter(String rootFolder) {
        if (rootFolder != null) {
            this.rootFolderFile = new File(rootFolder);
        } else {
            this.rootFolderFile = new File(".");
        }

        if (!rootFolderFile.isDirectory()) {
            throw new IllegalStateException("Directory " + rootFolder + " doesn't exist");
        }
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx)
            throws IOException {

        final Object message = ctx.getMessage();
        if (message instanceof DownloadCompletionHandler) {
            // Download completed
            return ctx.getStopAction();
        }

        final HttpContent httpContent = (HttpContent) ctx.getMessage();
        final HttpRequest request = (HttpRequest) httpContent.getHttpHeader();

        if (!httpContent.isLast()) {
            // swallow content
            return ctx.getStopAction();
        }

        final String localURL = extractLocalURL(request);

        final File file = new File(rootFolderFile, localURL);

        logger.info("Request file: " + file.getAbsolutePath());


        if (!file.isFile()) {
            final HttpPacket response = create404(request);
            ctx.write(response);
            
            return ctx.getStopAction();
        } else {
            ctx.suspend();
            final NextAction suspendAction = ctx.getSuspendAction();

            downloadFile(ctx, request, file);
            return suspendAction;
        }
    }

    private void downloadFile(FilterChainContext ctx,
            HttpRequest request, File file) throws IOException {
        final DownloadCompletionHandler downloadHandler =
                new DownloadCompletionHandler(ctx, request, file);
        downloadHandler.start();
    }

    private static HttpPacket create404(HttpRequest request) {
        final HttpResponse responseHeader = HttpResponse.builder().
                protocol(request.getProtocol()).status(404).
                reasonPhrase("Not Found").build();
        
        final HttpContent content =
                responseHeader.httpContentBuilder().
                content(MemoryUtils.wrap(null,
                "Can not find file, corresponding to URI: " + request.getRequestURIRef().getDecodedURI())).
                build();
        return content;
    }

    private static String extractLocalURL(HttpRequest request) {
        String url = request.getRequestURIRef().getDecodedURI();

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

    private static class DownloadCompletionHandler
            extends EmptyCompletionHandler<WriteResult>{

        private final MemoryManager memoryManager;
        private final InputStream in;
        private final FilterChainContext ctx;
        private final HttpResponse response;

        private volatile boolean isDone;

        public DownloadCompletionHandler(FilterChainContext ctx,
                HttpRequest request, File file) throws FileNotFoundException {
            
            in = new FileInputStream(file);
            this.ctx = ctx;
            response = HttpResponse.builder().
                protocol(request.getProtocol()).status(200).
                reasonPhrase("OK").chunked(true).build();
            memoryManager = ctx.getConnection().getTransport().getMemoryManager();
        }

        public void start() throws IOException {
            sendFileChunk();
        }

        public void sendFileChunk() throws IOException {
            final Buffer buffer = memoryManager.allocate(1024);
            
            final byte[] bufferByteArray = buffer.toByteBuffer().array();
            final int offset = buffer.toByteBuffer().arrayOffset();
            final int length = buffer.remaining();

            int bytesRead = in.read(bufferByteArray, offset, length);
            final HttpContent content;
            
            if (bytesRead == -1) {
                content = response.httpTrailerBuilder().build();
                isDone = true;
            } else {
                buffer.limit(bytesRead);
                content = response.httpContentBuilder().content(buffer).build();
            }
            
            ctx.write(content, this);
        }

        @Override
        public void completed(WriteResult result) {
            try {
                if (!isDone) {
                    sendFileChunk();
                } else {
                    close();
                    resume();
                }
            } catch (IOException e) {
                failed(e);
            }
        }

        @Override
        public void cancelled() {
            close();
            resume();
        }

        @Override
        public void failed(Throwable throwable) {
            close();
            resume();
        }

        public boolean isDone() {
            return isDone;
        }

        private void close() {
            try {
                in.close();
            } catch (IOException e) {
                logger.fine("Error closing a downloading file");
            }
        }

        private void resume() {
            ctx.setMessage(this);
            ctx.resume();
        }
    }
}
