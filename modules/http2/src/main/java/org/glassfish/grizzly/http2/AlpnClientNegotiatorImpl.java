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
import org.glassfish.grizzly.npn.AlpnClientNegotiator;

/**
 *
 * @author oleksiys
 */
class AlpnClientNegotiatorImpl implements AlpnClientNegotiator {
    private final static Logger LOGGER = Grizzly.logger(AlpnClientNegotiatorImpl.class);

    private static final String HTTP11 = "http/1.1";
    private final String[] supportedProtocolsStr;
    private final Http2ClientFilter filter;

    public AlpnClientNegotiatorImpl(final DraftVersion[] supportedHttp2Drafts,
            final Http2ClientFilter filter) {
        this.filter = filter;
        
        supportedProtocolsStr = new String[supportedHttp2Drafts.length + 1];
        for (int i = 0; i < supportedHttp2Drafts.length; i++) {
            supportedProtocolsStr[i] = supportedHttp2Drafts[i].getTlsId();
        }
        supportedProtocolsStr[supportedProtocolsStr.length - 1] = HTTP11;
    }

    @Override
    public String[] getProtocols(final SSLEngine sslEngine) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "Alpn getProtocols. Connection={0}, protocols={1}",
                    new Object[]{AlpnSupport.getConnection(sslEngine), Arrays.toString(supportedProtocolsStr)});
        }
        return supportedProtocolsStr;
    }

    @Override
    public void protocolSelected(final SSLEngine sslEngine, final String selectedProtocol) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "Alpn protocolSelected. Connection={0}, protocol={1}",
                    new Object[]{AlpnSupport.getConnection(sslEngine), selectedProtocol});
        }
        
        final Connection connection = AlpnSupport.getConnection(sslEngine);
        final DraftVersion draft = DraftVersion.fromString(selectedProtocol);
        if (draft != null) {
            final Http2Connection http2Connection =
                    filter.createClientHttp2Connection(draft, connection);
            
            // we expect preface
            http2Connection.getHttp2State().setDirectUpgradePhase();
            
            http2Connection.sendPreface();
        } else {
            // Never try HTTP2 for this connection
            Http2State.create(connection).setNeverHttp2();
        }
    }
    
}
