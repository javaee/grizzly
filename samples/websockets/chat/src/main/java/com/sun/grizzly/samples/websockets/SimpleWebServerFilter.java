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
package com.sun.grizzly.samples.websockets;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpPacket;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.HttpResponsePacket;
import com.sun.grizzly.memory.MemoryUtils;
import java.io.CharConversionException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Simple Web server implementation, which locates requested resources in a
 * local filesystem and transfers it asynchronously to a client.
 *
 * @author Alexey Stashok
 */
public class SimpleWebServerFilter extends BaseFilter {
    private static final Logger logger = Grizzly.logger(SimpleWebServerFilter.class);
    private final File rootFolderFile;

    /**
     * Construct a WebServer
     * @param rootFolder Root folder in a local filesystem, where server will look
     *                   for resources
     */
    public SimpleWebServerFilter(String rootFolder) {
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

        // Otherwise cast message to a HttpContent
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

        logger.info("Request file: " + file.getAbsolutePath());

        if (!file.isFile()) {
            // If file doesn't exist - response 404
            final HttpPacket response = create404(request);
            ctx.write(response);
        } else {
            // if file exists
            final int size = (int) file.length();
            final byte[] array = new byte[size];

            InputStream is = new FileInputStream(file);
            try {
                is.read(array);
            } finally  {
                is.close();
            }

            final HttpResponsePacket response = HttpResponsePacket.builder(request)
                .protocol(request.getProtocol())
                .status(200)
                .reasonPhrase("OK")
                .contentLength(size)
                .chunked(false)
                .build();

            Buffer buffer = MemoryUtils.wrap(null, array);
            final HttpContent content = HttpContent.builder(response)
                    .content(buffer)
                    .last(true)
                    .build();

            ctx.write(content);
        }

        // return stop action
        return ctx.getStopAction();
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
        final HttpContent content =
                responseHeader.httpContentBuilder()
                .content(MemoryUtils.wrap(null,
                "Can not find file, corresponding to URI: " + request.getRequestURIRef().getDecodedURI()))
                .last(true)
                .build();
        return content;
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

}
