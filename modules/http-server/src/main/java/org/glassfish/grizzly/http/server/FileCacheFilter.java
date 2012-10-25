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

package org.glassfish.grizzly.http.server;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.server.filecache.FileCache;

import java.io.IOException;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.Writer;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.http.Method;

/**
 *
 * @author oleksiys
 */
public class FileCacheFilter extends BaseFilter {

    private final FileCache fileCache;

    public FileCacheFilter(FileCache fileCache) {

        this.fileCache = fileCache;

    }


    // ----------------------------------------------------- Methods from Filter


    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {

        final HttpContent requestContent = (HttpContent) ctx.getMessage();
        final HttpRequestPacket request = (HttpRequestPacket) requestContent.getHttpHeader();
        
        if (fileCache.isEnabled() && Method.GET.equals(request.getMethod())) {
            final HttpPacket response = fileCache.get(request);
            if (response != null) {
                ctx.write(response);

                
                final Connection connection = ctx.getConnection();
                final Writer writer = connection.getTransport().getWriter(connection);
                if (writer instanceof AsyncQueueWriter) {
                    final AsyncQueueWriter asyncQueueWriter =
                            (AsyncQueueWriter) writer;
                    
                    if (!asyncQueueWriter.canWrite(connection, 1)) {  // if connection write queue is overloaded
                        // prepare context for suspend
                        final NextAction suspendAction = ctx.getSuspendAction();
                        ctx.suspend();

                        // notify when connection becomes writable, so we can resume it
                        asyncQueueWriter.notifyWritePossible(connection,
                                new WriteHandler() {

                            @Override
                            public void onWritePossible() throws Exception {
                                finish();
                            }

                            @Override
                            public void onError(Throwable t) {
                                finish();
                            }

                            private void finish() {
                                ctx.completeAndRecycle();
                            }
                        }, 1);

                        return suspendAction;
                    }
                }
                
                return ctx.getStopAction();
            }
        }

        return ctx.getInvokeAction();

    }

    public FileCache getFileCache() {
        return fileCache;
    }
}
