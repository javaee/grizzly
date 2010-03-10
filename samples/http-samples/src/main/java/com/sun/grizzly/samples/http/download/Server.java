/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.samples.http.download;

import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpServerFilter;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import java.io.IOException;
import java.util.logging.Logger;

/**
 *
 * @author oleksiys
 */
public class Server {
    private static final Logger logger = Grizzly.logger(Server.class);

    public static final String HOST = "localhost";
    public static final int PORT = 7777;

    public static void main(String[] args) throws IOException {
        FilterChainBuilder serverFilterChainBuilder = FilterChainBuilder.singleton();
        serverFilterChainBuilder.add(new TransportFilter());
        serverFilterChainBuilder.add(new HttpServerFilter());
        serverFilterChainBuilder.add(new WebServerFilter("/Users/oleksiys"));

        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.setProcessor(serverFilterChainBuilder.build());

        try {
            // binding transport to start listen on certain host and port
            transport.bind(HOST, PORT);

            // start the transport
            transport.start();

            logger.info("Press any key to stop the server...");
            System.in.read();
        } finally {
            logger.info("Stopping transport...");
            // stop the transport
            transport.stop();

            // release TransportManager resources like ThreadPool
            TransportFactory.getInstance().close();
            logger.info("Stopped transport...");
        }
    }
}
