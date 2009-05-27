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

import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.Reader;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorReader;
import org.glassfish.grizzly.streams.AbstractStreamReader;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.util.conditions.Condition;


/**
 *
 * @author oleksiys
 */
public class TCPNIOStreamReader extends AbstractStreamReader {
    public TCPNIOStreamReader(TCPNIOConnection connection) {
        super(connection);
    }

    public Future<Integer> notifyCondition(Condition<StreamReader> condition,
            CompletionHandler<Integer> completionHandler) {

        if (notifyObject != null) {
            throw new IllegalStateException("Only one available listener allowed!");
        }

        if (isClosed()) {
            EOFException exception = new EOFException();
            if (completionHandler != null) {
                completionHandler.failed(getConnection(), exception);
            }

            return new ReadyFutureImpl<Integer>(exception);
        }

        final int availableDataSize = availableDataSize();
        if (condition.check(this)) {
            if (completionHandler != null) {
                completionHandler.completed(getConnection(), availableDataSize);
            }

            return new ReadyFutureImpl<Integer>(availableDataSize);
        } else {
            if (isBlocking()) {
                return notifyConditionBlocking(condition, completionHandler);
            } else {
                return notifyConditionNonBlocking(condition, completionHandler);
            }
        }
    }
    

    private Future<Integer> notifyConditionNonBlocking(
            final Condition<StreamReader> condition,
            CompletionHandler<Integer> completionHandler) {

        final FutureImpl<Integer> future = new FutureImpl<Integer>(sync);
        synchronized (sync) {
            try {
                notifyObject = new NotifyObject(future, completionHandler, condition);

                Connection connection = getConnection();
                TCPNIOTransport transport = (TCPNIOTransport) connection.getTransport();
                transport.getAsyncQueueIO().getReader().read(connection, null, null,
                        new Interceptor() {

                            public int intercept(int event, Object context, Object result) {
                                if (event == Reader.READ_EVENT) {
                                    final ReadResult readResult = (ReadResult) result;
                                    final Buffer buffer = (Buffer) readResult.getMessage();
                                    readResult.setMessage(null);

                                    if (buffer == null) {
                                        return Interceptor.INCOMPLETED;
                                    }

                                    buffer.flip();
                                    append(buffer);

                                    if (future.isDone()) {
                                        return Interceptor.COMPLETED;
                                    }

                                    return Interceptor.INCOMPLETED |
                                            Interceptor.RESET;
                                }

                                return Interceptor.DEFAULT;
                            }
                        });
            } catch (IOException e) {
                future.failure(e);
            }
        }
        
        return future;
    }

    private Future<Integer> notifyConditionBlocking(
            Condition<StreamReader> condition,
            CompletionHandler<Integer> completionHandler) {

        FutureImpl<Integer> future = new FutureImpl<Integer>();
        notifyObject = new NotifyObject(future, completionHandler, condition);

        try {
            while (!future.isDone()) {
                final Buffer buffer = read0();
                append(buffer);
            }
        } catch (Exception e) {
            future.failure(e);
        }

        return future;
    }
    
    @Override
    protected Buffer read0() throws IOException {
        final Connection connection = getConnection();
        
        if (isBlocking()) {
            TCPNIOTransport transport = (TCPNIOTransport) connection.getTransport();
            Buffer buffer = newBuffer(bufferSize);

            try {
                TemporarySelectorReader reader =
                        (TemporarySelectorReader)
                        transport.getTemporarySelectorIO().getReader();
                Future future = reader.read(connection, buffer, null, null,
                        timeoutMillis, TimeUnit.MILLISECONDS);
                future.get();
                buffer.trim();
            } catch (Exception e) {
                buffer.dispose();
                throw new EOFException();
            }

            return buffer;
            
        } else {
            TCPNIOTransport transport = (TCPNIOTransport) connection.getTransport();
            Buffer buffer = newBuffer(bufferSize);

            try {
                int readBytes = transport.read(connection, buffer);
                if (readBytes <= 0) {
                    if (readBytes == -1) {
                        throw new EOFException();
                    }

                    buffer.dispose();
                    buffer = null;
                } else {
                    buffer.trim();
                }
            } catch (IOException e) {
                buffer.dispose();
                buffer = null;
                throw e;
            }

            return buffer;
        }
    }

    @Override
    protected final Object wrap(Buffer buffer) {
        return buffer;
    }

    @Override
    protected final Buffer unwrap(Object data) {
        return (Buffer) data;
    }
}
