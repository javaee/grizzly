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
package com.sun.grizzly.samples.smart.giop;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.sun.grizzly.Connection;
import com.sun.grizzly.StandaloneProcessorSelector;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.smart.SmartCodec;

/**
 * Simple GIOP client
 * 
 * @author Alexey Stashok
 */
public class GIOPClient {

    // @TODO comment the test out until Smart filter will be adjusted to new API
    public static void main(String[] args) throws Exception {
//        Connection connection = null;
//        // Create TCP NIO transport
//        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
//
//        // Initialize smart Codec
//        SmartCodec smartCodec = new SmartCodec(GIOPMessage.class);
//
//        try {
//            // start transport
//            transport.start();
//
//            // Connect client to the GIOP server
//            Future<Connection> future = transport.connect(GIOPServer.HOST,
//                    GIOPServer.PORT);
//
//            connection = future.get(10, TimeUnit.SECONDS);
//
//            // Enable standalone mode for this connection
//            // (don't use Filter chains or other I/O event processors)
//            connection.setPreferableProcessorSelector(new NullProcessorSelector());
//
//            // Initialize sample GIOP message
//            byte[] testMessage = new String("GIOP test").getBytes();
//            GIOPMessage sentMessage = new GIOPMessage((byte) 1, (byte) 2,
//                    (byte) 0x0F, (byte) 0, testMessage);
//
//            // Write message
//            Future<WriteResult> writeFuture =
//                    connection.write(sentMessage, smartCodec.getEncoder());
//
//            writeFuture.get(10, TimeUnit.SECONDS);
//
//            // Receive result back
//            Future<ReadResult> readFuture = connection.read(null,
//                    smartCodec.getDecoder());
//            ReadResult result = readFuture.get(10, TimeUnit.SECONDS);
//
//            // Check if echo returned message equal to original one
//            if (sentMessage.equals(result.getMessage())) {
//                System.out.println("ECHO COMPLETED!");
//            } else {
//                System.out.println("ECHO FAILED!");
//            }
//
//        } finally {
//            if (connection != null) {
//                connection.close();
//            }
//
//            transport.stop();
//            TransportFactory.getInstance().close();
//        }
    }
}
