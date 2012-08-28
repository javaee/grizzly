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

package org.glassfish.grizzly.websockets;

/**
 * Interface to allow notification of events occurring on specific
 * {@link WebSocket} instances.
 */
public interface WebSocketListener {

    /**
     * <p>
     * Invoked when {@link WebSocket#onClose(DataFrame)} has been called on a
     * particular {@link WebSocket} instance.
     * <p>
     *     
     * @param socket the {@link WebSocket} being closed.
     * @param frame the closing {@link DataFrame} sent by the remote end-point. 
     */
    void onClose(WebSocket socket, DataFrame frame);

    /**
     * <p>
     * Invoked when the opening handshake has been completed for a specific
     * {@link WebSocket} instance.
     * </p>
     * 
     * @param socket the newly connected {@link WebSocket}
     */
    void onConnect(WebSocket socket);

    /**
     * <p>
     * Invoked when {@link WebSocket#onMessage(String)} has been called on a 
     * particular {@link WebSocket} instance.
     * </p>
     * 
     * @param socket the {@link WebSocket} that received a message.
     * @param text the message received.
     */
    void onMessage(WebSocket socket, String text);

    /**
     * <p>
     * Invoked when {@link WebSocket#onMessage(String)} has been called on a
     * particular {@link WebSocket} instance.
     * </p>
     *
     * @param socket the {@link WebSocket} that received a message.
     * @param bytes the message received.
     */
    void onMessage(WebSocket socket, byte[] bytes);

    /**
     * <p>
     * Invoked when {@link WebSocket#onPing(DataFrame)} has been called on a 
     * particular {@link WebSocket} instance.
     * </p>
     * 
     * @param socket the {@link WebSocket} that received the ping.
     * @param bytes the payload of the ping frame, if any.
     */
    void onPing(WebSocket socket, byte[] bytes);

    /**
     * <p>
     * Invoked when {@link WebSocket#onPong(DataFrame)} has been called on a
     * particular {@link WebSocket} instance.
     * </p>
     *
     * @param socket the {@link WebSocket} that received the pong.
     * @param bytes  the payload of the pong frame, if any.
     */
    void onPong(WebSocket socket, byte[] bytes);

    /**
     * <p>
     * Invoked when {@link WebSocket#onFragment(boolean, String)} has been called
     * on a particular {@link WebSocket} instance.
     * </p>
     * 
     * @param socket the {@link WebSocket} received the message fragment.
     * @param fragment the message fragment.
     * @param last flag indicating if this was the last fragment.
     */
    void onFragment(WebSocket socket, String fragment, boolean last);

    /**
     * <p>
     * Invoked when {@link WebSocket#onFragment(boolean, byte[])} has been called
     * on a particular {@link WebSocket} instance.
     * </p>
     *
     * @param socket   the {@link WebSocket} received the message fragment.
     * @param fragment the message fragment.
     * @param last     flag indicating if this was the last fragment.
     */
    void onFragment(WebSocket socket, byte[] fragment, boolean last);

}