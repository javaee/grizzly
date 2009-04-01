/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
 */

package org.glassfish.grizzly.streams;

import java.io.EOFException;
import java.util.concurrent.Future;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.util.conditions.Condition;

/**
 *
 * @author oleksiys
 */
public abstract class StreamReaderDecorator extends AbstractStreamReader {
    protected StreamReader underlyingReader;

    public StreamReaderDecorator(StreamReader underlyingReader) {
        setUnderlyingReader(underlyingReader);
    }

    public StreamReader getUnderlyingReader() {
        return underlyingReader;
    }

    public void setUnderlyingReader(StreamReader underlyingReader) {
        this.underlyingReader = underlyingReader;
    }

    @Override
    public boolean isBlocking() {
        return underlyingReader.isBlocking();
    }

    @Override
    public void setBlocking(boolean isBlocking) {
        underlyingReader.setBlocking(isBlocking);
    }

    @Override
    public Connection getConnection() {
        if (underlyingReader != null) {
            return underlyingReader.getConnection();
        }

        return null;
    }


    public Future notifyCondition(Condition<StreamReader> condition,
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

    /**
     * Pulls data out from underlying {@link StreamReader} chain into this
     * {@link StreamReader}
     */
    public void pull() {
        if (underlyingReader == null) return;
        if (underlyingReader instanceof StreamReaderDecorator) {
            ((StreamReaderDecorator) underlyingReader).pull();
        }

        Buffer buffer;
        while((buffer = underlyingReader.getBuffer()) != null) {
            boolean wasAdded = appendBuffer(buffer);
            if (wasAdded) {
                underlyingReader.finishBuffer();
            } else {
                return;
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
            Buffer buffer = underlyingReader.getBuffer();
            if (appendBuffer(buffer)) {
                underlyingReader.finishBuffer();
            }

            if (!future.isDone()) {
                underlyingReader.notifyAvailable(1, this);
            }
        }

        public void updated(Connection connection, Object result) {
        }

    }
}
