/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import com.sun.grizzly.Context;
import com.sun.grizzly.portunif.DefaultFilterChainProtocolHandler;
import com.sun.grizzly.portunif.PUProtocolRequest;
import com.sun.grizzly.util.WorkerThread;

/**
 * This class maps the current request to its associated Container.
 *
 * @author Jeanfrancois Arcand
 */
public class WebProtocolHandler extends DefaultFilterChainProtocolHandler {
    public enum Mode {
        HTTP, HTTPS, HTTP_HTTPS, SIP, SIP_TLS
    }

    /**
     * The protocols supported by this handler.
     */
    protected String[][] protocols = {
        {"http"}, {"https"},
        {"https", "http"}, {"sip"}, {"sip", "sip_tls"}
    };
    private Mode mode;
    // --------------------------------------------------------------------//

    public WebProtocolHandler() {
        this(Mode.HTTP);
    }

    public WebProtocolHandler(final Mode mode) {
        this.mode = mode;
    }
    // --------------------------------------------------------------------//

    /**
     * Based on the context-root, configure Grizzly's ProtocolChain with the proper ProtocolFilter, and if available,
     * proper Adapter.
     *
     * @return true if the ProtocolFilter was properly set.
     */
    @Override
    public boolean handle(final Context context, final PUProtocolRequest protocolRequest)
        throws IOException {
        protocolRequest.setMapSelectionKey(true);
        protocolRequest.setExecuteFilterChain(true);
        return true;
    }
    // -------------------------------------------------------------------- //

    /**
     * filter Returns an array of supported protocols.
     *
     * @return an array of supported protocols.
     */
    @Override
    public String[] getProtocols() {
        return protocols[mode.ordinal()];
    }

    /**
     * Invoked when the SelectorThread is about to expire a SelectionKey.
     *
     * @return true if the SelectorThread should expire the SelectionKey, false if not.
     */
    @Override
    public boolean expireKey(final SelectionKey key) {
        return true;
    }

    /**
     * Returns <code>ByteBuffer</code>, where PUReadFilter will read data
     *
     * @return <code>ByteBuffer</code>
     */
    @Override
    public ByteBuffer getByteBuffer() {
        final WorkerThread workerThread = (WorkerThread) Thread.currentThread();
        if (workerThread.getSSLEngine() != null) {
            return workerThread.getInputBB();
        }
        return null;
    }
}
