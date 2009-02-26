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

package org.glassfish.grizzly.samples.lifecycle;

import java.io.IOException;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.samples.echo.EchoFilter;

/**
 * An example, how connections lifecycle could be controlled using Grizzly 2.0
 *
 * @author Alexey Stashok
 */
public class LifeCycleExample {
    public static final String HOST = "localhost";
    public static final int PORT = 7777;

    public static void main(String[] args) throws IOException {
        // Create TCP transport
        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();

        LifeCycleFilter lifeCycleFilter = new LifeCycleFilter();
        // Add TransportFilter, which is responsible
        // for reading and writing data to the connection
        transport.getFilterChain().add(new TransportFilter());
        // Add lifecycle filter to track the connections
        transport.getFilterChain().add(lifeCycleFilter);
        // Add echo filter
        transport.getFilterChain().add(new EchoFilter());

        try {
            // binding transport to start listen on certain host and port
            transport.bind(HOST, PORT);

            // start the transport
            transport.start();
            System.out.println("Press 'q and ENTER' to exit, or just ENTER to see statistics...");

            do {
                printStats(lifeCycleFilter);
            } while (System.in.read() != 'q');
        } finally {
            // stop the transport
            transport.stop();

            // release TransportManager resources like ThreadPool
            TransportFactory.getInstance().close();
        }
    }

    /**
     * Print the lifecycle statistics
     *
     * @param lifeCycleFilter the {@link LifeCycleFilter}
     */
    private static void printStats(LifeCycleFilter lifeCycleFilter) {
        System.out.println("The total number of connections ever connected: " +
                lifeCycleFilter.getTotalConnections());
        System.out.println("The number of active connections: " +
                lifeCycleFilter.getActiveConnections().size());
    }
}
