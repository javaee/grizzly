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

package com.sun.grizzly.nio.transport;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.TimeUnit;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.Reader;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.Writer;
import com.sun.grizzly.nio.NIOConnection;
import com.sun.grizzly.nio.tmpselectors.TemporarySelectorIO;

/**
 * TODO: implement :)
 * 
 * @author oleksiys
 */
public class UDPTemporarySelectorIO extends TemporarySelectorIO {

    public UDPTemporarySelectorIO(Reader reader, Writer writer) {
        super(reader, writer);
    }

    /**
     * Flush the buffer by looping until the {@link Buffer} is empty
     * @param channel {@link DatagramChannel}
     * @param bb the Buffer to write.
     * @return 
     * @throws java.io.IOException 
     */
//    public int send(NIOConnection connection, Buffer bb,
//            SocketAddress dstAddress, long timeout, TimeUnit timeunit)
//            throws IOException {
//
//        if (bb == null) {
//            throw new IllegalStateException("Buffer cannot be null.");
//        }
//
//        if (connection == null) {
//            throw new IllegalStateException("Connection cannot be null.");
//        }
//
//        long writeTimeout = TimeUnit.MILLISECONDS.convert(timeout, timeunit);
//        SelectionKey key = null;
//        Selector writeSelector = null;
//        int bytesProduced = 0;
//        try {
//            WriteResult result = connection.writeNow(dstAddress, bb);
//
//            bytesProduced = result.getWrittenSize();
//
//            if (bytesProduced == 0) {
//                if (writeSelector == null) {
//                    writeSelector = selectorPool.poll();
//                    if (writeSelector == null) {
//                        // Continue using the main one.
//                        return bytesProduced;
//                    }
//                    key = connection.getChannel().register(writeSelector,
//                            SelectionKey.OP_WRITE);
//                }
//
//                if (writeSelector.select(writeTimeout) > 0) {
//                    result = connection.writeNow(dstAddress, bb);
//                    bytesProduced = result.getWrittenSize();
//                } else {
//                    throw new IOException("Client disconnected");
//                }
//            }
//        } finally {
//            recycleTemporaryArtifacts(writeSelector, key);
//        }
//
//        return bytesProduced;
//    }
    

    /**
     * Method reads data from {@link DatagramChannel} to 
     * {@link Buffer}. If data is not immediately available - channel
     *  will be reregistered on temporary {@link Selector} and wait maximum
     * readTimeout milliseconds for data.
     * 
     * @param channel {@link DatagramChannel} to read data from
     * @param buffer {@link Buffer} to store read data to
     * 
     * @return sender's <code>SocketAddress</code> or null if nothing received
     * @throws {@link SocketAddress} if any error was occured during read
     */
//    public SocketAddress receive(NIOConnection connection,
//            Buffer buffer, long timeout, TimeUnit timeunit)
//            throws IOException {
//
//        long readTimeout = TimeUnit.MILLISECONDS.convert(timeout, timeunit);
//        SocketAddress senderAddress = null;
//        Selector readSelector = null;
//        SelectionKey tmpKey = null;
//
//        try {
//            ReadResult result = connection.readNow(buffer);
//            senderAddress = (SocketAddress) result.getSrcAddress();
//            if (senderAddress == null) {
//                readSelector = selectorPool.poll();
//
//                if (readSelector == null) {
//                    return null;
//                }
//
//                tmpKey = connection.getChannel().register(readSelector,
//                        SelectionKey.OP_READ);
//                tmpKey.interestOps(tmpKey.interestOps() | SelectionKey.OP_READ);
//                int code = readSelector.select(readTimeout);
//                tmpKey.interestOps(
//                        tmpKey.interestOps() & (~SelectionKey.OP_READ));
//
//                if (code > 0) {
//                    result = connection.readNow(buffer);
//                    senderAddress = (SocketAddress) result.getSrcAddress();
//                } else {
//                    senderAddress = null;
//                }
//            }
//        } finally {
//            recycleTemporaryArtifacts(readSelector, tmpKey);
//        }
//
//        return senderAddress;
//    }
}
