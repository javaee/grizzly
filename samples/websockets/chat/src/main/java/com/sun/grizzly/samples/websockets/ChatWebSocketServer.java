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

package com.sun.grizzly.samples.websockets;

import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpServerFilter;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.websockets.WebSocketApplication;
import com.sun.grizzly.websockets.WebSocketEngine;
import com.sun.grizzly.websockets.WebSocketFilter;

/**
 *
 * @author Alexey Stashok
 */
public class ChatWebSocketServer {
    public static final int PORT = 8080;
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Please provide a path to webapp in the command line");
            System.exit(0);
        }

        final String webappDir = args[0];
        
        FilterChainBuilder serverFilterChainBuilder = FilterChainBuilder.stateless();
        serverFilterChainBuilder.add(new TransportFilter());
        serverFilterChainBuilder.add(new HttpServerFilter());
        serverFilterChainBuilder.add(new WebSocketFilter());
        serverFilterChainBuilder.add(new SimpleWebServerFilter(webappDir));

        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.setProcessor(serverFilterChainBuilder.build());

        final WebSocketApplication chatApplication = new ChatApplication();

        WebSocketEngine.getEngine().registerApplication("/grizzly-websockets-chat/chat", chatApplication);

        try {
            transport.bind(PORT);
            transport.start();


            System.out.println("Press any key to stop the server...");
            System.in.read();
        } finally {
            transport.stop();
            TransportFactory.getInstance().close();
        }
    }
}
