/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

public interface WebSocket {

    /**
     * Indicates a normal closure, meaning whatever purpose the connection was established for has been fulfilled.
     */
    int NORMAL_CLOSURE = 1000;

    /**
     * Indicates that an endpoint is "going away", such as a server going down, or a browser having navigated away
     * from a page.
     */
    int END_POINT_GOING_DOWN = 1001;

    /**
     * Indicates that an endpoint is terminating the connection due to a protocol error.
     */
    int PROTOCOL_ERROR = 1002;

    /**
     * Indicates that an endpoint is terminating the connection because it has received a type of data it cannot accept
     * (e.g. an endpoint that understands only text data may send this if it receives a binary message.)
     */
    int INVALID_DATA = 1003;

    /**
     * indicates that an endpoint is terminating the connection because it has received a message that is too large.
     */
    int MESSAGE_TOO_LARGE = 1004;

    /**
     * a reserved value and MUST NOT be set as a status code in a Close control frame by an endpoint.  It is designated
     * for use in applications expecting a status code to indicate that no status code was actually present.
     */
    int NO_STATUS_CODE = 1005;

    /**
     * a reserved value and MUST NOT be set as a status code in a Close control frame by an endpoint.  It is designated
     * for use in applications expecting a status code to indicate that the connection was closed abnormally,
     * e.g. without sending or receiving a Close control frame.
     */
    int ABNORMAL_CLOSE = 1006;
    
    /**
     * Write the data to the socket.  This text will be converted to a UTF-8 encoded byte[] prior to sending.
     *
     * @param data
     */
    void send(String data);

    void send(byte[] data);

    /**
     * Sends a <code>ping</code> frame with the specified payload (if any).
     *
     * @param data optional payload.  Note that payload length is restricted
     *             to 125 bytes or less.
     *
     * @since 1.9.46
     */
    void sendPing(byte[] data);

    /**
     * <p>
     * Sends a <code>ping</code> frame with the specified payload (if any).
     * </p>
     * <p/>
     * <p>It may seem odd to send a pong frame, however, RFC-6455 states:</p>
     * <p/>
     * <p>
     * "A Pong frame MAY be sent unsolicited.  This serves as a
     * unidirectional heartbeat.  A response to an unsolicited Pong frame is
     * not expected."
     * </p>
     *
     * @param data optional payload.  Note that payload length is restricted
     *             to 125 bytes or less.
     *
     * @since 1.9.46
     */
    void sendPong(byte[] data);

    void stream(boolean last, String fragment);

    void stream(boolean last, byte[] fragment, int off, int len);

    void close();

    void close(int code);

    void close(int code, String reason);

    boolean isConnected();

    void setClosed();

    void onConnect();

    void onMessage(String text);

    void onMessage(byte[] data);

    void onFragment(boolean last, String payload);

    void onFragment(boolean last, byte[] payload);

    void onClose(DataFrame frame);

    void onPing(DataFrame frame);

    void onPong(DataFrame frame);

    boolean add(WebSocketListener listener);

    boolean remove(WebSocketListener listener);
}
