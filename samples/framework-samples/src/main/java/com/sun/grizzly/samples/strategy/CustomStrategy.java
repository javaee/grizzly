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

package com.sun.grizzly.samples.strategy;

import java.io.IOException;
import com.sun.grizzly.Strategy;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.samples.echo.EchoClient;
import com.sun.grizzly.samples.echo.EchoFilter;
import com.sun.grizzly.strategies.LeaderFollowerStrategy;
import com.sun.grizzly.strategies.SameThreadStrategy;
import com.sun.grizzly.strategies.SimpleDynamicStrategy;
import com.sun.grizzly.strategies.WorkerThreadStrategy;

/**
 * Sample shows how easy custom {@link Strategy} could be applied for a
 * {@link Transport}. In this example we use {@link LeaderFollowerStrategy} for
 * processing all I/O events occuring on {@link Connection}.
 *
 * To test this echo server you can use {@link EchoClient}.
 *
 * @see Strategy
 * @see LeaderFollowerStrategy
 * @see SameThreadStrategy
 * @see WorkerThreadStrategy
 * @see SimpleDynamicStrategy
 * 
 * @author Alexey Stashok
 */
public class CustomStrategy {
    public static final String HOST = "localhost";
    public static final int PORT = 7777;

    public static void main(String[] args) throws IOException {
        // Create TCP transport
        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();

        // Set the LeaderFollowerStrategy (any strategy could be applied this way)
        transport.setStrategy(new LeaderFollowerStrategy(transport));
        
        // Add TransportFilter, which is responsible
        // for reading and writing data to the connection
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(new EchoFilter());

        try {
            // binding transport to start listen on certain host and port
            transport.bind(HOST, PORT);

            // start the transport
            transport.start();

            System.out.println("Press any key to stop the server...");
            System.in.read();
        } finally {
            System.out.println("Stopping transport...");
            // stop the transport
            transport.stop();

            // release TransportManager resources like ThreadPool
            TransportFactory.getInstance().close();
            System.out.println("Stopped transport...");
        }
    }
}
