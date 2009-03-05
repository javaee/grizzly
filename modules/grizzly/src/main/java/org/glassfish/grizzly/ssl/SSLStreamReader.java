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

package org.glassfish.grizzly.ssl;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.streams.AbstractStreamReader;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamReader.Mode;
import org.glassfish.grizzly.util.conditions.Condition;

/**
 *
 * @author oleksiys
 */
public class SSLStreamReader extends AbstractStreamReader {
    protected StreamReader underlyingReader;

    public SSLStreamReader() {
    }

    public SSLStreamReader(StreamReader underlyingReader) {
        setUnderlyingReader(underlyingReader);
    }

    public StreamReader getUnderlyingReader() {
        return underlyingReader;
    }

    public void setUnderlyingReader(StreamReader underlyingReader) {
        this.underlyingReader = underlyingReader;
        if (underlyingReader != null) {
            try {
                checkBuffers();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public Mode getMode() {
        return underlyingReader.getMode();
    }

    @Override
    public void setMode(Mode mode) {
        underlyingReader.setMode(mode);
    }

    @Override
    public Connection getConnection() {
        return underlyingReader.getConnection();
    }

    @Override
    public synchronized Future notifyCondition(Condition<StreamReader> condition,
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
        if (condition.check(this)) {
            if (completionHandler != null) {
                completionHandler.completed(null, availableDataSize);
            }

            return new ReadyFutureImpl(availableDataSize);
        } else {
            FutureImpl future = new FutureImpl();
            notifyObject = new NotifyObject(future, completionHandler, condition);
            underlyingReader.notifyAvailable(1,
                    new FeederCompletionHandler(future, completionHandler));
            return future;
        }
    }

    @Override
    public synchronized boolean receiveData(Buffer buffer) {
        if (buffer == null) return false;
        
        boolean wasAdded = true;
        SSLEngine sslEngine = getSSLEngine();
        while(wasAdded && buffer.hasRemaining()) {
            ByteBuffer underlyingByteBuffer = (ByteBuffer) buffer.underlying();

            Buffer newBuffer = newBuffer(bufferSize);
            ByteBuffer appByteBuffer = (ByteBuffer) newBuffer.underlying();
            SSLEngineResult result;
            try {
                result = sslEngine.unwrap(underlyingByteBuffer, appByteBuffer);
            } catch (SSLException e) {
                newBuffer.dispose();
                throw new IllegalStateException(e);
            }

            if (result.getStatus() == Status.OK ||
                    result.getStatus() == Status.CLOSED) {
                if (result.bytesProduced() > 0 || result.bytesConsumed() > 0) {
                    newBuffer.flip();
                    wasAdded = super.receiveData(newBuffer);
                } else {
                    wasAdded = false;
                    newBuffer.dispose();
                }
            } else {
                newBuffer.dispose();
                wasAdded = false;
            }
        }

        if (wasAdded) {
            buffer.dispose();
        }

        return wasAdded;
    }


    public SSLEngine getSSLEngine() {
        SSLResourcesAccessor resourceAccessor =
                SSLResourcesAccessor.getInstance();
        return resourceAccessor.getSSLEngine(getConnection());
    }

    protected void unwrapAll() throws IOException {
        Buffer underlyingBuffer;
        while((underlyingBuffer = underlyingReader.getBuffer()) != null) {
            receiveData(underlyingBuffer);
            underlyingReader.finishBuffer();
        }
    }

    Future handshakeUnwrap(CompletionHandler completionHandler) throws IOException {
        return notifyCondition(new Condition<StreamReader>() {

            public boolean check(StreamReader state) {
                return getSSLEngine().getHandshakeStatus() !=
                        SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
            }
        }, completionHandler);
    }

    @Override
    protected Buffer read0() throws IOException {
        return underlyingReader.readBuffer();
    }

    private void checkBuffers() throws IOException {
        SSLEngine sslEngine = getSSLEngine();

        if (sslEngine != null) {
            int underlyingBufferSize = sslEngine.getSession().getPacketBufferSize();
            if (underlyingReader.getBufferSize() < underlyingBufferSize) {
                underlyingReader.setBufferSize(underlyingBufferSize);
            }

            int appBufferSize = sslEngine.getSession().getApplicationBufferSize();
            if (bufferSize < appBufferSize) {
                bufferSize = appBufferSize;
            }
        }
    }

    protected class FeederCompletionHandler implements CompletionHandler {
        private FutureImpl future;
        private CompletionHandler completionHandler;

        public FeederCompletionHandler(FutureImpl future, CompletionHandler completionHandler) {
            this.future = future;
            this.completionHandler = completionHandler;
        }

        
        public void cancelled(Connection connection) {
            if (completionHandler != null) {
                completionHandler.cancelled(connection);
            }
            future.cancel(true);
        }

        public void failed(Connection connection, Throwable throwable) {
            if (completionHandler != null) {
                completionHandler.failed(connection, throwable);
            }
            future.failure(throwable);
        }

        public void completed(Connection connection, Object result) {
            try {
                Buffer buffer = underlyingReader.getBuffer();
                if (receiveData(buffer)) {
                    underlyingReader.finishBuffer();
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }

            if (!future.isDone()) {
                underlyingReader.notifyAvailable(1, this);
            }
        }

        public void updated(Connection connection, Object result) {
        }

    }
}
