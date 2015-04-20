/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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
 * Simple {@link Endpoint} implementation.
 * 
 * The <tt>EndpointKey</tt> contains the endpoint address, that will be used by
 * a {@link ConnectorHandler} passed to {@link MultiEndpointPool} to establish a
 * new client-side {@link org.glassfish.grizzly.Connection}.
 * Additionally, the <tt>EndpointKey</tt> contains an internal key object
 * ({@link #getInternalKey()}) that is used in the {@link #equals(java.lang.Object)}
 * and {@link #hashCode()} methods.
 * 
 * @param <E>
 * @author Alexey Stashok
 */
public class EndpointKey<E> extends Endpoint<E> {
    private final Object internalKey;
    private final E targetEndpointAddress;
    private final E localEndpointAddress;
    
    private final ConnectorHandler<E> connectorHandler;

    /**
     * Construct <tt>EndpointKey</tt> based on the given internalKey and endpoint.
     * 
     * @param internalKey the internal key to be used in {@link #equals(java.lang.Object)}
     *          and {@link #hashCode()} methods
     * @param endpointAddress the endpoint address, that will be used by
     *          a {@link ConnectorHandler} passed to {@link MultiEndpointPool}
     *          to establish new client-side {@link org.glassfish.grizzly.Connection}
     */
    public EndpointKey(final Object internalKey, final E endpointAddress) {
        this(internalKey, endpointAddress, null, null);
    }

    /**
     * Construct <tt>EndpointKey</tt> based on the given internalKey, endpoint,
     * and local endpoint.
     *
     * @param internalKey the internal key to be used in {@link #equals(java.lang.Object)}
     *                    and {@link #hashCode()} methods
     * @param endpointAddress the endpoint address, that will be used by
     *                 a {@link ConnectorHandler} passed to {@link MultiEndpointPool}
     *                 to establish new client-side {@link org.glassfish.grizzly.Connection}
     * @param localEndpointAddress the local address that will be used by the
     *                      {@link ConnectorHandler} to bind the local side of
     *                      the outgoing connection.
     */
    public EndpointKey(final Object internalKey, final E endpointAddress,
            final E localEndpointAddress) {
        this(internalKey, endpointAddress, localEndpointAddress, null);
    }

    /**
     * Construct <tt>EndpointKey</tt> based on the given internalKey, endpoint, and
     * {@link ConnectorHandler}.
     * 
     * @param internalKey the internal key to be used in {@link #equals(java.lang.Object)}
     *          and {@link #hashCode()} methods
     * @param endpointAddress the endpoint address, that will be used by
     *          a {@link ConnectorHandler} passed to {@link MultiEndpointPool}
     *          to establish new client-side {@link org.glassfish.grizzly.Connection}
     * @param connectorHandler customized {@link ConnectorHandler} for this endpoint
     */
    public EndpointKey(final Object internalKey, final E endpointAddress,
            final ConnectorHandler<E> connectorHandler) {
        this(internalKey, endpointAddress, null, connectorHandler);
    }

    /**
     *
     * @param internalKey the internal key to be used in {@link #equals(java.lang.Object)}
     *                    and {@link #hashCode()} methods
     * @param endpointAddress the endpoint address, that will be used by
     *                 a {@link ConnectorHandler} passed to {@link MultiEndpointPool}
     *                 to establish new client-side {@link org.glassfish.grizzly.Connection}
     * @param localEndpointAddress the local address that will be used by the
     *                      {@link ConnectorHandler} to bind the local side of
     *                      the outgoing connection.
     * @param connectorHandler customized {@link ConnectorHandler} for this endpoint
     */
    public EndpointKey(final Object internalKey, final E endpointAddress,
                       final E localEndpointAddress,
                       final ConnectorHandler<E> connectorHandler) {
        if (internalKey == null) {
            throw new NullPointerException("internal key can't be null");
        }

        if (endpointAddress == null) {
            throw new NullPointerException("target endpoint address can't be null");
        }

        this.internalKey = internalKey;
        this.targetEndpointAddress = endpointAddress;
        this.localEndpointAddress = localEndpointAddress;
        this.connectorHandler = connectorHandler;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GrizzlyFuture<Connection> connect() {
        return (GrizzlyFuture) connectorHandler.connect(
                targetEndpointAddress, localEndpointAddress);
    }
    
    @Override
    public Object getId() {
        return getInternalKey();
    }
    
    /**
     * @return the internal key used in {@link #equals(java.lang.Object)}
     *          and {@link #hashCode()} methods
     */
    public Object getInternalKey() {
        return internalKey;
    }

    /**
     * @return the endpoint address, used by a {@link ConnectorHandler} passed
     * to {@link MultiEndpointPool} to establish new client-side {@link org.glassfish.grizzly.Connection}
     */
    public E getEndpoint() {
        return targetEndpointAddress;
    }

    /**
     * @return the local endpoint address that be bound to when making the
     *  outgoing connection.
     */
    public E getLocalEndpoint() {
        return localEndpointAddress;
    }

    /**
     * @return a customized {@link ConnectorHandler}, which will be used to
     * create {@link org.glassfish.grizzly.Connection}s to this endpoint.
     */
    public ConnectorHandler<E> getConnectorHandler() {
        return connectorHandler;
    }

    @Override
    public String toString() {
        return "EndpointKey{"
                    + "internalKey=" + internalKey
                    + ", targetEndpointAddress=" + targetEndpointAddress
                    + ", localEndpointAddress=" + localEndpointAddress
                    + ", connectorHandler=" + connectorHandler
                    + "} " + super.toString();
    }
}
