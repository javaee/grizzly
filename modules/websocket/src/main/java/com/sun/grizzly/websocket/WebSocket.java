/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2009 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.websocket;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import javax.net.ssl.SSLContext;

/**
 * API is minimalistic on purpose for now,
 * Its easy to add but not to remove from a public API.<br>
 * Please provide feedback.<br>
 * TODO: flexible(per client) and easy to use SSL config.
 *
 * Implementation is based on :<br>
 * <a href="http://tools.ietf.org/html/draft-hixie-thewebsocketprotocol"
 * >http://tools.ietf.org/html/draft-hixie-thewebsocketprotocol</a><br>
 *
 * @author gustav trede
 * @since 2009
 */
public abstract class WebSocket {

    /**
     * {@link WebSocketListener} onOpen method will be called if connection is
     * successfull.<br>
     * {@link URI} is not supported as parameter due to its currently not
     * compliant with
     * <a href="http://tools.ietf.org/html/rfc3490">RFC 3490 (IDNA)</a<br>
     * @param uri
     * @param eventlistener {@link WebSocketListener}
     * @return
     * @throws IOException
     */
    public static WebSocket open(String uri,WebSocketListener eventlistener)
           throws IOException{
       return WebSocketImpl.openClient(uri,eventlistener,null,null,null,null);
    }

    /**
     * {@link WebSocketListener} onOpen method will be called if connection is
     * successfull.<br>
     * {@link URI} is not supported as parameter due to its currently not
     * compliant with
     * <a href="http://tools.ietf.org/html/rfc3490">RFC 3490 (IDNA)</a<br>
     * @param uri
     * @param eventlistener {@link WebSocketListener}
     * @param origin {@link WebsocketHandshake#origin} optional
     * @param protocol {@link  WebSocketContext#protocol} optional
     * @param sslctx {@link SSLContext} if wss scheme is used
     * @return
     * @throws IOException
     */
    public static WebSocket open(String uri,WebSocketListener eventlistener,
           String origin, String protocol, SSLContext sslctx)throws IOException{
       return WebSocketImpl.
               openClient(uri,eventlistener,origin,protocol,sslctx,null);
    }

    /**
     * Sends a {@link DataFrame}.
     * When the method call has returned its safe to send the dataframe again.
     * 
     * @param dataframe {@link DataFrame}
     * @return false if failed.
     */
    public abstract boolean send(DataFrame dataframe);

    /**
     * TODO: add more jdoc.<br>
     * Sends a {@link String}.
     * @param textUTF8  {@link String}
     * @return false if failed.
     */
    public abstract boolean send(String textUTF8);

    /**
     * The bytes are assumed to represent valid dataframes(s) according to the
     * websocket protocol.
     *When the method call has returned its safe to send the rawdataframe again.
     *
     * @param rawdataframe  {@link ByteBuffer}
     * @return false if failed.
     */
    public abstract boolean send(ByteBuffer rawdataframe);


    /**
     * If readystate != closed it becomes closed and
     * eventlistener.onClosed is called and the connection is closed.
     * The only flusing of data is what channel.close() may perform.
     * {@link DataFrame}s that is aldready enqueued for
     * {@link WebSocketListener#onMessage} are not affected by closed state.
     */
    public abstract void close();

    /**
     * Returns the close cause or null if state is not closed.
     * @return
     */
    public abstract Throwable getClosedCause();

    /**
     * Returns the address of the endpoint this socket currently is
     * or has been connected to, else null is returned.
     * @return
     */
    public abstract SocketAddress getRemotAddress();

    /**
     * Returns the current estimated memory prevented from GC
     * in bytes by {@link DataFrame}s enqueued by the {@link #send} method that 
     * is not yet in the socket native send buffer.<br>
     * Very rougly its consuming 50 bytes per frame regardless
     * of its size and the concurrent number of times its enqueued
     *  due to the fact that data exists already in the original frame.<br>
     * Its however prevented from GC while enqueued so roughly its full size is
     * added to this buffered value anyhow, due to the complexity explained
     * above the datastructure overhead is not included( roughly 50 bytes)<br>
     * @return
     */
    public abstract int getBufferedSendBytes();

    /**
     * Returns the current estimated memory usage in bytes by {@link DataFrame}s
     * awaiting consumtion by {@link WebSocketListener#onMessage}.
     * @return
     */
    public abstract int getBufferedOnMessageBytes();

    /**
     *
     * @return
     */
    public abstract int getReadDataThrottledCounter();

    /**
     * Returns the {@link WebSocketContext}<br>
     * @return
     */
    public abstract WebSocketContext getWebSocketContext();

    /**
     * Returns true if this connecton is secure.
     * @return
     */
   // public abstract boolean isSecure();

    /**
     * Provides detailed status information.
     */
    @Override
    public abstract String toString();

}
