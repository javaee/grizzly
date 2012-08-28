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

package org.glassfish.grizzly.samples.http.download;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.impl.FutureImpl;

/**
 * HTTP client download filter.
 * This Filter is responsible for asynchronous downloading of a HTTP resource and
 * saving its content in a local file.
 *
 * @author Alexey Stashok
 */
public class ClientDownloadFilter extends BaseFilter {
    private final static Logger logger = Grizzly.logger(ClientDownloadFilter.class);
    
    // URI of a remote resource
    private final URI uri;
    // local filename, where content will be saved
    private final String fileName;
    
    // Download completion future
    private FutureImpl<String> completeFuture;

    // local file channel, where we save resource content
    private volatile FileChannel output;
    // number of bytes downloaded
    private volatile int bytesDownloaded;

    private final String resourcePath;

    /**
     * <tt>ClientDownloadFilter</tt> constructor
     *
     * @param uri {@link URI} of a remote resource to download
     * @param completeFuture download completion handler ({@link FutureImpl})
     */
    public ClientDownloadFilter(URI uri, FutureImpl<String> completeFuture) {
        this.uri = uri;
        
        // Extracting resource path
        resourcePath =
                uri.getPath().trim().length() > 0 ? uri.getPath().trim() : "/";

        int lastSlashIdx = resourcePath.lastIndexOf('/');
        if (lastSlashIdx != -1 && lastSlashIdx < resourcePath.length() - 1) {
            // if the path contains a filename - take it as local filename
            fileName = resourcePath.substring(lastSlashIdx + 1);
        } else {
            // if the path doesn't contain filename - we will use default filename
            fileName = "download#" + System.currentTimeMillis() + ".txt";
        }
        
        this.completeFuture = completeFuture;
    }

    /**
     * The method is called, when a client connection gets connected to a web
     * server.
     * When this method gets called by a framework - it means that client connection
     * has been established and we can send HTTP request to the web server.
     *
     * @param ctx Client connect processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        // Build the HttpRequestPacket, which will be sent to a server
        // We construct HTTP request version 1.1 and specifying the URL of the
        // resource we want to download
        final HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("GET")
                .uri(resourcePath).protocol(Protocol.HTTP_1_1)
                .header("Host", uri.getHost()).build();
        logger.log(Level.INFO, "Connected... Sending the request: {0}", httpRequest);

        // Write the request asynchronously
        ctx.write(httpRequest);

        // Return the stop action, which means we don't expect next filter to process
        // connect event
        return ctx.getStopAction();
    }

    /**
     * The method is called, when we receive a {@link HttpContent} from a server.
     * Once we receive one - we save the content chunk to a local file.
     * 
     * @param ctx Request processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        try {
            // Cast message to a HttpContent
            final HttpContent httpContent = (HttpContent) ctx.getMessage();

            logger.log(Level.FINE, "Got HTTP response chunk");
            if (output == null) {
                // If local file wasn't created - create it
                logger.log(Level.INFO, "HTTP response: {0}", httpContent.getHttpHeader());
                logger.log(Level.FINE, "Create a file: {0}", fileName);
                FileOutputStream fos = new FileOutputStream(fileName);
                output = fos.getChannel();
            }

            // Get HttpContent's Buffer
            final Buffer buffer = httpContent.getContent();

            logger.log(Level.FINE, "HTTP content size: {0}", buffer.remaining());
            if (buffer.remaining() > 0) {
                bytesDownloaded += buffer.remaining();
                
                // save Buffer to a local file, represented by FileChannel
                ByteBuffer byteBuffer = buffer.toByteBuffer();
                do {
                    output.write(byteBuffer);
                } while (byteBuffer.hasRemaining());
                
                // Dispose a content buffer
                buffer.dispose();
            }

            if (httpContent.isLast()) {
                // it's last HttpContent - we close the local file and
                // notify about download completion
                logger.log(Level.FINE, "Downloaded done: {0} bytes", bytesDownloaded);
                completeFuture.result(fileName);
                close();
            }
        } catch (IOException e) {
            close();
        }

        // Return stop action, which means we don't expect next filter to process
        // read event
        return ctx.getStopAction();
    }

    /**
     * The method is called, when the client connection will get closed.
     * Intercepting this method let's use release resources, like local FileChannel,
     * if it wasn't released before.
     *
     * @param ctx Request processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        close();
        return ctx.getStopAction();
    }

    /**
     * Method closes the local file channel, and if download wasn't completed -
     * notify {@link FutureImpl} about download failure.
     * 
     * @throws IOException If failed to close <em>localOutput</em>.
     */
    private void close() throws IOException {
        final FileChannel localOutput = this.output;
        // close the local file channel
        if (localOutput != null) {
            localOutput.close();
        }

        if (!completeFuture.isDone()) {
            //noinspection ThrowableInstanceNeverThrown
            completeFuture.failure(new IOException("Connection was closed"));
        }
    }
}
