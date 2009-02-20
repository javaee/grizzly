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

package com.sun.grizzly.connectioncache.client;


import com.sun.grizzly.ConnectorHandler;
import com.sun.grizzly.ConnectorHandlerPool;
import com.sun.grizzly.ConnectorInstanceHandler;
import com.sun.grizzly.Controller;
import com.sun.grizzly.Controller.Protocol;
import com.sun.grizzly.DefaultConnectorHandlerPool;
import com.sun.grizzly.connectioncache.spi.transport.ConnectionCacheFactory;
import com.sun.grizzly.connectioncache.spi.transport.ConnectionFinder;
import com.sun.grizzly.connectioncache.spi.transport.OutboundConnectionCache;

/**
 * <code>ConnectorInstanceHandler</code> which use a
 * <code>ConcurrentQueue</code> to pool <code>CacheableConnectorHandler</code>
 *
 * @author Alexey Stashok
 */
public class CacheableConnectorHandlerPool implements 
        ConnectorHandlerPool<CacheableConnectorHandler> {
    
    private Controller controller;
    private ConnectorHandlerPool protocolConnectorHandlerPool;
    private ConnectorInstanceHandler<CacheableConnectorHandler> connectorInstanceHandler;
    private OutboundConnectionCache<ConnectorHandler> outboundConnectionCache;
    private ConnectionFinder<ConnectorHandler> connectionFinder;
    
    public CacheableConnectorHandlerPool(Controller controller, int highWaterMark,
            int numberToReclaim, int maxParallel) {
            this(controller, highWaterMark, numberToReclaim, maxParallel, null);
    }
    
    public CacheableConnectorHandlerPool(Controller controller, int highWaterMark,
            int numberToReclaim, int maxParallel, ConnectionFinder<ConnectorHandler> connectionFinder) {
        this.controller = controller;
        this.outboundConnectionCache = 
                ConnectionCacheFactory.<ConnectorHandler>makeBlockingOutboundConnectionCache(
                "Grizzly outbound connection cache", highWaterMark, 
                numberToReclaim, maxParallel, Controller.logger());
        this.connectionFinder = connectionFinder;
        protocolConnectorHandlerPool = new DefaultConnectorHandlerPool(controller);
        connectorInstanceHandler = new CacheableConnectorInstanceHandler();
    }

    public CacheableConnectorHandler acquireConnectorHandler(Protocol protocol) {
        CacheableConnectorHandler connectorHandler = connectorInstanceHandler.acquire();
        connectorHandler.setProtocol(protocol);
        return connectorHandler;
    }
    
    public void releaseConnectorHandler(CacheableConnectorHandler connectorHandler) {
        /*
         * Do nothing here, because CacheableConnectorHandler should be
         * returned to pool, only when it will be really diassociated with
         * OutboundConnectionCache
         */
    }
    
    OutboundConnectionCache<ConnectorHandler> getOutboundConnectionCache() {
        return outboundConnectionCache;
    }

    ConnectionFinder getConnectionFinder() {
        return connectionFinder;
    }

    Controller getController() {
        return controller;
    }
    
    ConnectorHandlerPool getProtocolConnectorHandlerPool() {
        return protocolConnectorHandlerPool;
    }
            
    /**
     * Default <code>ConnectorInstanceHandler</code> which use a
     * <code>ConcurrentQueue</code> to pool {@link ConnectorHandler}
     */
    private class CacheableConnectorInstanceHandler extends
            ConnectorInstanceHandler.ConcurrentQueueConnectorInstanceHandler<CacheableConnectorHandler> {
        
        public CacheableConnectorHandler newInstance() {
            return new CacheableConnectorHandler(CacheableConnectorHandlerPool.this);
        }
    }
}
