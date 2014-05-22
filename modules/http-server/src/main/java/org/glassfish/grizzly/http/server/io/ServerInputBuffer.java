/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http.server.io;

import java.io.IOException;
import java.util.concurrent.Executor;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpBrokenContent;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.io.InputBuffer;
import org.glassfish.grizzly.http.server.Request;

/**
 * Server-side implementation of the {@link InputBuffer}.
 * 
 * @author Alexey Stashok
 */
public class ServerInputBuffer extends InputBuffer {
    private long totalReadContentInBytes;
    private Request serverRequest;
    
    public void initialize(final Request serverRequest,
            final FilterChainContext ctx) {
        this.serverRequest = serverRequest;
        super.initialize(serverRequest.getRequest(), ctx);
    }
    
    /**
     * Initiates asynchronous data receiving.
     *
     * This is service method, usually users don't have to call it explicitly.
     */
    @Override
    public void initiateAsyncronousDataReceiving() {
        if (!checkChunkedMaxPostSize()) {
            final HttpContent brokenContent =
                    HttpBrokenContent.builder(serverRequest.getRequest())
                    .error(new IOException("The HTTP request content exceeds max post size"))
                    .build();
            try {
                append(brokenContent);
            } catch (IOException ignored) {
            }
            
            return;
        }
        
        super.initiateAsyncronousDataReceiving();
    }

    @Override
    protected HttpContent blockingRead() throws IOException {
        if (!checkChunkedMaxPostSize()) {
            throw new IOException("The HTTP request content exceeds max post size");
        }
        
        return super.blockingRead();
    }

    @Override
    protected void updateInputContentBuffer(final Buffer buffer) {
        totalReadContentInBytes += buffer.remaining();
        super.updateInputContentBuffer(buffer);
    }
    
    
    @Override
    public void recycle() {
        serverRequest = null;
        totalReadContentInBytes = 0;
        super.recycle();
    }

    @Override
    protected Executor getThreadPool() {
        return serverRequest.getRequestExecutor();
    }

    private boolean checkChunkedMaxPostSize() {
        if (serverRequest.getRequest().isChunked()) {
            final long maxPostSize = serverRequest.getHttpFilter().getConfiguration().getMaxPostSize();
            return maxPostSize < 0 || maxPostSize > totalReadContentInBytes;
        }
        
        return true;
    }
}
