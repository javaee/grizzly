/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.ConnectorHandler;

/**
 * The key object representing single endpoint in the {@link MultiEndpointPool}.
 * 
 * The <tt>EndpointKey</tt> contains the endpoint address, that will be used by
 * a {@link ConnectorHandler} passed to {@link MultiEndpointPool} to establish a
 * new client-side {@link org.glassfish.grizzly.Connection}.
 * Additionally, the <tt>EndpointKey</tt> contains an internal key object
 * ({@link #getInternalKey()}) that is used in the {@link #equals(java.lang.Object)}
 * and {@link #hashCode()} methods.
 * 
 * @author Alexey Stashok
 */
public class EndpointKey<E> {
    private final Object internalKey;
    private final E endpoint;
    
    private final ConnectorHandler<E> connectorHandler;
    /**
     * Construct <tt>EndpointKey<tt> based on the given internalKey and endpoint.
     * 
     * @param internalKey the internal key to be used in {@link #equals(java.lang.Object)}
     *          and {@link #hashCode()} methods
     * @param endpoint the endpoint address, that will be used by
     *          a {@link ConnectorHandler} passed to {@link MultiEndpointPool}
     *          to establish new client-side {@link org.glassfish.grizzly.Connection}
     */
    public EndpointKey(final Object internalKey, final E endpoint) {
        this(internalKey, endpoint, null);
    }

    /**
     * Construct <tt>EndpointKey<tt> based on the given internalKey and endpoint.
     * 
     * @param internalKey the internal key to be used in {@link #equals(java.lang.Object)}
     *          and {@link #hashCode()} methods
     * @param endpoint the endpoint address, that will be used by
     *          a {@link ConnectorHandler} passed to {@link MultiEndpointPool}
     *          to establish new client-side {@link org.glassfish.grizzly.Connection}
     * @param connectorHandler customized {@link ConnectorHandler} this this endpoint
     */
    public EndpointKey(final Object internalKey, final E endpoint,
            final ConnectorHandler<E> connectorHandler) {
        this.internalKey = internalKey;
        this.endpoint = endpoint;
        this.connectorHandler = connectorHandler;
    }

    /**
     * Returns the internal key used in {@link #equals(java.lang.Object)}
     *          and {@link #hashCode()} methods
     */
    public Object getInternalKey() {
        return internalKey;
    }

    /**
     * Returns the endpoint address, used by a {@link ConnectorHandler} passed
     * to {@link MultiEndpointPool} to establish new client-side {@link org.glassfish.grizzly.Connection}
     */
    public E getEndpoint() {
        return endpoint;
    }

    /**
     * Returns a customized {@link ConnectorHandler}, which will be used to
     * create {@link org.glassfish.grizzly.Connection}s to this endpoint.
     */
    public ConnectorHandler<E> getConnectorHandler() {
        return connectorHandler;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return internalKey.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        return obj instanceof EndpointKey && internalKey.equals(
                ((EndpointKey) obj).internalKey);

    }
}
