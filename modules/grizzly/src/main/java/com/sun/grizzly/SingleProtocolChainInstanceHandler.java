/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly;

/**
 * Implementation of an {@link ProtocolChainInstanceHandler}.
 * Contains single {@link ProtocolChain} instance, which will be returned all
 * the time {@link ProtocolChainInstanceHandler#poll()} will be called.
 *
 * @author Jeanfrancois Arcand
 */
public class SingleProtocolChainInstanceHandler
            implements ProtocolChainInstanceHandler{


    /**
     * Single {@link ProtocolChain} instance.
     */
    protected volatile ProtocolChain protocolChain;


    public SingleProtocolChainInstanceHandler() {
        this(null);
    }

    public SingleProtocolChainInstanceHandler(ProtocolChain protocolChain) {
        this.protocolChain = protocolChain;
    }

    /**
     * Return a {@link ProtocolChain} instance. If no {@link ProtocolChain} was
     * set before, then new instance of {@link DefaultProtocolChain}
     * will be returned.
     * 
     * @return <tt>ProtocolChain</tt>
     */
    public ProtocolChain poll() {
        if (protocolChain == null) {
            synchronized(this) {
                if (protocolChain == null) {
                    protocolChain = new DefaultProtocolChain();
                }
            }
        }
        
        return protocolChain;
    }


    /**
     * Offer (add) an instance of ProtocolChain to this instance pool.
     * {@link StatelessProtocolChainInstanceHandler} has empty implementation
     * of the method.
     *
     * @param protocolChain - <tt>ProtocolChain</tt> to offer / add to the pool
     * @return boolean, true is always returned.
     */
    public boolean offer(ProtocolChain protocolChain) {
        if (this.protocolChain != protocolChain) {
            throw new IllegalArgumentException("Passed protocolChain argument " +
                    "doesn't correspond to one, holding by this handler instance");
        }
        return true;
    }


    /**
     * Get the stateless {@link ProtocolChain} instance, which is always 
     * returned via {@link StatelessProtocolChainInstanceHandler#poll()}.
     * 
     * @return the stateless {@link ProtocolChain} instance, which is always
     * returned via {@link StatelessProtocolChainInstanceHandler#poll()}.
     */
    public ProtocolChain getProtocolChain() {
        return protocolChain;
    }

    /**
     * Set the stateless {@link ProtocolChain} instance, which will be always
     * returned via {@link StatelessProtocolChainInstanceHandler#poll()}.
     *
     * @param protocolChain the stateless {@link ProtocolChain} instance, 
     * which will be always returned via
     * {@link StatelessProtocolChainInstanceHandler#poll()}.
     */
    public void setProtocolChain(ProtocolChain protocolChain) {
        this.protocolChain = protocolChain;
    }
}
