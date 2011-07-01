/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
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

import junit.framework.TestCase;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.net.Socket;
import java.net.BindException;
import java.net.SocketAddress;
import java.io.IOException;
import java.util.logging.Level;

import com.sun.grizzly.utils.ControllerUtils;

/**
 * @author Bongjae Chang
 */
public class ReusableSelectorHandlerTest extends TestCase {

    private static final int PORT = 17521;
    private static final int SLEEP_TIME = 3000; // ms

    private final InetAddress localInetAddress;
    private final InetSocketAddress localInetSocketAddress;

    public ReusableSelectorHandlerTest() throws UnknownHostException {
        localInetAddress = InetAddress.getByName("localhost");
        localInetSocketAddress = new InetSocketAddress( localInetAddress, PORT );
    }

    /**
     * Tests whether the server-side channel is reused on TCP or not.
     * The BindException is checked with setting setReuseAddress to be false.
     *
     * @throws IOException unexpected IO exception
     */
    public void testSimpleTCPConnect() throws IOException {
        final Controller controller = new Controller();
        SelectorHandler selectorHandler = SelectorHandlerFactory.createSelectorHandler( Controller.Protocol.TCP, true );
        ( (TCPSelectorHandler)selectorHandler ).setPort( PORT );
        ( (TCPSelectorHandler)selectorHandler ).setInet( localInetAddress );
        ( (TCPSelectorHandler)selectorHandler ).setReuseAddress( false );
        controller.addSelectorHandler( selectorHandler );

        Socket clientSocket = null;
        try {
            ControllerUtils.startController( controller );

            boolean result = false;
            Controller.logger().log( Level.FINE, "Try to get a connector handler with the local address which already has been bound." );
            try {
                tryToConnect( controller, Controller.Protocol.TCP, null, localInetSocketAddress );
            } catch( IOException ie ) {
                if( ie instanceof BindException ) {
                    result = true;
                    Controller.logger().log( Level.FINE, "Got the expected BindException." );
                    assertTrue( "Got the expected BindException.", true );
                } else {
                    Controller.logger().log( Level.FINE, "Got the unexpected error.", ie );
                    assertTrue( "Got the unexpected error.", false );
                }
            }
            if( !result )
                assertTrue( "The BindException was expected.", false );

            Controller.logger().log( Level.INFO, "Try to connect the local server." );
            clientSocket = new Socket( localInetAddress, PORT );
            Controller.logger().log( Level.INFO, "Wait for " + SLEEP_TIME + "(ms)" );
            try {
                Thread.sleep( SLEEP_TIME );
            } catch( InterruptedException e ) {
            }

            Controller.logger().log( Level.INFO, "Try to get a connector handler with the local address which already has been bound again." );
            try {
                tryToConnect( controller, Controller.Protocol.TCP, clientSocket.getLocalSocketAddress(), localInetSocketAddress );
            } catch( IOException ie ) {
                Controller.logger().log( Level.INFO, "Got the unexpected error.", ie );
                assertTrue( "Got the unexpected error.", false );
                throw ie;
            }
        } finally {
            if( clientSocket != null ) {
                try {
                    clientSocket.close();
                } catch( IOException e ) {
                }
            }
            controller.stop();
        }
    }

    /**
     * Tests whether the server-side channel is reused on UDP or not.
     * The BindException is checked with setting setReuseAddress to be false.
     *
     * @throws IOException unexpected IO exception
     */
    public void testSimpleUDPConnect() throws IOException {
        final Controller controller = new Controller();
        SelectorHandler selectorHandler = SelectorHandlerFactory.createSelectorHandler( Controller.Protocol.UDP, true );
        ( (UDPSelectorHandler)selectorHandler ).setPort( PORT );
        ( (UDPSelectorHandler)selectorHandler ).setInet( localInetAddress );
        ( (UDPSelectorHandler)selectorHandler ).setReuseAddress( false );
        controller.addSelectorHandler( selectorHandler );

        try {
            ControllerUtils.startController( controller );

            Controller.logger().log( Level.INFO, "Try to get a connector handler with the local address which already has been bound." );
            try {
                tryToConnect( controller, Controller.Protocol.UDP, localInetSocketAddress, localInetSocketAddress );
            } catch( IOException ie ) {
                Controller.logger().log( Level.INFO, "Got the unexpected error.", ie );
                assertTrue( "Got the unexpected error.", false );
                throw ie;
            }
        } finally {
            controller.stop();
        }
    }

    private void tryToConnect( Controller controller, Controller.Protocol protocol, SocketAddress remote, SocketAddress local ) throws IOException {
        ConnectorHandler connectorHandler = null;
        try {
            connectorHandler = controller.acquireConnectorHandler( protocol );
            connectorHandler.connect( remote, local );
        } finally {
            if( connectorHandler != null ) {
                try {
                    connectorHandler.close();
                } catch( IOException e ) {
                    e.printStackTrace();
                }
                controller.releaseConnectorHandler( connectorHandler );
            }
        }
    }

    public static void main( String[] args ) throws IOException {
        ReusableSelectorHandlerTest test = new ReusableSelectorHandlerTest();
        test.testSimpleTCPConnect();
        test.testSimpleUDPConnect();
    }
}
