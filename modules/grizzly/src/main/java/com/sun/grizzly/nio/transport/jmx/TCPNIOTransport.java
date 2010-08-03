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

package com.sun.grizzly.nio.transport.jmx;

import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedObject;

/**
 * TCP NIO Transport JMX object.
 * 
 * @author Alexey Stashok
 */
@ManagedObject
@Description("Grizzly TCP NIO Transport")
public class TCPNIOTransport extends NIOTransport {
    public TCPNIOTransport(com.sun.grizzly.nio.transport.TCPNIOTransport transport) {
        super(transport);
    }

    @ManagedAttribute(id="server-socket-so-timeout")
    public int getServerSocketSoTimeout() {
        return ((com.sun.grizzly.nio.transport.TCPNIOTransport) transport).getServerSocketSoTimeout();
    }

    @ManagedAttribute(id="client-socket-so-timeout")
    public int getClientSocketSoTimeout() {
        return ((com.sun.grizzly.nio.transport.TCPNIOTransport) transport).getClientSocketSoTimeout();
    }

    @ManagedAttribute(id="socket-tcp-no-delay")
    public boolean getTcpNoDelay() {
        return ((com.sun.grizzly.nio.transport.TCPNIOTransport) transport).isTcpNoDelay();
    }

    @ManagedAttribute(id="socket-reuse-address")
    public boolean getReuseAddress() {
        return ((com.sun.grizzly.nio.transport.TCPNIOTransport) transport).isReuseAddress();
    }

    @ManagedAttribute(id="socket-linger")
    public int getLinger() {
        return ((com.sun.grizzly.nio.transport.TCPNIOTransport) transport).getLinger();
    }

    @ManagedAttribute(id="socket-keep-alive")
    public boolean getKeepAlive() {
        return ((com.sun.grizzly.nio.transport.TCPNIOTransport) transport).isKeepAlive();
    }

    @ManagedAttribute(id="client-connect-timeout-millis")
    public int getConnectTimeout() {
        return ((com.sun.grizzly.nio.transport.TCPNIOTransport) transport).getConnectionTimeout();
    }
}
