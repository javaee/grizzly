/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.websockets;

import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Connection;
import com.sun.grizzly.GrizzlyFuture;
import com.sun.grizzly.websockets.frame.Frame;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link WebSocket} base implementation, which partially implements
 * {@link WebSocket} interface.
 *
 * @see WebSocket
 * @see ClientWebSocket
 * @see ServerWebSocket
 * 
 * @author Alexey Stashok
 */
public abstract class WebSocketBase implements WebSocket {
    protected final WebSocketMeta meta;
    protected final Connection connection;

    /**
     * The {@link Frame}, which is being currently parsed.
     */
    private Frame parsingFrame;

    private final AtomicBoolean isClosed = new AtomicBoolean();

    /**
     * Construct a <tt>WebSocketBase</tt>.
     *
     * @param connection underlying Grizzly {@link Connection}.
     * @param meta {@link WebSocketMeta} info
     */
    public WebSocketBase(Connection connection, WebSocketMeta meta) {
        this.connection = connection;
        this.meta = meta;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URI getURI() {
        return meta.getURI();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WebSocketMeta getMeta() {
        return meta;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getConnection() {
        return connection;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final GrizzlyFuture<Frame> send(Frame frame) throws IOException {
        return send(frame, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GrizzlyFuture<Frame> send(Frame frame,
            CompletionHandler<Frame> completionHandler) throws IOException {
        return connection.write(frame, completionHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        if (!isClosed.getAndSet(true)) {
            getHandler().onClose(this);
            
            final GrizzlyFuture future = send(Frame.createCloseFrame());
            try {
                future.get(200, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
            }
            
            connection.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnected() {
        return !isClosed.get();
    }

    /**
     * Get the currently parsing {@link Frame}.
     *
     * @return the currently parsing {@link Frame}.
     */
    Frame getParsingFrame() {
        return parsingFrame;
    }

    /**
     * Set the currently parsing {@link Frame}.
     *
     * @param parsingFrame the currently parsing {@link Frame}.
     */
    void setParsingFrame(Frame parsingFrame) {
        this.parsingFrame = parsingFrame;
    }
}
