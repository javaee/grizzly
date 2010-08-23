/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.util.DataStructures;
import java.util.Queue;
import java.util.concurrent.Callable;

/**
 * This interface is responsible of handling {@link ConnectorHandler}
 * instance creation. This interface can be used to implement a connection
 * pooling mechanism.
 *
 * @param E ConnectorHandler implementation this pool will manage
 * @author Jeanfrancois
 */
public interface ConnectorInstanceHandler<E extends ConnectorHandler> {
    
    
    /**
     * Acquire a {@link ConnectorHandler}
     * @return instance of ConnectorHandler
     */
    public E acquire();
    
    
    /**
     * Release a {@link ConnectorHandler}
     * @param connectorHandler release connector handler
     */
    public void release(E connectorHandler);
    
    /**
     * Concurrent Queue ConnectorInstanceHandler implementation
     * @param E ConnectorHandler implementation this pool will manage
     */
    public abstract class ConcurrentQueueConnectorInstanceHandler<E extends ConnectorHandler> implements ConnectorInstanceHandler<E> {
        /**
         * Simple queue used to pool {@link ConnectorHandler}
         */
        private final Queue<E> pool;
        
        
        public ConcurrentQueueConnectorInstanceHandler(){
            pool = (Queue<E>) DataStructures.getCLQinstance();
        }
        
        /**
         * Acquire a {@link ConnectorHandler}
         */
        public E acquire(){
            E connectorHandler = pool.poll();
            if (connectorHandler == null){
                connectorHandler = newInstance();
            }
            return connectorHandler;
        }
        
        /**
         * Release a {@link ConnectorHandler}
         */
        public void release(E connectorHandler){
            pool.offer(connectorHandler);
        }
        
        public abstract E newInstance();
    }
    
    /**
     * Concurrent Queue ConnectorInstanceHandler implementation
     * @param E ConnectorHandler implementation this pool will manage
     */
    public class ConcurrentQueueDelegateCIH<E extends ConnectorHandler> 
            extends ConcurrentQueueConnectorInstanceHandler<E> {
        
        // ConnectorHandler instance creator
        private final Callable<E> delegate;
        
        public ConcurrentQueueDelegateCIH(Callable<E> delegate) {
            this.delegate = delegate;
        }
        
        public E newInstance() {
            try {
                return delegate.call();
            } catch(Exception e) {
                throw new IllegalStateException("Unexpected exception", e);
            }
        }
    }
}
