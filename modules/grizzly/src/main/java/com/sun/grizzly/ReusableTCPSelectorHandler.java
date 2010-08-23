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

import com.sun.grizzly.util.Copyable;

import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.IOException;

/**
 * This class extends a TCP implementation of a <code>SelectorHandler</code>
 * and reuses the accepted <code>SocketChannel</code> if a user tries to connect the same remote address.  
 *
 * This class is useful for only CLIENT_SERVER Role.
 * When the <code>ServerSocketChannel</code> of <code>TCPSelectorHandler</code> accepts a <code>SocketChannel</code>,
 * it is stored in <code>acceptedSocketChannelMap</code> which is based on
 * the key(remote <code>SocketAddress</code>) and the value(<code>SocketChannel</code>) pair.
 * Because of reusing the <code>SocketChannel</code>,
 * if a user has initialized the <code>Selectorhandler</code> with <code>inet</code>,
 * when a user tries to connect the remote with a local address for binding it,
 * duplicated binding will be prevented.
 *
 * Here is scenario.
 * - accepted a <code>SocketChannel</code> from a remoteAddress
 * - tries to connect the remoteAddress with <code>Controller</code> like this.
 * "connectorHandler = controller.acquireConnectorHandler( Protocol.TCP );"
 * "connectorHandler.connect( remoteAddress, localAddress );"
 * Then, the accepted <code>SocketChannel</code> of the remoteAddress is reused for I/O operations.
 * Note: Actually, connectorHandler.close() doesn't allow the <code>SocketChannel</code> to be closed
 * because the <code>SocketChannel</code> is shared between server-side and client-side.
 * But you should guarantee calling connectorHandler.close() in order to prevent any connection leak,
 * after you have used connectorHandler.connect() once.
 *
 * @author Bongjae Chang
 */
public class ReusableTCPSelectorHandler extends TCPSelectorHandler {

    private final ConcurrentHashMap<SocketAddress, SocketChannel> acceptedSocketChannelMap =
            new ConcurrentHashMap<SocketAddress, SocketChannel>();
    private final CopyOnWriteArrayList<SocketChannel> reusableSocketChannels =
            new CopyOnWriteArrayList<SocketChannel>();

    public ReusableTCPSelectorHandler() {
        super( Role.CLIENT_SERVER );
    }

    @Override
    public void copyTo( Copyable copy ) {
        super.copyTo( copy );
        if( !( copy instanceof ReusableTCPSelectorHandler ) )
            return;
        ReusableTCPSelectorHandler copyHandler = (ReusableTCPSelectorHandler) copy;
        copyHandler.acceptedSocketChannelMap.putAll( acceptedSocketChannelMap );
        copyHandler.reusableSocketChannels.addAll( reusableSocketChannels );
    }

    @Override
    protected SelectableChannel getSelectableChannel( SocketAddress remoteAddress, SocketAddress localAddress ) throws IOException {
        SelectableChannel selectableChannel = null;
        if( localAddress != null ) {
            if( inet != null && localAddress instanceof InetSocketAddress ) {
                InetSocketAddress inetSocketAddress = (InetSocketAddress)localAddress;
                if( inet.equals( inetSocketAddress.getAddress() ) )
                    selectableChannel = getUsedSelectableChannel( remoteAddress );
            }
        } else {
            selectableChannel = getUsedSelectableChannel( remoteAddress );
        }
        if( selectableChannel == null )
            selectableChannel = super.getSelectableChannel( remoteAddress, localAddress );
        return selectableChannel;
    }

    private SelectableChannel getUsedSelectableChannel( SocketAddress remoteAddress ) {
        if( remoteAddress != null ) {
            SocketChannel acceptedSocketChannel = acceptedSocketChannelMap.get( remoteAddress );
            if( acceptedSocketChannel != null )
                reusableSocketChannels.add( acceptedSocketChannel );
            return acceptedSocketChannel;
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        super.shutdown();
        acceptedSocketChannelMap.clear();
        reusableSocketChannels.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SelectableChannel acceptWithoutRegistration( SelectionKey key ) throws IOException {
        SocketChannel acceptedSocketChannel = ( (ServerSocketChannel)key.channel() ).accept();
        if( acceptedSocketChannel != null ) {
            SocketAddress remoteSocketAddress = null;
            Socket acceptedSocket = acceptedSocketChannel.socket();
            if( acceptedSocket != null )
                remoteSocketAddress = acceptedSocket.getRemoteSocketAddress();
            if( remoteSocketAddress != null )
                acceptedSocketChannelMap.put( remoteSocketAddress, acceptedSocketChannel );
        }
        return acceptedSocketChannel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void closeChannel( SelectableChannel channel ) {
        if( channel instanceof SocketChannel ) {
            SocketChannel socketChannel = (SocketChannel)channel;
            if( reusableSocketChannels.remove( socketChannel ) )
                return;
            Socket socket = socketChannel.socket();
            SocketAddress remoteSocketAddress = null;
            if( socket != null )
                remoteSocketAddress = socket.getRemoteSocketAddress();
            if( remoteSocketAddress != null )
                acceptedSocketChannelMap.remove( remoteSocketAddress );
        }
        super.closeChannel( channel );
    }
}
