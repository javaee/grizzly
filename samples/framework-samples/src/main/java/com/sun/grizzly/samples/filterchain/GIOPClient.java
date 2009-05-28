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
package com.sun.grizzly.samples.filterchain;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.sun.grizzly.Connection;
import com.sun.grizzly.StandaloneProcessorSelector;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;

/**
 * Simple GIOP client
 * 
 * @author Alexey Stashok
 */
public class GIOPClient {

    // @TODO comment the test out until Smart filter will be adjusted to new API
    public static void main(String[] args) throws Exception {
        Connection connection = null;
        StreamReader reader = null;
        StreamWriter writer = null;
        // Create TCP NIO transport
        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();

        try {
            // start transport
            transport.start();

            // Connect client to the GIOP server
            Future<Connection> future = transport.connect(GIOPServer.HOST,
                    GIOPServer.PORT);

            connection = future.get(10, TimeUnit.SECONDS);

            // Initialize reader and writer
            reader = connection.getStreamReader();
            writer = connection.getStreamWriter();

            // Enable standalone mode for this connection
            // (don't use Filter chains or other I/O event processors)
            connection.setProcessorSelector(new StandaloneProcessorSelector());

            // Initialize sample GIOP message
            byte[] testMessage = new String("GIOP test").getBytes();
            GIOPMessage sentMessage = new GIOPMessage((byte) 1, (byte) 2,
                    (byte) 0x0F, (byte) 0, testMessage);

            int size = writeGIOPMessage(writer, sentMessage);

            Future messageAvailFuture = reader.notifyAvailable(size);
            messageAvailFuture.get(10, TimeUnit.SECONDS);

            GIOPMessage rcvMessage = readerGIOPMessage(reader);
            
            // Check if echo returned message equal to original one
            if (sentMessage.equals(rcvMessage)) {
                System.out.println("DONE!");
            } else {
                System.out.println("Messages are not equal!");
            }

        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    private static int writeGIOPMessage(StreamWriter writer,
            GIOPMessage message) throws IOException, InterruptedException {

        // GIOP header
        writer.writeByteArray(message.getGIOPHeader());

        // Major version
        writer.writeByte(message.getMajor());

        // Minor version
        writer.writeByte(message.getMinor());

        // Flags
        writer.writeByte(message.getFlags());

        // Value
        writer.writeByte(message.getValue());

        // Body length
        writer.writeInt(message.getBodyLength());

        // Body
        writer.writeByteArray(message.getBody());

        // Flush the message
        writer.flush();

        return 12 + message.getBodyLength();
    }

    private static GIOPMessage readerGIOPMessage(StreamReader reader)
            throws IOException {

        GIOPMessage message = new GIOPMessage();

        // GIOP header
        message.setGIOPHeader(reader.readByte(), reader.readByte(),
                reader.readByte(), reader.readByte());

        // Major version
        message.setMajor(reader.readByte());

        // Minor version
        message.setMinor(reader.readByte());

        // Flags
        message.setFlags(reader.readByte());

        // Value
        message.setValue(reader.readByte());

        // Body length
        message.setBodyLength(reader.readInt());

        // Body
        byte[] body = new byte[message.getBodyLength()];
        reader.readByteArray(body);
        message.setBody(body);

        return message;
    }
}
