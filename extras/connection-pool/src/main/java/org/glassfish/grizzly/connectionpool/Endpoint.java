/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.connectionpool;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ConnectorHandler;
import org.glassfish.grizzly.GrizzlyFuture;

/**
 * The abstract class, which describes a pool endpoint and has a method, which
 * creates new {@link Connection} to the endpoint.
 * 
 * @param <E> the address type, for example for TCP transport it's {@link SocketAddress}
 * 
 * @author Alexey Stashok
 */
public abstract class Endpoint<E> {
    public abstract Object getId();
    public abstract GrizzlyFuture<Connection> connect();
    
    /**
     * The method is called, once new {@link Connection} related to the
     * <tt>Endpoint</tt> is established.
     * 
     * @param connection the {@link Connection}
     * @param pool the pool, to which the {@link Connection} is bound
     */
    protected void onConnect(Connection connection, SingleEndpointPool<E> pool) {
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof Endpoint)) {
            return false;
        }

        return getId().equals(((Endpoint) o).getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }
    
    /**
     * Convenient <tt>Endpoint</tt> factory for cases, when user has a
     * {@link ConnectorHandler} and endpoint address.
     */
    public static final class Factory {

        public static <E> Endpoint<E> create(final E targetAddress,
                final ConnectorHandler<E> connectorHandler) {
            return create(targetAddress, null, connectorHandler);
        }

        public static <E> Endpoint<E> create(
                final E targetAddress, final E localAddress,
                final ConnectorHandler<E> connectorHandler) {
            return create(targetAddress.toString() +
                    (localAddress != null
                            ? localAddress.toString()
                            : ""),
                    targetAddress, localAddress, connectorHandler);
        }
        
        public static <E> Endpoint<E> create(final Object id,
                final E targetAddress, final E localAddress,
                final ConnectorHandler<E> connectorHandler) {
            return new Endpoint<E>() {

                @Override
                public Object getId() {
                    return id;
                }

                @Override
                public GrizzlyFuture<Connection> connect() {
                    return (GrizzlyFuture<Connection>)
                            connectorHandler.connect(targetAddress, localAddress);
                }
            };
        }
    }
}
