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

package org.glassfish.grizzly.nio.transport;

import org.glassfish.grizzly.nio.AbstractNIOConnection;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.IndexedAttributeHolder;
import org.glassfish.grizzly.nio.SelectorRunner;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.AbstractStreamReader;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.streams.AbstractStreamWriter;

/**
 * {@link org.glassfish.grizzly.Connection} implementation
 * for the {@link TCPNIOTransport}
 *
 * @author Alexey Stashok
 */
public class TCPNIOConnection extends AbstractNIOConnection<SocketAddress> {
    private Logger logger = Grizzly.logger;

    private AbstractStreamReader streamReader;

    private AbstractStreamWriter streamWriter;
    
    public TCPNIOConnection(TCPNIOTransport transport,
            SelectableChannel channel) {
        this.transport = transport;
        this.channel = channel;
        readBufferSize = transport.getReadBufferSize();
        writeBufferSize = transport.getWriteBufferSize();
        
        streamReader = new TCPNIOStreamReader(this);
        streamWriter = new TCPNIOStreamWriter(this);
    }

    @Override
    protected void setSelectionKey(SelectionKey selectionKey) {
        super.setSelectionKey(selectionKey);
    }

    @Override
    protected void setSelectorRunner(SelectorRunner selectorRunner) {
        super.setSelectorRunner(selectorRunner);
    }

    public StreamReader getStreamReader() {
        return streamReader;
    }

    public StreamWriter getStreamWriter() {
        return streamWriter;
    }

    protected void preClose() {
        try {
            transport.fireIOEvent(IOEvent.CLOSED, this);
        } catch (IOException e) {
            Grizzly.logger.log(Level.FINE, "Unexpected IOExcption occurred, " +
                    "when firing CLOSE event");
        }
    }

    protected AttributeHolder initializeAttributeHolder() {
        return new IndexedAttributeHolder(transport.getAttributeBuilder());
    }

    /**
     * Returns the address of the endpoint this <tt>Connection</tt> is
     * connected to, or <tt>null</tt> if it is unconnected.
     * @return the address of the endpoint this <tt>Connection</tt> is
     *         connected to, or <tt>null</tt> if it is unconnected.
     */
    public SocketAddress getPeerAddress() {
        if (channel != null) {
            return ((SocketChannel) channel).socket().getRemoteSocketAddress();
        }

        return null;
    }
    
    /**
     * Returns the local address of this <tt>Connection</tt>,
     * or <tt>null</tt> if it is unconnected.
     * @return the local address of this <tt>Connection</tt>,
     *      or <tt>null</tt> if it is unconnected.
     */
    public SocketAddress getLocalAddress() {
        if (channel != null) {
            return ((SocketChannel) channel).socket().getLocalSocketAddress();
        }

        return null;
    }
}
    
