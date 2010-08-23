/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly;

import java.nio.channels.SelectableChannel;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.InetSocketAddress;

/**
 * This class extends a UDP implementation of a <code>SelectorHandler</code>
 * and reuses the opened <code>DatagramChannel</code> if a user tries to connect any remote addresses.
 *
 * This class is useful for only CLIENT_SERVER Role.
 * Because of reusing the <code>DatagramChannel</code>,
 * if a user has initialized the <code>Selectorhandler</code> with <code>inet</code>,
 * when a user tries to connect a remote with a local address for binding it,
 * duplicated binding will be prevented.
 *
 * Here is scenario.
 * - opened a <code>DatagramChannel</code> from SelectorHandler with CLIENT_SERVER role
 * - tries to connect the remoteAddress with <code>Controller</code> like this.
 * "connectorHandler = controller.acquireConnectorHandler( Protocol.UDP );"
 * "connectorHandler.connect( remoteAddress, localAddress );"
 * Then, the opened <code>DatagramChannel</code> is reused for I/O operations.
 * Note: Actually, connectorHandler.close() doesn't allow the <code>DatagramChannel</code> to be closed
 * because the <code>DatagramChannel</code> is shared between server-side and client-side.
 * But you should guarantee calling connectorHandler.close() in order to prevent any connection leak,
 * after you have used connectorHandler.connect() once. 
 *
 * @author Bongjae Chang
 */
public class ReusableUDPSelectorHandler extends UDPSelectorHandler {

    public ReusableUDPSelectorHandler() {
        super( Role.CLIENT_SERVER );
    }

    @Override
    protected SelectableChannel getSelectableChannel( SocketAddress remoteAddress, SocketAddress localAddress ) throws IOException {
        SelectableChannel selectableChannel = null;
        if( localAddress != null ) {
            if( inet != null && localAddress instanceof InetSocketAddress ) {
                InetSocketAddress inetSocketAddress = (InetSocketAddress)localAddress;
                if( inet.equals( inetSocketAddress.getAddress() ) )
                    selectableChannel = getUsedSelectableChannel();
            }
        } else {
            selectableChannel = getUsedSelectableChannel();
        }
        if( selectableChannel == null )
            selectableChannel = super.getSelectableChannel( remoteAddress, localAddress );
        return selectableChannel;
    }

    private SelectableChannel getUsedSelectableChannel() {
        if( role != Role.CLIENT && datagramChannel != null && datagramSocket != null )
            return datagramChannel;
        else
            return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void closeChannel( SelectableChannel channel ) {
        if( datagramChannel == channel )
            return;
        super.closeChannel( channel );
    }
}
