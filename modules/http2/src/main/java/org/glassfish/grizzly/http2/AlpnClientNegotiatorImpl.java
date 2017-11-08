/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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


class AlpnClientNegotiatorImpl extends AlpnNegotiatorBase implements AlpnClientNegotiator {
    private final static Logger LOGGER = Grizzly.logger(AlpnClientNegotiatorImpl.class);

    private final Http2ClientFilter filter;

    public AlpnClientNegotiatorImpl(final Http2ClientFilter filter) {
        this.filter = filter;
    }

    @Override
    public String[] getProtocols(final SSLEngine sslEngine) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Alpn getProtocols. Connection={0}, protocols={1}",
                    new Object[]{AlpnSupport.getConnection(sslEngine), Arrays.toString(SUPPORTED_PROTOCOLS)});
        }
        return SUPPORTED_PROTOCOLS.clone();
    }

    @Override
    public void protocolSelected(final SSLEngine sslEngine, final String selectedProtocol) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Alpn protocolSelected. Connection={0}, protocol={1}",
                    new Object[]{AlpnSupport.getConnection(sslEngine), selectedProtocol});
        }
        
        final Connection connection = AlpnSupport.getConnection(sslEngine);
        if (HTTP2.equals(selectedProtocol)) {
            final Http2Session http2Session =
                    filter.createClientHttp2Session(connection);
            
            // we expect preface
            http2Session.getHttp2State().setDirectUpgradePhase();
            
            http2Session.sendPreface();
        } else {
            // Never try HTTP2 for this connection
            Http2State.create(connection).setNeverHttp2();
        }
    }
    
}
