/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http2;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.npn.AlpnServerNegotiator;

// ---------------------------------------------------------- Nested Classes

final class AlpnServerNegotiatorImpl implements AlpnServerNegotiator {
    private final static Logger LOGGER = Grizzly.logger(AlpnServerNegotiatorImpl.class);

    private static final String HTTP11 = "http/1.1";
    private final String[] supportedProtocols;
    private final Http2BaseFilter filter;
    // ---------------------------------------------------- Constructors

    public AlpnServerNegotiatorImpl(final DraftVersion[] supportedDrafts,
            final Http2ServerFilter http2HandlerFilter) {
        this.filter = http2HandlerFilter;
        supportedProtocols = new String[supportedDrafts.length + 1];
        for (int i = 0; i < supportedDrafts.length; i++) {
            supportedProtocols[i] = supportedDrafts[i].getTlsId();
        }
        supportedProtocols[supportedProtocols.length - 1] = HTTP11;
    }

    // ------------------------------- Methods from ServerSideNegotiator
    @Override
    public String selectProtocol(SSLEngine sslEngine, String[] clientProtocols) {
        final Connection connection = AlpnSupport.getConnection(sslEngine);
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "Alpn selectProtocol. Connection={0} sslEngine={1} clientProtocols={2}", new Object[]{connection, sslEngine, Arrays.toString(clientProtocols)});
        }
        for (String supportedProtocol : supportedProtocols) {
            for (String clientProtocol : clientProtocols) {
                if (supportedProtocol.equals(clientProtocol)) {
                    if (LOGGER.isLoggable(Level.INFO)) {
                        LOGGER.log(Level.INFO, "Alpn select {0}", clientProtocol);
                    }
                    configureHttp2(connection, clientProtocol);
                    return clientProtocol;
                }
            }
        }
        
        // Never try HTTP2 for this connection
        Http2State.create(connection).setNeverHttp2();
        
        return null;
    }

    private void configureHttp2(final Connection connection, final String supportedProtocol) {
        final DraftVersion http2Version = DraftVersion.fromString(supportedProtocol);
        // If HTTP2 is supported - initialize HTTP2 connection
        if (http2Version != null) {
            // Create HTTP2 connection and bind it to the Grizzly connection
            final Http2Connection http2Connection = filter.createHttp2Connection(
                    http2Version, connection, true);
            
            // we expect client preface
            http2Connection.getHttp2State().setDirectUpgradePhase();
            // !!! DON'T SEND PREFACE HERE
            // SSL connection (handshake) is not established yet and if we try
            // to send preface here SSLBaseFilter will try to flush it right away,
            // because it doesn't queue the output data like SSLFilter.
//            http2Connection.enableHttp2Output();
//            http2Connection.sendPreface();
        }
    }
    
} // END ProtocolNegotiator
