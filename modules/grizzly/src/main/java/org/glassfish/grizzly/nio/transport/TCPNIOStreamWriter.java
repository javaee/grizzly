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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.nio.tmpselectors.AbstractTemporarySelectorWriter;
import org.glassfish.grizzly.streams.AbstractStreamWriter;

/**
 *
 * @author Alexey Stashok
 */
public class TCPNIOStreamWriter extends AbstractStreamWriter {
    public TCPNIOStreamWriter(TCPNIOConnection connection) {
        super(connection);
    }
    
    protected Future flush0(Buffer current,
            CompletionHandler completionHandler) throws IOException {
        current.flip();
        if (current.hasRemaining()) {
            TCPNIOTransport transport = (TCPNIOTransport) connection.getTransport();
            if (mode == Mode.BLOCKING) {
                AbstractTemporarySelectorWriter writer =
                        (AbstractTemporarySelectorWriter)
                        transport.getTemporarySelectorIO().getWriter();
                return writer.write(connection, null, current,
                        completionHandler, null,
                        getTimeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
            } else {
                return transport.getAsyncQueueIO().getWriter().write(
                        connection, current, completionHandler);
            }
        } else {
            if (completionHandler != null) {
                completionHandler.completed(null, TCPNIOStreamWriter.this);
            }

            return new ReadyFutureImpl(TCPNIOStreamWriter.this);
        }
    }

    protected Future close0(final CompletionHandler completionHandler)
            throws IOException {
        if (buffer != null && buffer.position() > 0) {
            final FutureImpl<TCPNIOStreamWriter> future =
                    new FutureImpl<TCPNIOStreamWriter>();

            try {
                overflow(buffer, new CompletionHandler() {

                    public void cancelled(Connection connection) {
                        close();
                    }

                    public void failed(Connection connection, Throwable throwable) {
                        close();
                    }

                    public void completed(Connection connection, Object result) {
                        close();
                    }

                    public void updated(Connection connection, Object result) {
                    }

                    public void close() {
                        try {
                            connection.close();
                        } catch (IOException e) {
                        } finally {
                            completionHandler.completed(null,
                                    TCPNIOStreamWriter.this);
                            future.setResult(TCPNIOStreamWriter.this);
                        }
                    }
                });
            } catch (IOException e) {
            }

            return future;
        } else {
            if (completionHandler != null) {
                completionHandler.completed(null, TCPNIOStreamWriter.this);
            }

            return new ReadyFutureImpl(TCPNIOStreamWriter.this);
        }
    }
}
