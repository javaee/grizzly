/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
package org.glassfish.grizzly.nio.transport;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorWriter;
import org.glassfish.grizzly.streams.AbstractStreamWriter;

/**
 *
 * @author Alexey Stashok
 */
public class UDPNIOStreamWriter extends AbstractStreamWriter {
    private int sentBytesCounter;

    public UDPNIOStreamWriter(UDPNIOConnection connection) {
        super(connection);
    }

    @Override
    public Future<Integer> flush(CompletionHandler<Integer> completionHandler)
            throws IOException {
        return super.flush(new ResetCounterCompletionHandler(completionHandler));
    }
    

    protected Future<Integer> flush0(Buffer current,
            CompletionHandler<Integer> completionHandler) throws IOException {
        current.flip();
        UDPNIOTransport transport = (UDPNIOTransport) connection.getTransport();
        if (isBlocking()) {
            TemporarySelectorWriter writer =
                    (TemporarySelectorWriter)
                    transport.getTemporarySelectorIO().getWriter();

            Future<WriteResult<Buffer, SocketAddress>> future =
                    writer.write(connection, null, current,
                    new CompletionHandlerAdapter(null, completionHandler),
                    null,
                    getTimeout(TimeUnit.MILLISECONDS),
                    TimeUnit.MILLISECONDS);

            try {
                return new ReadyFutureImpl<Integer>(future.get().getWrittenSize());
            } catch (Exception e) {
                throw new IOException(
                        "UDPNIOStreamWriter.flush0(): unexpected exception. " +
                        e.getMessage());
            }
        } else {
            FutureImpl<Integer> future = new FutureImpl<Integer>();

            transport.getAsyncQueueIO().getWriter().write(
                    connection, current,
                    new CompletionHandlerAdapter(future, completionHandler));

            return future;
        }
    }

    protected Future<Integer> close0(
            final CompletionHandler<Integer> completionHandler)
            throws IOException {
        
        if (buffer != null && buffer.position() > 0) {
            final FutureImpl<Integer> future =
                    new FutureImpl<Integer>();

            try {
                overflow(new CompletionHandler<Integer>() {

                    public void cancelled(Connection connection) {
                        close(ZERO);
                    }

                    public void failed(Connection connection, Throwable throwable) {
                        close(ZERO);
                    }

                    public void completed(Connection connection, Integer result) {
                        close(result);
                    }

                    public void updated(Connection connection, Integer result) {
                    }

                    public void close(Integer result) {
                        try {
                            connection.close();
                        } catch (IOException e) {
                        } finally {
                            if (completionHandler != null) {
                                completionHandler.completed(null, result);
                            }
                            
                            future.setResult(result);
                        }
                    }
                });
            } catch (IOException e) {
            }

            return future;
        } else {
            if (completionHandler != null) {
                completionHandler.completed(null, ZERO);
            }

            return new ReadyFutureImpl(ZERO);
        }
    }

    private final class CompletionHandlerAdapter
            implements CompletionHandler<WriteResult<Buffer, SocketAddress>> {

        private final FutureImpl<Integer> future;
        private final CompletionHandler<Integer> completionHandler;

        public CompletionHandlerAdapter(FutureImpl<Integer> future,
                CompletionHandler<Integer> completionHandler) {
            this.future = future;
            this.completionHandler = completionHandler;
        }

        public void cancelled(Connection connection) {
            if (completionHandler != null) {
                completionHandler.cancelled(connection);
            }

            if (future != null) {
                future.cancel(false);
            }
        }

        public void failed(Connection connection, Throwable throwable) {
            if (completionHandler != null) {
                completionHandler.failed(connection, throwable);
            }

            if (future != null) {
                future.failure(throwable);
            }
        }

        public void completed(Connection connection, WriteResult result) {
            sentBytesCounter += result.getWrittenSize();
            int totalSentBytes = sentBytesCounter;

            if (completionHandler != null) {
                completionHandler.completed(connection, totalSentBytes);
            }

            if (future != null) {
                future.setResult(totalSentBytes);
            }
        }

        public void updated(Connection connection, WriteResult result) {
            if (completionHandler != null) {
                completionHandler.updated(connection, sentBytesCounter +
                        result.getWrittenSize());
            }
        }
    }

    private final class ResetCounterCompletionHandler
            implements CompletionHandler<Integer> {

        private final CompletionHandler<Integer> parentCompletionHandler;

        public ResetCounterCompletionHandler(CompletionHandler<Integer> parentCompletionHandler) {
            this.parentCompletionHandler = parentCompletionHandler;
        }

        public void cancelled(Connection connection) {
            if (parentCompletionHandler != null) {
                parentCompletionHandler.cancelled(connection);
            }
        }

        public void failed(Connection connection, Throwable throwable) {
            if (parentCompletionHandler != null) {
                parentCompletionHandler.failed(connection, throwable);
            }
        }

        public void completed(Connection connection, Integer result) {
            sentBytesCounter = 0;
            if (parentCompletionHandler != null) {
                parentCompletionHandler.completed(connection, result);
            }
        }

        public void updated(Connection connection, Integer result) {
            if (parentCompletionHandler != null) {
                parentCompletionHandler.updated(connection, result);
            }
        }

    }
}
