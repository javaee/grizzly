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
package org.glassfish.grizzly.samples.echo;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;

/**
 * The simple client, which sends a message to the echo server
 * and waits for response
 * @author Alexey Stashok
 */
public class EchoClient {

    public static void main(String[] args) throws IOException,
            ExecutionException, InterruptedException, TimeoutException {

        Connection connection = null;
        StreamReader reader = null;
        StreamWriter writer = null;
        
        // Create the TCP transport
        TCPNIOTransport transport = TransportFactory.getInstance().
                createTCPTransport();

        try {
            // start the transport
            transport.start();

            // perform async. connect to the server
            Future<Connection> future = transport.connect(EchoServer.HOST,
                    EchoServer.PORT);
            // wait for connect operation to complete
            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);

            assert connection != null;

            writer = connection.getStreamWriter();
            String message = "Echo test";
            byte[] sendBytes = message.getBytes();

            // sync. write the complete message using
            // temporary selectors if required
            writer.writeByteArray(sendBytes);
            Future<Integer> writeFuture = writer.flush();
            writeFuture.get();

            assert writeFuture.isDone();

            reader = connection.getStreamReader();
            // allocate the buffer for receiving bytes
            byte[] receiveBytes = new byte[sendBytes.length];
            Future readFuture = reader.notifyAvailable(receiveBytes.length);

            readFuture.get();

            reader.readByteArray(receiveBytes);

            // check the result
            assert Arrays.equals(sendBytes, receiveBytes);
        } finally {
            // close the client connection
            if (connection != null) {
                connection.close();
            }

            // stop the transport
            transport.stop();
            // release TransportManager resources like ThreadPool etc.
            TransportFactory.getInstance().close();
        }
    }
}