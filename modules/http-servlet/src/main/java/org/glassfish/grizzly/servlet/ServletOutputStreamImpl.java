/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.glassfish.grizzly.servlet;

import java.io.IOException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.localization.LogMessages;

/**
 *
 * @author Jeanfrancois Arcand
 */
public class ServletOutputStreamImpl extends ServletOutputStream {

    private final HttpServletResponseImpl servletResponse;
    private NIOOutputStream outputStream;

    private WriteHandler writeHandler = null;
    private boolean hasSetWriteListener = false;
    private boolean prevIsReady = true;

    private static final ThreadLocal<Boolean> CAN_WRITE_SCOPE =
            new ThreadLocal<Boolean>();
    
    protected ServletOutputStreamImpl(
            final HttpServletResponseImpl servletResponse) {
        this.servletResponse = servletResponse;
    }

    protected void initialize() throws IOException {
        this.outputStream = servletResponse.getResponse().createOutputStream();
    }

    @Override
    public void write(int i) throws IOException {
        if (!prevIsReady) {
            throw new IllegalStateException(
                    LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_NON_BLOCKING_ERROR());
        }
        
        outputStream.write(i);
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (!prevIsReady) {
            throw new IllegalStateException(
                    LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_NON_BLOCKING_ERROR());
        }
        
        outputStream.write(b, off, len);
    }

    /**
     * Will send the buffer to the client.
     */
    @Override
    public void flush() throws IOException {
        if (!prevIsReady) {
            throw new IllegalStateException(
                    LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_NON_BLOCKING_ERROR());
        }
        
        outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        if (!prevIsReady) {
            throw new IllegalStateException(
                    LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_NON_BLOCKING_ERROR());
        }
        
        outputStream.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReady() {
        if (!hasSetWriteListener) {
            throw new IllegalStateException(
                    LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_OUTPUTSTREAM_ISREADY_ERROR());
        }
        
        if (!prevIsReady) {
            return false;
        }
        
        boolean result = outputStream.canWrite();
        if (!result) {
            if (hasSetWriteListener) {
                prevIsReady = false; // Not data available
                CAN_WRITE_SCOPE.set(Boolean.TRUE);
                try {
                    outputStream.notifyCanWrite(writeHandler);
                } finally {
                    CAN_WRITE_SCOPE.remove();
                }
                
            } else {
                prevIsReady = true;  // Allow next .isReady() call to check underlying inputStream
            }
        }
        
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWriteListener(WriteListener writeListener) {
        if (hasSetWriteListener) {
            throw new IllegalStateException("The WriteListener has already been set.");
        }

        final HttpServletRequestImpl req = servletResponse.servletRequest;
        if (!(req.isAsyncStarted() || req.isUpgrade())) {
            throw new IllegalStateException(
                    LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_OUTPUTSTREAM_SETWRITELISTENER_ERROR());
        }
        
        writeHandler = new WriteHandlerImpl(writeListener);
        hasSetWriteListener = true;
    }
    
    void recycle() {
        outputStream = null;
        
        writeHandler = null;
        prevIsReady = true;
        hasSetWriteListener = false;
    }
    
    class WriteHandlerImpl implements WriteHandler {
        private WriteListener writeListener = null;

        private WriteHandlerImpl(WriteListener listener) {
            writeListener = listener;
        }

        @Override
        public void onWritePossible() throws Exception {
            if (!Boolean.TRUE.equals(CAN_WRITE_SCOPE.get())) {
                invokeWriteCallback();
            } else {
                AsyncContextImpl.pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        invokeWriteCallback();
                    }
                });
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (!Boolean.TRUE.equals(CAN_WRITE_SCOPE.get())) {
                writeListener.onError(t);
            } else {
                AsyncContextImpl.pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        writeListener.onError(t);
                    }
                });
            }
        }


        private void invokeWriteCallback() {
            prevIsReady = true;
            try {
                writeListener.onWritePossible();
            } catch (Throwable t) {
                writeListener.onError(t);
            }
        }

    }    
}
