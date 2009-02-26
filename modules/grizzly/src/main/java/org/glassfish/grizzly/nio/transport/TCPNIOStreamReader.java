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
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.Reader;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.streams.AbstractStreamReader;


/**
 *
 * @author oleksiys
 */
public class TCPNIOStreamReader extends AbstractStreamReader {

    public enum Mode {
        NON_BLOCKING, BLOCKING, FEEDER;
    }

    private TCPNIOConnection connection;
    private Mode mode;

    public TCPNIOStreamReader(TCPNIOConnection connection) {
        this.connection = connection;
//        mode = Mode.FEEDER;
        mode = connection.isBlocking() ? Mode.BLOCKING : Mode.NON_BLOCKING;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    @Override
    public synchronized boolean receiveData(final Buffer buffer) {
        return receiveDataAsync(buffer);
    }

    public boolean receiveDataAsync(final Buffer buffer) {
        return super.receiveData(buffer);
    }

    @Override
    public synchronized Future notifyAvailable(int size,
            CompletionHandler completionHandler) {

        if (notifyObject != null) {
            throw new IllegalStateException("Only one available listener allowed!");
        }

        if (isClosed()) {
            EOFException exception = new EOFException();
            if (completionHandler != null) {
                completionHandler.failed(null, exception);
            }

            return new ReadyFutureImpl(exception);
        }

        int availableDataSize = availableDataSize();
        if (availableDataSize >= size) {
            if (completionHandler != null) {
                completionHandler.completed(null, availableDataSize);
            }

            return new ReadyFutureImpl(availableDataSize);
        } else {

            switch (mode) {
                case NON_BLOCKING:
                    return notifyAvailableNonBlocking(size, completionHandler);
                case BLOCKING:
                    return notifyAvailableBlocking(size, completionHandler);
                case FEEDER:
                    return notifyAvailableFeeder(size, completionHandler);
            }
            return null;
        }

    }
    
    private Future notifyAvailableNonBlocking(final int size,
            CompletionHandler completionHandler) {

        final FutureImpl future = new FutureImpl();
        notifyObject = new NotifyObject(future, completionHandler, size);

        TCPNIOTransport transport = (TCPNIOTransport) connection.getTransport();
        try {
            transport.getAsyncQueueIO().getReader().read(connection, null, null,
                    new Interceptor() {
                        public int intercept(int event, Object context, Object result) {
                            if (event == Reader.AFTER_READ_EVENT) {
                                ReadResult readResult = (ReadResult) result;
                                Buffer buffer = (Buffer) readResult.getMessage();
                                readResult.setMessage(null);

                                if (buffer == null) {
                                    return Interceptor.INCOMPLETED;
                                }

                                buffer.flip();
                                receiveData(buffer);

                                if (future.isDone()) {
                                    return Interceptor.COMPLETED;
                                }

                                return Interceptor.INCOMPLETED;
                            }

                            return Interceptor.DEFAULT;
                        }
                    });
        } catch (IOException e) {
            future.failure(e);
        }

        return future;
    }

    private Future notifyAvailableBlocking(int size,
            CompletionHandler completionHandler) {

        FutureImpl future = new FutureImpl();
        notifyObject = new NotifyObject(future, completionHandler, size);

        try {
            while (!future.isDone()) {
                Buffer buffer = read0();
                receiveData(buffer);
            }
        } catch (Exception e) {
            future.failure(e);
        }

        return future;
    }

    private Future notifyAvailableFeeder(int size,
            CompletionHandler completionHandler) {
        FutureImpl future = new FutureImpl();
        notifyObject = new NotifyObject(future, completionHandler, size);
        return future;
    }
    
    @Override
    protected Buffer read0() throws IOException {
        switch(mode) {
            case NON_BLOCKING:
            {
                TCPNIOTransport transport = (TCPNIOTransport) connection.getTransport();
                Buffer buffer = transport.getMemoryManager().allocate(
                        connection.getReadBufferSize());

                try {
                    int readBytes = transport.read(connection, buffer);
                    if (readBytes <= 0) {
                        buffer.dispose();
                        if (readBytes == -1) {
                            throw new EOFException();
                        }
                        buffer = null;
                    } else {
                        buffer.trim();
                    }
                } catch (IOException e) {
                    buffer.dispose();
                    buffer = null;
                }

                return buffer;
            }

            case BLOCKING:
            {
                TCPNIOTransport transport = (TCPNIOTransport) connection.getTransport();
                Buffer buffer = transport.getMemoryManager().allocate(
                        connection.getReadBufferSize());

                try {
                    Future future =
                            transport.getTemporarySelectorIO().getReader().read(
                            connection, buffer);
                    future.get();
                    buffer.trim();
                } catch (Exception e) {
                    buffer.dispose();
                    throw new EOFException();
                }

                return buffer;
            }

            default:
                return null;
        }
    }
}
