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
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.http.HttpFilter;
import com.sun.grizzly.http.core.HttpContent;
import com.sun.grizzly.http.core.HttpRequest;
import com.sun.grizzly.impl.FutureImpl;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author oleksiys
 */
public class ClientDownloadFilter extends BaseFilter {
    private final static Logger logger = Grizzly.logger(ClientDownloadFilter.class);
    
    private final URI uri;
    private final String fileName;
    
    private FutureImpl<String> completeFuture;

    private volatile FileChannel output;
    private volatile int bytesDownloaded;

    public ClientDownloadFilter(URI uri, FutureImpl<String> completeFuture) {
        this.uri = uri;
        
        String resourcePath =
                uri.getPath().trim().length() > 0 ? uri.getPath().trim() : "/";

        int lastSlashIdx = resourcePath.lastIndexOf('/');
        if (lastSlashIdx != -1 && lastSlashIdx < resourcePath.length() - 1) {
            fileName = resourcePath.substring(lastSlashIdx + 1);
        } else {
            fileName = "download#" + System.currentTimeMillis() + ".txt";
        }
        
        this.completeFuture = completeFuture;
    }

    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        final HttpRequest httpRequest = HttpRequest.builder().method("GET")
                .uri(uri.toString()).protocol(HttpFilter.HTTP_1_1)
                .header("Host", uri.getHost()).build();
        logger.log(Level.INFO, "Connected... Sending the request: " + httpRequest);

        ctx.write(httpRequest);

        return ctx.getStopAction();
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        try {
            final HttpContent httpContent = (HttpContent) ctx.getMessage();

            logger.log(Level.FINE, "Got HTTP response chunk");
            if (output == null) {
                logger.log(Level.INFO, "HTTP response: " + httpContent.getHttpHeader());
                logger.log(Level.FINE, "Create a file: " + fileName);
                FileOutputStream fos = new FileOutputStream(fileName);
                output = fos.getChannel();
            }

            Buffer buffer = httpContent.getContent();

            logger.log(Level.FINE, "HTTP content size: " + buffer.remaining());
            if (buffer.remaining() > 0) {
                bytesDownloaded += buffer.remaining();
                
                ByteBuffer byteBuffer = buffer.toByteBuffer();
                do {
                    output.write(byteBuffer);
                } while (byteBuffer.hasRemaining());
                
                buffer.dispose();
            }

            if (httpContent.isLast()) {
                logger.log(Level.FINE, "Downloaded done: " + bytesDownloaded + " bytes");
                completeFuture.result(fileName);
                close();
            }
        } catch (IOException e) {
            close();
        }

        return ctx.getStopAction();
    }

    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        close();
        return ctx.getStopAction();
    }

    private void close() throws IOException {
        final FileChannel localOutput = this.output;
        if (localOutput != null) {
            localOutput.close();
        }

        if (!completeFuture.isDone()) {
            completeFuture.failure(new IOException("Connection was closed"));
        }
    }
}
