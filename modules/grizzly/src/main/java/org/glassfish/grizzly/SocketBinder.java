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

package org.glassfish.grizzly;

import java.io.IOException;
import java.net.SocketAddress;

/**
 * Common API for {@link Socket} based {@link Transport}s, which are able
 * to bind server {@link Socket} to specific address and listen for incoming
 * data.
 *
 * @author Alexey Stashok
 */
public interface SocketBinder {
    /**
     * Binds Transport to the specific port on localhost.
     *
     * @param port
     * @throws java.io.IOException
     */
    public void bind(int port) throws IOException;

    /**
     * Binds Transport to the specific host and port.
     *
     * @param host the local host the server will bind to
     * @param port
     * @throws java.io.IOException
     */
    public void bind(String host, int port) throws IOException;

    /**
     * Binds Transport to the specific host and port.
     * @param host the local host the server will bind to
     * @param port
     * @param backlog the maximum length of the queue
     * @throws java.io.IOException
     */
    public void bind(String host, int port, int backlog) throws IOException;

    /**
     * Binds Transport to the specific SocketAddress.
     *
     * @param socketAddress the local address the server will bind to
     * @throws java.io.IOException
     */
    public void bind(SocketAddress socketAddress) throws IOException;

    /**
     * Binds Transport to the specific SocketAddress.
     *
     * @param socketAddress the local address the server will bind to
     * @param backlog the maximum length of the queue
     * @throws java.io.IOException
     */
    public void bind(SocketAddress socketAddress, int backlog) throws IOException;

    /**
     * Unbinds {@link Transport}.
     *
     * @throws java.io.IOException
     */
    public void unbind() throws IOException;

}
