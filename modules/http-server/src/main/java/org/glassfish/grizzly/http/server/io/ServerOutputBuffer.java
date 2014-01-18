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

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.io.OutputBuffer;
import org.glassfish.grizzly.http.server.Response;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import org.glassfish.grizzly.localization.LogMessages;

public class ServerOutputBuffer extends OutputBuffer {

    private Response serverResponse;

    public void initialize(final Response response,
            final FilterChainContext ctx) {
        super.initialize(response.getResponse(), response.isSendFileEnabled(), ctx);
        this.serverResponse = response;
    }

    @Override
    public void sendfile(final File file, final long offset, final long length,
            final CompletionHandler<WriteResult> handler) {

        if (!sendfileEnabled) {
                   throw new IllegalStateException("sendfile support isn't available.");
        }

        // check the suspend status at the time this method was invoked
        // and take action based on this value
        final boolean suspendedAtStart = serverResponse.isSuspended();
        final CompletionHandler<WriteResult> ch;
        if (suspendedAtStart && handler != null) {
            // provided CompletionHandler assumed to manage suspend/resume
            ch = handler;
        } else if (!suspendedAtStart && handler != null) {
            // provided CompletionHandler assumed to not managed suspend/resume
            ch = suspendAndCreateHandler(handler);
        } else {
            // create internal CompletionHandler that will take the
            // appropriate action depending on the current suspend status
            ch = createInternalCompletionHandler(file, suspendedAtStart);
        }
        super.sendfile(file, offset, length, ch);
    }

    @Override
    public void recycle() {
        serverResponse = null;
        super.recycle();
    }

    @Override
    protected Executor getThreadPool() {
        return serverResponse.getRequest().getRequestExecutor();
    }
    
    private CompletionHandler<WriteResult> createInternalCompletionHandler(
                final File file, final boolean suspendedAtStart) {

            CompletionHandler<WriteResult> ch;
            if (!suspendedAtStart) {
                serverResponse.suspend();
            }
            ch = new CompletionHandler<WriteResult>() {
                @Override
                public void cancelled() {
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.log(Level.WARNING,
                                LogMessages.WARNING_GRIZZLY_HTTP_SERVER_SERVEROUTPUTBUFFER_FILE_TRANSFER_CANCELLED(file.getAbsolutePath()));
                    }
                    serverResponse.resume();
                }

                @Override
                public void failed(Throwable throwable) {
                    if (LOGGER.isLoggable(Level.SEVERE)) {
                        LOGGER.log(Level.SEVERE,
                                LogMessages.WARNING_GRIZZLY_HTTP_SERVER_SERVEROUTPUTBUFFER_FILE_TRANSFER_FAILED(file.getAbsolutePath(), throwable.getMessage()),
                                throwable);
                    }
                    serverResponse.resume();
                }

                @Override
                public void completed(WriteResult result) {
                    serverResponse.resume();
                }

                @Override
                public void updated(WriteResult result) {
                    // no-op
                }
            };
            return ch;

        }

        private CompletionHandler<WriteResult> suspendAndCreateHandler(
                final CompletionHandler<WriteResult> handler) {
            serverResponse.suspend();
            return new CompletionHandler<WriteResult>() {

                @Override
                public void cancelled() {
                    handler.cancelled();
                    serverResponse.resume();
                }

                @Override
                public void failed(Throwable throwable) {
                    handler.failed(throwable);
                    serverResponse.resume();
                }

                @Override
                public void completed(WriteResult result) {
                    handler.completed(result);
                    serverResponse.resume();
                }

                @Override
                public void updated(WriteResult result) {
                    handler.updated(result);
                }
            };
        }
}
