/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.aio;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import org.glassfish.grizzly.AbstractReader;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.Reader;
import org.glassfish.grizzly.impl.ReadyFutureImpl;

/**
 *
 * @author oleksiys
 */
public abstract class AIOBlockingReader extends AbstractReader<SocketAddress> {

    private static final Logger LOGGER = Grizzly.logger(AIOBlockingReader.class);
    
    public static final int DEFAULT_BUFFER_SIZE = 8192;
    protected int defaultBufferSize = DEFAULT_BUFFER_SIZE;
    protected final AIOTransport transport;

    public AIOBlockingReader(final AIOTransport transport) {
        this.transport = transport;
    }

    @Override
    public GrizzlyFuture<ReadResult<Buffer, SocketAddress>> read(
            final Connection connection, final Buffer message,
            final CompletionHandler<ReadResult<Buffer, SocketAddress>> completionHandler,
            final Interceptor<ReadResult> interceptor) throws IOException {

        if (connection == null || !(connection instanceof AIOConnection)) {
            throw new IllegalStateException(
                    "Connection should be AIOConnection and cannot be null.");
        }

        final ReadResult<Buffer, SocketAddress> currentResult =
                ReadResult.<Buffer, SocketAddress>create(connection, message, null, 0);

        final int readBytes = read0(connection, interceptor,
                currentResult, message);

        if (readBytes > 0) {

            if (completionHandler != null) {
                completionHandler.completed(currentResult);
            }

            if (interceptor != null) {
                interceptor.intercept(COMPLETE_EVENT, connection, currentResult);
            }

            return ReadyFutureImpl.create(currentResult);
        } else {
            return ReadyFutureImpl.create(new EOFException());
        }
    }

    private int read0(final Connection connection,
            final Interceptor<ReadResult> interceptor,
            final ReadResult currentResult, final Buffer buffer)
            throws IOException {

        boolean isCompleted = false;
        while (!isCompleted) {
            isCompleted = true;
            final int readBytes = read0(connection, currentResult, buffer);

            if (readBytes <= 0) {
                return -1;
            } else {
                if (interceptor != null) {
                    isCompleted = (interceptor.intercept(Reader.READ_EVENT,
                            null, currentResult) & Interceptor.COMPLETED) != 0;
                }
            }
        }

        return currentResult.getReadSize();
    }

    public AIOTransport getTransport() {
        return transport;
    }
    

    protected abstract int read0(final Connection connection,
            final ReadResult currentResult, final Buffer buffer)
            throws IOException;    
}
