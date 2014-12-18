/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.nio.transport;

import java.io.IOException;
import java.net.SocketAddress;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.streams.AbstractStreamWriter;
import org.glassfish.grizzly.streams.BufferedOutput;

/**
 *
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public final class DefaultStreamWriter extends AbstractStreamWriter {
    public DefaultStreamWriter(Connection connection) {
        super(connection, new Output(connection));
    }

    @Override
    public GrizzlyFuture<Integer> flush(
            final CompletionHandler<Integer> completionHandler)
            throws IOException {
        return super.flush(new ResetCounterCompletionHandler(
                (Output) output, completionHandler));
    }

    public final static class Output extends BufferedOutput {
        private final Connection connection;
        private int sentBytesCounter;

        public Output(Connection connection) {
            super(connection.getWriteBufferSize());
            this.connection = connection;
        }


        @Override
        protected GrizzlyFuture<Integer> flush0(Buffer buffer,
                final CompletionHandler<Integer> completionHandler)
                throws IOException {
            
            final FutureImpl<Integer> future = SafeFutureImpl.create();
            
            if (buffer == null) {
                buffer = Buffers.EMPTY_BUFFER;
            }

            connection.write(buffer,
                    new CompletionHandlerAdapter(this, future, completionHandler));
            return future;
        }

        @Override
        protected Buffer newBuffer(int size) {
            return connection.getMemoryManager().allocate(size);
        }

        @Override
        protected Buffer reallocateBuffer(Buffer oldBuffer, int size) {
            return connection.getMemoryManager().reallocate(oldBuffer, size);
        }

        @Override
        protected void onClosed() throws IOException {
            connection.closeSilently();
        }
    }

    private final static class CompletionHandlerAdapter
            implements CompletionHandler<WriteResult<Buffer, SocketAddress>> {

        private final Output output;
        private final FutureImpl<Integer> future;
        private final CompletionHandler<Integer> completionHandler;

        public CompletionHandlerAdapter(Output output,
                FutureImpl<Integer> future,
                CompletionHandler<Integer> completionHandler) {
            this.output = output;
            this.future = future;
            this.completionHandler = completionHandler;
        }

        @Override
        public void cancelled() {
            if (completionHandler != null) {
                completionHandler.cancelled();
            }

            if (future != null) {
                future.cancel(false);
            }
        }

        @Override
        public void failed(Throwable throwable) {
            if (completionHandler != null) {
                completionHandler.failed(throwable);
            }

            if (future != null) {
                future.failure(throwable);
            }
        }

        @Override
        public void completed(WriteResult result) {
            output.sentBytesCounter += result.getWrittenSize();
            int totalSentBytes = output.sentBytesCounter;

            if (completionHandler != null) {
                completionHandler.completed(totalSentBytes);
            }

            if (future != null) {
                future.result(totalSentBytes);
            }
        }

        @Override
        public void updated(WriteResult result) {
            if (completionHandler != null) {
                completionHandler.updated(output.sentBytesCounter
                        + (int) result.getWrittenSize());
            }
        }
    }

    private final static class ResetCounterCompletionHandler
            implements CompletionHandler<Integer> {

        private final Output output;
        private final CompletionHandler<Integer> parentCompletionHandler;

        public ResetCounterCompletionHandler(Output output,
                CompletionHandler<Integer> parentCompletionHandler) {
            this.output = output;
            this.parentCompletionHandler = parentCompletionHandler;
        }

        @Override
        public void cancelled() {
            if (parentCompletionHandler != null) {
                parentCompletionHandler.cancelled();
            }
        }

        @Override
        public void failed(Throwable throwable) {
            if (parentCompletionHandler != null) {
                parentCompletionHandler.failed(throwable);
            }
        }

        @Override
        public void completed(Integer result) {
            output.sentBytesCounter = 0;
            if (parentCompletionHandler != null) {
                parentCompletionHandler.completed(result);
            }
        }

        @Override
        public void updated(Integer result) {
            if (parentCompletionHandler != null) {
                parentCompletionHandler.updated(result);
            }
        }
    }
}
