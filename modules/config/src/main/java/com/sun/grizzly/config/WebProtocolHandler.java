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
    // --------------------------------------------------------------------//
    private final String[] protocols;

    private final boolean isStickyMapping;
    
    public WebProtocolHandler(String protocolName, boolean isStickyMapping) {
        this.protocols = new String[] {protocolName};
        this.isStickyMapping = isStickyMapping;
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
        protocolRequest.setMapSelectionKey(isStickyMapping);
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
        return protocols;
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
