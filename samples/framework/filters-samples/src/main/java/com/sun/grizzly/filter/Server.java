/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.filter;

import com.sun.grizzly.Context;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SelectionKey;

/**
 * Server API for communicating  with {@link Client}.
 * @author John Vieten 16.09.2008
 * @version 1.0
 */
public interface Server {
    /**
     * If a server wants to write to a specific connection.
     * @param connection in the momment should be a {@link Context}
     * @return OutputStream for writing to client
     */
    OutputStream getOutputStream(Context connection);

      /**
     * If a server wants to write to a specific connection.
     * @param connection in the momment should be a {@link SelectionKey}
     * @return OutputStream for writing to client
     */
    OutputStream getOutputStream(SelectionKey connection);

    /**
     * Gets called when {@link Client} calls  this server.
     * This may be done by {@link Client#callRemote} or
     * {@link Client#getOutputStream} 
     * 
     * @param inputStream bytes received from client
     * @param outputStream bytes that server wants to reply
     * @param SessionId (can be null) if client has added an session id
     * @param serverContext Probably {@link Context} gives Server access to Transport
     * framework
     */
    void service(InputStream inputStream, 
            ProtocolOutputStream outputStream,
            Integer SessionId,
            Object serverContext);
    /**
     * Inits and starts Server
     */
    void start();
    /**
     * Stops Server
     */
    void stop();

}
