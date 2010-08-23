/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.websockets;

import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Connection;
import com.sun.grizzly.GrizzlyFuture;
import com.sun.grizzly.websockets.frame.Frame;
import java.io.IOException;
import java.net.URI;

/**
 * General WebSocket unit interface.
 *
 * @see WebSocketBase
 * 
 * @author Alexey Stashok
 */
public interface WebSocket {

    /**
     * Send a message, represented as WebSocket {@link Frame}.
     * @param frame {@link Frame}.
     * @return {@link GrizzlyFuture}, which could be used to control the sending completion state.
     * 
     * @throws IOException
     */
    public GrizzlyFuture<Frame> send(Frame frame) throws IOException;

    /**
     * Send a message, represented as WebSocket {@link Frame}.
     * @param frame {@link Frame}.
     * @param completionHandler {@link CompletionHandler}, which could be used
     *        to control the message sending state.
     * @return {@link GrizzlyFuture}, which could be used to control the sending
     *        completion state.
     *
     * @throws IOException
     */
    public GrizzlyFuture<Frame> send(Frame frame,
            CompletionHandler<Frame> completionHandler) throws IOException;

    /**
     * Close the <tt>WebSocket</tt>.
     * The close operation will do the following steps:
     * 1) try to send a <tt>close frame</tt>
     * 2) call {@link WebSocketHandler#onClose(com.sun.grizzly.websockets.WebSocket)} method
     * 3) close the underlying {@link Connection}
     *
     * @throws IOException
     */
    public void close() throws IOException;

    /**
     * Returns <tt>true</tt> if the <tt>WebSocket</tt> is connected and ready to
     * operate, or <tt>false</tt> otherwise.
     *
     * @return <tt>true</tt> if the <tt>WebSocket</tt> is connected and ready to
     * operate, or <tt>false</tt> otherwise.
     */
    public boolean isConnected();

    /**
     * Gets the <tt>WebSocket</tt> URI.
     *
     * @return the <tt>WebSocket</tt> URI.
     */
    public URI getURI();

    /**
     * Gets the <tt>WebSocket</tt>'s underlying {@link Connection}.
     *
     * @return the <tt>WebSocket</tt>'s underlying {@link Connection}.
     */
    public Connection getConnection();

    /**
     * Returns the <tt>WebSocket</tt>'s meta data.
     *
     * @return {@link WebSocketMeta>.
     */
    public WebSocketMeta getMeta();

    /**
     * Returns the <tt>WebSocket</tt>'s events handler.
     * 
     * @return {@link WebSocketHandler}.
     */
    public WebSocketHandler getHandler();
}
