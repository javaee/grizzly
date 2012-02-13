/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.utils;

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ParallelWriteFilter extends BaseFilter {
    
        private static final Logger LOGGER = Grizzly.logger(ParallelWriteFilter.class);
        private final int packetsNumber;
        private final int size;

        private final ExecutorService executorService;

        public ParallelWriteFilter(ExecutorService executorService,
                                   int packetsNumber, int size) {
            this.executorService = executorService;
            this.packetsNumber = packetsNumber;
            this.size = size;
        }

        @Override
        public NextAction handleRead(final FilterChainContext ctx) throws IOException {
            final Connection connection = ctx.getConnection();
            for (int i = 0; i < packetsNumber; i++) {
                final int packetNumber = i;

                executorService.submit(new Runnable() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public void run() {
                        final char[] chars = new char[size];
                        Arrays.fill(chars, (char) ('0' + (packetNumber % 10)));
                        final String content = new String(chars);
                        final FutureImpl<Boolean> completionHandlerFuture =
                                SafeFutureImpl.create();
                        try {

                            connection.write(content, new CompletionHandler<WriteResult>() {
                                @Override
                                public void cancelled() {
                                    completionHandlerFuture.failure(new IOException("cancelled"));
                                }

                                @Override
                                public void failed(Throwable throwable) {
                                    completionHandlerFuture.failure(throwable);
                                }

                                @Override
                                public void completed(WriteResult result) {
                                    completionHandlerFuture.result(true);
                                }

                                @Override
                                public void updated(WriteResult result) {
                                }
                            });

                            completionHandlerFuture.get(10, TimeUnit.SECONDS);

                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "sending packet #" + packetNumber, e);
                        }
                    }
                });
            }

            return ctx.getInvokeAction();
        }
    }
